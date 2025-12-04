package client.nowhere.model;

public record CollaborativeTextPhaseInfo(
    GameState phaseId,
    String phaseQuestion,
    String phaseInstructions,
    CollaborativeMode collaborativeMode,
    String collaborativeModeInstructions,
    Story storyToIterateOn,
    PhaseType phaseType,
    Boolean showGameBoard
) {}

