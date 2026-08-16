package client.nowhere;

import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import client.nowhere.support.FullGameScenario;
import client.nowhere.support.GameFlowClient;
import client.nowhere.support.LiveBackend;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Same coverage as FullGameFourPlayersTest, plus a 5th, class-less player - GameState.
 * getOutcomeTypeOffset uses a different offset formula once playerCount > 4 (e.g.
 * HOW_DOES_THIS_RESOLVE's offset becomes 3 instead of 2 - see GameState.java), so this exercises
 * that branch instead of the <= 4 branch every other test in this suite exercises. The 5th
 * player's story is left untouched by every scenario, so it also serves as the "no repercussion ->
 * round-2 prequel-eligible" control case (see FullGameScenario.assertRoundOneEffects's dynamic
 * prequel-eligibility loop).
 */
class FullGameFivePlayersTest {

    @Test
    void playsAFullGameToEnding() {
        GameFlowClient client = new GameFlowClient(LiveBackend.restTemplate());
        FullGameScenario scenario = new FullGameScenario(LiveBackend.restTemplate());

        GameSession finalState = scenario.play(5);

        assertThat(finalState.getGameState()).isEqualTo(GameState.ENDING);

        client.deleteGame(finalState.getGameCode());
    }
}
