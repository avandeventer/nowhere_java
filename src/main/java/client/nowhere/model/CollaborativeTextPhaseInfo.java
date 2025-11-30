package client.nowhere.model;

public record CollaborativeTextPhaseInfo(
    String phaseQuestion,
    String phaseInstructions,
    CollaborativeMode collaborativeMode,
    String collaborativeModeInstructions,
    Story storyToIterateOn,
    PhaseType phaseType,
    Boolean showGameBoard
) {}

