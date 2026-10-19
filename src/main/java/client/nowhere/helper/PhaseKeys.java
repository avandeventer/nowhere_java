package client.nowhere.helper;

import client.nowhere.model.GameBoard;
import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import client.nowhere.model.PlayerCoordinates;

import java.util.EnumSet;
import java.util.Set;

import static client.nowhere.model.GameState.*;

/**
 * Decides which Firestore phase document (gameSessions/{code}/phases/{phaseKey}) a collaborative
 * text phase lives in, and which votes in it belong to the current vote.
 *
 * Keys are scoped so that a new round or a new encounter starts from an empty phase, which replaces
 * the old practice of clearing the embedded phase map on every navigation:
 * - Encounter-scoped phases are re-initialized at every encounter: {phaseId}_r{round}_x{X}y{Y}
 * - LOCATION_VOTING / LOCATION_OPTION_MAKE_CHOICE_VOTING: round 0 has its own key, and rounds 1+
 *   share one, because round 2 votes on the second half of the locations picked in round 1.
 * - Everything else is round-scoped: {phaseId}_r{round}
 *
 * Votes always carry a voteScope of r{round}_x{X}y{Y}: MAKE_CHOICE_VOTING's submissions span the
 * whole round, but each encounter is its own vote.
 */
public final class PhaseKeys {

    private static final Set<GameState> ENCOUNTER_SCOPED = EnumSet.of(
            MAKE_PARTNER_CHOICE_VOTING,
            ACCEPT_PARTNER_CHOICE_VOTING,
            MAKE_OUTCOME_CHOICE_VOTING,
            NAVIGATE_VOTING
    );

    private static final Set<GameState> SHARED_AFTER_ROUND_ZERO = EnumSet.of(
            LOCATION_VOTING,
            LOCATION_OPTION_MAKE_CHOICE_VOTING
    );

    private PhaseKeys() {
    }

    public static String phaseKey(GameSession gameSession, GameState phaseId) {
        int round = roundNumber(gameSession);
        if (SHARED_AFTER_ROUND_ZERO.contains(phaseId)) {
            return phaseId.name() + "_r" + Math.min(round, 1);
        }
        if (ENCOUNTER_SCOPED.contains(phaseId)) {
            return phaseId.name() + "_r" + round + "_" + encounterKey(gameSession);
        }
        return phaseId.name() + "_r" + round;
    }

    public static String voteScope(GameSession gameSession) {
        return "r" + roundNumber(gameSession) + "_" + encounterKey(gameSession);
    }

    private static int roundNumber(GameSession gameSession) {
        return gameSession.getRoundNumber() == null ? 0 : gameSession.getRoundNumber();
    }

    private static String encounterKey(GameSession gameSession) {
        GameBoard gameBoard = gameSession.getGameBoard();
        PlayerCoordinates coordinates = gameBoard == null ? null : gameBoard.getPlayerCoordinates();
        if (coordinates == null) {
            return "none";
        }
        return "x" + coordinates.getxCoordinate() + "y" + coordinates.getyCoordinate();
    }
}
