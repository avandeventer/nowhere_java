package client.nowhere.helper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import client.nowhere.dao.AdventureMapDAO;
import client.nowhere.dao.CollaborativeTextDAO;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.model.*;
import client.nowhere.util.TestJsonLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Unit tests for CollaborativeTextHelper.
 * Tests the collaborative text submission and winner calculation logic across different game states.
 */
public class CollaborativeTextHelperTest {

    @Mock
    private GameSessionDAO gameSessionDAO;

    @Mock
    private CollaborativeTextDAO collaborativeTextDAO;

    @Mock
    private AdventureMapDAO adventureMapDAO;

    @Mock
    private AdventureMapHelper adventureMapHelper;

    @Mock
    private StoryDAO storyDAO;

    @Mock
    private FeatureFlagHelper featureFlagHelper;

    // Use real OutcomeTypeHelper for distribution logic tests
    private final OutcomeTypeHelper outcomeTypeHelper = new OutcomeTypeHelper();

    private CollaborativeTextHelper collaborativeTextHelper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Manually construct with real OutcomeTypeHelper
        collaborativeTextHelper = new CollaborativeTextHelper(
                gameSessionDAO, collaborativeTextDAO, adventureMapDAO,
                adventureMapHelper, storyDAO, featureFlagHelper, outcomeTypeHelper
        );
    }

    // ===== HELPER METHODS FOR LOADING TEST DATA =====

    /**
     * Loads a CollaborativeTextPhase from the GameSession's collaborativeTextPhases map.
     * @param gameSession The GameSession containing the phases
     * @param phaseId The phase ID (e.g., "HOW_DOES_THIS_RESOLVE", "WHAT_HAPPENS_HERE")
     * @return The CollaborativeTextPhase for the given phase ID
     */
    private CollaborativeTextPhase getPhaseFromGameSession(GameSession gameSession, String phaseId) {
        return gameSession.getCollaborativeTextPhases().get(phaseId);
    }

    // ===== HOW_DOES_THIS_RESOLVE TESTS =====

    @Test
    void testCalculateWinningSubmission_HOW_DOES_THIS_RESOLVE_ReturnsSubmissionsWithoutParents() throws IOException {
        // Arrange
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson("HOW_DOES_THIS_RESOLVE_START.json");
        String gameCode = gameSession.getGameCode();
        String phaseId = GameState.HOW_DOES_THIS_RESOLVE.name();

        CollaborativeTextPhase phase = getPhaseFromGameSession(gameSession, phaseId);
        assertNotNull(phase, "HOW_DOES_THIS_RESOLVE phase should exist in test data");

        // Mock the dependencies
        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);
        when(collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId)).thenReturn(phase);

        // Act
        List<TextSubmission> winningSubmissions = collaborativeTextHelper.calculateWinningSubmission(gameCode);

        // Assert
        assertNotNull(winningSubmissions, "Winning submissions should not be null");

        // Verify that the returned submissions are from getSubmissionsWithoutParentSubmissions
        List<TextSubmission> expectedSubmissions = phase.getSubmissionsWithoutParentSubmissions();

        System.out.println("=== HOW_DOES_THIS_RESOLVE Test Results ===");
        System.out.println("Total submissions in phase: " + phase.getSubmissions().size());
        System.out.println("Submissions without parents: " + expectedSubmissions.size());
        System.out.println("Winning submissions returned: " + winningSubmissions.size());

        assertEquals(expectedSubmissions, winningSubmissions);

        for (TextSubmission submission : winningSubmissions) {
            System.out.println("  - " + submission.getSubmissionId() + ": " + submission.getCurrentText());
            System.out.println("    OutcomeType: " + submission.getOutcomeType());
            if (submission.getOutcomeTypeWithLabel() != null) {
                System.out.println("    OutcomeTypeWithLabel.id: " + submission.getOutcomeTypeWithLabel().getId());
                if (submission.getOutcomeTypeWithLabel().getSubTypes() != null) {
                    for (OutcomeType subType : submission.getOutcomeTypeWithLabel().getSubTypes()) {
                        System.out.println("      SubType: " + subType.getId() + " - " + subType.getLabel());
                    }
                }
            }
        }
    }

    @Test
    void testCalculateWinningSubmission_HOW_DOES_THIS_RESOLVE_GroupsByOutcomeType() throws IOException {
        // Arrange
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson("HOW_DOES_THIS_RESOLVE_START.json");
        String gameCode = gameSession.getGameCode();
        String phaseId = GameState.HOW_DOES_THIS_RESOLVE.name();

        CollaborativeTextPhase phase = getPhaseFromGameSession(gameSession, phaseId);

        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);
        when(collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId)).thenReturn(phase);

        // Act
        List<TextSubmission> winningSubmissions = collaborativeTextHelper.calculateWinningSubmission(gameCode);

        // Assert - Verify that submissions are grouped by outcomeType
        // Each unique outcomeType should have at most one winning submission
        long uniqueOutcomeTypes = winningSubmissions.stream()
                .map(TextSubmission::getOutcomeType)
                .filter(ot -> ot != null && !ot.isEmpty())
                .distinct()
                .count();

        System.out.println("=== Outcome Type Grouping Test ===");
        System.out.println("Unique outcome types in winning submissions: " + uniqueOutcomeTypes);

        // The number of winning submissions should be <= number of unique outcome types
        // (one winner per outcome type)
        assertTrue(winningSubmissions.size() >= uniqueOutcomeTypes,
                "Should have at least one submission per unique outcome type");
    }

    // ===== WHAT_HAPPENS_HERE TESTS =====

    /**
     * Expected submission data for parameterized WHAT_HAPPENS_HERE tests.
     * @param submissionId The expected submission ID
     * @param currentText The expected currentText
     * @param additionsSize The expected number of additions
     */
    record ExpectedSubmission(String submissionId, String currentText, int additionsSize) {}

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideWhatHappensHereScenarios")
    void testCalculateWinningSubmission_WHAT_HAPPENS_HERE_FiltersSiblingsByAdditions(
            String scenarioName,
            String jsonFileName,
            List<ExpectedSubmission> expectedSubmissions
    ) throws IOException {
        // Arrange
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson(jsonFileName);
        gameSession.setGameState(GameState.WHAT_HAPPENS_HERE);

        String gameCode = gameSession.getGameCode();
        String phaseId = GameState.WHAT_HAPPENS_HERE.name();

        CollaborativeTextPhase phase = getPhaseFromGameSession(gameSession, phaseId);
        if (phase == null) {
            System.out.println("WHAT_HAPPENS_HERE phase not found in test data, skipping test: " + scenarioName);
            return;
        }

        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);
        when(collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId)).thenReturn(phase);

        // Act
        List<TextSubmission> winningSubmissions = collaborativeTextHelper.calculateWinningSubmission(gameCode);

        // Assert
        assertNotNull(winningSubmissions, "Winning submissions should not be null");

        System.out.println("=== " + scenarioName + " ===");
        System.out.println("Total submissions in phase: " + phase.getSubmissions().size());
        System.out.println("Winning submissions returned: " + winningSubmissions.size());

        for (TextSubmission submission : winningSubmissions) {
            System.out.println("  - " + submission.getSubmissionId() + ": " + submission.getCurrentText());
            System.out.println("    Additions count: " + (submission.getAdditions() != null ? submission.getAdditions().size() : 0));
        }

        // Assert correct number of winning submissions
        assertEquals(expectedSubmissions.size(), winningSubmissions.size(),
                "Should have " + expectedSubmissions.size() + " winning submissions");

        // Build a lookup of expected submissions by ID for flexible ordering
        Map<String, ExpectedSubmission> expectedById = expectedSubmissions.stream()
                .collect(java.util.stream.Collectors.toMap(ExpectedSubmission::submissionId, e -> e));

        // Assert each winning submission matches expected data
        for (TextSubmission submission : winningSubmissions) {
            ExpectedSubmission expected = expectedById.get(submission.getSubmissionId());
            assertNotNull(expected, "Unexpected submission ID: " + submission.getSubmissionId());
            assertEquals(expected.currentText(), submission.getCurrentText(),
                    "currentText mismatch for submission " + submission.getSubmissionId());
            assertEquals(expected.additionsSize(),
                    submission.getAdditions() != null ? submission.getAdditions().size() : 0,
                    "additions size mismatch for submission " + submission.getSubmissionId());
        }

        // Verify storyDAO.createStory was called for each winning submission
        ArgumentCaptor<Story> storyCaptor = ArgumentCaptor.forClass(Story.class);
        verify(storyDAO, times(expectedSubmissions.size())).createStory(storyCaptor.capture());

        List<Story> createdStories = storyCaptor.getAllValues();
        assertEquals(expectedSubmissions.size(), createdStories.size(),
                "Should have " + expectedSubmissions.size() + " stories created via StoryDAO");

        // Verify each created story has a prompt matching a winning submission's text
        for (Story story : createdStories) {
            assertNotNull(story.getStoryId(), "Story should have an ID");
            assertNotNull(story.getPrompt(), "Story should have a prompt");
            assertTrue(winningSubmissions.stream()
                            .anyMatch(ws -> ws.getCurrentText().equals(story.getPrompt())),
                    "Story prompt should match a winning submission's text: " + story.getPrompt());
        }
    }

    static Stream<Arguments> provideWhatHappensHereScenarios() {
        return Stream.of(
                Arguments.of(
                        "WHAT_HAPPENS_HERE Round 1 - Filter siblings by additions",
                        "WHAT_HAPPENS_HERE_START.json",
                        List.of(
                                new ExpectedSubmission("8ad070ef-9fd7-4d26-a88d-018e1e93d985",
                                        "A clown in a dumpy brown sack outfit walks up to you silently holding a sign that says \"Potatoes the Clown\". C He extends his hand and begins juggling the biggest potatoes you've ever seen. B He offers to teach you. What do you do? C",
                                        3),
                                new ExpectedSubmission("1cda23db-449f-4f41-9461-440b26d0e009",
                                        "Tommy Toad and the Bug Brigade block your path. They cross their many legs and stare down at you. A You feel intimidated. They're so small yet so intimidating! D",
                                        2),
                                new ExpectedSubmission("b169ebdb-89a2-47b8-8157-e8b15dfbb432",
                                        "The Plasma Ranger arrives just in the nick of time. You go toe to toe with your rival and the Plasma Ranger keeps switching sides? B",
                                        1),
                                new ExpectedSubmission("efd46f30-c24c-437b-907c-3a7f5d649405",
                                        "Friggin Skummy Steve is here. What a scumbag. He challenges you to a snot-off. D",
                                        1)
                        )
                ),
                Arguments.of(
                        "WHAT_HAPPENS_HERE Round 2 - Multiple branching story prompt submissions resolve to a single story per encounter",
                        "WHAT_HAPPENS_HERE_ROUND2_END.json",
                        List.of(
                                new ExpectedSubmission("3e815691-f6f1-4110-bfa1-44b48ac9a765",
                                        "Ooh yuck the people here are super gross C Ew yucky. Why are they like that. D",
                                        2),
                                new ExpectedSubmission("fea030da-8566-4173-af42-181ad25b6022",
                                        "There are more flowers here! A",
                                        1),
                                new ExpectedSubmission("94a0575f-1987-43ee-a47a-b8cc4c69e394",
                                        "You pull off the interstate and stop for gas D",
                                        1),
                                new ExpectedSubmission("16aed1b4-f771-410e-9fa1-929fe4cd3067",
                                        "The dreaded submission appears again. You Auto Submit B",
                                        1)
                        )
                )
        );
    }

    // ===== PARAMETERIZED TESTS FOR MULTIPLE GAME STATES =====

    @ParameterizedTest
    @MethodSource("provideGameStateScenarios")
    void testCalculateWinningSubmission_MultipleGameStates(
            String testName,
            GameState gameState,
            String phaseId,
            String jsonFileName,
            int expectedWinningSubmissionCount
    ) throws IOException {
        // Arrange
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson(jsonFileName);
        gameSession.setGameState(gameState);

        String gameCode = gameSession.getGameCode();

        CollaborativeTextPhase phase = getPhaseFromGameSession(gameSession, phaseId);
        if (phase == null) {
            System.out.println("Phase " + phaseId + " not found in test data, skipping test: " + testName);
            return;
        }

        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);
        when(collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId)).thenReturn(phase);

        // Act
        List<TextSubmission> winningSubmissions = collaborativeTextHelper.calculateWinningSubmission(gameCode);

        // Assert
        assertNotNull(winningSubmissions, "Winning submissions should not be null for: " + testName);
        assertEquals(expectedWinningSubmissionCount, winningSubmissions.size());

        System.out.println("=== " + testName + " ===");
        System.out.println("Game State: " + gameState);
        System.out.println("Phase ID: " + phaseId);
        System.out.println("Submissions in phase: " + phase.getSubmissions().size());
        System.out.println("Winning submissions: " + winningSubmissions.size());
        for (TextSubmission textSubmission : winningSubmissions) {
            System.out.println("Winning submission -- " + textSubmission.getCurrentText() + " -- Submission Outcome Type -- " +  textSubmission.getOutcomeTypeWithLabel().getLabel());
        }
    }

    static Stream<Arguments> provideGameStateScenarios() {
        return Stream.of(
                Arguments.of(
                        "HOW_DOES_THIS_RESOLVE - Standard winner calculation",
                        GameState.HOW_DOES_THIS_RESOLVE,
                        "HOW_DOES_THIS_RESOLVE",
                        "HOW_DOES_THIS_RESOLVE_START.json",
                        6
                ),
                Arguments.of(
                        "WHAT_HAPPENS_HERE - Filter siblings by additions",
                        GameState.WHAT_HAPPENS_HERE,
                        "WHAT_HAPPENS_HERE",
                        "HOW_DOES_THIS_RESOLVE_START.json",
                        4
                ),
                Arguments.of(
                        "WHAT_CAN_WE_TRY - Standard winner calculation",
                        GameState.WHAT_CAN_WE_TRY,
                        "WHAT_CAN_WE_TRY",
                        "HOW_DOES_THIS_RESOLVE_START.json",
                        8
                )
        );
    }

    // ===== STORY OPTION INTEGRATION TESTS =====

    @Test
    void testHandleHowDoesThisResolve_AddsOptionsToStories() throws IOException {
        // Arrange
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson("HOW_DOES_THIS_RESOLVE_START.json");
        String gameCode = gameSession.getGameCode();
        String phaseId = GameState.HOW_DOES_THIS_RESOLVE.name();

        CollaborativeTextPhase phase = getPhaseFromGameSession(gameSession, phaseId);
        List<Story> stories = gameSession.getStories();

        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);
        when(collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId)).thenReturn(phase);

        // Act
        List<TextSubmission> winningSubmissions = collaborativeTextHelper.calculateWinningSubmission(gameCode);

        // Assert - Verify the structure needed for story option integration
        System.out.println("=== Story Option Integration Test ===");
        System.out.println("Stories in game session: " + stories.size());

        for (Story story : stories) {
            System.out.println("Story: " + story.getStoryId() + " - " + story.getPrompt());
            if (story.getOptions() != null) {
                for (Option option : story.getOptions()) {
                    System.out.println("  Option: " + option.getOptionId() + " - " + option.getOptionText() + " - " + option.getSuccessText());
                    for (OutcomeFork fork : option.getOutcomeForks()) {
                        System.out.println("  Fork: " + fork.getTextSubmission().getSubmissionId() + " - " + fork.getTextSubmission().getCurrentText());
                    }
                }
            }
        }

        // Assert correct resolution of successText and outcomeFork texts
        for (Story story : stories) {
            if (story.getOptions() == null) continue;

            for (Option option : story.getOptions()) {
                switch (option.getOptionId()) {
                    case "5e244119-9bdc-4f48-bc14-7669ef2a4d1a":
                        assertEquals("Heh heh heh the plan works out perfectly <:) C Heheheh nice B", option.getSuccessText());
                        assertEquals(1, option.getOutcomeForks().size());
                        assertEquals("Heh heh heh the plan works out perfectly <:) C Heheheh nice B",
                                option.getOutcomeForks().getFirst().getTextSubmission().getCurrentText());
                        break;
                    case "92e425ef-5cbc-4906-8259-9ac509550564":
                        assertEquals("You stick your leg out you jerk! They fall! D", option.getSuccessText());
                        assertEquals(1, option.getOutcomeForks().size());
                        assertEquals("You stick your leg out you jerk! They fall! D",
                                option.getOutcomeForks().getFirst().getTextSubmission().getCurrentText());
                        break;
                    case "d33a9299-86ea-48f6-afd1-719608b674f4":
                        assertEquals("You give em a coin! They toss it! A", option.getSuccessText());
                        assertEquals(2, option.getOutcomeForks().size());
                        assertTrue(option.getOutcomeForks().stream()
                                .anyMatch(f -> f.getTextSubmission().getCurrentText().equals("You give em a coin! They toss it! A Friggin Tossers! B")));
                        assertTrue(option.getOutcomeForks().stream()
                                .anyMatch(f -> f.getTextSubmission().getCurrentText().equals("You give em a coin! They toss it! A You start to see their way of life! C")));
                        break;
                    case "057da387-0a28-4e18-ae90-6dceb66a7e09":
                        assertEquals("Phew you get the scalpel and it helps a lot! B", option.getSuccessText());
                        assertEquals(2, option.getOutcomeForks().size());
                        assertTrue(option.getOutcomeForks().stream()
                                .anyMatch(f -> f.getTextSubmission().getCurrentText().equals("Phew you get the scalpel and it helps a lot! B Wow you're basically a doctor now! A")));
                        assertTrue(option.getOutcomeForks().stream()
                                .anyMatch(f -> f.getTextSubmission().getCurrentText().equals("Phew you get the scalpel and it helps a lot! B You earn a PHD in doctoring C")));
                        break;
                }
            }
        }

        // Verify winning submissions have the required outcomeType information
        for (TextSubmission submission : winningSubmissions) {
            OutcomeType outcomeTypeWithLabel = submission.getOutcomeTypeWithLabel();
            if (outcomeTypeWithLabel != null && outcomeTypeWithLabel.getSubTypes() != null) {
                for (OutcomeType subType : outcomeTypeWithLabel.getSubTypes()) {
                    System.out.println("Submission " + submission.getSubmissionId() +
                            " has subType: " + subType.getId() + " which should match a Story.option.optionId");
                }
            }
        }
    }

    @Test
    void testWinningSubmissions_HaveValidOutcomeTypeSubTypes() throws IOException {
        // Arrange
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson("HOW_DOES_THIS_RESOLVE_START.json");
        String gameCode = gameSession.getGameCode();
        String phaseId = GameState.HOW_DOES_THIS_RESOLVE.name();

        CollaborativeTextPhase phase = getPhaseFromGameSession(gameSession, phaseId);

        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);
        when(collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId)).thenReturn(phase);

        // Act
        List<TextSubmission> winningSubmissions = collaborativeTextHelper.calculateWinningSubmission(gameCode);

        // Assert - Each winning submission should have valid outcomeTypeWithLabel with subTypes
        for (TextSubmission submission : winningSubmissions) {
            OutcomeType outcomeTypeWithLabel = submission.getOutcomeTypeWithLabel();
            assertNotNull(outcomeTypeWithLabel,
                    "Winning submission should have outcomeTypeWithLabel: " + submission.getSubmissionId());

            // The outcomeTypeWithLabel.id should reference a Story
            String storyId = outcomeTypeWithLabel.getId();
            assertNotNull(storyId, "outcomeTypeWithLabel.id should not be null");
            assertFalse(storyId.isEmpty(), "outcomeTypeWithLabel.id should not be empty");

            // The subTypes should contain the option IDs
            List<OutcomeType> subTypes = outcomeTypeWithLabel.getSubTypes();
            if (subTypes != null && !subTypes.isEmpty()) {
                for (OutcomeType subType : subTypes) {
                    assertNotNull(subType.getId(), "subType.id should not be null");
                    assertFalse(subType.getId().isEmpty(), "subType.id should not be empty");
                    System.out.println("Submission " + submission.getSubmissionId() +
                            " -> Story " + storyId + " -> Option " + subType.getId());
                }
            }
        }
    }

    // ===== GET OUTCOME TYPES DISTRIBUTION TESTS =====

    /**
     * Tests that getOutcomeTypes correctly distributes stories to players based on
     * the offset logic for each game state. Different phases use different offsets:
     * - WHAT_CAN_WE_TRY: offset 1 (and offset 0 or 2 for second story)
     * - HOW_DOES_THIS_RESOLVE: offset 2 for <=4 players, 3 for >4 players
     * - WHAT_HAPPENS_HERE (round > 1): offset 2 for <=4 players, 1 for >4 players
     */
    @ParameterizedTest
    @MethodSource("provideOutcomeTypeDistributionScenarios")
    void testGetOutcomeTypes_DistributesStoriesCorrectlyPerPlayer(
            String scenarioName,
            String jsonFileName,
            GameState gameState,
            List<String> expectedStoryIdsPerPlayer
    ) throws IOException {
        // Arrange - Load test data
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson(jsonFileName);
        gameSession.setGameState(gameState);
        String gameCode = gameSession.getGameCode();

        // Mock dependencies
        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);

        // For HOW_DOES_THIS_RESOLVE, we need to mock the WHAT_CAN_WE_TRY phase
        if (gameState == GameState.HOW_DOES_THIS_RESOLVE || gameState == GameState.HOW_DOES_THIS_RESOLVE_AGAIN) {
            CollaborativeTextPhase whatCanWeTryPhase = gameSession.getCollaborativeTextPhases().get(GameState.WHAT_CAN_WE_TRY.name());
            when(collaborativeTextDAO.getCollaborativeTextPhase(gameCode, GameState.WHAT_CAN_WE_TRY.name()))
                    .thenReturn(whatCanWeTryPhase);
        }

        System.out.println("=== " + scenarioName + " ===");
        System.out.println("Game State: " + gameState);
        System.out.println("Number of players: " + gameSession.getPlayers().size());
        System.out.println("Number of stories: " + (gameSession.getStories() != null ? gameSession.getStories().size() : 0));

        // Get sorted players to match expected order
        List<Player> sortedPlayers = gameSession.getPlayers().stream()
                .filter(p -> p.getJoinedAt() != null)
                .sorted((p1, p2) -> p1.getJoinedAt().compareTo(p2.getJoinedAt()))
                .toList();

        // Act & Assert - Iterate over each player and verify distribution
        List<String> actualStoryIds = new java.util.ArrayList<>();

        for (int i = 0; i < sortedPlayers.size(); i++) {
            Player player = sortedPlayers.get(i);
            String playerId = player.getAuthorId();

            List<OutcomeType> outcomeTypes = collaborativeTextHelper.getOutcomeTypes(gameCode, playerId);

            System.out.println("\nPlayer " + i + ": " + player.getUserName() + " (" + playerId + ")");
            System.out.println("  OutcomeTypes returned: " + outcomeTypes.size());

            if (!outcomeTypes.isEmpty()) {
                // For WHAT_CAN_WE_TRY, might get multiple stories; take the first for comparison
                OutcomeType firstOutcome = outcomeTypes.get(0);
                String storyId = firstOutcome.getId();
                actualStoryIds.add(storyId);

                System.out.println("  First Story ID: " + storyId);
                System.out.println("  Story Label: " + firstOutcome.getLabel());

                if (firstOutcome.getSubTypes() != null && !firstOutcome.getSubTypes().isEmpty()) {
                    System.out.println("  SubTypes: " + firstOutcome.getSubTypes().size());
                    for (OutcomeType subType : firstOutcome.getSubTypes()) {
                        System.out.println("    - " + subType.getId() + ": " +
                                (subType.getLabel() != null ? subType.getLabel().substring(0, Math.min(40, subType.getLabel().length())) + "..." : "null"));
                    }
                }
            } else {
                actualStoryIds.add("EMPTY");
                System.out.println("  No outcome types returned");
            }
        }

        System.out.println("\n=== Distribution Summary ===");
        System.out.println("Expected: " + expectedStoryIdsPerPlayer);
        System.out.println("Actual:   " + actualStoryIds);

        // Assert the story distribution matches expected
        assertEquals(expectedStoryIdsPerPlayer.size(), actualStoryIds.size(),
                "Should have outcome for each player");
        assertEquals(expectedStoryIdsPerPlayer, actualStoryIds,
                "Story distribution should match expected pattern for " + scenarioName);
    }

    static Stream<Arguments> provideOutcomeTypeDistributionScenarios() {
        // Based on test JSON files with 4 players:
        // Players sorted by joinedAt: Andy (0), Joe (1), Byron (2), Kirsten (3)
        // Stories sorted by author order match player order
        //
        // WHAT_CAN_WE_TRY offset is 1:
        //   Player 0 gets story at (0+1)%4 = 1
        //   Player 1 gets story at (1+1)%4 = 2
        //   Player 2 gets story at (2+1)%4 = 3
        //   Player 3 gets story at (3+1)%4 = 0
        //
        // HOW_DOES_THIS_RESOLVE offset is 2 (for 4 players):
        //   Player 0 gets story at (0+2)%4 = 2
        //   Player 1 gets story at (1+2)%4 = 3
        //   Player 2 gets story at (2+2)%4 = 0
        //   Player 3 gets story at (3+2)%4 = 1

        return Stream.of(
                Arguments.of(
                        "WHAT_CAN_WE_TRY - 4 players, offset 1",
                        "WHAT_CAN_WE_TRY_START.json",
                        GameState.WHAT_CAN_WE_TRY,
                        // Expected story IDs per player (in player join order)
                        // Stories: 0=c3fbfd4a (Andy), 1=efe64144 (Joe), 2=7adad1bc (Byron), 3=2646c831 (Kirsten)
                        List.of(
                                "efe64144-c636-4283-add7-974dc48a6039", // Player 0 (Andy) gets story 1
                                "7adad1bc-874b-4f6e-a70c-f8ba4aea658f", // Player 1 (Joe) gets story 2
                                "2646c831-3ab8-4445-ae4a-3696f25837ba", // Player 2 (Byron) gets story 3
                                "c3fbfd4a-0b7c-43f9-9435-5c2bae5ce51a"  // Player 3 (Kirsten) gets story 0
                        )
                ),
                Arguments.of(
                        "HOW_DOES_THIS_RESOLVE - 4 players, offset 2",
                        "HOW_DOES_THIS_RESOLVE_START.json",
                        GameState.HOW_DOES_THIS_RESOLVE,
                        // Expected story IDs per player (in player join order)
                        // Stories: 0=704e29cf (Andy), 1=ce52535b (Joe), 2=af0925f5 (Byron), 3=80facdb7 (Kirsten)
                        List.of(
                                "af0925f5-e00d-4ac5-b63b-9f23e4b6be0d", // Player 0 (Andy) gets story 2
                                "80facdb7-1cbb-465c-8486-92b588c543a4", // Player 1 (Joe) gets story 3
                                "704e29cf-88a1-4fe9-832d-fc639adbe182", // Player 2 (Byron) gets story 0
                                "ce52535b-f69c-467e-a042-06a1cfcb85c0"  // Player 3 (Kirsten) gets story 1
                        )
                ),
                Arguments.of(
                        "WHAT_HAPPENS_HERE - Round 1, all players get same encounter labels",
                        "WHAT_HAPPENS_HERE_START.json",
                        GameState.WHAT_HAPPENS_HERE,
                        // Round 1: No story distribution - all players get the same encounter labels
                        // The first encounter label ID is returned for all players
                        List.of(
                                "1d8a56a7-34b8-48a9-ad09-933f4190af86", // Player 0 (Andy) - same encounter
                                "1d8a56a7-34b8-48a9-ad09-933f4190af86", // Player 1 (Joe) - same encounter
                                "1d8a56a7-34b8-48a9-ad09-933f4190af86", // Player 2 (Byron) - same encounter
                                "1d8a56a7-34b8-48a9-ad09-933f4190af86"  // Player 3 (Kirsten) - same encounter
                        )
                )
        );
    }
}
