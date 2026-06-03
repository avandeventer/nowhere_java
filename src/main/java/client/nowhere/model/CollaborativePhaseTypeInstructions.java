package client.nowhere.model;

public record CollaborativePhaseTypeInstructions(
    String phaseModeInstructions,
    String contributionPhaseInstructions
) {
    public static CollaborativePhaseTypeInstructions empty() {
        return new CollaborativePhaseTypeInstructions("", "");
    }
}
