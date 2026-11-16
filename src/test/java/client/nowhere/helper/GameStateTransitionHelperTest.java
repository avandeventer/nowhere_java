package client.nowhere.helper;

import client.nowhere.dao.ActiveSessionDAO;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.exception.GameStateException;
import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GameStateTransitionHelperTest {

    private static final String GAME_CODE = "GAME12";

    private GameSessionHelper gameSessionHelper;
    private GameSessionDAO gameSessionDAO;
    private ActiveSessionDAO activeSessionDAO;
    private TransitionQueue queue;
    private final List<Runnable> localTasks = new ArrayList<>();

    @BeforeEach
    void setUp() {
        gameSessionHelper = mock(GameSessionHelper.class);
        gameSessionDAO = mock(GameSessionDAO.class);
        activeSessionDAO = mock(ActiveSessionDAO.class);
        queue = mock(TransitionQueue.class);
    }

    private GameStateTransitionHelper helper(TransitionQueue transitionQueue) {
        return new GameStateTransitionHelper(gameSessionHelper, activeSessionDAO, transitionQueue, localTasks::add);
    }

    @Test
    void nextGameState_runsTheTransitionInlineAndReleasesItsLock() {
        GameSession advanced = new GameSession();
        when(activeSessionDAO.lockTransition(GAME_CODE, null, true)).thenReturn(Optional.of("lock"));
        when(gameSessionHelper.updateToNextGameState(GAME_CODE)).thenReturn(advanced);

        assertSame(advanced, helper(queue).nextGameState(GAME_CODE));

        verify(activeSessionDAO).unlockTransition(GAME_CODE, "lock");
    }

    @Test
    void nextGameState_whileATransitionIsRunning_returnsTheCurrentGameWithoutAdvancing() {
        GameSession current = new GameSession();
        when(activeSessionDAO.lockTransition(GAME_CODE, null, true)).thenReturn(Optional.empty());
        when(gameSessionHelper.getGame(GAME_CODE)).thenReturn(current);

        assertSame(current, helper(queue).nextGameState(GAME_CODE));

        verify(gameSessionHelper, never()).updateToNextGameState(anyString());
    }

    @Test
    void nextGameState_releasesItsLockWhenTheTransitionFails() {
        when(activeSessionDAO.lockTransition(GAME_CODE, null, true)).thenReturn(Optional.of("lock"));
        when(gameSessionHelper.updateToNextGameState(GAME_CODE)).thenThrow(new GameStateException("not yet"));

        assertThrows(GameStateException.class, () -> helper(queue).nextGameState(GAME_CODE));

        verify(activeSessionDAO).unlockTransition(GAME_CODE, "lock");
    }

    @Test
    void allPlayersDone_queuesTheLockedTransitionWithoutRunningIt() {
        when(activeSessionDAO.lockTransition(GAME_CODE, GameState.LOCATION_VOTING, false)).thenReturn(Optional.of("lock"));

        helper(queue).allPlayersDone(GAME_CODE, GameState.LOCATION_VOTING);

        verify(queue).enqueue(GAME_CODE, "lock");
        verify(gameSessionHelper, never()).updateToNextGameState(anyString());
    }

    @Test
    void allPlayersDone_whenSomeoneElseLockedIt_doesNothing() {
        when(activeSessionDAO.lockTransition(GAME_CODE, GameState.LOCATION_VOTING, false)).thenReturn(Optional.empty());

        helper(queue).allPlayersDone(GAME_CODE, GameState.LOCATION_VOTING);

        verify(queue, never()).enqueue(anyString(), anyString());
    }

    @Test
    void allPlayersDone_whenQueueingFails_runsTheTransitionInTheRequest() {
        when(activeSessionDAO.lockTransition(GAME_CODE, GameState.LOCATION_VOTING, false)).thenReturn(Optional.of("lock"));
        doThrow(new RuntimeException("queue down")).when(queue).enqueue(anyString(), anyString());
        when(activeSessionDAO.startTransition(GAME_CODE, "lock")).thenReturn(true);

        helper(queue).allPlayersDone(GAME_CODE, GameState.LOCATION_VOTING);

        verify(gameSessionHelper).updateToNextGameState(GAME_CODE);
        verify(activeSessionDAO).unlockTransition(GAME_CODE, "lock");
    }

    @Test
    void allPlayersDone_withoutAQueue_runsTheTransitionOnTheLocalExecutor() {
        when(activeSessionDAO.lockTransition(GAME_CODE, GameState.LOCATION_VOTING, false)).thenReturn(Optional.of("lock"));
        when(activeSessionDAO.startTransition(GAME_CODE, "lock")).thenReturn(true);

        helper(null).allPlayersDone(GAME_CODE, GameState.LOCATION_VOTING);
        verify(gameSessionHelper, never()).updateToNextGameState(anyString());

        localTasks.forEach(Runnable::run);
        verify(gameSessionHelper).updateToNextGameState(GAME_CODE);
        verify(activeSessionDAO).unlockTransition(GAME_CODE, "lock");
    }

    @Test
    void runQueued_forALockAlreadyStartedOrReleased_doesNothing() {
        when(activeSessionDAO.startTransition(GAME_CODE, "lock")).thenReturn(false);

        helper(queue).runQueued(GAME_CODE, "lock");

        verify(gameSessionHelper, never()).updateToNextGameState(anyString());
        verify(activeSessionDAO, never()).unlockTransition(anyString(), anyString());
    }

    @Test
    void runQueued_swallowsAFailedTransitionAfterReleasingItsLock() {
        when(activeSessionDAO.startTransition(GAME_CODE, "lock")).thenReturn(true);
        when(gameSessionHelper.updateToNextGameState(GAME_CODE)).thenThrow(new RuntimeException("boom"));

        assertDoesNotThrow(() -> helper(queue).runQueued(GAME_CODE, "lock"));

        verify(activeSessionDAO).unlockTransition(GAME_CODE, "lock");
    }

    @Test
    void verifyQueuedRequest_withoutAQueue_refusesEveryRequest() {
        assertThrows(IllegalStateException.class, () -> helper(null).verifyQueuedRequest("Bearer anything"));
        verify(queue, never()).verify(any());
    }
}
