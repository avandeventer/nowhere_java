package client.nowhere.model;

import java.util.*;

public class ActivePlayerSession {
    String gameCode = "";
    String playerId = "";
    String playerChoiceOptionId = "";
    String selectedLocationOptionId = "";
    Story story;
    Location location;
    List<String> outcomeDisplay;
    List<String> locationOutcomeDisplay;
    Story ritualStory;
    boolean setNextPlayerTurn;
    Map<String, Boolean> isPlayerDoneWithTurn;
    RepercussionOutput repercussions;
    boolean startTimer;

    public ActivePlayerSession() {
        this.isPlayerDoneWithTurn = new HashMap<>();
        this.repercussions = new RepercussionOutput();
        this.startTimer = false;
    }

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
        this.location = updatedSession.getLocation();
        this.outcomeDisplay = updatedSession.getOutcomeDisplay();
        this.setNextPlayerTurn = updatedSession.isSetNextPlayerTurn();
        this.selectedLocationOptionId = updatedSession.getSelectedLocationOptionId();
        this.locationOutcomeDisplay = updatedSession.getLocationOutcomeDisplay();
        this.repercussions = updatedSession.getRepercussions();
        this.startTimer = updatedSession.isStartTimer();
        if (
            updatedSession.getIsPlayerDoneWithTurn() != null
            && updatedSession.getIsPlayerDoneWithTurn().size() > 0
        ) {
            this.isPlayerDoneWithTurn = updatedSession.getIsPlayerDoneWithTurn();
        }
    }

    public void setFirstPlayerTurn(List<Player> players) {
        String firstPlayerTurnId = players.stream()
                .min(Comparator.comparing(Player::getJoinedAt)).get().getAuthorId();
        setPlayerId(firstPlayerTurnId);
    }

    public Story getStory() {
        return story;
    }

    public void setStory(Story story) {
        this.story = story;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
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

    public void resetActivePlayerSession() {
        this.story = new Story();
        this.location = null;
        this.playerChoiceOptionId = "";
        this.outcomeDisplay = new ArrayList<>();
        this.playerId = "";
        this.setNextPlayerTurn = false;
        this.selectedLocationOptionId = "";
        this.locationOutcomeDisplay = new ArrayList<>();
        this.startTimer = false;
    }

    public void resetPlayerDoneWithTurn(List<Player> players) {
        if (this.isPlayerDoneWithTurn == null) {
            this.isPlayerDoneWithTurn = new HashMap<>();
        }

        for(Player player : players) {
            this.isPlayerDoneWithTurn.put(player.getAuthorId(), false);
        }
    }

    public Map<String, Boolean> getIsPlayerDoneWithTurn() {
        return isPlayerDoneWithTurn;
    }

    public void setIsPlayerDoneWithTurn(Map<String, Boolean> isPlayerDoneWithTurn) {
        this.isPlayerDoneWithTurn = isPlayerDoneWithTurn;
    }

    public RepercussionOutput getRepercussions() {
        return repercussions;
    }

    public void setRepercussions(RepercussionOutput repercussions) {
        this.repercussions = repercussions;
    }

    public boolean isStartTimer() {
        return startTimer;
    }

    public void setStartTimer(boolean startTimer) {
        this.startTimer = startTimer;
    }
}
