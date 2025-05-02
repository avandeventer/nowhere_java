package client.nowhere.helper;

import client.nowhere.dao.EndingDAO;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.RitualDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static client.nowhere.model.GameState.LOCATION_SELECT;
import static client.nowhere.model.GameState.LOCATION_SELECT_AGAIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

public class GameSessionHelperTest {

    @Mock
    private GameSessionDAO gameSessionDAO;

    @Mock
    private StoryDAO storyDAO;

    @Mock
    private RitualDAO ritualDAO;

    @Mock
    private EndingDAO endingDAO;

    @InjectMocks
    private GameSessionHelper gameSessionHelper;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @ParameterizedTest
    @MethodSource("provideGameSessionScenarios")
    void testUpdateGameSessionStart_MultipleScenarios(
            int playerCount,
            List<String> playerIdAssignments,
            List<String> playedStoryPlayerIds,
            String testName,
            GameState currentGameState
    ) {
        List<Player> players = createPlayers(playerCount);
        GameSession gameSession = createGameSession(currentGameState, playerIdAssignments, players, playedStoryPlayerIds);

        when(gameSessionDAO.getGame(anyString())).thenReturn(gameSession, gameSession);
        when(gameSessionDAO.getPlayers(anyString())).thenReturn(players);
        when(gameSessionDAO.updateGameSession(any())).thenAnswer(invocation -> invocation.getArgument(0));

        GameSession updated = gameSessionHelper.updateToNextGameState("test123");

        System.out.println("✅ Ran test: " + testName);
        assertEquals(currentGameState.getNextGameState().getNextGameState(), updated.getGameState());
        assertTrue(updated.getStories().stream().noneMatch(story -> story.getAuthorId().isEmpty()));

        for (Story story : updated.getStories()) {
            assertNotEquals(story.getPlayerId(), story.getAuthorId(),
                    "Player cannot author their own story");
        }

        updated.getActiveGameStateSession().getIsPlayerDone().forEach((authorId, doneStatus) -> assertFalse(doneStatus));

        // Count how many stories each author was assigned
        Map<String, Long> authorCounts = updated.getStories().stream()
                .collect(Collectors.groupingBy(Story::getAuthorId, Collectors.counting()));

        List<String> sequelStories =
                updated.getStories().stream().filter(story -> !story.getPrequelStoryId().isEmpty())
                        .map(Story::getPrequelStoryId)
                .collect(Collectors.toList());

        if (playedStoryPlayerIds.size() == 0) {
            assertEquals(0, sequelStories.size());
        } else {
            assertTrue(sequelStories.size() > 0, "We'd expect to see at least one sequel story if there are played stories available");
            assertThat(sequelStories).doesNotHaveDuplicates();
        }

        System.out.println("✅ Ran test: " + testName);
        System.out.println("🧾 Story Authorship Counts:");
        authorCounts.forEach((author, count) -> {
            System.out.printf("   - %s wrote %d stories", author, count);
            System.out.printf(" and they wrote %s sequel stories%n",
                    updated.getStories().stream().filter(
                        story ->
                            story.getAuthorId().equals(author)
                            && !story.getPrequelStoryId().isEmpty()
                        ).count());
        });

        // Assert fair distribution
        long max = authorCounts.values().stream().mapToLong(Long::longValue).max().orElse(0);
        long min = authorCounts.values().stream().mapToLong(Long::longValue).min().orElse(0);
        assertTrue(max - min <= 1,
                "Story authorship should be fairly balanced (difference between most and least is at most 1)");
    }

    static Stream<Arguments> provideGameSessionScenarios() {
        return Stream.of(
                Arguments.of(
                        3,
                        Arrays.asList("author0", "author1", "author2"),
                        new ArrayList<>(),
                        "Each player owns one story",
                        LOCATION_SELECT
                ),
                Arguments.of(
                        5,
                        Arrays.asList("author0", "author1", "author0", "author2", "author3", "author4"),
                        new ArrayList<>(),
                        "One player owns two stories",
                        LOCATION_SELECT
                ),
                Arguments.of(
                        4,
                        Arrays.asList("author0", "author0", "author0", "author1", "author2", "author3"),
                        new ArrayList<>(),
                        "Many stories owned by the same player",
                        LOCATION_SELECT
                ),
                Arguments.of(
                        4,
                        Arrays.asList("author0", "author1", "author2", "author3", "author0", "author1"),
                        new ArrayList<>(),
                        "More stories than players, unevenly distributed",
                        LOCATION_SELECT
                ),
                Arguments.of(
                        6,
                        Arrays.asList("author0", "author1", "author2", "author3", "author4", "author5", "author0", "author1", "author2", "author3", "author4", "author5"),
                        new ArrayList<>(),
                        "Multiple stories for each player",
                        LOCATION_SELECT
                ),
                Arguments.of(
                        6,
                        Arrays.asList("author0", "author1", "author2", "author3", "author4", "author5", "author0", "author1", "author2", "author3", "author4", "author5"),
                        Arrays.asList("author0", "author1", "author2", "author3", "author4", "author5"),
                        "Multiple stories for each player, one played story each",
                        LOCATION_SELECT_AGAIN
                )
        );
    }

    private List<Player> createPlayers(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> {
                    Player p = new Player();
                    p.setAuthorId("author" + i);
                    p.setUserName("Player " + i);
                    return p;
                }).collect(Collectors.toList());
    }

    private GameSession createGameSession(GameState state, List<String> playerIds, List<Player> players, List<String> playedStoryPlayerIds) {
        GameSession session = new GameSession();
        session.setGameCode("test123");
        session.setGameState(state);
        session.setPlayers(players);
        ActiveGameStateSession activeGameStateSession = new ActiveGameStateSession();
        players.forEach(player -> activeGameStateSession.getIsPlayerDone().put(player.getAuthorId(), true));
        session.setActiveGameStateSession(activeGameStateSession);

        List<Story> stories = new ArrayList<>();
        for (int i = 0; i < playerIds.size(); i++) {
            Story s = new Story();
            s.setStoryId("s" + i);
            s.setPlayerId(playerIds.get(i));
            s.setAuthorId("");  // to be filled by system
            s.setOptions(Arrays.asList(new Option(), new Option()));
            stories.add(s);
        }

        for (int i = 0; i < playedStoryPlayerIds.size(); i++) {
            Story s = new Story();
            s.setStoryId("ssequel" + i);
            s.setPlayerId(playedStoryPlayerIds.get(i));
            s.setAuthorId(playedStoryPlayerIds.get(i+1 >= playedStoryPlayerIds.size() ? 0 : i+1));
            s.setSelectedOptionId("1");
            s.setOptions(Arrays.asList(new Option(), new Option()));
            stories.add(s);
        }

        session.setStories(stories);
        return session;
    }
}
