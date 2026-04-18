package client.nowhere.model;

import java.util.List;

public record CollaborativeTextPhaseInfo(
    GameState phaseId,
    String phaseQuestion,
    String phaseInstructions,
    CollaborativeMode collaborativeMode,
    String collaborativeModeInstructions,
    Story storyToIterateOn,
    PhaseType phaseType,
    Boolean showGameBoard,
    List<TextSubmission> locationVotingSubmissions,
    List<Player> activePlayers,
    List<Player> playersAtLocation
) {}

