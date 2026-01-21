package client.nowhere.helper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import client.nowhere.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import client.nowhere.dao.AdventureMapDAO;
import client.nowhere.dao.EndingDAO;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.RitualDAO;
import client.nowhere.dao.StoryDAO;

import static client.nowhere.model.GameState.GENERATE_OCCUPATION_AUTHORS;
import static client.nowhere.model.GameState.GENERATE_WRITE_OPTION_AUTHORS;
import static client.nowhere.model.GameState.GENERATE_WRITE_PROMPT_AUTHORS;
import static client.nowhere.model.GameState.LOCATION_SELECT;
import static client.nowhere.model.GameState.WHAT_OCCUPATIONS_ARE_THERE;
import static client.nowhere.model.GameState.WHERE_CAN_WE_GO;
import static client.nowhere.model.GameState.WRITE_PROMPTS;

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
    private UserProfileHelper userProfileHelper;

    @Mock
    private AdventureMapDAO adventureMapDAO;

    @Mock
    private FeatureFlagHelper featureFlagHelper;

    @InjectMocks
    private GameSessionHelper gameSessionHelper;

    @Mock
    private CollaborativeTextHelper collaborativeTextHelper;

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

        when(gameSessionDAO.getGame(anyString())).thenAnswer(invocation -> deepCopy(gameSession));
        when(gameSessionDAO.getPlayers(anyString())).thenReturn(players);
        when(gameSessionDAO.updateGameSession(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(storyDAO.getStories(anyString())).thenAnswer(invocation -> gameSession.getStories());
        when(storyDAO.updateStory(any(Story.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GameSession updated = gameSessionHelper.updateToNextGameState("test123");

        System.out.println("✅ Ran test: " + testName);
        
        // Different assertions based on starting state
        if (currentGameState == LOCATION_SELECT) {
            // Starting from LOCATION_SELECT: goes through GENERATE_WRITE_PROMPT_AUTHORS -> WRITE_PROMPTS
            assertEquals(GENERATE_WRITE_PROMPT_AUTHORS.getNextGameState(GameMode.TOWN_MODE, false, 1), updated.getGameState());
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

            if (playedStoryPlayerIds.isEmpty()) {
                assertEquals(0, sequelStorySelectedOptionIds.size());
            } else {
                assertTrue(!sequelStorySelectedOptionIds.isEmpty(), "We'd expect to see at least one sequel story if there are played stories available");
                assertThat(sequelStorySelectedOptionIds).doesNotHaveDuplicates();
                List<String> sequelOptionIds = updated.getStories().stream().filter(story -> !story.getPrequelStoryId().isEmpty())
                        .flatMap(story -> story.getOptions().stream())
                        .map(Option::getOptionId)
                        .collect(Collectors.toList());
            }

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
        } else if (currentGameState == WRITE_PROMPTS) {
            // Starting from WRITE_PROMPTS: goes through GENERATE_WRITE_OPTION_AUTHORS -> WRITE_OPTIONS
            assertEquals(GENERATE_WRITE_OPTION_AUTHORS.getNextGameState(GameMode.TOWN_MODE, false, 1), updated.getGameState());
            
            // Get the updated stories from the DAO (which were updated by assignStoryOptionAuthors)
            List<Story> updatedStories = storyDAO.getStories("test123");
            
            // Filter to only stories that need outcome authors (have playerId and no selectedOptionId)
            List<Story> storiesNeedingOutcomeAuthors = updatedStories.stream()
                    .filter(story -> !story.getPlayerId().isEmpty() && story.getSelectedOptionId().isEmpty())
                    .collect(Collectors.toList());
            
            // Count how many outcomes each author was assigned
            Map<String, Long> outcomeAuthorCounts = storiesNeedingOutcomeAuthors.stream()
                    .flatMap(story -> story.getOptions().stream())
                    .filter(option -> !option.getOutcomeAuthorId().isEmpty())
                    .collect(Collectors.groupingBy(Option::getOutcomeAuthorId, Collectors.counting()));

            System.out.println("🧾 Outcome Author Assignment Counts:");
            outcomeAuthorCounts.forEach((author, count) -> 
                System.out.printf("   - %s assigned to write %d outcomes%n", author, count)
            );
            
            // Verify all options have outcome authors assigned
            for (Story story : storiesNeedingOutcomeAuthors) {
                for (Option option : story.getOptions()) {
                    assertFalse(option.getOutcomeAuthorId().isEmpty(), 
                        "Every option should have an outcome author for story " + story.getStoryId());
                }
            }

            // Verify that outcome authors are different from the story author and player
            for (Story story : storiesNeedingOutcomeAuthors) {
                for (Option option : story.getOptions()) {
                    assertNotEquals(story.getAuthorId(), option.getOutcomeAuthorId(),
                        "Outcome author should not be the story author for story " + story.getStoryId());
                    assertNotEquals(story.getPlayerId(), option.getOutcomeAuthorId(),
                        "Outcome author should not be the story's player for story " + story.getStoryId());
                }
            }

            // CRITICAL: Verify that eligible players get outcome assignments
            // This catches the bug where a player who should be eligible gets zero assignments
            // A player is eligible if they are NOT excluded from at least one story
            // (i.e., they are neither the author nor the playerId of that story)
            List<String> allPlayerIds = players.stream()
                    .map(Player::getAuthorId)
                    .collect(Collectors.toList());
            
            // For each player, check if they are eligible for at least one story
            for (String playerId : allPlayerIds) {
                boolean isEligibleForAtLeastOneStory = storiesNeedingOutcomeAuthors.stream()
                        .anyMatch(story -> 
                            !story.getAuthorId().equals(playerId) && 
                            !story.getPlayerId().equals(playerId)
                        );
                
                if (isEligibleForAtLeastOneStory) {
                    long assignmentCount = outcomeAuthorCounts.getOrDefault(playerId, 0L);
                    assertTrue(assignmentCount > 0,
                        String.format("Player %s is eligible for at least one story but got %d outcome assignments. " +
                            "This indicates a distribution bug. All counts: %s", 
                            playerId, assignmentCount, outcomeAuthorCounts));
                }
            }

            // Assert fair distribution of outcome authors
            long max = outcomeAuthorCounts.values().stream().mapToLong(Long::longValue).max().orElse(0);
            long min = outcomeAuthorCounts.values().stream().mapToLong(Long::longValue).min().orElse(0);
            int totalOptions = storiesNeedingOutcomeAuthors.size() * 2; // 2 options per story
            int expectedPerAuthor = totalOptions / playerCount;
            
            // With one author per story approach, distribution should be very even (max-min <= 1)
            long variance = max - min;
            assertTrue(variance <= 1,
                String.format("Outcome author assignment should be evenly balanced (max-min=%d, expected <=1, totalOptions=%d, expectedPerAuthor=%d, got counts: %s)", 
                    variance, totalOptions, expectedPerAuthor, outcomeAuthorCounts));
            
            // Verify that all options in a story share the same outcome author
            for (Story story : storiesNeedingOutcomeAuthors) {
                List<String> outcomeAuthorIds = story.getOptions().stream()
                        .map(Option::getOutcomeAuthorId)
                        .collect(Collectors.toList());
                // With the new logic, all options in a story should have the SAME author
                if (outcomeAuthorIds.size() > 1) {
                    String firstAuthorId = outcomeAuthorIds.get(0);
                    assertThat(outcomeAuthorIds).allMatch(id -> id.equals(firstAuthorId),
                        "All options in story " + story.getStoryId() + " should have the same outcome author");
                }
            }
            
            // Additional assertion: Verify fair distribution among eligible players
            // Count how many players are actually eligible (not excluded from all stories)
            long eligiblePlayerCount = allPlayerIds.stream()
                    .filter(playerId -> storiesNeedingOutcomeAuthors.stream()
                            .anyMatch(story -> 
                                !story.getAuthorId().equals(playerId) && 
                                !story.getPlayerId().equals(playerId)
                            ))
                    .count();
            
            int playersWithAssignments = outcomeAuthorCounts.size();
            // In a balanced distribution, we expect most or all eligible players to get assignments
            // The exact number depends on the story distribution, but we should have at least 2 players
            // with assignments in a 4-player game with multiple stories
            assertTrue(playersWithAssignments >= Math.min(2, eligiblePlayerCount),
                String.format("Expected at least %d players to have assignments (out of %d eligible), but got %d. " +
                    "This might indicate a distribution issue. Counts: %s",
                    Math.min(2, eligiblePlayerCount), eligiblePlayerCount, playersWithAssignments, outcomeAuthorCounts));
        }
    }

    static Stream<Arguments> provideGameSessionScenarios() {
        return Stream.of(
                Arguments.of(
                        10,
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
                        4,
                        Arrays.asList("author0", "author1", "author2", "author3"),
                        new ArrayList<>(),
                        "Outcome authors: 4 players, 1 story each",
                        WRITE_PROMPTS
                ),
                Arguments.of(
                        5,
                        Arrays.asList("author0", "author1", "author2", "author3", "author4", "author0", "author1", "author2", "author3", "author4"),
                        new ArrayList<>(),
                        "Outcome authors: 5 players, 2 stories each",
                        WRITE_PROMPTS
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
        session.setAdventureMap(new AdventureMap());

        List<Story> stories = new ArrayList<>();
        for (int i = 0; i < playerIds.size(); i++) {
            Story s = new Story();
            s.setStoryId("s" + i);
            s.setPlayerId(playerIds.get(i));
            
            // Set authorId based on state
            if (state == WRITE_PROMPTS) {
                // For WRITE_PROMPTS state, stories should already have an authorId (different from player)
                // Use player 2 positions ahead to ensure we have enough eligible authors for outcome assignment
                int storyAuthorIdx = (i + 2) % players.size();
                s.setAuthorId(players.get(storyAuthorIdx).getAuthorId());
                s.setPrompt("Test prompt for story " + i);
            } else {
                s.setAuthorId("");  // to be filled by system
            }
            
            // Ensure selectedOptionId is empty for WRITE_PROMPTS state
            s.setSelectedOptionId("");
            
            s.setOptions(Arrays.asList(new Option(), new Option()));
            for (int j = 0; j < s.getOptions().size(); j++) {
                Option option = s.getOptions().get(j);
                option.setOptionId(s.getStoryId() + "o" + j);
                
                // For WRITE_PROMPTS state, set empty success and failure text to test outcome assignment
                // This ensures they qualify for outcome author assignment
                if (state == WRITE_PROMPTS) {
                    option.setSuccessText("");
                    option.setFailureText("");
                }
            }
            stories.add(s);
        }

        for (int i = 0; i < playedStoryPlayerIds.size(); i++) {
            Story s = new Story();
            s.setStoryId("ssequel" + i);
            s.setPlayerId(playedStoryPlayerIds.get(i));
            s.setVisited(true);
            s.setAuthorId(playedStoryPlayerIds.get(i+1 >= playedStoryPlayerIds.size() ? 0 : i+1));
            s.setOptions(Arrays.asList(new Option(), new Option()));
            s.setSelectedOptionId(s.getOptions().get(0).getOptionId());
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

    @Test
    void testUpdateGameSession_GENERATE_OCCUPATION_AUTHORS_Phase() {
        // Arrange
        List<Player> players = createPlayers(3);
        GameSession gameSession = createGameSession(WHERE_CAN_WE_GO, new ArrayList<>(), players, new ArrayList<>());
        
        // Set up adventure map with custom stat types (after createGameSession which creates an empty one)
        List<StatType> statTypes = createCustomStatTypes();
        gameSession.getAdventureMap().setStatTypes(statTypes);
        
        // Debug: Print the setup
        System.out.println("Test setup - Adventure map: " + gameSession.getAdventureMap());
        System.out.println("Test setup - Stat types: " + gameSession.getAdventureMap().getStatTypes());
        
        // Create some locations with options for the assignLocationOptionAuthors method
        List<Location> locations = createTestLocations(players);
        gameSession.getAdventureMap().setLocations(locations);
        
        when(gameSessionDAO.getGame(anyString())).thenAnswer(invocation -> deepCopy(gameSession));
        when(gameSessionDAO.getPlayers(anyString())).thenReturn(players);
        when(gameSessionDAO.updateGameSession(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(gameSessionDAO.updatePlayer(any(Player.class))).thenAnswer(invocation -> {
            Player player = invocation.getArgument(0);
            System.out.println("updatePlayer called for player: " + player.getAuthorId() + " with stats: " + player.getPlayerStats());
            return player;
        });
        when(adventureMapDAO.getLocations(anyString())).thenReturn(locations);
        when(adventureMapDAO.updateLocation(anyString(), any(Location.class))).thenAnswer(invocation -> invocation.getArgument(1));

        // Act
        System.out.println("About to call updateToNextGameState with game state: " + gameSession.getGameState());
        GameSession updated;
        try {
            updated = gameSessionHelper.updateToNextGameState("test123");
            System.out.println("After updateToNextGameState, new game state: " + updated.getGameState());
        } catch (Exception e) {
            System.out.println("Exception thrown in updateToNextGameState: " + e.getMessage());
            e.printStackTrace();
            throw e;
        }

        // Assert
        // The GENERATE_OCCUPATION_AUTHORS phase should advance to the next phase
        assertEquals(GENERATE_OCCUPATION_AUTHORS.getNextGameState(GameMode.TOWN_MODE, false, 1), updated.getGameState());
        
        // Verify that each player has their base player stats set
        for (Player player : updated.getPlayers()) {
            // Debug: Print player stats
            System.out.println("Player " + player.getAuthorId() + " stats: " + player.getPlayerStats());
            System.out.println("Player " + player.getAuthorId() + " stats size: " + (player.getPlayerStats() != null ? player.getPlayerStats().size() : "null"));
            assertNotNull(player.getPlayerStats(), "Player stats should not be null for player " + player.getAuthorId());
            assertEquals(statTypes.size(), player.getPlayerStats().size(), "Player should have " + statTypes.size() + " stats");
            
            // Verify that each player stat has the correct stat type and base value (4)
            for (PlayerStat playerStat : player.getPlayerStats()) {
                assertNotNull(playerStat.getStatType(), "PlayerStat should not be null");
                assertEquals(4, playerStat.getValue(), "PlayerStat value should be 4");
                assertTrue(statTypes.contains(playerStat.getStatType()), "PlayerStat should contain one of the stat types");
            }
        }
        
        // Verify that the adventure map and stat types are preserved
        assertNotNull(updated.getAdventureMap());
        assertNotNull(updated.getAdventureMap().getStatTypes());
        assertEquals(statTypes.size(), updated.getAdventureMap().getStatTypes().size());
        
        // Verify that locations are preserved
        assertNotNull(updated.getAdventureMap().getLocations());
        assertEquals(locations.size(), updated.getAdventureMap().getLocations().size());
    }

    private List<StatType> createCustomStatTypes() {
        List<StatType> statTypes = new ArrayList<>();
        
        StatType strength = new StatType("Strength");
        strength.setFavorType(false);
        strength.setFavorEntity("");
        statTypes.add(strength);
        
        StatType magic = new StatType("Magic");
        magic.setFavorType(false);
        magic.setFavorEntity("");
        statTypes.add(magic);
        
        StatType charisma = new StatType("Charisma");
        charisma.setFavorType(true);
        charisma.setFavorEntity("Nobles");
        statTypes.add(charisma);
        
        return statTypes;
    }

    private List<Location> createTestLocations(List<Player> players) {
        List<Location> locations = new ArrayList<>();
        
        for (int i = 0; i < players.size(); i++) {
            Location location = new Location();
            location.setId("location" + i);
            location.setAuthorId(players.get(i).getAuthorId());
            location.setLabel("Test Location " + i);
            
            List<Option> options = new ArrayList<>();
            Option option1 = new Option();
            option1.setOptionId("option1_" + i);
            option1.setOptionText("Option 1 for location " + i);
            option1.setSuccessText(""); // Empty to test assignment
            option1.setFailureText(""); // Empty to test assignment
            options.add(option1);
            
            Option option2 = new Option();
            option2.setOptionId("option2_" + i);
            option2.setOptionText("Option 2 for location " + i);
            option2.setSuccessText(""); // Empty to test assignment
            option2.setFailureText(""); // Empty to test assignment
            options.add(option2);
            
            location.setOptions(options);
            locations.add(location);
        }
        
        return locations;
    }

    @ParameterizedTest
    @MethodSource("providePreamblePhaseScenarios")
    void testUpdateGameSession_PREAMBLE_Phase_AdventureMapIntegration(
            String scenarioName,
            String adventureId,
            String userId,
            String expectedSaveGameId,
            boolean shouldCallUserProfileHelper
    ) {
        // Arrange
        List<Player> players = createPlayers(2);
        GameSession gameSession = createGameSession(WHAT_OCCUPATIONS_ARE_THERE, new ArrayList<>(), players, new ArrayList<>());
        
        // Set up adventure map and user profile ID
        AdventureMap adventureMap = new AdventureMap();
        adventureMap.setAdventureId(adventureId);
        gameSession.setAdventureMap(adventureMap);
        gameSession.setUserProfileId(userId);
        
        // Mock the dependencies
        when(gameSessionDAO.getGame(anyString())).thenAnswer(invocation -> deepCopy(gameSession));
        when(gameSessionDAO.getPlayers(anyString())).thenReturn(players);
        when(gameSessionDAO.updateGameSession(any())).thenAnswer(invocation -> invocation.getArgument(0));

        when(userProfileHelper.saveGameSessionAdventureMapToUserProfile(any(GameSession.class))).thenReturn(expectedSaveGameId);

        // Act
        GameSession updated = gameSessionHelper.updateToNextGameState("test123");

        // Assert
        verify(userProfileHelper).saveGameSessionAdventureMapToUserProfile(any(GameSession.class));
        assertEquals(expectedSaveGameId, updated.getSaveGameId());

        // Verify that the adventure map and user profile ID are preserved
        assertNotNull(updated.getAdventureMap());
        assertEquals(adventureId, updated.getAdventureMap().getAdventureId());
        assertEquals(userId, updated.getUserProfileId());
    }

    private static Stream<Arguments> providePreamblePhaseScenarios() {
        return Stream.of(
            Arguments.of(
                "Adventure map does NOT exist in user profile",
                "test-adventure-123",
                "user-123",
                "save-game-123", // expectedSaveGameId
                true  // shouldCallUserProfileHelper
            ),
            Arguments.of(
                "Adventure map DOES exist in user profile",
                "existing-adventure-456",
                "user-456",
                "", // expectedSaveGameId (null when not called)
                false // shouldCallUserProfileHelper
            )
        );
    }

    private GameSession deepCopy(GameSession session) {
        GameSession copy = new GameSession();
        copy.setGameCode(session.getGameCode());
        copy.setGameState(session.getGameState());
        copy.setPlayers(new ArrayList<>(session.getPlayers()));
        copy.setStories(session.getStories());
        copy.setActiveGameStateSession(session.getActiveGameStateSession());
        copy.setAdventureMap(session.getAdventureMap());
        copy.setUserProfileId(session.getUserProfileId());
        
        // Debug: Print player stats in the copy
        System.out.println("deepCopy - Players in copy:");
        for (Player player : copy.getPlayers()) {
            System.out.println("  Player " + player.getAuthorId() + " stats: " + player.getPlayerStats());
        }
        
        return copy;
    }

}
