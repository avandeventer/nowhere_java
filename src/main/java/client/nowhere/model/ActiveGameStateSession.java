package client.nowhere.model;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ActiveGameStateSession {

    String gameCode;
    Map<String, Boolean> isPlayerDone;
    // The phase each player was marked done in. Done flags are blind writes (no transaction), so a
    // late write from the previous phase can land after the reset; only entries matching the current
    // gameState count towards "everyone is done". isPlayerDone stays for the TV's progress display.
    Map<String, String> donePhase;

    public ActiveGameStateSession() {
        this.isPlayerDone = new HashMap<>();
        this.donePhase = new HashMap<>();
    }

    public ActiveGameStateSession(String gameCode) {
        this.gameCode = gameCode;
        this.isPlayerDone = new HashMap<>();
        this.donePhase = new HashMap<>();
    }

    public Map<String, Boolean> getIsPlayerDone() {
        return isPlayerDone;
    }

    public void setIsPlayerDone(Map<String, Boolean> isPlayerDone) {
        this.isPlayerDone = isPlayerDone;
    }

    public Map<String, String> getDonePhase() {
        return donePhase;
    }

    public void setDonePhase(Map<String, String> donePhase) {
        this.donePhase = donePhase;
    }

    public void resetPlayerDoneStatus(List<Player> players) {

        if (this.isPlayerDone == null) {
            this.isPlayerDone = new HashMap<>();
        }

        for(Player player : players) {
            this.isPlayerDone.put(player.getAuthorId(), false);
        }
        this.donePhase = new HashMap<>();
    }

    public String getGameCode() {
        return gameCode;
    }

    public void setGameCode(String gameCode) {
        this.gameCode = gameCode;
    }

    public void update(ActiveGameStateSession activeSession) { }

}
