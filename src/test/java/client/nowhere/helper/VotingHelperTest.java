package client.nowhere.helper;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

/**
 * Unit tests for VotingHelper.
 * Tests the voting submission retrieval and player status updates for MAKE_CHOICE_VOTING phase.
 */
public class VotingHelperTest {

    @Mock
    private GameSessionDAO gameSessionDAO;

    @Mock
    private CollaborativeTextDAO collaborativeTextDAO;

    @Mock
    private ActiveSessionHelper activeSessionHelper;

    @Mock
    private StoryDAO storyDAO;

    // Use real OutcomeTypeHelper instead of mock
    private final OutcomeTypeHelper outcomeTypeHelper = new OutcomeTypeHelper();

    private VotingHelper votingHelper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Manually construct VotingHelper with real OutcomeTypeHelper
        votingHelper = new VotingHelper(
                gameSessionDAO,
                collaborativeTextDAO,
                activeSessionHelper,
                outcomeTypeHelper,
                storyDAO
        );
    }

    // ===== MAKE_CHOICE_VOTING TESTS =====

    /**
     * Tests that for MAKE_CHOICE_VOTING phase at different coordinates,
     * exactly one player gets submissions (the active player) and the rest are set to done.
     */
    @ParameterizedTest
    @MethodSource("provideCoordinates")
    void testGetVotingSubmissionsForPlayer_MAKE_CHOICE_VOTING_OneActivePlayerPerCoordinate(
            String gameSessionFileName,
            int xCoordinate,
            int yCoordinate,
            List<String> expectedActivePlayerIds
    ) throws IOException {
        // Arrange - Load test data
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson(gameSessionFileName);
        String gameCode = gameSession.getGameCode();

        // Set player coordinates to test different stories
        PlayerCoordinates coords = new PlayerCoordinates();
        coords.setxCoordinate(xCoordinate);
        coords.setyCoordinate(yCoordinate);
        gameSession.getGameBoard().setPlayerCoordinates(coords);

        // Get the story at current player coordinates (computed by GameSession)
        Story storyAtCoords = gameSession.getStoryAtCurrentPlayerCoordinates();
        Encounter encounterAtCoords = gameSession.getGameBoard().getEncounterAtPlayerCoordinates();

        // Mock the game session DAO
        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);

        System.out.println("=== Testing coordinates (" + xCoordinate + ", " + yCoordinate + ") ===");
        System.out.println("Story at coordinates: " + (storyAtCoords != null ? storyAtCoords.getPrompt().substring(0, Math.min(50, storyAtCoords.getPrompt().length())) + "..." : "null"));
        System.out.println("Encounter storyId: " + (encounterAtCoords != null ? encounterAtCoords.getStoryId() : "null"));

        // Act - Call the method for each player and track results
        int activePlayerCount = 0;
        int inactivePlayerCount = 0;
        List<String> activePlayerIds = new ArrayList<>();

        for (Player player : gameSession.getPlayers()) {
            String playerId = player.getAuthorId();
            List<TextSubmission> submissions = votingHelper.getVotingSubmissionsForPlayer(gameCode, playerId);

            if (submissions != null && !submissions.isEmpty()) {
                activePlayerCount++;
                activePlayerIds.add(playerId);
                System.out.println("  ACTIVE: " + player.getUserName() + " - got " + submissions.size() + " submissions");
            } else {
                inactivePlayerCount++;
                System.out.println("  INACTIVE: " + player.getUserName() + " - set to done");
            }
        }

        System.out.println("Active players: " + activePlayerCount + ", Inactive players: " + inactivePlayerCount);

        // Assert - All players should be accounted for
        assertEquals(gameSession.getPlayers().size(), activePlayerCount + inactivePlayerCount, "All players should be accounted for");
        assertEquals(expectedActivePlayerIds, activePlayerIds, "Active player IDs should rotate for each coordinate");

        for(Player player : gameSession.getPlayers()) {
            if (!expectedActivePlayerIds.contains(player.getAuthorId())) {
                verify(activeSessionHelper, times(1)).update(eq(gameCode), eq(gameSession.getGameState()), eq(player.getAuthorId()), eq(true));
            }
        }
    }

    static Stream<Arguments> provideCoordinates() {
        return Stream.of(
                Arguments.of("MAKE_CHOICE_VOTING_START.json", 0, 0, List.of("24fca1a7-efc5-4813-8530-448f0e5c29d1")),
                Arguments.of("MAKE_CHOICE_VOTING_START.json", 1, 0, List.of("91732a20-794c-424a-8d69-8b4dedac4f85")),
                Arguments.of("MAKE_CHOICE_VOTING_START.json", 2, 0, List.of("884bcc3e-cd30-4714-91c2-b47827466f29")),
                Arguments.of("MAKE_CHOICE_VOTING_START.json", 3, 0, List.of("abddf3d8-c59d-43d2-b91c-dc7ebc83da27")),
                Arguments.of("MAKE_CHOICE_VOTING_STORY2.json", 0, 0, List.of("7c7228ca-b43a-4b45-b94c-8593d6404a0e")),
                Arguments.of("MAKE_CHOICE_VOTING_STORY2.json", 1, 0, List.of("f1805f47-b2c5-49a8-9cf1-c8a61845ad7b")),
                Arguments.of("MAKE_CHOICE_VOTING_STORY2.json", 2, 0, List.of("eaa157ee-9d88-4d49-a728-4c8abca37119")),
                Arguments.of("MAKE_CHOICE_VOTING_STORY2.json", 3, 0, List.of("c0e0de02-3aae-4e32-ab15-0cac32557778")),
                Arguments.of("MAKE_OUTCOME_CHOICE_VOTING_START.json", 2, 0, List.of("c0e0de02-3aae-4e32-ab15-0cac32557778", "7c7228ca-b43a-4b45-b94c-8593d6404a0e", "f1805f47-b2c5-49a8-9cf1-c8a61845ad7b"))
        );
    }

    @Test
    void testGetVotingSubmissionsForPlayer_MAKE_CHOICE_VOTING_AllPlayersIteration() throws IOException {
        // Arrange - Load test data
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson("MAKE_CHOICE_VOTING_START.json");
        String gameCode = gameSession.getGameCode();

        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);

        System.out.println("=== All Players Iteration Test ===");
        System.out.println("Game Code: " + gameCode);
        System.out.println("Game State: " + gameSession.getGameState());
        System.out.println("Number of players: " + gameSession.getPlayers().size());
        System.out.println("Number of stories: " + gameSession.getStories().size());

        // Act - Iterate over all players
        int activePlayerCount = 0;
        int inactivePlayerCount = 0;

        for (Player player : gameSession.getPlayers()) {
            String playerId = player.getAuthorId();
            List<TextSubmission> submissions = votingHelper.getVotingSubmissionsForPlayer(gameCode, playerId);

            System.out.println("\nPlayer: " + player.getUserName());
            System.out.println("  Author ID: " + playerId);
            System.out.println("  Submissions count: " + (submissions != null ? submissions.size() : 0));

            if (submissions != null && !submissions.isEmpty()) {
                activePlayerCount++;
                for (TextSubmission sub : submissions) {
                    System.out.println("    - " + sub.getCurrentText().substring(0, Math.min(50, sub.getCurrentText().length())) + "...");
                }
            } else {
                inactivePlayerCount++;
            }
        }

        System.out.println("\n=== Summary ===");
        System.out.println("Active players (got submissions): " + activePlayerCount);
        System.out.println("Inactive players (empty/set to done): " + inactivePlayerCount);

        // Assert - In MAKE_CHOICE_VOTING, typically only one player is active per story
        // The sum should equal total players
        assertEquals(gameSession.getPlayers().size(), activePlayerCount + inactivePlayerCount,
                "All players should be accounted for");
    }

    @Test
    void testGetVotingSubmissionsForPlayer_MAKE_CHOICE_VOTING_VerifySubmissionContent() throws IOException {
        // Arrange
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson("MAKE_CHOICE_VOTING_START.json");
        String gameCode = gameSession.getGameCode();
        String phaseId = GameState.MAKE_CHOICE_VOTING.name();

        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);

        // Get the story at current coordinates
        Story currentStory = gameSession.getStoryAtCurrentPlayerCoordinates();
        assertNotNull(currentStory, "Story at current coordinates should exist");

        Player firstPlayer = gameSession.getPlayers().getFirst();

        List<TextSubmission> submissions = votingHelper.getVotingSubmissionsForPlayer(gameCode, firstPlayer.getAuthorId());

        System.out.println("=== Submission Content Verification ===");
        System.out.println("Current story: " + currentStory.getPrompt());
        System.out.println("Player: " + firstPlayer.getUserName());
        System.out.println("Submissions returned: " + (submissions != null ? submissions.size() : 0));

        if (submissions != null && !submissions.isEmpty()) {
            for (TextSubmission submission : submissions) {
                System.out.println("  Submission: " + submission.getSubmissionId());
                System.out.println("    Text: " + submission.getCurrentText());
                System.out.println("    OutcomeType: " + submission.getOutcomeType());
                System.out.println("    Additions: " + submission.getAdditions().size());

                // Verify submission has required fields
                assertNotNull(submission.getSubmissionId(), "Submission should have ID");
                assertNotNull(submission.getCurrentText(), "Submission should have text");
            }
        }
    }

    @Test
    void testPlayerIdsSetOnMakeChoiceVote() throws IOException {
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson("MAKE_CHOICE_VOTING_START.json");
        String gameCode = gameSession.getGameCode();
        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);
        PlayerVote playerVote = new PlayerVote("", "24fca1a7-efc5-4813-8530-448f0e5c29d1", "edefbad5-4e7f-4769-9637-c7aa3c246c7f", 1);
        votingHelper.submitPlayerVotes(
                gameSession.getGameCode(),
                List.of(playerVote)
        );
        ArgumentCaptor<Story> storyCaptor = ArgumentCaptor.forClass(Story.class);
        verify(storyDAO, times(1)).updateStory(storyCaptor.capture());

        Story capturedStory = storyCaptor.getValue();
        assertEquals(List.of("24fca1a7-efc5-4813-8530-448f0e5c29d1"), capturedStory.getPlayerIds());

        for(Player player : gameSession.getPlayers()) {
            if (!capturedStory.getPlayerIds().contains(player.getAuthorId())) {
                verify(activeSessionHelper, times(1)).update(eq(gameCode), eq(GameState.MAKE_CHOICE_VOTING), eq(player.getAuthorId()), eq(true));
            }
        }
    }

    @Test
    void testPlayerIdsSetOnMakeOutcomeChoiceVote() throws IOException {
        GameSession gameSession = TestJsonLoader.loadGameSessionFromJson("MAKE_OUTCOME_CHOICE_VOTING_START.json");
        String gameCode = gameSession.getGameCode();
        when(gameSessionDAO.getGame(gameCode)).thenReturn(gameSession);
        PlayerVote playerVoteRank1 = new PlayerVote("", "f1805f47-b2c5-49a8-9cf1-c8a61845ad7b", "7d19c18e-d8c8-4189-8c22-87167321d840", 1);
        PlayerVote playerVoteRank2 = new PlayerVote("", "f1805f47-b2c5-49a8-9cf1-c8a61845ad7b", "0658b409-70a7-4f78-9633-455f2437ec26", 2);

        votingHelper.submitPlayerVotes(
                gameSession.getGameCode(),
                List.of(playerVoteRank1, playerVoteRank2)
        );

        //Verify active player for the story is set to done instead of being allowed to vote on their own outcomes
        verify(activeSessionHelper, times(1)).update(eq(gameCode), eq(GameState.MAKE_OUTCOME_CHOICE_VOTING), eq("eaa157ee-9d88-4d49-a728-4c8abca37119"), eq(true));
    }
}
