package client.nowhere.helper;

import client.nowhere.model.GameBoard;
import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import client.nowhere.model.PlayerCoordinates;
import org.junit.jupiter.api.Test;

import static client.nowhere.model.GameState.*;
import static org.junit.jupiter.api.Assertions.*;

class PhasePathsTest {

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
        assertEquals("gameSessions/CODE/rounds/1/phases/WHAT_HAPPENS_HERE", PhasePaths.phasePath(gameAt(1, 0, 0), WHAT_HAPPENS_HERE));
        // Moving to another encounter in the same round keeps the same phase
        assertEquals(PhasePaths.phasePath(gameAt(1, 0, 0), WHAT_CAN_WE_TRY), PhasePaths.phasePath(gameAt(1, 3, 0), WHAT_CAN_WE_TRY));
        // A new round starts a new phase
        assertNotEquals(PhasePaths.phasePath(gameAt(1, 0, 0), WHAT_CAN_WE_TRY), PhasePaths.phasePath(gameAt(2, 0, 0), WHAT_CAN_WE_TRY));
    }

    @Test
    void makeChoiceVotingSubmissionsSpanTheRound() {
        // HOW_DOES_THIS_RESOLVE writes every encounter's options into MAKE_CHOICE_VOTING up front
        assertEquals(PhasePaths.phasePath(gameAt(1, 0, 0), MAKE_CHOICE_VOTING), PhasePaths.phasePath(gameAt(1, 4, 0), MAKE_CHOICE_VOTING));
    }

    @Test
    void encounterPhasesAreScopedToTheEncounter() {
        assertEquals("gameSessions/CODE/rounds/1/encounters/x2y0/phases/MAKE_PARTNER_CHOICE_VOTING",
                PhasePaths.phasePath(gameAt(1, 2, 0), MAKE_PARTNER_CHOICE_VOTING));
        for (GameState phaseId : new GameState[]{MAKE_PARTNER_CHOICE_VOTING, ACCEPT_PARTNER_CHOICE_VOTING, MAKE_OUTCOME_CHOICE_VOTING, NAVIGATE_VOTING}) {
            assertNotEquals(PhasePaths.phasePath(gameAt(1, 2, 0), phaseId), PhasePaths.phasePath(gameAt(1, 3, 0), phaseId), phaseId.name());
        }
    }

    @Test
    void eachRoundHasItsOwnLocationVoting() {
        assertEquals("gameSessions/CODE/rounds/1/phases/LOCATION_VOTING", PhasePaths.phasePath(gameAt(1, 0, 0), LOCATION_VOTING));
        assertEquals("gameSessions/CODE/rounds/2/phases/LOCATION_VOTING", PhasePaths.phasePath(gameAt(2, 5, 0), LOCATION_VOTING));
        // Round 2 reads round 1's pool through an explicit round
        assertEquals("gameSessions/CODE/rounds/1/phases/LOCATION_VOTING", PhasePaths.phasePath(gameAt(2, 5, 0), LOCATION_VOTING, 1));
    }

    @Test
    void eachEncounterIsItsOwnVote() {
        assertEquals("x2y0", PhasePaths.voteScope(gameAt(1, 2, 0)));
        assertNotEquals(PhasePaths.voteScope(gameAt(1, 2, 0)), PhasePaths.voteScope(gameAt(1, 3, 0)));
    }

    @Test
    void missingCoordinatesStillProduceAPath() {
        GameSession gameSession = new GameSession("CODE", GameState.LOCATION_VOTING);
        gameSession.setGameBoard(null);
        assertEquals("none", PhasePaths.voteScope(gameSession));
        assertEquals("gameSessions/CODE/rounds/0/encounters/none/phases/NAVIGATE_VOTING", PhasePaths.phasePath(gameSession, NAVIGATE_VOTING));
    }
}
