package client.nowhere.model;

public class Encounter {
    EncounterLabel encounterLabel;
    String storyId;
    String storyPrompt;

    public Encounter(EncounterLabel encounterLabel, String storyId, String storyPrompt) {
        this.encounterLabel = encounterLabel;
        this.storyId = storyId;
        this.storyPrompt = storyPrompt;
    }

    public EncounterLabel getEncounterLabel() {
        return encounterLabel;
    }

    public void setEncounterLabel(EncounterLabel encounterLabel) {
        this.encounterLabel = encounterLabel;
    }

    public String getStoryId() {
        return storyId;
    }

    public void setStoryId(String storyId) {
        this.storyId = storyId;
    }

    public String getStoryPrompt() {
        return storyPrompt;
    }

    public void setStoryPrompt(String storyPrompt) {
        this.storyPrompt = storyPrompt;
    }
}
