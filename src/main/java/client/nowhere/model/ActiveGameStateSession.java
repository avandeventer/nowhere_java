package client.nowhere.model;

import java.util.ArrayList;
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
    // Who has submitted their own text in each writing phase, keyed by submissionKey (phase + round).
    // Kept for the whole game (transitions don't reset it), so a player who reloads mid-phase knows
    // they already submitted.
    Map<String, List<String>> submittedPlayers;

    public ActiveGameStateSession() {
        this.isPlayerDone = new HashMap<>();
        this.donePhase = new HashMap<>();
        this.submittedPlayers = new HashMap<>();
    }

    public ActiveGameStateSession(String gameCode) {
        this.gameCode = gameCode;
        this.isPlayerDone = new HashMap<>();
        this.donePhase = new HashMap<>();
        this.submittedPlayers = new HashMap<>();
    }

    /** The submittedPlayers key for a writing phase in a round, e.g. "WHAT_HAPPENS_HERE:1". */
    public static String submissionKey(GameState phaseId, Integer roundNumber) {
        return phaseId.name() + ":" + (roundNumber == null ? 0 : roundNumber);
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

    public Map<String, List<String>> getSubmittedPlayers() {
        return submittedPlayers;
    }

    public void setSubmittedPlayers(Map<String, List<String>> submittedPlayers) {
        this.submittedPlayers = submittedPlayers;
    }

    public boolean hasSubmitted(String submissionKey, String authorId) {
        return submittedPlayers != null
                && submittedPlayers.getOrDefault(submissionKey, new ArrayList<>()).contains(authorId);
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
