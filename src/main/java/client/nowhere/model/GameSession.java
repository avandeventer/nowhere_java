package client.nowhere.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GameSession {

    String gameCode;
    List<Player> players;
    GameState gameState;
    ActivePlayerSession activePlayerSession;
    ActiveGameStateSession activeGameStateSession;
    List<Story> stories;
    AdventureMap adventureMap;
    boolean didWeSucceed = false;
    int totalPointsTowardsVictory = 0;
    List<Ending> endings;
    List<Story> rituals;
    String userProfileId;
    String saveGameId;
    Integer storiesToWritePerRound = 1;
    Integer storiesToPlayPerRound = 1;
    
    // Collaborative text phases for world-building
    Map<String, CollaborativeTextPhase> collaborativeTextPhases;

    public GameSession() {
        if(this.activeGameStateSession == null) {
            this.activeGameStateSession = new ActiveGameStateSession(this.gameCode);
        }
        this.collaborativeTextPhases = new HashMap<>();
        this.saveGameId = "";
    }

    public GameSession(String gameCode) {
        this.gameCode = gameCode;
        this.players = new ArrayList<>();
        this.activePlayerSession = new ActivePlayerSession();
        this.activeGameStateSession = new ActiveGameStateSession(gameCode);
        this.adventureMap = new AdventureMap();
        this.rituals = new ArrayList<>();
        this.collaborativeTextPhases = new HashMap<>();
    }

    public GameSession(String gameCode, GameState gameState) {
        this.gameCode = gameCode;
        this.gameState = gameState;
        this.players = new ArrayList<>();
        this.activePlayerSession = new ActivePlayerSession();
        this.activeGameStateSession = new ActiveGameStateSession();
    }

    public String getGameCode() {
        return gameCode;
    }

    public void setGameCode(String gameCode) {
        this.gameCode = gameCode;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public void setPlayers(List<Player> players) {
        this.players = players;
    }

    public GameState getGameState() {
        return gameState;
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    public void setGameStateToNext() {
        this.gameState = this.gameState.getNextGameState();
    }

    public ActivePlayerSession getActivePlayerSession() {
        return activePlayerSession;
    }

    public void setActivePlayerSession(ActivePlayerSession activePlayerSession) {
        this.activePlayerSession = activePlayerSession;
    }

    public List<Story> getStories() {
        return stories;
    }

    public void setStories(List<Story> stories) {
        this.stories = stories;
    }

    public ActiveGameStateSession getActiveGameStateSession() {
        return activeGameStateSession;
    }

    public void setActiveGameStateSession(ActiveGameStateSession activeGameStateSession) {
        this.activeGameStateSession = activeGameStateSession;
    }

    public AdventureMap getAdventureMap() {
        return adventureMap;
    }

    public void setAdventureMap(AdventureMap adventureMap) {
        this.adventureMap = adventureMap;
    }

    public boolean getDidWeSucceed() {
        return didWeSucceed;
    }

    public void setDidWeSucceed(boolean didWeSucceed) {
        this.didWeSucceed = didWeSucceed;
    }

    public List<Ending> getEndings() {
        return endings;
    }

    public void setEndings(List<Ending> endings) {
        this.endings = endings;
    }

    public String getUserProfileId() {
        return userProfileId;
    }

    public void setUserProfileId(String userProfileId) {
        this.userProfileId = userProfileId;
    }

    public String getSaveGameId() {
        return saveGameId;
    }

    public void setSaveGameId(String saveGameId) {
        this.saveGameId = saveGameId;
    }

    public boolean isAfterGameState(GameState gameState) {
        return this.getGameState().ordinal() > gameState.ordinal();
    }

    public Integer getStoriesToWritePerRound() {
        return storiesToWritePerRound;
    }

    public void setStoriesToWritePerRound(Integer storiesToWritePerRound) {
        this.storiesToWritePerRound = storiesToWritePerRound;
    }

    public Integer getStoriesToPlayPerRound() {
        return storiesToPlayPerRound;
    }

    public void setStoriesToPlayPerRound(Integer storiesToPlayPerRound) {
        this.storiesToPlayPerRound = storiesToPlayPerRound;
    }

    public List<Story> getRituals() {
        return rituals;
    }

    public void setRituals(List<Story> rituals) {
        this.rituals = rituals;
    }

    public int getTotalPointsTowardsVictory() {
        return totalPointsTowardsVictory;
    }

    public void setTotalPointsTowardsVictory(int totalPointsTowardsVictory) {
        this.totalPointsTowardsVictory = totalPointsTowardsVictory;
    }

    public Map<String, CollaborativeTextPhase> getCollaborativeTextPhases() {
        return collaborativeTextPhases;
    }

    public void setCollaborativeTextPhases(Map<String, CollaborativeTextPhase> collaborativeTextPhases) {
        this.collaborativeTextPhases = collaborativeTextPhases;
    }

    public CollaborativeTextPhase getCollaborativeTextPhase(String phaseId) {
        return collaborativeTextPhases.get(phaseId);
    }

    public void addCollaborativeTextPhase(CollaborativeTextPhase phase) {
        this.collaborativeTextPhases.put(phase.getPhaseId(), phase);
    }

    public void skipAdventureMapCreateMode() {
        this.gameState = GameState.PREAMBLE;
    }
}
