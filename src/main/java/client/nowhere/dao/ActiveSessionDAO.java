package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.ActivePlayerSession;
import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import client.nowhere.model.TransitionLock;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldMask;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

@Component
public class ActiveSessionDAO {

    // A transition takes seconds; a lock older than this belongs to one that died mid-way
    static final Duration TRANSITION_LOCK_TTL = Duration.ofMinutes(2);

    private final Firestore db;

    @Autowired
    public ActiveSessionDAO(Firestore db) {
        this.db = db;
    }

    public ActivePlayerSession updateActivePlayerSession(String gameCode, ActivePlayerSession activeSession) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);

        ActivePlayerSession activeSessionToUpdate = new ActivePlayerSession();
        try {
            DocumentSnapshot gameSession = FirestoreDAOUtil.getGameSession(gameSessionRef);
            GameSession game = FirestoreDAOUtil.mapGameSession(gameSession);
            activeSessionToUpdate = game.getActivePlayerSession();
            activeSessionToUpdate.update(activeSession);

            ApiFuture<WriteResult> result = gameSessionRef.update("activePlayerSession", activeSessionToUpdate);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
            System.out.println("Object " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue reading the game session", e);
        }
        return activeSessionToUpdate;
    }

    public boolean updateActiveGameStateSession(String gameCode, GameState gamePhase, String authorId, boolean isDone) {
        return updateActiveGameStateSession(gameCode, gamePhase, List.of(authorId), isDone);
    }

    /**
     * Marks every player in authorIds done for gamePhase, and returns true if every player is now
     * done in the game's current state (the caller then locks and starts the transition). Returns
     * false without writing if the game has already moved past gamePhase.
     *
     * No transaction: every player in a phase marks themselves done at about the same time, and a
     * transaction on the one game document made them queue behind each other (~6s for the last
     * LOCATION_VOTING voter). Instead each request blindly writes just its own players' fields, then
     * reads the done flags back. Two requests can both see everyone done; lockTransition lets only
     * one of them start the transition. A write that lands after the game moved on is ignored by the
     * "everyone done" check because it is stamped with gamePhase (ActiveGameStateSession.donePhase).
     */
    public boolean updateActiveGameStateSession(String gameCode, GameState gamePhase, Collection<String> authorIds, boolean isDone) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
        GameSession before = readFields(gameSessionRef, "gameState");
        if (!gamePhase.equals(before.getGameState())) {
            System.out.println("Players " + authorIds + " update skipped - game phase mismatch. Expected: " + gamePhase + ", Current: " + before.getGameState());
            return false;
        }
        return markDoneAndCheck(gameSessionRef, gamePhase, authorIds, isDone);
    }

    /**
     * updateActiveGameStateSession without its first read, for a caller that read the game in
     * gamePhase moments ago (VotingHelper). If the game moved on in between, the stale flags are
     * stamped with gamePhase, so they don't count towards the new state.
     */
    public boolean markPlayersDoneInCheckedState(String gameCode, GameState gamePhase, Collection<String> authorIds) {
        return markDoneAndCheck(db.collection("gameSessions").document(gameCode), gamePhase, authorIds, true);
    }

    private boolean markDoneAndCheck(DocumentReference gameSessionRef, GameState gamePhase, Collection<String> authorIds, boolean isDone) {
        if (isDone && !authorIds.isEmpty()) {
            List<Object> fieldsAndValues = new ArrayList<>();
            for (String authorId : authorIds) {
                fieldsAndValues.add(FieldPath.of("activeGameStateSession", "isPlayerDone", authorId));
                fieldsAndValues.add(true);
                fieldsAndValues.add(FieldPath.of("activeGameStateSession", "donePhase", authorId));
                fieldsAndValues.add(gamePhase.name());
            }
            await(gameSessionRef.update((FieldPath) fieldsAndValues.get(0), fieldsAndValues.get(1),
                    fieldsAndValues.subList(2, fieldsAndValues.size()).toArray()));
            System.out.println("Players " + authorIds + " marked as done in " + gamePhase);
        }

        GameSession after = readFields(gameSessionRef, "gameState", "players", "activeGameStateSession");
        return gamePhase.equals(after.getGameState()) && after.areAllPlayersDoneInCurrentState();
    }

    /**
     * Records that authorId submitted their own text in the phase submissionKey names
     * (ActiveGameStateSession.submissionKey). A blind arrayUnion of one field, so players don't
     * contend with each other and a repeat submission changes nothing.
     */
    public void markSubmitted(String gameCode, String submissionKey, String authorId) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
        await(gameSessionRef.update(FieldPath.of("activeGameStateSession", "submittedPlayers", submissionKey),
                FieldValue.arrayUnion(authorId)));
    }

    /**
     * Locks the transition out of the game's current state, so only one request runs it. With
     * expectedState set, locks only if the game is still in that state. Fails (empty) while
     * another live lock for the current state exists. Also turns on the clients' loading indicator.
     *
     * started=true is for a caller that runs the transition itself right away (PUT /game/next);
     * false for one handed to the background, which calls startTransition when it actually begins.
     */
    public Optional<String> lockTransition(String gameCode, GameState expectedState, boolean started) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
        return await(db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.getAll(new DocumentReference[]{gameSessionRef}, FieldMask.of("gameState", "transitionLock")).get().getFirst();
            GameSession game = FirestoreDAOUtil.mapGameSession(snapshot);
            if (game.getGameState() == null || (expectedState != null && !expectedState.equals(game.getGameState()))) {
                return Optional.<String>empty();
            }
            if (isLiveLock(game.getTransitionLock(), game.getGameState())) {
                System.out.println("Transition out of " + game.getGameState() + " in game " + gameCode + " is already locked (" + game.getTransitionLock().getLockId() + ")");
                return Optional.<String>empty();
            }
            Timestamp now = Timestamp.now();
            String lockId = UUID.randomUUID().toString();
            TransitionLock lock = new TransitionLock(lockId, game.getGameState().name(), now, started ? now : null);
            transaction.update(gameSessionRef,
                    "transitionLock", lock,
                    "activePlayerSession.nextGameStateLoading", true);
            return Optional.of(lockId);
        }));
    }

    /**
     * Marks a background transition's lock as started. Returns false if the lock is gone (the
     * transition failed and released it, or a later one replaced it) or already started (a
     * redelivered Cloud Task), in which case the caller must not run the transition.
     */
    public boolean startTransition(String gameCode, String lockId) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
        return await(db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.getAll(new DocumentReference[]{gameSessionRef}, FieldMask.of("transitionLock")).get().getFirst();
            TransitionLock lock = FirestoreDAOUtil.mapGameSession(snapshot).getTransitionLock();
            if (lock == null || !lockId.equals(lock.getLockId()) || lock.getStartedAt() != null) {
                return false;
            }
            transaction.update(gameSessionRef, "transitionLock.startedAt", Timestamp.now());
            return true;
        }));
    }

    /** Removes the lock once its transition has finished or failed, if it is still that lock. */
    public void unlockTransition(String gameCode, String lockId) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
        await(db.runTransaction(transaction -> {
            DocumentSnapshot snapshot = transaction.getAll(new DocumentReference[]{gameSessionRef}, FieldMask.of("transitionLock")).get().getFirst();
            TransitionLock lock = FirestoreDAOUtil.mapGameSession(snapshot).getTransitionLock();
            if (lock != null && lockId.equals(lock.getLockId())) {
                transaction.update(gameSessionRef,
                        "transitionLock", FieldValue.delete(),
                        "activePlayerSession.nextGameStateLoading", false);
            }
            return null;
        }));
    }

    static boolean isLiveLock(TransitionLock lock, GameState currentState) {
        return lock != null
                && currentState.name().equals(lock.getFromState())
                && lock.getLockedAt() != null
                && lock.getLockedAt().toDate().toInstant().plus(TRANSITION_LOCK_TTL).isAfter(Instant.now());
    }

    private GameSession readFields(DocumentReference gameSessionRef, String... fields) {
        DocumentSnapshot snapshot = await(db.getAll(new DocumentReference[]{gameSessionRef}, FieldMask.of(fields))).getFirst();
        return FirestoreDAOUtil.mapGameSession(snapshot);
    }

    private static <T> T await(ApiFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResourceException("Interrupted while updating the game state session", e);
        } catch (ExecutionException e) {
            throw new ResourceException("Failed to update game state session", e);
        }
    }
}
