package client.nowhere.model;

public class ActivePlayerSession {
    String gameCode = "";
    String playerId = "";
    String playerChoiceOptionId = "";
    Story story;
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
        if(!updatedSession.getPlayerChoiceOptionId().isEmpty()) {
            this.playerChoiceOptionId = updatedSession.getPlayerChoiceOptionId();
        }

        if(!updatedSession.getPlayerId().isEmpty()) {
            this.playerId = updatedSession.getPlayerId();
        }

        if(updatedSession.getStory() != null) {
            this.story = updatedSession.getStory();
        }

        this.setNextPlayerTurn = updatedSession.setNextPlayerTurn;
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

}
