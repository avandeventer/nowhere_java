package client.nowhere.model;

/**
 * Record to hold base phase information including all three game states
 */
public record PhaseBaseInfo(
    String phaseQuestion,
    String baseInstructions,
    CollaborativeMode collaborativeMode,
    GameState collaboratingState,
    GameState votingState,
    GameState winningState
) {}

