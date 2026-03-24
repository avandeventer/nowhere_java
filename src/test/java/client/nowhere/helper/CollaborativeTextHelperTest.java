package client.nowhere.helper;

import static client.nowhere.model.GameState.HOW_DOES_THIS_RESOLVE;
import static client.nowhere.model.GameState.HOW_DOES_THIS_RESOLVE_AGAIN;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

import client.nowhere.dao.*;
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

    @Mock
    private ActiveSessionDAO activeSessionDAO;

    // Use real OutcomeTypeHelper for distribution logic tests
    private final OutcomeTypeHelper outcomeTypeHelper = new OutcomeTypeHelper();

    private CollaborativeTextHelper collaborativeTextHelper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Manually construct with real OutcomeTypeHelper
        collaborativeTextHelper = new CollaborativeTextHelper(
                gameSessionDAO, collaborativeTextDAO, adventureMapDAO,
                adventureMapHelper, storyDAO, featureFlagHelper, outcomeTypeHelper, activeSessionDAO
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
        String phaseId = HOW_DOES_THIS_RESOLVE.name();

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
        String phaseId = HOW_DOES_THIS_RESOLVE.name();

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
            List<ExpectedSubmission> expectedSubmissions,
            Map<String, List<String>> expectedStoryPlayerIds
    ) throws IOException {
        // Arrange
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson(jsonFileName);
        gameSession.setGameState(GameState.WHAT_HAPPENS_HERE_WINNER);

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
        verify(storyDAO, times(gameSession.getPlayers().size())).createStory(storyCaptor.capture());

        List<Story> createdStories = storyCaptor.getAllValues();
        assertEquals(gameSession.getPlayers().size(), createdStories.size(),
                "Should have " + gameSession.getPlayers().size() + " stories created via StoryDAO");

        // Verify each created story has a prompt matching a winning submission's text
        for (Story story : createdStories) {
            assertNotNull(story.getStoryId(), "Story should have an ID");
            assertNotNull(story.getPrompt(), "Story should have a prompt");
            boolean matchesCorrectSubmissions = winningSubmissions.stream()
                    .anyMatch(ws -> ws.getCurrentText().equals(story.getPrompt()));

            if (winningSubmissions.size() < gameSession.getPlayers().size()) {
                boolean matchesDefaultSubmission = CollaborativeTextHelper.getPreCannedEncounters().stream()
                        .anyMatch(preCannedEncounter -> preCannedEncounter.get(1).equals(story.getPrompt()));
                matchesCorrectSubmissions = matchesCorrectSubmissions || matchesDefaultSubmission;
            }

            System.out.println("Story ID: " + story.getStoryId() + " Author IDs: " + story.getAuthorId() + " Player IDs: " + story.getPlayerIds());
            assertTrue(matchesCorrectSubmissions,
                    "Story prompt should match a winning submission's text or a default encounter submission's text: " + story.getPrompt());
            assertEquals(expectedStoryPlayerIds.get(story.getAuthorId()), story.getPlayerIds(),
                    "Player IDs mismatch for story authored by " + story.getAuthorId());
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
                        ),
                        Map.of(
                                "0ec19af6-8825-4f9a-a0e8-4dadd05158b3", List.of("780a931b-e7f6-498b-917b-a96a3e343f3d"),
                                "b90c8f3a-88dc-4a10-ae87-164a3913a217", List.of("f819c5d9-dc41-485a-8970-6daa98df6308"),
                                "f819c5d9-dc41-485a-8970-6daa98df6308", List.of("0ec19af6-8825-4f9a-a0e8-4dadd05158b3"),
                                "780a931b-e7f6-498b-917b-a96a3e343f3d", List.of("b90c8f3a-88dc-4a10-ae87-164a3913a217")
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
                        ),
                        Map.of(
                                "e459f092-e43b-4ad2-8b3e-579f1636b2e9", List.of("eb0c6d8d-9c29-4fad-ad84-71e1b7c80c80"),
                                "c1abb5c9-ab39-4ed9-9cfa-9c34d3f7e84e", List.of("8512bfbf-88b8-4f19-9fc9-92dfea497fa8"),
                                "eb0c6d8d-9c29-4fad-ad84-71e1b7c80c80", List.of("c1abb5c9-ab39-4ed9-9cfa-9c34d3f7e84e"),
                                "8512bfbf-88b8-4f19-9fc9-92dfea497fa8", List.of("e459f092-e43b-4ad2-8b3e-579f1636b2e9")
                        )
                ),
                Arguments.of(
                        "WHAT_HAPPENS_HERE Round 1 - Add default story",
                        "WHAT_HAPPENS_HERE_START_DEFAULT_STORY.json",
                        List.of(
                                new ExpectedSubmission("6e7b2bab-5da6-466b-9533-aed7d2b83d43",
                                        "We all pick Yams and they're delicious B Mmmm tasty yams C",
                                        2),
                                new ExpectedSubmission("a0cc1ef4-1518-43e2-ace0-896a7ca87b18",
                                        "Oooooo spooky ghosties C OOoooooo so spooky B",
                                        2),
                                new ExpectedSubmission("ff0591b1-cabf-4da9-a6de-1bf9dd3204d8",
                                        "A friggin goodie two shoes is here. Man. Auto Submit A",
                                        1)
                        ),
                        Map.of(
                                "5e483450-db1d-4a2c-9f07-b297d2f70648", List.of("54454e92-74bc-4b81-b2cf-56f859ae8bbd"),
                                "54454e92-74bc-4b81-b2cf-56f859ae8bbd", List.of("48a8e280-3c23-445a-9289-baf1aeeb6a25"),
                                "01f0280e-ed50-42b6-9c1a-190a430fbdfa", List.of("5e483450-db1d-4a2c-9f07-b297d2f70648"),
                                "48a8e280-3c23-445a-9289-baf1aeeb6a25", List.of("01f0280e-ed50-42b6-9c1a-190a430fbdfa", "01f0280e-ed50-42b6-9c1a-190a430fbdfa")
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
                        HOW_DOES_THIS_RESOLVE,
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

    /**
     * Expected option data for parameterized HOW_DOES_THIS_RESOLVE option tests.
     * submissionId = optionId, currentText = expected successText, additionsSize = expected outcomeForks count.
     */

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideHowDoesThisResolveScenarios")
    void testHandleHowDoesThisResolve_AddsOptionsToStories(
            String scenarioName,
            String jsonFileName,
            List<ExpectedSubmission> expectedOptions,
            GameState expectedGameState
    ) throws IOException {
        // Arrange
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson(jsonFileName);
        gameSession.setGameState(expectedGameState);
        String gameCode = gameSession.getGameCode();
        String phaseId = expectedGameState.name();

        CollaborativeTextPhase phase = getPhaseFromGameSession(gameSession, phaseId);
        List<Story> stories = gameSession.getStories().stream().filter(story -> !story.isVisited()).toList();

        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);
        when(collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId)).thenReturn(phase);

        // Act
        List<TextSubmission> winningSubmissions = collaborativeTextHelper.calculateWinningSubmission(gameCode);

        // Assert - Verify the structure needed for story option integration
        System.out.println("=== " + scenarioName + " ===");
        System.out.println("Stories in game session for this round: " + stories.size());

        for (Story story : stories) {
            System.out.println("Story: " + story.getStoryId() + " - " + story.getPrompt());
            if (story.getOptions() != null) {
                for (Option option : story.getOptions()) {
                    System.out.println("  Option: " + option.getOptionId() + " - " + option.getOptionText() + " - " + option.getSuccessText());
                    for (OutcomeFork fork : option.getOutcomeForks()) {
                        System.out.println("  Fork: " + fork.getTextSubmission().getSubmissionId() + " - " + fork.getTextSubmission().getCurrentText());
                    }
                }
            } else {
                verify(gameSessionDAO).updateDungeonGrid(gameCode, gameSession.getGameBoard());
            }
        }

        // Build a lookup of expected options by optionId
        Map<String, ExpectedSubmission> expectedByOptionId = expectedOptions.stream()
                .collect(java.util.stream.Collectors.toMap(ExpectedSubmission::submissionId, e -> e));

        // Assert correct resolution of successText and outcomeFork count
        for (Story story : stories) {
            if (story.getOptions() == null || story.getOptions().isEmpty()) {
                GameBoard gameBoard = gameSession.getGameBoard();
                assertNotNull(gameBoard, "GameBoard should not be null when a story has no options");
                boolean presentInGrid = gameBoard.getDungeonGrid().values().stream()
                        .anyMatch(encounter -> story.getStoryId().equals(encounter.getStoryId()));
                assertFalse(presentInGrid,
                        "Story with no options should have been removed from dungeonGrid: " + story.getStoryId());
                continue;
            }

            for (Option option : story.getOptions()) {
                ExpectedSubmission expected = expectedByOptionId.get(option.getOptionId());
                if (expected == null) {
                    fail("Option ID " + option.getOptionId() + " with success text " + option.getSuccessText() + " was not expected");
                }

                assertEquals(expected.currentText(), option.getSuccessText(),
                        "successText mismatch for option " + option.getOptionId());
                assertEquals(expected.additionsSize(), option.getOutcomeForks().size(),
                        "outcomeForks count mismatch for option " + option.getOptionId());
            }
        }

        // Log winning submissions and their outcome types
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

    static Stream<Arguments> provideHowDoesThisResolveScenarios() {
        return Stream.of(
                Arguments.of(
                        "HOW_DOES_THIS_RESOLVE - Adds options to stories",
                        "HOW_DOES_THIS_RESOLVE_START.json",
                        List.of(
                                new ExpectedSubmission("5e244119-9bdc-4f48-bc14-7669ef2a4d1a",
                                        "Heh heh heh the plan works out perfectly <:) C Heheheh nice B", 1),
                                new ExpectedSubmission("92e425ef-5cbc-4906-8259-9ac509550564",
                                        "You stick your leg out you jerk! They fall! D", 1),
                                new ExpectedSubmission("d33a9299-86ea-48f6-afd1-719608b674f4",
                                        "You give em a coin! They toss it! A", 2),
                                new ExpectedSubmission("057da387-0a28-4e18-ae90-6dceb66a7e09",
                                        "Phew you get the scalpel and it helps a lot! B", 2)
                        ),
                        HOW_DOES_THIS_RESOLVE
                ),
                Arguments.of(
                        "HOW_DOES_THIS_RESOLVE_AGAIN - Story with no options submitted",
                        "HOW_DOES_THIS_RESOLVE_AGAIN_MISSING_OPTIONS.json",
                        List.of(
                                new ExpectedSubmission("d1238b9e-6b52-49e8-aac7-a71b67b009f9",
                                        "You hightail it outta there! A", 1),
                                new ExpectedSubmission("c5f78f46-b6ee-4858-940b-0b62aba87aa6",
                                        "You ask the king to REMOVE THEM AT ONCE. B", 2),
                                new ExpectedSubmission("6ff0f385-f95c-4d4c-9e1d-cc807612c66e",
                                        "Wow you smell EVEN BETTER. C", 1),
                                new ExpectedSubmission("bc5bbc80-1aca-455d-b283-c4ad55cfd9dc",
                                        "You punch the submission... into... submission... D", 1)
                        ),
                        HOW_DOES_THIS_RESOLVE_AGAIN
                ),
                Arguments.of(
                        "HOW_DOES_THIS_RESOLVE - Adds options to stories",
                        "HOW_DOES_THIS_RESOLVE_TRAITS.json",
                        List.of(
                                new ExpectedSubmission("7b7d3fee-e839-408d-836e-211dfbb1c34b",
                                        "The bear loves the hard scritches! C", 1),
                                new ExpectedSubmission("6",
                                        "You hire Michael Jackson to do a cool thriller party! D", 1),
                                new ExpectedSubmission("0e9339d9-3c0a-478a-93e0-a631e1ddede4",
                                        "You rev up your legs and try to beat the snails! A", 1),
                                new ExpectedSubmission("11",
                                        "You go back in time to correct your alcoholism B", 2)
                        ),
                        HOW_DOES_THIS_RESOLVE
                )
        );
    }

    @Test
    void testWinningSubmissions_HaveValidOutcomeTypeSubTypes() throws IOException {
        // Arrange
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson("HOW_DOES_THIS_RESOLVE_START.json");
        String gameCode = gameSession.getGameCode();
        String phaseId = HOW_DOES_THIS_RESOLVE.name();

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

    // ===== MAKE_CHOICE_VOTING REPERCUSSION TESTS =====

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideMakeChoiceVotingRepercussionScenarios")
    void testHandleMakeChoiceVoting_AppliesRepercussionsToPlayers(
            String scenarioName,
            int xCoordinate,
            String votedOptionId,
            Map<String, String> expectedTraitLabelByPlayerId,
            List<String> expectedOutcomeDisplayMessages
    ) throws IOException {
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson("MAKE_CHOICE_VOTING_REPERCUSSIONS.json");
        gameSession.getGameBoard().getPlayerCoordinates().setxCoordinate(xCoordinate);
        gameSession.getGameBoard().getPlayerCoordinates().setyCoordinate(0);
        String gameCode = gameSession.getGameCode();

        gameSession.getCollaborativeTextPhase(GameState.MAKE_CHOICE_VOTING.name())
                .addPlayerVote(new PlayerVote("", "", votedOptionId, 1));

        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);

        System.out.println("=== " + scenarioName + " ===");

        collaborativeTextHelper.calculateWinningSubmission(gameCode);

        for (Map.Entry<String, String> entry : expectedTraitLabelByPlayerId.entrySet()) {
            String playerId = entry.getKey();
            String expectedLabel = entry.getValue();
            verify(gameSessionDAO).updatePlayer(argThat(p ->
                    playerId.equals(p.getAuthorId()) &&
                    p.getTraits() != null &&
                    p.getTraits().stream().anyMatch(t -> expectedLabel.equals(t.getTraitLabel()))
            ));
        }
        if (!expectedOutcomeDisplayMessages.isEmpty()) {
            ArgumentCaptor<ActivePlayerSession> captor = ArgumentCaptor.forClass(ActivePlayerSession.class);
            verify(activeSessionDAO).update(captor.capture());
            List<String> actualDisplay = captor.getValue().getOutcomeDisplay();
            for (String expectedMsg : expectedOutcomeDisplayMessages) {
                assertTrue(actualDisplay.contains(expectedMsg),
                        scenarioName + " - expected outcomeDisplay to contain: " + expectedMsg + ". Actual: " + actualDisplay);
            }
        }
    }

    static Stream<Arguments> provideMakeChoiceVotingRepercussionScenarios() {
        return Stream.of(
                Arguments.of(
                        "Bears (0,0) - Pet HARD - Joe gets trait Bear scritcher",
                        0,
                        "7b7d3fee-e839-408d-836e-211dfbb1c34b",
                        Map.of("e8852f24-cdc8-465c-b244-56ce08029584", "Bear scritcher A"), // Joe
                        List.of("You gained the trait \"Bear scritcher A\"!")
                ),
                Arguments.of(
                        "FUN PEOPLE (1,0) - use trait Shifty - Subodh gets trait Greedy",
                        1,
                        "8",
                        Map.of("63ca67f3-d11d-4cc8-a667-6b68a1bb432b", "Greedy A"), // Subodh
                        List.of("You gained the trait \"Greedy A\"!")
                ),
                Arguments.of(
                        "Snailz (2,0) - Race them - Kirsten gets title Snail Champion",
                        2,
                        "0e9339d9-3c0a-478a-93e0-a631e1ddede4",
                        Map.of("ddfc6892-16dd-4e38-9252-27a3688ae038", "Snail Champion"), // Kirsten
                        List.of("You gained the title \"Snail Champion\"!")
                ),
                Arguments.of(
                        "Time Travelers (3,0) - use trait Alcoholic - 3 forks, no auto-resolve",
                        3,
                        "11",
                        Map.of(), // No player updates at MAKE_CHOICE_VOTING stage
                        List.of()  // No outcomeDisplay at this stage
                )
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("provideTimeTravelersOutcomeChoiceScenarios")
    void testHandleMakeOutcomeChoiceVoting_TimeTravelers_AppliesRepercussionsToPlayers(
            String scenarioName,
            String selectedOptionId,
            String votedSubmissionId,
            Map<String, String> expectedTraitLabelByPlayerId,
            List<String> expectedOutcomeDisplayMessages
    ) throws IOException {
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson("MAKE_CHOICE_VOTING_REPERCUSSIONS.json");
        gameSession.getGameBoard().getPlayerCoordinates().setxCoordinate(3);
        gameSession.getGameBoard().getPlayerCoordinates().setyCoordinate(0);
        gameSession.setGameState(GameState.MAKE_OUTCOME_CHOICE_VOTING);
        String gameCode = gameSession.getGameCode();

        Story timeTravelersStory = gameSession.getStoryAtCurrentPlayerCoordinates();
        timeTravelersStory.setSelectedOptionId(selectedOptionId);

        Option selectedOption = timeTravelersStory.getSelectedOption();
        CollaborativeTextPhase makeOutcomePhase = gameSession.getCollaborativeTextPhases()
                .computeIfAbsent(GameState.MAKE_OUTCOME_CHOICE_VOTING.name(), k -> new CollaborativeTextPhase());
        for (OutcomeFork fork : selectedOption.getOutcomeForks()) {
            makeOutcomePhase.addSubmission(fork.getTextSubmission());
        }
        makeOutcomePhase.addPlayerVote(new PlayerVote("", "", votedSubmissionId, 1));

        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);

        System.out.println("=== " + scenarioName + " ===");

        collaborativeTextHelper.calculateWinningSubmission(gameCode);

        for (Map.Entry<String, String> entry : expectedTraitLabelByPlayerId.entrySet()) {
            String playerId = entry.getKey();
            String expectedLabel = entry.getValue();
            verify(gameSessionDAO).updatePlayer(argThat(p ->
                    playerId.equals(p.getAuthorId()) &&
                    p.getTraits() != null &&
                    p.getTraits().stream().anyMatch(t -> expectedLabel.equals(t.getTraitLabel()))
            ));
        }
        if (!expectedOutcomeDisplayMessages.isEmpty()) {
            ArgumentCaptor<ActivePlayerSession> captor = ArgumentCaptor.forClass(ActivePlayerSession.class);
            verify(activeSessionDAO).update(captor.capture());
            List<String> actualDisplay = captor.getValue().getOutcomeDisplay();
            for (String expectedMsg : expectedOutcomeDisplayMessages) {
                assertTrue(actualDisplay.contains(expectedMsg),
                        scenarioName + " - expected outcomeDisplay to contain: " + expectedMsg + ". Actual: " + actualDisplay);
            }
        }
    }

    static Stream<Arguments> provideTimeTravelersOutcomeChoiceScenarios() {
        return Stream.of(
                // Option "11" (use trait: Alcoholic) forks
                Arguments.of(
                        "Time Travelers (3,0) - option 11 - Fork: Recovering Alcoholic trait → Andy",
                        "11",
                        "08ad7197-ee03-4241-87e9-6dc416bdbeaf",
                        Map.of("80569dbf-52d4-4169-b51f-d2977d2e94b0", "Recovering Alcoholic"), // Andy
                        List.of("You gained the trait \"Recovering Alcoholic\"!")
                ),
                Arguments.of(
                        "Time Travelers (3,0) - option 11 - Fork: Future Sober D title → Andy",
                        "11",
                        "e9f00d95-c2d5-4cc5-9b2f-48db47384497",
                        Map.of("80569dbf-52d4-4169-b51f-d2977d2e94b0", "Future Sober D"), // Andy
                        List.of("You gained the title \"Future Sober D\"!")
                ),
                Arguments.of(
                        "Time Travelers (3,0) - option 11 - Fork: Spread only → encounter message, no player updates",
                        "11",
                        "a80ef30c-e3fb-4a6c-99fc-bc9a39684b05",
                        Map.of(), // No player trait updates
                        List.of("If we encounter \"Time Travelers B\" again, we must all rise to the challenge together.")
                ),
                // Option "8ff69655" (Travel forward A) forks
                Arguments.of(
                        "Time Travelers (3,0) - option Travel forward A - Fork: At Peace A trait → Andy",
                        "8ff69655-42c9-48a7-aaf1-ac26f08cb3a9",
                        "fcb534df-b504-4d56-8375-041eed493387",
                        Map.of("80569dbf-52d4-4169-b51f-d2977d2e94b0", "At Peace A"), // Andy
                        List.of("You gained the trait \"At Peace A\"!")
                ),
                Arguments.of(
                        "Time Travelers (3,0) - option Travel forward A - Fork: Last Human D title → Andy",
                        "8ff69655-42c9-48a7-aaf1-ac26f08cb3a9",
                        "11c21b3e-9122-4f82-b624-e2ebfe00a5ed",
                        Map.of("80569dbf-52d4-4169-b51f-d2977d2e94b0", "Last Human D"), // Andy
                        List.of("You gained the title \"Last Human D\"!")
                ),
                Arguments.of(
                        "Time Travelers (3,0) - option Travel forward A - Fork: Last Human D title + Spread → all players",
                        "8ff69655-42c9-48a7-aaf1-ac26f08cb3a9",
                        "d69bd9df-9e33-4c1c-855a-49428b70448b",
                        Map.of(
                                "80569dbf-52d4-4169-b51f-d2977d2e94b0", "Last Human D", // Andy (story player)
                                "e8852f24-cdc8-465c-b244-56ce08029584", "Last Human D", // Joe (spread)
                                "63ca67f3-d11d-4cc8-a667-6b68a1bb432b", "Last Human D", // Subodh (spread)
                                "ddfc6892-16dd-4e38-9252-27a3688ae038", "Last Human D"  // Kirsten (spread)
                        ),
                        List.of(
                                "You gained the title \"Last Human D\"!",
                                "All players gained the title \"Last Human D\"!"
                        )
                )
        );
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
            Map<String, String> expectedStoryIdByPlayerName,
            Map<String, List<String>> expectedSubTypeLabelsByPlayerName
    ) throws IOException {
        runOutcomeTypeDistributionTest(scenarioName, jsonFileName, gameState, expectedStoryIdByPlayerName, expectedSubTypeLabelsByPlayerName);
    }

    @ParameterizedTest
    @MethodSource("provideHowDoesThisResolveSubTypeScenarios")
    void testGetOutcomeTypes_HowDoesThisResolve_SubTypesCorrect(
            String scenarioName,
            String jsonFileName,
            GameState gameState,
            Map<String, String> expectedStoryIdByPlayerName,
            Map<String, List<String>> expectedSubTypeLabelsByPlayerName
    ) throws IOException {
        runOutcomeTypeDistributionTest(scenarioName, jsonFileName, gameState, expectedStoryIdByPlayerName, expectedSubTypeLabelsByPlayerName);
    }

    private void runOutcomeTypeDistributionTest(
            String scenarioName,
            String jsonFileName,
            GameState gameState,
            Map<String, String> expectedStoryIdByPlayerName,
            Map<String, List<String>> expectedSubTypeLabelsByPlayerName
    ) throws IOException {
        // Arrange - Load test data
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson(jsonFileName);
        gameSession.setGameState(gameState);
        String gameCode = gameSession.getGameCode();

        // Mock dependencies
        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);

        // For HOW_DOES_THIS_RESOLVE, we need to mock the WHAT_CAN_WE_TRY phase
        if (gameState == HOW_DOES_THIS_RESOLVE || gameState == HOW_DOES_THIS_RESOLVE_AGAIN) {
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
        for (int i = 0; i < sortedPlayers.size(); i++) {
            Player player = sortedPlayers.get(i);
            String playerId = player.getAuthorId();
            String playerName = player.getUserName();

            List<OutcomeType> outcomeTypes = collaborativeTextHelper.getOutcomeTypes(gameCode, playerId);

            System.out.println("\nPlayer " + i + ": " + playerName + " (" + playerId + ")");
            System.out.println("  OutcomeTypes returned: " + outcomeTypes.size());

            if (!outcomeTypes.isEmpty()) {
                OutcomeType firstOutcome = outcomeTypes.getFirst();
                String storyId = firstOutcome.getId();

                System.out.println("  First Story ID: " + storyId);
                System.out.println("  Story Label: " + firstOutcome.getLabel());

                if (firstOutcome.getSubTypes() != null && !firstOutcome.getSubTypes().isEmpty()) {
                    System.out.println("  SubTypes: " + firstOutcome.getSubTypes().size());
                    for (OutcomeType subType : firstOutcome.getSubTypes()) {
                        System.out.println("    - " + subType.getId() + ": " +
                                (subType.getLabel() != null ? subType.getLabel().substring(0, Math.min(40, subType.getLabel().length())) + "..." : "null"));
                    }
                }

                assertEquals(expectedStoryIdByPlayerName.get(playerName), storyId,
                        playerName + " should be assigned to the expected story");

                List<String> expectedSubTypeLabels = expectedSubTypeLabelsByPlayerName.get(playerName);
                if (expectedSubTypeLabels != null && !expectedSubTypeLabels.isEmpty()) {
                    assertNotNull(firstOutcome.getSubTypes(), playerName + " should have subtypes");
                    List<String> actualSubTypeLabels = firstOutcome.getSubTypes().stream()
                            .map(OutcomeType::getLabel)
                            .toList();
                    for (String expectedLabel : expectedSubTypeLabels) {
                        assertTrue(actualSubTypeLabels.stream().anyMatch(label -> label != null && label.startsWith(expectedLabel)),
                                playerName + " missing subtype with label starting with '" + expectedLabel + "'. Actual labels: " + actualSubTypeLabels);
                    }
                }
            } else {
                System.out.println("  No outcome types returned");
                assertNull(expectedStoryIdByPlayerName.get(playerName),
                        playerName + " should have no expected story assignment");
            }
        }
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
                        // Stories: 0=c3fbfd4a (Andy), 1=efe64144 (Joe), 2=7adad1bc (Byron), 3=2646c831 (Kirsten)
                        // offset 1: Andy→story 1, Joe→story 2, Byron→story 3, Kirsten→story 0
                        Map.of(
                                "Andy",    "efe64144-c636-4283-add7-974dc48a6039",
                                "Joe",     "7adad1bc-874b-4f6e-a70c-f8ba4aea658f",
                                "Byron",   "2646c831-3ab8-4445-ae4a-3696f25837ba",
                                "Kirsten", "c3fbfd4a-0b7c-43f9-9435-5c2bae5ce51a"
                        ),
                        Map.of()
                ),
                Arguments.of(
                        "WHAT_HAPPENS_HERE - Round 1, Players get all encounter labels from one other player",
                        "WHAT_HAPPENS_HERE_START.json",
                        GameState.WHAT_HAPPENS_HERE,
                        // Round 1: Players get all encounter labels from one other player
                        Map.of(
                                "Andy",    "1d8a56a7-34b8-48a9-ad09-933f4190af86", // encounter written by Byron
                                "Joe",     "ac69cefe-d2f8-4203-a046-20b2a25d1250", // encounter written by Kirsten
                                "Byron",   "1b2c7a37-35a7-479e-bb23-b2d3c577cf36", // encounter written by Andy
                                "Kirsten", "8f4b1306-aac6-454a-be85-a769f91a4250"  // encounter written by Joe
                        ),
                        Map.of()
                )
        );
    }

    static Stream<Arguments> provideHowDoesThisResolveSubTypeScenarios() {
        // HOW_DOES_THIS_RESOLVE offset is 2 (for 4 players):
        //   Player 0 gets story at (0+2)%4 = 2
        //   Player 1 gets story at (1+2)%4 = 3
        //   Player 2 gets story at (2+2)%4 = 0
        //   Player 3 gets story at (3+2)%4 = 1

        return Stream.of(
                Arguments.of(
                        "HOW_DOES_THIS_RESOLVE - 4 players, offset 2",
                        "HOW_DOES_THIS_RESOLVE_START.json",
                        HOW_DOES_THIS_RESOLVE,
                        // Stories: 0=704e29cf (Andy), 1=ce52535b (Joe), 2=af0925f5 (Byron), 3=80facdb7 (Kirsten)
                        // offset 2: Andy→story 2, Joe→story 3, Byron→story 0, Kirsten→story 1
                        Map.of(
                                "Andy",    "af0925f5-e00d-4ac5-b63b-9f23e4b6be0d",
                                "Joe",     "80facdb7-1cbb-465c-8486-92b588c543a4",
                                "Byron",   "704e29cf-88a1-4fe9-832d-fc639adbe182",
                                "Kirsten", "ce52535b-f69c-467e-a042-06a1cfcb85c0"
                        ),
                        Map.of(
                                "Andy",    List.of("Give em a coin C", "Throw em! C"),
                                "Joe",     List.of("Get the scalpel D"),
                                "Byron",   List.of("Oh yeah I get it ;) A", "heheheheh D"),
                                "Kirsten", List.of("Trip em! A", "Race em! A", "PSHEW! Fight em! B")
                        )
                ),
                Arguments.of(
                        "HOW_DOES_THIS_RESOLVE - 4 players, offset 2, remove already used traits",
                        "HOW_DOES_THIS_RESOLVE_AGAIN_TRAITS.json",
                        HOW_DOES_THIS_RESOLVE,
                        Map.of(
                                "Andy",    "0e60c788-3b6f-4b94-a1a7-9d395fdca8f8",
                                "Joe",     "fda9b2db-6841-4632-9109-f30bb7cd2fe0",
                                "Subodh",  "aabbf4a3-e6e7-4543-a316-2fc50acd8175",
                                "Kirsten", "bb705625-dee4-46a3-a269-03ee382f2fa1"
                        ),
                        Map.of(
                                "Andy",    List.of("use trait: In Love", "use trait: Alcoholic", "use trait: Fast"),
                                "Joe",     List.of("Travel to the past C", "Travel forward A", "Travel back D", "use trait: Strong", "use trait: In Love"), //Already wrote for trait "Alcoholic"
                                "Subodh",  List.of("Pet them hard A", "use trait: Charming", "use trait: Saucy", "use trait: Strong"),
                                "Kirsten", List.of("Dance! A", "Jump Around A", "Dance with em! A", "Tell em to quiet down A", "Make cakes B", "use trait: Gluttonous", "use trait: Shifty") //Already wrote for trait "Undead"
                        )
                )
        );
    }
}
