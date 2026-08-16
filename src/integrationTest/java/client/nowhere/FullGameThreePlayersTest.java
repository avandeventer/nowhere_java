package client.nowhere;

import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import client.nowhere.support.FullGameScenario;
import client.nowhere.support.GameFlowClient;
import client.nowhere.support.LiveBackend;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plays a full DUNGEON_MODE game with 3 players against the real deployed backend, from world
 * setup through both writing rounds to ENDING. 3 players stays on the "else" branch of
 * GameState.getOutcomeTypeOffset (playerCount <= 4), and covers 3 of the 4 PlayerClass
 * repercission scenarios FullGameScenario builds during round 1 (Fabulist/Companion is skipped -
 * there's no 4th player to hold that class; see FullGameFourPlayersTest for full coverage).
 *
 * Stops at ENDING rather than FINALE: ENDING currently misreports as a voting phase (see
 * src/integrationTest/README.md, "Known bug ... ENDING misclassified as a voting phase") and the
 * ENDING/EPILOGUE mechanic is being reworked, so FullGameScenario treats reaching ENDING as the
 * game being over.
 */
class FullGameThreePlayersTest {

    @Test
    void playsAFullGameToEnding() {
        GameFlowClient client = new GameFlowClient(LiveBackend.restTemplate());
        FullGameScenario scenario = new FullGameScenario(LiveBackend.restTemplate());

        GameSession finalState = scenario.play(3);

        assertThat(finalState.getGameState()).isEqualTo(GameState.ENDING);

        client.deleteGame(finalState.getGameCode());
    }
}
