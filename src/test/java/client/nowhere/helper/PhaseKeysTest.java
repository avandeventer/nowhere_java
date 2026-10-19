package client.nowhere.helper;

import client.nowhere.model.GameBoard;
import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import client.nowhere.model.PlayerCoordinates;
import org.junit.jupiter.api.Test;

import static client.nowhere.model.GameState.*;
import static org.junit.jupiter.api.Assertions.*;

class PhaseKeysTest {

    private static GameSession gameAt(int round, int x, int y) {
        GameSession gameSession = new GameSession("CODE", GameState.WHAT_HAPPENS_HERE);
        gameSession.setRoundNumber(round);
        GameBoard gameBoard = new GameBoard();
        gameBoard.setPlayerCoordinates(new PlayerCoordinates(x, y));
        gameSession.setGameBoard(gameBoard);
        return gameSession;
    }

    @Test
    void writingPhasesAreRoundScoped() {
        assertEquals("WHAT_HAPPENS_HERE_r1", PhaseKeys.phaseKey(gameAt(1, 0, 0), WHAT_HAPPENS_HERE));
        // Moving to another encounter in the same round keeps the same phase
        assertEquals(PhaseKeys.phaseKey(gameAt(1, 0, 0), WHAT_CAN_WE_TRY), PhaseKeys.phaseKey(gameAt(1, 3, 0), WHAT_CAN_WE_TRY));
        // A new round starts a new phase
        assertNotEquals(PhaseKeys.phaseKey(gameAt(1, 0, 0), WHAT_CAN_WE_TRY), PhaseKeys.phaseKey(gameAt(2, 0, 0), WHAT_CAN_WE_TRY));
    }

    @Test
    void makeChoiceVotingSubmissionsSpanTheRound() {
        // HOW_DOES_THIS_RESOLVE writes every encounter's options into MAKE_CHOICE_VOTING up front
        assertEquals(PhaseKeys.phaseKey(gameAt(1, 0, 0), MAKE_CHOICE_VOTING), PhaseKeys.phaseKey(gameAt(1, 4, 0), MAKE_CHOICE_VOTING));
    }

    @Test
    void encounterPhasesAreScopedToTheEncounter() {
        assertEquals("MAKE_PARTNER_CHOICE_VOTING_r1_x2y0", PhaseKeys.phaseKey(gameAt(1, 2, 0), MAKE_PARTNER_CHOICE_VOTING));
        for (GameState phaseId : new GameState[]{MAKE_PARTNER_CHOICE_VOTING, ACCEPT_PARTNER_CHOICE_VOTING, MAKE_OUTCOME_CHOICE_VOTING, NAVIGATE_VOTING}) {
            assertNotEquals(PhaseKeys.phaseKey(gameAt(1, 2, 0), phaseId), PhaseKeys.phaseKey(gameAt(1, 3, 0), phaseId), phaseId.name());
        }
    }

    @Test
    void locationVotingRoundTwoReusesRoundOne() {
        assertEquals("LOCATION_VOTING_r0", PhaseKeys.phaseKey(gameAt(0, 0, 0), LOCATION_VOTING));
        assertEquals("LOCATION_VOTING_r1", PhaseKeys.phaseKey(gameAt(1, 0, 0), LOCATION_VOTING));
        assertEquals("LOCATION_VOTING_r1", PhaseKeys.phaseKey(gameAt(2, 5, 0), LOCATION_VOTING));
        assertEquals("LOCATION_OPTION_MAKE_CHOICE_VOTING_r1", PhaseKeys.phaseKey(gameAt(2, 5, 0), LOCATION_OPTION_MAKE_CHOICE_VOTING));
    }

    @Test
    void eachEncounterAndRoundIsItsOwnVote() {
        assertEquals("r1_x2y0", PhaseKeys.voteScope(gameAt(1, 2, 0)));
        assertNotEquals(PhaseKeys.voteScope(gameAt(1, 2, 0)), PhaseKeys.voteScope(gameAt(1, 3, 0)));
        // LOCATION_VOTING shares a phase across rounds 1 and 2, but not votes
        assertNotEquals(PhaseKeys.voteScope(gameAt(1, 2, 0)), PhaseKeys.voteScope(gameAt(2, 2, 0)));
    }

    @Test
    void missingCoordinatesStillProduceAKey() {
        GameSession gameSession = new GameSession("CODE", GameState.LOCATION_VOTING);
        gameSession.setGameBoard(null);
        assertEquals("r0_none", PhaseKeys.voteScope(gameSession));
        assertEquals("NAVIGATE_VOTING_r0_none", PhaseKeys.phaseKey(gameSession, NAVIGATE_VOTING));
    }
}
