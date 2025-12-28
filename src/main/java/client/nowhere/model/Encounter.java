package client.nowhere.model;

public class Encounter {
    EncounterLabel encounterLabel;
    String storyId;
    String storyPrompt;
    EncounterType encounterType;
    boolean visited;

    public Encounter() {
        this.encounterLabel = new EncounterLabel();
        this.storyId = "";
        this.storyPrompt = "";
        this.encounterType = EncounterType.NORMAL;
        this.visited = false;
    }

    public Encounter(EncounterLabel encounterLabel, EncounterType encounterType, String storyId, String storyPrompt) {
        this.encounterLabel = encounterLabel;
        this.storyId = storyId;
        this.encounterType =  encounterType;
        this.storyPrompt = storyPrompt;
        this.visited = false;
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

    public boolean isVisited() {
        return visited;
    }

    public void setVisited(boolean visited) {
        this.visited = visited;
    }

    public EncounterType getEncounterType() {
        return encounterType;
    }

    public void setEncounterType(EncounterType encounterType) {
        this.encounterType = encounterType;
    }
}
