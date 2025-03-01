package client.nowhere.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActiveGameStateSession {

    String gameCode;
    Map<String, Boolean> isPlayerDone;

    public ActiveGameStateSession() {
        this.isPlayerDone = new HashMap<>();
    }

    public ActiveGameStateSession(String gameCode) {
        this.gameCode = gameCode;
        this.isPlayerDone = new HashMap<>();
    }

    public Map<String, Boolean> getIsPlayerDone() {
        return isPlayerDone;
    }

    public void setIsPlayerDone(Map<String, Boolean> isPlayerDone) {
        this.isPlayerDone = isPlayerDone;
    }

    public void resetPlayerDoneStatus(List<Player> players) {

        if (this.isPlayerDone == null) {
            this.isPlayerDone = new HashMap<>();
        }

        for(Player player : players) {
            this.isPlayerDone.put(player.getAuthorId(), false);
        }
    }

    public String getGameCode() {
        return gameCode;
    }

    public void setGameCode(String gameCode) {
        this.gameCode = gameCode;
    }

    public void update(ActiveGameStateSession activeSession) { }
}
