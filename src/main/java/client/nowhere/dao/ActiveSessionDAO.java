package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.ActiveGameStateSession;
import client.nowhere.model.ActivePlayerSession;
import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldMask;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.common.util.concurrent.MoreExecutors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
public class ActiveSessionDAO {

    private final Firestore db;

    @Autowired
    public ActiveSessionDAO(Firestore db) {
        this.db = db;
    }

    /**
     * Blind, fire-and-forget write of just the nextGameStateLoading flag. Unlike
     * updateActivePlayerSession, this does not read the document first - it targets the
     * nested field directly so the loading indicator can flip for clients without waiting
     * on a full GameSession fetch/deserialize.
     */
    public void setNextGameStateLoading(String gameCode, boolean isLoading) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
        ApiFuture<WriteResult> result = gameSessionRef.update("activePlayerSession.nextGameStateLoading", isLoading);
        ApiFutures.addCallback(result, new ApiFutureCallback<WriteResult>() {
            @Override
            public void onFailure(Throwable throwable) {
                System.out.println("Failed to set nextGameStateLoading for game " + gameCode + ": " + throwable.getMessage());
            }

            @Override
            public void onSuccess(WriteResult writeResult) {
                // No-op: fire-and-forget, nothing downstream depends on this write completing.
            }
        }, MoreExecutors.directExecutor());
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
     * Marks every player in authorIds done for gamePhase in one transaction, and returns true if
     * every player is now done (the caller then progresses the game). Players already done are
     * left alone. Returns false without writing if the game has already moved past gamePhase.
     *
     * The transaction reads only the fields it needs: the whole GameSession document is several
     * hundred KB once stories pile up, and every player in a phase runs this at about the same
     * time, so reading and deserializing all of it on every attempt made this the slowest call in
     * POST /collaborativeText/votes and GET /collaborativeText/voting.
     */
    public boolean updateActiveGameStateSession(String gameCode, GameState gamePhase, Collection<String> authorIds, boolean isDone) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
        FieldMask doneCheckFields = FieldMask.of("gameState", "players", "activeGameStateSession");

        try {
            // Use Firestore transaction to ensure atomicity and prevent race conditions
            return db.runTransaction(transaction -> {
                DocumentSnapshot gameSession = transaction.getAll(new DocumentReference[]{gameSessionRef}, doneCheckFields).get().getFirst();
                GameSession game = FirestoreDAOUtil.mapGameSession(gameSession);
                ActiveGameStateSession activeSessionToUpdate = game.getActiveGameStateSession();

                if (!gamePhase.equals(game.getGameState())) {
                    System.out.println("Players " + authorIds + " update skipped - game phase mismatch. Expected: " + gamePhase + ", Current: " + game.getGameState());
                    return false; // No progression needed
                }

                boolean changed = false;
                for (String authorId : authorIds) {
                    // Idempotency: players already done are skipped
                    if (Boolean.TRUE.equals(activeSessionToUpdate.getIsPlayerDone().get(authorId))) {
                        System.out.println("Player " + authorId + " already marked as done, skipping duplicate update");
                    } else if (isDone) {
                        activeSessionToUpdate.getIsPlayerDone().put(authorId, true);
                        System.out.println("Player " + authorId + " marked as done");
                        changed = true;
                    }
                }

                // Update only the active game state session
                if (changed) {
                    transaction.update(gameSessionRef, "activeGameStateSession", activeSessionToUpdate);
                }
                // Don't progress here - the helper layer progresses the game when this is true
                return game.areAllPlayersDone();
            }).get();
            
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("Failed to update game state session", e);
        }
    }
}
