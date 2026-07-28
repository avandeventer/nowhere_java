package client.nowhere.helper;

import client.nowhere.dao.*;
import client.nowhere.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Reproduces the DIGIDESTINED clarifier report using the exact ids from the reported game session
 * (story 9c65be87, option 0b973710, fork 6ea0c29f, repercussion/trait 7b31b0fc). Confirms that
 * getOutcomeTypes(DEFINING_TRAITS_VOTING) resolves a repercussion-sourced trait's clarifier to its
 * originating story id.
 */
public class DigidestinedBugReproTest {

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
    @Mock
    private EndingDAO endingDAO;

    private final OutcomeTypeHelper outcomeTypeHelper = new OutcomeTypeHelper();
    private CollaborativeTextHelper collaborativeTextHelper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        collaborativeTextHelper = new CollaborativeTextHelper(
                gameSessionDAO, collaborativeTextDAO, adventureMapDAO,
                adventureMapHelper, storyDAO, featureFlagHelper, outcomeTypeHelper, activeSessionDAO,
                endingDAO
        );
    }

    @Test
    void minimalRepro_repercussionSourcedTraitGetsClarifier() {
        String traitId = "7b31b0fc-46ce-497f-b015-729a21db09a5";
        String storyId = "9c65be87-b0cd-4602-8174-f89e0d56a7cd";
        String optionId = "0b973710-ee11-4fb3-9156-b6d65587fe7a";
        String forkSubmissionId = "6ea0c29f-ab08-4583-9468-4bc0321ff0d3";
        String playerId = "62577453-0d0b-4617-a5c9-bff54c28f532";

        Player player = new Player();
        player.setAuthorId(playerId);
        player.setUserName("Andy");
        List<Trait> traits = new ArrayList<>();
        traits.add(new Trait(traitId, "DIGIDESTINED", TraitType.STANDARD));
        player.setTraits(traits);

        Repercussion repercussion = new Repercussion("Trait", "DIGIDESTINED");
        repercussion.setRepercussionId(traitId);
        OutcomeFork fork = new OutcomeFork();
        TextSubmission textSubmission = new TextSubmission();
        textSubmission.setSubmissionId(forkSubmissionId);
        fork.setTextSubmission(textSubmission);
        List<Repercussion> repercussions = new ArrayList<>();
        repercussions.add(repercussion);
        fork.setRepercussions(repercussions);

        Option option = new Option();
        option.setOptionId(optionId);
        option.setOutcomeForks(List.of(fork));
        option.setSelectedForkId(forkSubmissionId);

        Story story = new Story();
        story.setStoryId(storyId);
        story.setOptions(List.of(option));
        story.setSelectedOptionId(optionId);

        GameSession gameSession = new GameSession();
        gameSession.setGameCode("TEST1");
        gameSession.setGameState(GameState.DEFINING_TRAITS_VOTING);
        gameSession.setPlayers(List.of(player));
        gameSession.setStories(List.of(story));

        when(gameSessionDAO.getGame("TEST1")).thenReturn(gameSession);

        List<OutcomeType> outcomeTypes = collaborativeTextHelper.getOutcomeTypes("TEST1", playerId);

        OutcomeType digidestined = outcomeTypes.stream().filter(o -> o.getId().equals(traitId)).findFirst().orElseThrow();
        assertEquals(storyId, digidestined.getClarifier());
    }
}