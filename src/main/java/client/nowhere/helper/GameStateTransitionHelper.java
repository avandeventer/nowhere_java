package client.nowhere.helper;

import client.nowhere.dao.ActiveSessionDAO;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Starts game state transitions so each runs exactly once (ActiveSessionDAO.lockTransition), whichever
 * request asks first:
 * - PUT /game/next runs it inline and returns the new game, or returns the current game if a
 *   transition is already running.
 * - The request that marks the last player done (POST /votes, GET /voting, PUT
 *   /activeGameStateSession) locks it and hands it to the background, then responds. Clients
 *   follow the new state through Firestore as usual.
 *
 * Background transitions go through Cloud Tasks when a queue is configured (Cloud Run), which calls
 * POST /internal/transitions -> runQueued. Without a queue (local, integration tests) they run on
 * an in-process executor.
 */
@Component
public class GameStateTransitionHelper {

    private final GameSessionHelper gameSessionHelper;
    private final GameSessionDAO gameSessionDAO;
    private final ActiveSessionDAO activeSessionDAO;
    private final TransitionQueue transitionQueue;
    private final Executor localExecutor;

    @Autowired
    public GameStateTransitionHelper(GameSessionHelper gameSessionHelper,
                                 GameSessionDAO gameSessionDAO,
                                 ActiveSessionDAO activeSessionDAO,
                                 ObjectProvider<TransitionQueue> transitionQueue) {
        this(gameSessionHelper, gameSessionDAO, activeSessionDAO, transitionQueue.getIfAvailable(), localExecutor());
    }

    // On Cloud Run without a queue, background threads would get throttled CPU, so run the
    // transition in the request as before (K_SERVICE is set by Cloud Run)
    private static Executor localExecutor() {
        if (System.getenv("K_SERVICE") != null) {
            return Runnable::run;
        }
        return Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "game-state-transition");
            thread.setDaemon(true);
            return thread;
        });
    }

    GameStateTransitionHelper(GameSessionHelper gameSessionHelper,
                          GameSessionDAO gameSessionDAO,
                          ActiveSessionDAO activeSessionDAO,
                          TransitionQueue transitionQueue,
                          Executor localExecutor) {
        this.gameSessionHelper = gameSessionHelper;
        this.gameSessionDAO = gameSessionDAO;
        this.activeSessionDAO = activeSessionDAO;
        this.transitionQueue = transitionQueue;
        this.localExecutor = localExecutor;
    }

    /** PUT /game/next */
    public GameSession nextGameState(String gameCode) {
        Optional<String> lockId = activeSessionDAO.lockTransition(gameCode, null, true);
        if (lockId.isEmpty()) {
            return gameSessionDAO.getGame(gameCode);
        }
        return run(gameCode, lockId.get());
    }

    /** Every player is done in gamePhase: start the transition out of it in the background. */
    public void allPlayersDone(String gameCode, GameState gamePhase) {
        Optional<String> lockId = activeSessionDAO.lockTransition(gameCode, gamePhase, false);
        if (lockId.isEmpty()) {
            return;
        }
        System.out.println("All players are done in " + gamePhase + ", queueing the transition for game " + gameCode);
        if (transitionQueue == null) {
            localExecutor.execute(() -> runQueued(gameCode, lockId.get()));
            return;
        }
        try {
            transitionQueue.enqueue(gameCode, lockId.get());
        } catch (RuntimeException e) {
            // Better a slow response than a game stuck until someone presses next
            System.err.println("Failed to queue the transition for game " + gameCode + ", running it in this request: " + e.getMessage());
            runQueued(gameCode, lockId.get());
        }
    }

    /** Runs a transition handed to the background, unless it was already run or given up. */
    public void runQueued(String gameCode, String lockId) {
        if (!activeSessionDAO.startTransition(gameCode, lockId)) {
            System.out.println("Transition " + lockId + " for game " + gameCode + " already started or released, skipping");
            return;
        }
        try {
            run(gameCode, lockId);
        } catch (RuntimeException e) {
            System.err.println("Background transition " + lockId + " for game " + gameCode + " failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void verifyQueuedRequest(String authorizationHeader) {
        if (transitionQueue == null) {
            throw new IllegalStateException("No transition queue is configured");
        }
        transitionQueue.verify(authorizationHeader);
    }

    // Releasing on failure lets PUT /game/next retry straight away instead of after the lock expires
    private GameSession run(String gameCode, String lockId) {
        try {
            return gameSessionHelper.updateToNextGameState(gameCode);
        } finally {
            activeSessionDAO.unlockTransition(gameCode, lockId);
        }
    }

    @PreDestroy
    public void shutdown() {
        if (localExecutor instanceof ExecutorService executorService) {
            executorService.shutdown();
        }
    }
}
