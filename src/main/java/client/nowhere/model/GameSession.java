package client.nowhere.model;

import java.util.ArrayList;
import java.util.List;

public class GameSession {

    String gameCode;
    List<Player> players;
    GameState gameState;
    ActivePlayerSession activePlayerSession;
    ActiveGameStateSession activeGameStateSession;
    List<Story> stories;
    AdventureMap adventureMap;
    boolean didWeSucceed = false;
    List<Ending> endings;
    String userProfileId;
    String saveGameId;
    Integer storiesToWritePerRound = 1;

    public GameSession() {
        if(this.activeGameStateSession == null) {
            this.activeGameStateSession = new ActiveGameStateSession(this.gameCode);
        }
    }

    public GameSession(String gameCode) {
        this.gameCode = gameCode;
        this.players = new ArrayList<>();
        this.activePlayerSession = new ActivePlayerSession();
        this.activeGameStateSession = new ActiveGameStateSession(gameCode);
        this.adventureMap = new AdventureMap();
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
        this.gameState = getNextGameState();
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

    public GameState getNextGameState() {
        switch(this.gameState) {
            case INIT -> {
                return GameState.LOCATION_SELECT;
            }
            case LOCATION_SELECT -> {
                return GameState.START;
            }
            case START -> {
                return GameState.WRITE_PROMPTS;
            }
            case WRITE_PROMPTS -> {
                return GameState.GENERATE_WRITE_OPTION_AUTHORS;
            }
            case GENERATE_WRITE_OPTION_AUTHORS -> {
                return GameState.WRITE_OPTIONS;
            }
            case WRITE_OPTIONS -> {
                return GameState.ROUND1;
            }
            case ROUND1 -> {
                return GameState.START_PHASE2;
            }
            case START_PHASE2 -> {
                return GameState.WRITE_PROMPTS_AGAIN;
            }
            case WRITE_PROMPTS_AGAIN -> {
                return GameState.LOCATION_SELECT_AGAIN;
            }
            case LOCATION_SELECT_AGAIN -> {
                return GameState.GENERATE_WRITE_OPTION_AUTHORS_AGAIN;
            }
            case GENERATE_WRITE_OPTION_AUTHORS_AGAIN -> {
                return GameState.WRITE_OPTIONS_AGAIN;
            }
            case WRITE_OPTIONS_AGAIN -> {
                return GameState.ROUND2;
            }
            case ROUND2 -> {
                return GameState.RITUAL;
            }
            case RITUAL -> {
                return GameState.GENERATE_ENDINGS;
            }
            case GENERATE_ENDINGS -> {
                return GameState.WRITE_ENDINGS;
            }
            case WRITE_ENDINGS -> {
                return GameState.ENDING;
            }
            case ENDING -> {
                return GameState.FINALE;
            }
            default -> {
                return GameState.INIT;
            }
        }
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
}
