package client.nowhere.model;

import java.util.List;

public class ActivePlayerSession {
    String gameCode = "";
    String playerId = "";
    String playerChoiceOptionId = "";
    String selectedLocationOptionId = "";
    Story story;
    List<String> outcomeDisplay;
    List<String> locationOutcomeDisplay;
    Story ritualStory;
    boolean setNextPlayerTurn;

    public ActivePlayerSession() { }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getPlayerChoiceOptionId() {
        return playerChoiceOptionId;
    }

    public void setPlayerChoiceOptionId(String playerChoiceOptionId) {
        this.playerChoiceOptionId = playerChoiceOptionId;
    }

    public void update(ActivePlayerSession updatedSession) {
        this.playerChoiceOptionId = updatedSession.getPlayerChoiceOptionId();
        this.playerId = updatedSession.getPlayerId();
        this.story = updatedSession.getStory();
        this.outcomeDisplay = updatedSession.getOutcomeDisplay();
        this.setNextPlayerTurn = updatedSession.setNextPlayerTurn;
        this.selectedLocationOptionId = updatedSession.selectedLocationOptionId;
        this.locationOutcomeDisplay = updatedSession.getLocationOutcomeDisplay();
    }

    public Story getStory() {
        return story;
    }

    public void setStory(Story story) {
        this.story = story;
    }

    public String getGameCode() {
        return gameCode;
    }

    public void setGameCode(String gameCode) {
        this.gameCode = gameCode;
    }

    public boolean isSetNextPlayerTurn() {
        return setNextPlayerTurn;
    }

    public void setSetNextPlayerTurn(boolean setNextPlayerTurn) {
        this.setNextPlayerTurn = setNextPlayerTurn;
    }

    public List<String> getOutcomeDisplay() {
        return outcomeDisplay;
    }

    public void setOutcomeDisplay(List<String> outcomeDisplay) {
        this.outcomeDisplay = outcomeDisplay;
    }

    public String getSelectedLocationOptionId() {
        return selectedLocationOptionId;
    }

    public void setSelectedLocationOptionId(String selectedLocationOptionId) {
        this.selectedLocationOptionId = selectedLocationOptionId;
    }

    public List<String> getLocationOutcomeDisplay() {
        return locationOutcomeDisplay;
    }

    public void setLocationOutcomeDisplay(List<String> locationOutcomeDisplay) {
        this.locationOutcomeDisplay = locationOutcomeDisplay;
    }

    public Story getRitualStory() {
        return ritualStory;
    }

    public void setRitualStory(Story ritualStory) {
        this.ritualStory = ritualStory;
    }
}
