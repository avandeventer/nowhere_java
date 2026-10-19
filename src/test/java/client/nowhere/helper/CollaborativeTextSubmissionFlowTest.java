package client.nowhere.helper;

import client.nowhere.dao.*;
import client.nowhere.model.*;
import client.nowhere.util.InMemoryCollaborativeTextDAO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Submissions, branches, views and ballots each live in their own document now (CollaborativeTextDAO).
 * These tests walk the request-level flows against the in-memory DAO.
 */
class CollaborativeTextSubmissionFlowTest {

    private static final String GAME_CODE = "FLOW";
    private static final String ALICE = "alice";
    private static final String BOB = "bob";
    private static final String CARA = "cara";
    private static final String DEV = "dev";

    @Mock private GameSessionDAO gameSessionDAO;
    @Mock private AdventureMapDAO adventureMapDAO;
    @Mock private AdventureMapHelper adventureMapHelper;
    @Mock private StoryDAO storyDAO;
    @Mock private FeatureFlagHelper featureFlagHelper;
    @Mock private ActiveSessionDAO activeSessionDAO;
    @Mock private EndingDAO endingDAO;
    @Mock private ActiveSessionHelper activeSessionHelper;

    private InMemoryCollaborativeTextDAO collaborativeTextDAO;
    private CollaborativeTextHelper collaborativeTextHelper;
    private VotingHelper votingHelper;
    private GameSession gameSession;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        collaborativeTextDAO = new InMemoryCollaborativeTextDAO();
        OutcomeTypeHelper outcomeTypeHelper = new OutcomeTypeHelper();
        collaborativeTextHelper = new CollaborativeTextHelper(gameSessionDAO, collaborativeTextDAO, adventureMapDAO,
                adventureMapHelper, storyDAO, featureFlagHelper, outcomeTypeHelper, activeSessionDAO, endingDAO);
        votingHelper = new VotingHelper(gameSessionDAO, collaborativeTextDAO, activeSessionHelper, outcomeTypeHelper, storyDAO);

        gameSession = new GameSession(GAME_CODE, GameState.HOW_DOES_THIS_RESOLVE);
        gameSession.setStories(new ArrayList<>());
        when(gameSessionDAO.getGame(GAME_CODE)).thenReturn(gameSession);
        when(featureFlagHelper.getFlagValue(anyString())).thenReturn(false);
    }

    private static TextAddition addition(String authorId, String text, String parentSubmissionId) {
        return new TextAddition(null, authorId, text, parentSubmissionId, new OutcomeType("story-1", "A story"));
    }

    private String submitRoot(String authorId, String text) {
        CollaborativeTextPhase response = collaborativeTextHelper.submitTextAddition(GAME_CODE, addition(authorId, text, null));
        return response.getSubmissions().getLast().getSubmissionId();
    }

    @Test
    void submitResponseHoldsOnlyTheAuthorsSubmissions() {
        submitRoot(ALICE, "Alice's root");
        CollaborativeTextPhase response = collaborativeTextHelper.submitTextAddition(GAME_CODE, addition(BOB, "Bob's root", null));

        assertEquals(GameState.HOW_DOES_THIS_RESOLVE.name(), response.getPhaseId());
        assertEquals(1, response.getSubmissions().size());
        assertEquals(BOB, response.getSubmissions().getFirst().getAuthorId());
        // The full phase still has both
        assertEquals(2, collaborativeTextHelper.getCollaborativeTextPhase(GAME_CODE).getSubmissions().size());
    }

    @Test
    void branchingCopiesTheParentAndTheParentStopsBeingAWinner() {
        String rootId = submitRoot(ALICE, "Alice begins");
        collaborativeTextHelper.submitTextAddition(GAME_CODE, addition(BOB, "and Bob continues", rootId));

        List<TextSubmission> submissions = collaborativeTextHelper.getCollaborativeTextPhase(GAME_CODE).getSubmissions();
        TextSubmission branch = submissions.stream().filter(s -> !s.getSubmissionId().equals(rootId)).findFirst().orElseThrow();
        assertEquals("Alice begins and Bob continues", branch.getCurrentText());
        assertEquals("Alice begins", branch.getOriginalText());
        assertEquals(2, branch.getAdditions().size());
        assertEquals(ALICE, branch.getOriginalAuthorId());

        // getSubmissionsWithoutParentSubmissions drops the root once something branches from it
        List<TextSubmission> winners = collaborativeTextHelper.getWinningSubmissions(GAME_CODE);
        assertEquals(List.of(branch.getSubmissionId()), winners.stream().map(TextSubmission::getSubmissionId).toList());
    }

    @Test
    void branchingFromAMissingSubmissionIsRejected() {
        assertThrows(RuntimeException.class,
                () -> collaborativeTextHelper.submitTextAddition(GAME_CODE, addition(BOB, "orphan", "no-such-submission")));
    }

    @Test
    void emptyPhaseIsReturnedInsteadOfAnError() {
        CollaborativeTextPhase phase = collaborativeTextHelper.getCollaborativeTextPhase(GAME_CODE);
        assertEquals(GameState.HOW_DOES_THIS_RESOLVE.name(), phase.getPhaseId());
        assertTrue(phase.getSubmissions().isEmpty());
        assertTrue(collaborativeTextHelper.getWinningSubmissions(GAME_CODE).isEmpty());
    }

    @Test
    void availableSubmissionsAreLimitedToTwoViewersAndSkipTheAuthor() {
        String rootId = submitRoot(ALICE, "Alice begins");

        assertTrue(collaborativeTextHelper.getAvailableSubmissionsForPlayer(GAME_CODE, ALICE, 2, null, false).isEmpty(),
                "Authors don't get their own submissions");
        assertEquals(rootId, collaborativeTextHelper.getAvailableSubmissionsForPlayer(GAME_CODE, BOB, 2, null, false).getFirst().getSubmissionId());
        assertEquals(rootId, collaborativeTextHelper.getAvailableSubmissionsForPlayer(GAME_CODE, CARA, 2, null, false).getFirst().getSubmissionId());
        assertTrue(collaborativeTextHelper.getAvailableSubmissionsForPlayer(GAME_CODE, DEV, 2, null, false).isEmpty(),
                "Bob and Cara are both viewing it");

        // Bob asking again (without showNewSubmissions) releases his old view first, so he gets it back
        assertEquals(rootId, collaborativeTextHelper.getAvailableSubmissionsForPlayer(GAME_CODE, BOB, 2, null, false).getFirst().getSubmissionId());
        assertTrue(collaborativeTextHelper.getAvailableSubmissionsForPlayer(GAME_CODE, DEV, 2, null, false).isEmpty());
    }

    @Test
    void resubmittingVotesReplacesThePlayersBallot() {
        gameSession.setGameState(GameState.LOCATION_VOTING);

        votingHelper.submitPlayerVotes(GAME_CODE, List.of(new PlayerVote("", ALICE, "location-a", 1)));
        CollaborativeTextPhase response = votingHelper.submitPlayerVotes(GAME_CODE, List.of(
                new PlayerVote("", ALICE, "location-b", 1),
                new PlayerVote("", ALICE, "location-c", 2)));

        List<PlayerVote> ballot = collaborativeTextDAO.allVotes(GameState.LOCATION_VOTING).get(ALICE);
        assertEquals(List.of("location-b", "location-c"), ballot.stream().map(PlayerVote::getSubmissionId).toList());
        assertEquals(List.of(ALICE), response.getPlayersWhoVoted());
    }

    @Test
    void votesWithoutAPlayerIdAreRejected() {
        gameSession.setGameState(GameState.LOCATION_VOTING);
        assertThrows(RuntimeException.class,
                () -> votingHelper.submitPlayerVotes(GAME_CODE, List.of(new PlayerVote("", null, "location-a", 1))));
    }
}
