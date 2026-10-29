package client.nowhere.helper;

import client.nowhere.model.GameBoard;
import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import client.nowhere.model.PlayerCoordinates;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static client.nowhere.model.GameState.*;

/**
 * Decides which Firestore document a collaborative text phase lives in, and which votes in it
 * belong to the current vote. The phase's submissions, votes and views are subcollections of it.
 *
 * - Most phases run once per round: gameSessions/{code}/rounds/{round}/phases/{phaseId}
 * - Phases that run at every encounter of a round get their own document per encounter:
 *   gameSessions/{code}/rounds/{round}/encounters/x{X}y{Y}/phases/{phaseId}
 *
 * phaseId is always GameState.getPhaseId(), so a new round or encounter starts from an empty phase.
 *
 * A round's stories live next to its phases: gameSessions/{code}/rounds/{round}/stories/{storyId}
 * (see storiesPath and StoryDAO).
 *
 * Only the leaf documents (stories, submissions, votes, views) are ever written; round, encounter
 * and phase documents exist only as parents of their subcollections. The collection names below
 * are the whole layout, so GameSessionDAO.deleteGame can walk it.
 *
 * MAKE_CHOICE_VOTING's submissions are written once per round (every encounter's options), but each
 * encounter is its own vote, so ballots carry a voteScope of the encounter: x{X}y{Y}.
 */
public final class PhasePaths {

    private static final Set<GameState> ENCOUNTER_SCOPED = EnumSet.of(
            MAKE_PARTNER_CHOICE_VOTING,
            ACCEPT_PARTNER_CHOICE_VOTING,
            MAKE_OUTCOME_CHOICE_VOTING,
            NAVIGATE_VOTING
    );

    // gameSessions/{code}/ROUNDS/{round}
    public static final String ROUNDS = "rounds";
    // .../rounds/{round}/PHASES | ENCOUNTERS | STORIES
    public static final String PHASES = "phases";
    public static final String ENCOUNTERS = "encounters";
    public static final String STORIES = "stories";
    // {phasePath}/SUBMISSIONS | VOTES | VIEWS (CollaborativeTextDAO)
    public static final String SUBMISSIONS = "submissions";
    public static final String VOTES = "votes";
    public static final String VIEWS = "views";
    public static final List<String> PHASE_COLLECTIONS = List.of(SUBMISSIONS, VOTES, VIEWS);

    private PhasePaths() {
    }

    public static String phasePath(GameSession gameSession, GameState phaseId) {
        return phasePath(gameSession, phaseId, roundNumber(gameSession));
    }

    /** The phase's path in a specific round, e.g. to read an earlier round's phase. */
    public static String phasePath(GameSession gameSession, GameState phaseId, int roundNumber) {
        String roundPath = roundPath(gameSession.getGameCode(), roundNumber);
        if (ENCOUNTER_SCOPED.contains(phaseId)) {
            return roundPath + "/" + ENCOUNTERS + "/" + encounterId(gameSession) + "/" + PHASES + "/" + phaseId.name();
        }
        return roundPath + "/" + PHASES + "/" + phaseId.name();
    }

    /** A round's stories, one document per story: gameSessions/{code}/rounds/{round}/stories/{storyId} */
    public static String storiesPath(String gameCode, int roundNumber) {
        return roundPath(gameCode, roundNumber) + "/" + STORIES;
    }

    /** Every round of a game: gameSessions/{code}/rounds */
    public static String roundsPath(String gameCode) {
        return "gameSessions/" + gameCode + "/" + ROUNDS;
    }

    private static String roundPath(String gameCode, int roundNumber) {
        return roundsPath(gameCode) + "/" + roundNumber;
    }

    public static String voteScope(GameSession gameSession) {
        return encounterId(gameSession);
    }

    private static int roundNumber(GameSession gameSession) {
        return gameSession.getRoundNumber() == null ? 0 : gameSession.getRoundNumber();
    }

    // Coordinates identify an encounter within a round: navigation only ever moves to x + 1
    // (CollaborativeTextHelper.navigateToNextCoordinate), so no square is visited twice in a round.
    private static String encounterId(GameSession gameSession) {
        GameBoard gameBoard = gameSession.getGameBoard();
        PlayerCoordinates coordinates = gameBoard == null ? null : gameBoard.getPlayerCoordinates();
        if (coordinates == null) {
            return "none";
        }
        return "x" + coordinates.getxCoordinate() + "y" + coordinates.getyCoordinate();
    }
}
