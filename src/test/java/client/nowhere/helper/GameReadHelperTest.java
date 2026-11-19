package client.nowhere.helper;

import client.nowhere.dao.AdventureMapDAO;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.model.*;
import com.google.api.core.ApiFutures;
import com.google.api.core.SettableApiFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class GameReadHelperTest {

    private static final String GAME_CODE = "GAME12";

    private GameSessionDAO gameSessionDAO;
    private StoryDAO storyDAO;
    private AdventureMapDAO adventureMapDAO;
    private GameReadHelper helper;
    private GameSession gameDocument;

    @BeforeEach
    void setUp() {
        gameSessionDAO = mock(GameSessionDAO.class);
        storyDAO = mock(StoryDAO.class);
        adventureMapDAO = mock(AdventureMapDAO.class);
        helper = new GameReadHelper(gameSessionDAO, storyDAO, adventureMapDAO);

        gameDocument = new GameSession();
        gameDocument.setGameCode(GAME_CODE);
        gameDocument.setRoundNumber(2);
        GameBoard board = new GameBoard();
        board.setPlayerCoordinates(new PlayerCoordinates(0, 0));
        board.setEncounter(0, 0, new Encounter(new EncounterLabel("id", "label"), EncounterType.NORMAL, "current", "prompt"));
        gameDocument.setGameBoard(board);
        when(gameSessionDAO.getGame(GAME_CODE)).thenReturn(gameDocument);
    }

    @Test
    void getFullGame_startsTheMapReadBeforeTheGameDocumentAndReadsStoriesForItsRound() {
        // The map read is still in flight when the game document is read
        SettableApiFuture<AdventureMap> mapRead = SettableApiFuture.create();
        AdventureMap map = new AdventureMap();
        when(adventureMapDAO.getAsync(GAME_CODE)).thenAnswer(invocation -> mapRead);
        when(gameSessionDAO.getGame(GAME_CODE)).thenAnswer(invocation -> {
            assertFalse(mapRead.isDone());
            mapRead.set(map);
            return gameDocument;
        });
        List<Story> stories = List.of(new Story());
        when(storyDAO.loadStoriesAsync(GAME_CODE, 2)).thenReturn(ApiFutures.immediateFuture(stories));

        GameSession game = helper.getFullGame(GAME_CODE);

        assertSame(gameDocument, game);
        assertSame(stories, game.getStories());
        assertSame(map, game.getAdventureMap());
        var order = inOrder(adventureMapDAO, gameSessionDAO, storyDAO);
        order.verify(adventureMapDAO).getAsync(GAME_CODE);
        order.verify(gameSessionDAO).getGame(GAME_CODE);
        order.verify(storyDAO).loadStoriesAsync(GAME_CODE, 2);
    }

    @Test
    void getGameWithMap_readsNoStories() {
        AdventureMap map = new AdventureMap();
        when(adventureMapDAO.getAsync(GAME_CODE)).thenReturn(ApiFutures.immediateFuture(map));

        GameSession game = helper.getGameWithMap(GAME_CODE);

        assertSame(map, game.getAdventureMap());
        assertNull(game.getStories());
        verifyNoInteractions(storyDAO);
    }

    @Test
    void getCurrentEncounterStory_readsOnlyTheStoryAtTheCurrentCoordinates() {
        Story current = new Story();
        when(storyDAO.getStory(GAME_CODE, "current", 2)).thenReturn(current);

        assertSame(current, helper.getCurrentEncounterStory(gameDocument));
        verify(storyDAO, never()).loadStoriesAsync(anyString(), anyInt());
        verify(storyDAO, never()).loadStories(anyString(), anyInt());
    }

    @Test
    void getCurrentEncounterStory_withNoEncounterAtTheCoordinates_readsNothing() {
        gameDocument.getGameBoard().setPlayerCoordinates(new PlayerCoordinates(5, 5));

        assertNull(helper.getCurrentEncounterStory(gameDocument));
        verifyNoInteractions(storyDAO);
    }
}
