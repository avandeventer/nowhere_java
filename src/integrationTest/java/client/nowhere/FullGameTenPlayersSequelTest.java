package client.nowhere;

import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import client.nowhere.support.FullGameScenario;
import client.nowhere.support.GameFlowClient;
import client.nowhere.support.LiveBackend;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Upper-bound load scenario: plays a full DUNGEON_MODE game with 10 players against the real
 * deployed backend, to reproduce the gameSessions/{gameCode} document outgrowing Firestore's
 * 1 MiB per-document limit by round 2. Expected to FAIL until that's fixed - it exists so the
 * scenario can be run the same way before and after the fix.
 *
 * Deliberately heavier than the 3/4/5-player tests:
 * - every player has a class (Scribe/Bard/Herald/Fabulist, cycled), and every class holder
 *   beyond the first of each class attaches their repercussion to another round-1 story;
 * - submission text is padded to the player-client's 150-character limit;
 * - in every SHARE_TEXT phase each player also adds to other players' submissions
 *   EXTRA_ADDITIONS_PER_PLAYER times, the way real players keep contributing after their own
 *   submission, instead of the harness's usual one-root-per-player minimum;
 * - sequelLocationScopeMode is combined with the full class repercussion scenarios, so round 2
 *   has a real mix of round-1 stories that are re-offered as sequels (winning option had no
 *   trait/title/companion effect) and ones that aren't - asserted per story by
 *   FullGameScenario.assertRoundOneEffects - plus the split round-2 LOCATION_VOTING and sequel
 *   location-scoping assertion from FullGameSequelLocationScopeTest.
 *
 * - the backend also gets the rest of a real game's HTTP traffic (TV and player-client reads,
 *   the TV's PUT /activePlayerSession timer writes - see FullGameScenario.simulateClientTraffic),
 *   with each phase's per-player calls fired concurrently, one thread per player, like 10 phones.
 *
 * GameSession size (total and per top-level field) and per-phase wall-clock time are logged on
 * every phase transition, and every HTTP call's latency is recorded by endpoint and phase. Both
 * summary tables print at the end whether the game finishes or fails - see SessionSizeLog and
 * LatencyLog. A failed run leaves its game in place for debugging, same as the other full-game
 * tests.
 */
class FullGameTenPlayersSequelTest {

    private static final int EXTRA_ADDITIONS_PER_PLAYER = 3;

    @Test
    void playsATenPlayerSequelGameToEnding() {
        GameFlowClient client = new GameFlowClient(LiveBackend.restTemplate());
        FullGameScenario scenario = new FullGameScenario(LiveBackend.restTemplate())
                .cycleClasses()
                .mixSequelEligibility()
                .realisticSubmissionText()
                .extraAdditionsPerPlayer(EXTRA_ADDITIONS_PER_PLAYER)
                .simulateClientTraffic()
                .concurrentPlayers()
                .logSessionSize()
                .logLatency();

        GameSession finalState = scenario.play(10, true);

        assertThat(finalState.getGameState()).isEqualTo(GameState.ENDING);

        client.deleteGame(finalState.getGameCode());
    }
}
