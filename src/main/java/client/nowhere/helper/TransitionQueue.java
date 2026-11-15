package client.nowhere.helper;

/**
 * Hands a locked game state transition to something that will call POST /internal/transitions with it,
 * so the request that locked it can respond straight away. Only present when a queue is
 * configured (CloudTasksTransitionQueue); without one GameStateTransitionHelper runs it in-process.
 */
public interface TransitionQueue {

    void enqueue(String gameCode, String lockId);

    /** Throws if the request to /internal/transitions did not come from this queue. */
    void verify(String authorizationHeader);
}
