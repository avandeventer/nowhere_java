package client.nowhere.dao;

import client.nowhere.model.ActiveGameStateSession;
import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import client.nowhere.model.Player;
import client.nowhere.model.TransitionLock;
import com.google.cloud.Timestamp;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ActiveSessionDAOTest {

    private static final String GAME_CODE = "GAME12";

    private Firestore db;
    private DocumentReference gameSessionRef;
    private Transaction transaction;
    private ActiveSessionDAO dao;

    @BeforeEach
    void setUp() {
        db = mock(Firestore.class);
        gameSessionRef = mock(DocumentReference.class);
        transaction = mock(Transaction.class);
        CollectionReference gameSessions = mock(CollectionReference.class);
        when(db.collection("gameSessions")).thenReturn(gameSessions);
        when(gameSessions.document(GAME_CODE)).thenReturn(gameSessionRef);
        when(gameSessionRef.update(any(FieldPath.class), any(), any(Object[].class)))
                .thenReturn(ApiFutures.immediateFuture(mock(WriteResult.class)));
        when(db.runTransaction(any())).thenAnswer(invocation -> {
            Transaction.Function<?> function = invocation.getArgument(0);
            return ApiFutures.immediateFuture(function.updateCallback(transaction));
        });
        dao = new ActiveSessionDAO(db);
    }

    @Test
    void markingTheLastPlayerDone_writesOnlyTheirFieldsAndReportsEveryoneDone() {
        // Before the write B isn't done; after it, both are done in LOCATION_VOTING
        readsReturn(game(GameState.LOCATION_VOTING, Map.of("A", "LOCATION_VOTING")),
                game(GameState.LOCATION_VOTING, Map.of("A", "LOCATION_VOTING", "B", "LOCATION_VOTING")));

        boolean allDone = dao.updateActiveGameStateSession(GAME_CODE, GameState.LOCATION_VOTING, List.of("B"), true);

        assertTrue(allDone);
        verify(gameSessionRef).update(
                eq(FieldPath.of("activeGameStateSession", "isPlayerDone", "B")), eq(true),
                eq(FieldPath.of("activeGameStateSession", "donePhase", "B")), eq("LOCATION_VOTING"));
        verify(db, never()).runTransaction(any());
    }

    @Test
    void aDoneMarkLeftOverFromThePreviousPhase_doesNotCountTowardsEveryoneDone() {
        // A's flag was written by a late request from WHAT_HAPPENS_HERE after the reset
        readsReturn(game(GameState.LOCATION_VOTING, Map.of()),
                game(GameState.LOCATION_VOTING, Map.of("A", "WHAT_HAPPENS_HERE", "B", "LOCATION_VOTING")));

        assertFalse(dao.updateActiveGameStateSession(GAME_CODE, GameState.LOCATION_VOTING, List.of("B"), true));
    }

    @Test
    void markPlayersDoneInCheckedState_writesWithoutReadingTheStateFirst() {
        // Only the read after the write happens
        readsReturn(game(GameState.LOCATION_VOTING, Map.of("A", "LOCATION_VOTING", "B", "LOCATION_VOTING")));

        assertTrue(dao.markPlayersDoneInCheckedState(GAME_CODE, GameState.LOCATION_VOTING, List.of("B")));

        verify(db, times(1)).getAll(any(DocumentReference[].class), any(FieldMask.class));
        verify(gameSessionRef).update(
                eq(FieldPath.of("activeGameStateSession", "isPlayerDone", "B")), eq(true),
                eq(FieldPath.of("activeGameStateSession", "donePhase", "B")), eq("LOCATION_VOTING"));
    }

    @Test
    void markSubmitted_addsThePlayerToThatPhaseAndRoundsListOnly() {
        when(gameSessionRef.update(any(FieldPath.class), any(), any(Object[].class)))
                .thenReturn(ApiFutures.immediateFuture(mock(WriteResult.class)));

        dao.markSubmitted(GAME_CODE, "WHAT_HAPPENS_HERE:1", "A");

        verify(gameSessionRef).update(
                eq(FieldPath.of("activeGameStateSession", "submittedPlayers", "WHAT_HAPPENS_HERE:1")),
                eq(FieldValue.arrayUnion("A")));
        verify(db, never()).runTransaction(any());
    }

    @Test
    void markingDoneForAPhaseTheGameHasLeft_writesNothing() {
        readsReturn(game(GameState.WHAT_HAPPENS_HERE, Map.of()));

        assertFalse(dao.updateActiveGameStateSession(GAME_CODE, GameState.LOCATION_VOTING, List.of("B"), true));

        verify(gameSessionRef, never()).update(any(FieldPath.class), any(), any(Object[].class));
    }

    @Test
    void lockTransition_locksTheCurrentStateAndTurnsOnLoading() {
        transactionReads(lockState(GameState.LOCATION_VOTING, null));

        Optional<String> lockId = dao.lockTransition(GAME_CODE, GameState.LOCATION_VOTING, false);

        assertTrue(lockId.isPresent());
        verify(transaction).update(eq(gameSessionRef),
                eq("transitionLock"), argThat((TransitionLock lock) -> lock.getLockId().equals(lockId.get())
                        && lock.getFromState().equals("LOCATION_VOTING") && lock.getStartedAt() == null),
                eq("activePlayerSession.nextGameStateLoading"), eq(true));
    }

    @Test
    void lockTransition_failsWhileAnotherLockOnTheCurrentStateIsLive() {
        transactionReads(lockState(GameState.LOCATION_VOTING, lock("other", "LOCATION_VOTING", Instant.now())));

        assertTrue(dao.lockTransition(GAME_CODE, null, true).isEmpty());
        verify(transaction, never()).update(any(DocumentReference.class), anyString(), any(), any(Object[].class));
    }

    @Test
    void lockTransition_ignoresAnExpiredLockOrOneForAnEarlierState() {
        Instant expired = Instant.now().minus(ActiveSessionDAO.TRANSITION_LOCK_TTL).minus(Duration.ofSeconds(1));
        transactionReads(lockState(GameState.LOCATION_VOTING, lock("dead", "LOCATION_VOTING", expired)));
        assertTrue(dao.lockTransition(GAME_CODE, null, true).isPresent());

        transactionReads(lockState(GameState.LOCATION_VOTING, lock("finished", "WHAT_HAPPENS_HERE", Instant.now())));
        assertTrue(dao.lockTransition(GAME_CODE, null, true).isPresent());
    }

    @Test
    void lockTransition_withAnExpectedStateTheGameHasLeft_locksNothing() {
        transactionReads(lockState(GameState.WHAT_HAPPENS_HERE, null));

        assertTrue(dao.lockTransition(GAME_CODE, GameState.LOCATION_VOTING, false).isEmpty());
    }

    @Test
    void startTransition_startsAnUnstartedLockOnlyOnce() {
        transactionReads(lockState(GameState.LOCATION_VOTING, lock("mine", "LOCATION_VOTING", Instant.now())));
        assertTrue(dao.startTransition(GAME_CODE, "mine"));
        assertFalse(dao.startTransition(GAME_CODE, "someone-elses"));

        TransitionLock started = lock("mine", "LOCATION_VOTING", Instant.now());
        started.setStartedAt(Timestamp.now());
        transactionReads(lockState(GameState.LOCATION_VOTING, started));
        assertFalse(dao.startTransition(GAME_CODE, "mine"));
    }

    @Test
    void unlockTransition_removesOnlyItsOwnLock() {
        transactionReads(lockState(GameState.LOCATION_VOTING, lock("newer", "LOCATION_VOTING", Instant.now())));
        dao.unlockTransition(GAME_CODE, "older");
        verify(transaction, never()).update(any(DocumentReference.class), anyString(), any(), any(Object[].class));

        dao.unlockTransition(GAME_CODE, "newer");
        verify(transaction).update(gameSessionRef,
                "transitionLock", FieldValue.delete(),
                "activePlayerSession.nextGameStateLoading", false);
    }

    // Masked db.getAll reads return these games in order (the last one repeats)
    private void readsReturn(GameSession... games) {
        List<DocumentSnapshot> snapshots = new ArrayList<>();
        for (GameSession game : games) {
            snapshots.add(snapshotOf(game));
        }
        var stubbing = when(db.getAll(any(DocumentReference[].class), any(FieldMask.class)));
        for (DocumentSnapshot snapshot : snapshots) {
            stubbing = stubbing.thenReturn(ApiFutures.immediateFuture(List.of(snapshot)));
        }
    }

    private void transactionReads(GameSession game) {
        DocumentSnapshot snapshot = snapshotOf(game);
        when(transaction.getAll(any(DocumentReference[].class), any(FieldMask.class)))
                .thenReturn(ApiFutures.immediateFuture(List.of(snapshot)));
    }

    private static DocumentSnapshot snapshotOf(GameSession game) {
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
        when(snapshot.exists()).thenReturn(true);
        when(snapshot.toObject(GameSession.class)).thenReturn(game);
        return snapshot;
    }

    private static GameSession game(GameState state, Map<String, String> donePhase) {
        GameSession game = new GameSession();
        game.setGameState(state);
        List<Player> players = new ArrayList<>();
        for (String authorId : List.of("A", "B")) {
            Player player = new Player();
            player.setAuthorId(authorId);
            players.add(player);
        }
        game.setPlayers(players);
        ActiveGameStateSession session = new ActiveGameStateSession();
        session.setDonePhase(new HashMap<>(donePhase));
        game.setActiveGameStateSession(session);
        return game;
    }

    private static GameSession lockState(GameState state, TransitionLock lock) {
        GameSession game = new GameSession();
        game.setGameState(state);
        game.setTransitionLock(lock);
        return game;
    }

    private static TransitionLock lock(String lockId, String fromState, Instant lockedAt) {
        return new TransitionLock(lockId, fromState, Timestamp.ofTimeSecondsAndNanos(lockedAt.getEpochSecond(), lockedAt.getNano()), null);
    }
}
