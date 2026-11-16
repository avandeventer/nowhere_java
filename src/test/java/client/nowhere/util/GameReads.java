package client.nowhere.util;

import client.nowhere.dao.AdventureMapDAO;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.helper.GameReadHelper;
import client.nowhere.model.GameSession;
import client.nowhere.model.Story;
import com.google.api.core.ApiFutures;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.function.Function;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Stubs the split game reads from one fixture game per game code, the way Firestore holds it:
 * - GameSessionDAO.getGame: the game document only (a copy without stories or map), so code that
 *   uses stories or the map without reading them fails instead of passing on a full fixture
 * - StoryDAO.loadStories(Async) / getStory / getStoryIdsForPlayer: the fixture's stories
 * - AdventureMapDAO.get(Async): the fixture's map
 * The fixture function runs on every read, so per-test changes and fresh copies still apply.
 * Build GameReadHelper over the same mocks with gameReadHelper(...).
 */
public final class GameReads {

    private GameReads() { }

    public static GameReadHelper gameReadHelper(GameSessionDAO gameSessionDAO, StoryDAO storyDAO, AdventureMapDAO adventureMapDAO) {
        return new GameReadHelper(gameSessionDAO, storyDAO, adventureMapDAO);
    }

    public static void stub(GameSessionDAO gameSessionDAO, StoryDAO storyDAO, AdventureMapDAO adventureMapDAO,
                            Function<String, GameSession> fixture) {
        when(gameSessionDAO.getGame(anyString())).thenAnswer(invocation ->
                gameDocumentOnly(fixture.apply(invocation.getArgument(0))));
        stubStoriesAndMap(storyDAO, adventureMapDAO, fixture);
    }

    /** Only the story and map reads, for tests that stub GameSessionDAO.getGame themselves. */
    public static void stubStoriesAndMap(StoryDAO storyDAO, AdventureMapDAO adventureMapDAO,
                                         Function<String, GameSession> fixture) {
        when(storyDAO.loadStories(anyString(), anyInt())).thenAnswer(invocation ->
                stories(fixture, invocation.getArgument(0)));
        when(storyDAO.loadStoriesAsync(anyString(), anyInt())).thenAnswer(invocation ->
                ApiFutures.immediateFuture(stories(fixture, invocation.getArgument(0))));
        when(storyDAO.getStory(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
            String storyId = invocation.getArgument(1);
            return stories(fixture, invocation.getArgument(0)).stream()
                    .filter(story -> storyId.equals(story.getStoryId()))
                    .findFirst()
                    .orElse(null);
        });
        when(storyDAO.getStoryIdsForPlayer(anyString(), anyString(), anyInt())).thenAnswer(invocation -> {
            String playerId = invocation.getArgument(1);
            return stories(fixture, invocation.getArgument(0)).stream()
                    .filter(story -> playerId.equals(story.getPlayerId()))
                    .map(Story::getStoryId)
                    .toList();
        });
        if (adventureMapDAO != null) {
            when(adventureMapDAO.get(anyString())).thenAnswer(invocation ->
                    fixture.apply(invocation.getArgument(0)).getAdventureMap());
            when(adventureMapDAO.getAsync(anyString())).thenAnswer(invocation ->
                    ApiFutures.immediateFuture(fixture.apply(invocation.getArgument(0)).getAdventureMap()));
        }
    }

    private static List<Story> stories(Function<String, GameSession> fixture, String gameCode) {
        GameSession game = fixture.apply(gameCode);
        return game == null || game.getStories() == null ? List.of() : game.getStories();
    }

    /** A shallow copy (nested objects shared, as with a real read) without stories or map. */
    public static GameSession gameDocumentOnly(GameSession game) {
        if (game == null) {
            return null;
        }
        GameSession document = new GameSession();
        try {
            for (Field field : GameSession.class.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                field.setAccessible(true);
                field.set(document, field.get(game));
            }
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
        document.setStories(null);
        document.setAdventureMap(null);
        return document;
    }
}
