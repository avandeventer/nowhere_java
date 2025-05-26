package client.nowhere.helper;

import client.nowhere.dao.*;
import client.nowhere.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.*;
import java.util.*;
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

    @Mock
    private UserProfileDAO userProfileDAO;

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
            GameState currentGameState,
            List<String> mockSaveGameSequelStoryOptions
    ) {
        List<Player> players = createPlayers(playerCount);
        GameSession gameSession = createGameSession(currentGameState, playerIdAssignments, players, playedStoryPlayerIds, mockSaveGameSequelStoryOptions);

        when(gameSessionDAO.getGame(anyString())).thenAnswer(invocation -> deepCopy(gameSession));
        when(gameSessionDAO.getPlayers(anyString())).thenReturn(players);
        when(gameSessionDAO.updateGameSession(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(userProfileDAO.getSaveGameSequelStories(
                gameSession.getUserProfileId(),
                gameSession.getAdventureMap().getAdventureId(),
                gameSession.getSaveGameId(),
                gameSession.getStories().stream().filter(story -> !story.getSelectedOptionId().isEmpty()).collect(Collectors.toList())
            )
        ).thenReturn(buildSavedSequelStories(mockSaveGameSequelStoryOptions));

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

        List<String> sequelStorySelectedOptionIds =
                updated.getStories().stream().filter(story -> !story.getPrequelStoryId().isEmpty())
                        .map(Story::getPrequelStorySelectedOptionId)
                .collect(Collectors.toList());

        if (playedStoryPlayerIds.size() == 0) {
            assertEquals(0, sequelStorySelectedOptionIds.size());
        } else {
            assertTrue(sequelStorySelectedOptionIds.size() > 0, "We'd expect to see at least one sequel story if there are played stories available");
            assertThat(sequelStorySelectedOptionIds).doesNotHaveDuplicates();
            List<String> sequelOptionIds = updated.getStories().stream().filter(story -> !story.getPrequelStoryId().isEmpty())
                    .flatMap(story -> story.getOptions().stream())
                    .map(Option::getOptionId)
                    .collect(Collectors.toList());
            sequelOptionIds.forEach(sequelOptionId -> assertThat(mockSaveGameSequelStoryOptions).doesNotContain(sequelOptionId));
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
                        LOCATION_SELECT,
                        new ArrayList<>()
                ),
                Arguments.of(
                        5,
                        Arrays.asList("author0", "author1", "author0", "author2", "author3", "author4"),
                        new ArrayList<>(),
                        "One player owns two stories",
                        LOCATION_SELECT,
                        new ArrayList<>()
                ),
                Arguments.of(
                        4,
                        Arrays.asList("author0", "author0", "author0", "author1", "author2", "author3"),
                        new ArrayList<>(),
                        "Many stories owned by the same player",
                        LOCATION_SELECT,
                        new ArrayList<>()
                ),
                Arguments.of(
                        4,
                        Arrays.asList("author0", "author1", "author2", "author3", "author0", "author1"),
                        new ArrayList<>(),
                        "More stories than players, unevenly distributed",
                        LOCATION_SELECT,
                        new ArrayList<>()
                ),
                Arguments.of(
                        6,
                        Arrays.asList("author0", "author1", "author2", "author3", "author4", "author5", "author0", "author1", "author2", "author3", "author4", "author5"),
                        new ArrayList<>(),
                        "Multiple stories for each player",
                        LOCATION_SELECT,
                        new ArrayList<>()
                ),
                Arguments.of(
                        6,
                        Arrays.asList("author0", "author1", "author2", "author3", "author4", "author5", "author0", "author1", "author2", "author3", "author4", "author5"),
                        Arrays.asList("author0", "author1", "author2", "author3", "author4", "author5"),
                        "Multiple stories for each player, one played story each",
                        LOCATION_SELECT_AGAIN,
                        new ArrayList<>(Arrays.asList("someOtherOption1", "someOtherOption2", "someOtherOption3"))
                ),
                Arguments.of(
                        6,
                        Arrays.asList("author0", "author1", "author2", "author3", "author4", "author5", "author0", "author1", "author2", "author3", "author4", "author5"),
                        Arrays.asList("author0", "author1", "author2", "author3", "author4", "author5"),
                        "Multiple stories for each player, one played story each, remove previous sequel stories",
                        LOCATION_SELECT_AGAIN,
                        new ArrayList<>(Arrays.asList("o1", "o2", "o3"))
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

    private GameSession createGameSession(GameState state, List<String> playerIds, List<Player> players, List<String> playedStoryPlayerIds, List<String> saveGameSequelOptionIds) {
        GameSession session = new GameSession();
        session.setGameCode("test123");
        session.setGameState(state);
        session.setPlayers(players);
        ActiveGameStateSession activeGameStateSession = new ActiveGameStateSession();
        players.forEach(player -> activeGameStateSession.getIsPlayerDone().put(player.getAuthorId(), true));
        session.setActiveGameStateSession(activeGameStateSession);
        session.setAdventureMap(new AdventureMap());

        List<Story> stories = new ArrayList<>();
        for (int i = 0; i < playerIds.size(); i++) {
            Story s = new Story();
            s.setStoryId("s" + i);
            s.setPlayerId(playerIds.get(i));
            s.setAuthorId("");  // to be filled by system
            s.setOptions(Arrays.asList(new Option(), new Option()));
            for (int j = 0; j < s.getOptions().size(); j++) {
                s.getOptions().get(j).setOptionId(s.getStoryId() + "o" + j);
            }
            stories.add(s);
        }

        Queue saveGameOptionIdQueue = new LinkedList(saveGameSequelOptionIds);

        for (int i = 0; i < playedStoryPlayerIds.size(); i++) {
            Story s = new Story();
            s.setStoryId("ssequel" + i);
            s.setPlayerId(playedStoryPlayerIds.get(i));
            s.setVisited(true);
            s.setAuthorId(playedStoryPlayerIds.get(i+1 >= playedStoryPlayerIds.size() ? 0 : i+1));
            Object optionId = saveGameOptionIdQueue.poll();
            s.setOptions(Arrays.asList(new Option(), new Option()));
            s.setSelectedOptionId(s.getOptions().get(0).getOptionId());
            if (optionId != null) {
                s.getOptions().get(0).setOptionId(optionId.toString());
                s.setSelectedOptionId(optionId.toString());
            }
            stories.add(s);
        }

        session.setStories(stories);
        return session;
    }

    private List<Story> buildSavedSequelStories(List<String> optionIds) {
        List<Story> savedSequelStories = new ArrayList<>();
        for (String optionId : optionIds) {
            Story story = new Story();
            story.setPrequelStorySelectedOptionId(optionId);
            story.setOptions(Arrays.asList(new Option(), new Option()));
            savedSequelStories.add(story);
        }
        return savedSequelStories;
    }

    private GameSession deepCopy(GameSession session) {
        GameSession copy = new GameSession();
        copy.setGameCode(session.getGameCode());
        copy.setGameState(session.getGameState());
        copy.setPlayers(new ArrayList<>(session.getPlayers()));
        copy.setStories(session.getStories());
        copy.setActiveGameStateSession(session.getActiveGameStateSession());
        copy.setAdventureMap(session.getAdventureMap());
        return copy;
    }

}
