package client.nowhere;

import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import client.nowhere.support.FullGameScenario;
import client.nowhere.support.GameFlowClient;
import client.nowhere.support.LiveBackend;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plays a full DUNGEON_MODE game with 4 players (one of each PlayerClass: Scribe/Bard/Herald/
 * Fabulist) against the real deployed backend. FullGameScenario builds and asserts all four
 * repercission scenarios during round 1 - see its class javadoc for exactly what each one covers
 * (Title/Trait/Companion/combined Trait+All-Players, plus a forced real multi-candidate vote on
 * both MAKE_CHOICE_VOTING and MAKE_OUTCOME_CHOICE_VOTING) and src/integrationTest/README.md for
 * the design rationale.
 */
class FullGameFourPlayersTest {

    @Test
    void playsAFullGameToEnding() {
        GameFlowClient client = new GameFlowClient(LiveBackend.restTemplate());
        FullGameScenario scenario = new FullGameScenario(LiveBackend.restTemplate());

        GameSession finalState = scenario.play(4);

        assertThat(finalState.getGameState()).isEqualTo(GameState.ENDING);

        client.deleteGame(finalState.getGameCode());
    }
}
