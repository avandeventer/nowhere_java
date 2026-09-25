package client.nowhere.model;

import client.nowhere.helper.OutcomeTypeHelper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Checks the join-order offsets in GameState.getOutcomeTypeOffset against each other, for every
 * story author, at each supported player count. A writer at index w assigned with offset k works
 * on the story authored by (w + k) mod n; see OutcomeTypeHelper.getPlayerAssignment.
 */
public class GameStateOffsetTest {

    @ParameterizedTest(name = "{0} players")
    @ValueSource(ints = {3, 4, 5, 6, 7, 8})
    void storyRolesDoNotCollide(int numPlayers) {
        int whatHappensHere = GameState.WHAT_HAPPENS_HERE.getOutcomeTypeOffset(numPlayers);
        int makeChoice = GameState.MAKE_CHOICE_VOTING.getOutcomeTypeOffset(numPlayers);
        int howDoesThisResolve = GameState.HOW_DOES_THIS_RESOLVE.getOutcomeTypeOffset(numPlayers);
        int whatCanWeTrySecond = GameState.WHAT_CAN_WE_TRY.getOutcomeTypeOffset(numPlayers);

        for (int author = 0; author < numPlayers; author++) {
            int writtenFor = OutcomeTypeHelper.getOffsetPlayerIndex(author, whatHappensHere, numPlayers);
            int owner = playerAssignedTo(author, makeChoice, numPlayers);
            int outcomeWriter = playerAssignedTo(author, howDoesThisResolve, numPlayers);
            int optionWriter = playerAssignedTo(author, 1, numPlayers);
            int secondOptionWriter = playerAssignedTo(author, whatCanWeTrySecond, numPlayers);

            assertEquals(writtenFor, owner,
                    "Story by player " + author + " was written for player " + writtenFor
                            + " but handleWhatHappensHere gives it to player " + owner);
            assertNotEquals(owner, outcomeWriter,
                    "Player " + owner + " would write HOW_DOES_THIS_RESOLVE outcomes for their own story");
            assertNotEquals(author, outcomeWriter,
                    "Player " + author + " would write HOW_DOES_THIS_RESOLVE outcomes for a story they wrote");
            assertNotEquals(owner, optionWriter,
                    "Player " + owner + " would write WHAT_CAN_WE_TRY options for their own story");
            assertNotEquals(owner, secondOptionWriter,
                    "Player " + owner + " would write WHAT_CAN_WE_TRY options for their own story");
        }
    }

    /** The player index whose assignment with this offset lands on the given author. */
    private static int playerAssignedTo(int author, int offset, int numPlayers) {
        for (int player = 0; player < numPlayers; player++) {
            if (OutcomeTypeHelper.getOffsetPlayerIndex(player, offset, numPlayers) == author) {
                return player;
            }
        }
        throw new IllegalStateException("No player maps to author " + author);
    }
}
