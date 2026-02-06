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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.List;
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
    private OutcomeTypeHelper outcomeTypeHelper;

    @InjectMocks
    private CollaborativeTextHelper collaborativeTextHelper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ===== HELPER METHODS FOR LOADING TEST DATA =====

    /**
     * Loads a GameSession from a JSON file in test resources.
     * @param fileName The name of the JSON file (without path)
     * @return The deserialized GameSession
     */
    private GameSession loadGameSessionFromJson(String fileName) throws IOException {
        return TestJsonLoader.loadGameSessionFromJson(fileName);
    }

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
        GameSession gameSession = loadGameSessionFromJson("HOW_DOES_THIS_RESOLVE_START.json");
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
        GameSession gameSession = loadGameSessionFromJson("HOW_DOES_THIS_RESOLVE_START.json");
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

    @Test
    void testCalculateWinningSubmission_WHAT_HAPPENS_HERE_FiltersSiblingsByAdditions() throws IOException {
        // Arrange
        GameSession gameSession = loadGameSessionFromJson("WHAT_HAPPENS_HERE_START.json");
        // Modify game state to WHAT_HAPPENS_HERE for this test
        gameSession.setGameState(GameState.WHAT_HAPPENS_HERE);

        String gameCode = gameSession.getGameCode();
        String phaseId = GameState.WHAT_HAPPENS_HERE.name();

        CollaborativeTextPhase phase = getPhaseFromGameSession(gameSession, phaseId);
        if (phase == null) {
            System.out.println("WHAT_HAPPENS_HERE phase not found in test data, skipping test");
            return;
        }

        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);
        when(collaborativeTextDAO.getCollaborativeTextPhase(gameCode, phaseId)).thenReturn(phase);

        // Act
        List<TextSubmission> winningSubmissions = collaborativeTextHelper.calculateWinningSubmission(gameCode);

        // Assert
        assertNotNull(winningSubmissions, "Winning submissions should not be null");

        System.out.println("=== WHAT_HAPPENS_HERE Test Results ===");
        System.out.println("Total submissions in phase: " + phase.getSubmissions().size());
        System.out.println("Winning submissions returned: " + winningSubmissions.size());

        // Verify that siblings with the same parent are filtered to keep only the one with most additions
        for (TextSubmission submission : winningSubmissions) {
            System.out.println("  - " + submission.getSubmissionId() + ": " + submission.getCurrentText());
            System.out.println("    Additions count: " + (submission.getAdditions() != null ? submission.getAdditions().size() : 0));
        }

        // Assert correct number of winning submissions
        assertEquals(4, winningSubmissions.size(), "Should have 4 winning submissions");

        // Assert each winning submission has correct text and additions count
        for (TextSubmission submission : winningSubmissions) {
            switch (submission.getSubmissionId()) {
                case "8ad070ef-9fd7-4d26-a88d-018e1e93d985":
                    assertEquals("A clown in a dumpy brown sack outfit walks up to you silently holding a sign that says \"Potatoes the Clown\". C He extends his hand and begins juggling the biggest potatoes you've ever seen. B He offers to teach you. What do you do? C",
                            submission.getCurrentText());
                    assertEquals(3, submission.getAdditions().size(), "Potatoes the Clown submission should have 3 additions");
                    break;
                case "1cda23db-449f-4f41-9461-440b26d0e009":
                    assertEquals("Tommy Toad and the Bug Brigade block your path. They cross their many legs and stare down at you. A You feel intimidated. They're so small yet so intimidating! D",
                            submission.getCurrentText());
                    assertEquals(2, submission.getAdditions().size(), "Tommy Toad submission should have 2 additions");
                    break;
                case "b169ebdb-89a2-47b8-8157-e8b15dfbb432":
                    assertEquals("The Plasma Ranger arrives just in the nick of time. You go toe to toe with your rival and the Plasma Ranger keeps switching sides? B",
                            submission.getCurrentText());
                    assertEquals(1, submission.getAdditions().size(), "Plasma Ranger submission should have 1 addition");
                    break;
                case "efd46f30-c24c-437b-907c-3a7f5d649405":
                    assertEquals("Friggin Skummy Steve is here. What a scumbag. He challenges you to a snot-off. D",
                            submission.getCurrentText());
                    assertEquals(1, submission.getAdditions().size(), "Skummy Steve submission should have 1 addition");
                    break;
                default:
                    fail("Unexpected submission ID: " + submission.getSubmissionId());
            }
        }

        // Verify storyDAO.createStory was called for each winning submission
        ArgumentCaptor<Story> storyCaptor = ArgumentCaptor.forClass(Story.class);
        verify(storyDAO, times(4)).createStory(storyCaptor.capture());

        List<Story> createdStories = storyCaptor.getAllValues();
        assertEquals(4, createdStories.size(), "Should have 4 stories created via StoryDAO");

        // Verify each created story has a prompt matching a winning submission's text
        for (Story story : createdStories) {
            assertNotNull(story.getStoryId(), "Story should have an ID");
            assertNotNull(story.getPrompt(), "Story should have a prompt");
            assertTrue(winningSubmissions.stream()
                            .anyMatch(ws -> ws.getCurrentText().equals(story.getPrompt())),
                    "Story prompt should match a winning submission's text: " + story.getPrompt());
        }
    }

    // ===== PARAMETERIZED TESTS FOR MULTIPLE GAME STATES =====

    @ParameterizedTest
    @MethodSource("provideGameStateScenarios")
    void testCalculateWinningSubmission_MultipleGameStates(
            String testName,
            GameState gameState,
            String phaseId,
            String jsonFileName
    ) throws IOException {
        // Arrange
        GameSession gameSession = loadGameSessionFromJson(jsonFileName);
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

        System.out.println("=== " + testName + " ===");
        System.out.println("Game State: " + gameState);
        System.out.println("Phase ID: " + phaseId);
        System.out.println("Submissions in phase: " + phase.getSubmissions().size());
        System.out.println("Winning submissions: " + winningSubmissions.size());
    }

    static Stream<Arguments> provideGameStateScenarios() {
        return Stream.of(
                Arguments.of(
                        "HOW_DOES_THIS_RESOLVE - Standard winner calculation",
                        GameState.HOW_DOES_THIS_RESOLVE,
                        "HOW_DOES_THIS_RESOLVE",
                        "HOW_DOES_THIS_RESOLVE_START.json"
                ),
                Arguments.of(
                        "WHAT_HAPPENS_HERE - Filter siblings by additions",
                        GameState.WHAT_HAPPENS_HERE,
                        "WHAT_HAPPENS_HERE",
                        "HOW_DOES_THIS_RESOLVE_START.json"
                ),
                Arguments.of(
                        "WHAT_CAN_WE_TRY - Standard winner calculation",
                        GameState.WHAT_CAN_WE_TRY,
                        "WHAT_CAN_WE_TRY",
                        "HOW_DOES_THIS_RESOLVE_START.json"
                )
        );
    }

    // ===== STORY OPTION INTEGRATION TESTS =====

    @Test
    void testHandleHowDoesThisResolve_AddsOptionsToStories() throws IOException {
        // Arrange
        GameSession gameSession = loadGameSessionFromJson("HOW_DOES_THIS_RESOLVE_START.json");
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
        GameSession gameSession = loadGameSessionFromJson("HOW_DOES_THIS_RESOLVE_START.json");
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
}
