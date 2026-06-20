package client.nowhere.model;

public record CollaborativePhaseTypeInstructions(
    String phaseModeInstructions,
    String contributionPhaseInstructions,
    int characterLimit
) {
    public static CollaborativePhaseTypeInstructions empty() {
        return new CollaborativePhaseTypeInstructions("", "", 150);
    }
}
