package client.nowhere;

import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import client.nowhere.support.FullGameScenario;
import client.nowhere.support.GameFlowClient;
import client.nowhere.support.LiveBackend;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plays a full DUNGEON_MODE game with 4 players against the real deployed backend, using
 * FullGameScenario's sequelLocationScopeMode: a single round-1 story gets an ALL_PLAYERS-alone
 * repercussion (no accompanying TRAIT/TITLE/COMPANION), round 2's LOCATION_VOTING is deliberately
 * split so players end up at different locations, and once round 2's WHAT_HAPPENS_HERE resolves,
 * asserts that the resulting "sequel" story's playerIds are scoped to players at the sequel's own
 * location rather than every player in the game - see
 * CollaborativeTextHelper.handleWhatHappensHereStreamlined's clarifier/prequelStory branch, and
 * FullGameScenario.assertSequelLocationScopeEffects for exactly what's checked (and why it isn't
 * exact-equality - the "distribution-assigned +1 player" caveat documented there).
 */
class FullGameSequelLocationScopeTest {

    @Test
    void sequelStoryPlayerIdsAreScopedToItsOwnLocation() {
        GameFlowClient client = new GameFlowClient(LiveBackend.restTemplate());
        FullGameScenario scenario = new FullGameScenario(LiveBackend.restTemplate());

        GameSession finalState = scenario.play(4, true);

        assertThat(finalState.getGameState()).isEqualTo(GameState.ENDING);

        client.deleteGame(finalState.getGameCode());
    }
}
