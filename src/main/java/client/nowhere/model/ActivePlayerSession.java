package client.nowhere.model;

public class ActivePlayerSession {
    String gameCode;
    String playerId;
    String playerChoiceOptionId;
    Story story;

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
}
