package client.nowhere.helper;

import client.nowhere.dao.AdventureMapDAO;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.exception.ResourceException;
import client.nowhere.model.AdventureMap;
import client.nowhere.model.Encounter;
import client.nowhere.model.GameSession;
import client.nowhere.model.Story;
import com.google.api.core.ApiFuture;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * Reads a game's parts from their own DAOs. GameSessionDAO.getGame returns only the game document;
 * every story (StoryDAO) and the adventure map (AdventureMapDAO) are separate documents, so a flow
 * reads just the parts it uses:
 * - getFullGame: game document, every story and the map (GET /game, transitions, /outcomeTypes)
 * - getGameWithMap: game document and the map
 * - getCurrentEncounterStory: the story at the current coordinates of a game already read
 */
@Component
public class GameReadHelper {

    private final GameSessionDAO gameSessionDAO;
    private final StoryDAO storyDAO;
    private final AdventureMapDAO adventureMapDAO;

    @Autowired
    public GameReadHelper(GameSessionDAO gameSessionDAO, StoryDAO storyDAO, AdventureMapDAO adventureMapDAO) {
        this.gameSessionDAO = gameSessionDAO;
        this.storyDAO = storyDAO;
        this.adventureMapDAO = adventureMapDAO;
    }

    /**
     * The game with every story and its adventure map. The map is read alongside the game
     * document; stories once the game document gives the round number.
     */
    public GameSession getFullGame(String gameCode) {
        ApiFuture<AdventureMap> adventureMap = adventureMapDAO.getAsync(gameCode);
        GameSession gameSession = gameSessionDAO.getGame(gameCode);
        ApiFuture<List<Story>> stories = storyDAO.loadStoriesAsync(gameCode, roundNumber(gameSession));
        gameSession.setAdventureMap(await(adventureMap));
        gameSession.setStories(await(stories));
        return gameSession;
    }

    /** The game with its adventure map but no stories, both read at once. */
    public GameSession getGameWithMap(String gameCode) {
        ApiFuture<AdventureMap> adventureMap = adventureMapDAO.getAsync(gameCode);
        GameSession gameSession = gameSessionDAO.getGame(gameCode);
        gameSession.setAdventureMap(await(adventureMap));
        return gameSession;
    }

    /** The story of the encounter at the game's current coordinates, or null if there isn't one. */
    public Story getCurrentEncounterStory(GameSession gameSession) {
        Encounter encounter = gameSession.getGameBoard() == null ? null : gameSession.getGameBoard().getEncounterAtPlayerCoordinates();
        if (encounter == null || encounter.getStoryId() == null || encounter.getStoryId().isEmpty()) {
            return null;
        }
        return storyDAO.getStory(gameSession.getGameCode(), encounter.getStoryId(), roundNumber(gameSession));
    }

    private static int roundNumber(GameSession gameSession) {
        return gameSession.getRoundNumber() == null ? 0 : gameSession.getRoundNumber();
    }

    private static <T> T await(ApiFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResourceException("Interrupted while reading the game", e);
        } catch (ExecutionException e) {
            throw new ResourceException("There was an issue reading the game", e);
        }
    }
}
