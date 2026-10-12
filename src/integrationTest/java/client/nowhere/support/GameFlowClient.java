package client.nowhere.support;

import client.nowhere.model.*;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

/**
 * Thin wrapper around the real HTTP endpoints the Angular clients call, used to script a full
 * game end-to-end. Responses are deserialized into the same typed model classes the server itself
 * uses (GameSession, Story, Player, TextSubmission, ...) rather than generic Maps, so callers get
 * real getters instead of string-keyed lookups.
 */
public class GameFlowClient {

    private final RestTemplate restTemplate;

    public GameFlowClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public GameSession createGame(String userProfileId, String adventureId) {
        String url = "/game?userProfileId={userProfileId}&adventureId={adventureId}&gameMode=DUNGEON_MODE";
        return restTemplate.postForObject(url, null, GameSession.class, userProfileId, adventureId);
    }

    public Player joinPlayer(String gameCode, String userName) {
        Player player = new Player(gameCode, userName);
        return restTemplate.postForObject("/player", player, Player.class);
    }

    public void selectStartingLocation(String gameCode, String authorId, String locationId) {
        Player player = new Player();
        player.setGameCode(gameCode);
        player.setAuthorId(authorId);
        player.setSelectedLocationId(locationId);
        restTemplate.exchange("/player", HttpMethod.PUT, new HttpEntity<>(player), Player.class);
    }

    public List<PlayerClassOption> getPlayerClasses() {
        PlayerClassOption[] result = restTemplate.getForObject("/player/classes", PlayerClassOption[].class);
        return result == null ? List.of() : List.of(result);
    }

    /**
     * Mirrors selectStartingLocation's minimal-body PUT /player pattern. Must be called right
     * after joinPlayer, before any stat/trait-granting phase runs: Player.updatePlayer
     * (model/Player.java) unconditionally overwrites traits/titles/playerStats from whatever's on
     * the incoming body, and a fresh Player() has those as null - calling this later would wipe
     * out any traits/titles the player had already accumulated.
     */
    public void selectPlayerClass(String gameCode, String authorId, PlayerClassOption playerClass) {
        Player player = new Player();
        player.setGameCode(gameCode);
        player.setAuthorId(authorId);
        player.setPlayerClass(playerClass);
        restTemplate.exchange("/player", HttpMethod.PUT, new HttpEntity<>(player), Player.class);
    }

    public List<RepercussionTypeOption> getPlayerRepercussionTypes(String gameCode, String authorId) {
        RepercussionTypeOption[] result = restTemplate.getForObject(
                "/collaborativeText/repercussionTypes?gameCode={gameCode}&authorId={authorId}",
                RepercussionTypeOption[].class, gameCode, authorId);
        return result == null ? List.of() : List.of(result);
    }

    public void markPhaseDone(String gameCode, String gamePhase, String authorId) {
        String url = "/activeGameStateSession?gameCode={gameCode}&gamePhase={gamePhase}&authorId={authorId}&isDone=true";
        restTemplate.exchange(url, HttpMethod.PUT, HttpEntity.EMPTY, Void.class, gameCode, gamePhase, authorId);
    }

    public GameSession getGame(String gameCode) {
        return restTemplate.getForObject("/game?gameCode={gameCode}", GameSession.class, gameCode);
    }

    /** Same GET /game as getGame, but the raw response body - used to measure serialized size. */
    public String getGameJson(String gameCode) {
        return restTemplate.getForObject("/game?gameCode={gameCode}", String.class, gameCode);
    }

    public GameSession nextGameState(String gameCode) {
        return restTemplate.exchange(
                        "/game/next?gameCode={gameCode}", HttpMethod.PUT, HttpEntity.EMPTY, GameSession.class, gameCode)
                .getBody();
    }

    public CollaborativeTextPhaseInfo getPhaseInfo(String gameCode) {
        try {
            return restTemplate.getForObject("/collaborativeText/phaseInfo?gameCode={gameCode}", CollaborativeTextPhaseInfo.class, gameCode);
        } catch (Exception e) {
            return null;
        }
    }

    public List<OutcomeType> getOutcomeTypes(String gameCode, String playerId) {
        OutcomeType[] result = restTemplate.getForObject(
                "/collaborativeText/outcomeTypes?gameCode={gameCode}&playerId={playerId}",
                OutcomeType[].class, gameCode, playerId);
        return result == null ? List.of() : List.of(result);
    }

    public List<TextSubmission> getVotingCandidates(String gameCode, String playerId) {
        TextSubmission[] result = restTemplate.getForObject(
                "/collaborativeText/voting?gameCode={gameCode}&playerId={playerId}",
                TextSubmission[].class, gameCode, playerId);
        return result == null ? List.of() : List.of(result);
    }

    /**
     * Mirrors the real player-client's updateAvailableSubmissions -> getAvailableSubmissionsForPlayer
     * (collaborative-text.component.ts): surfaces OTHER players' submissions in the current phase
     * that this player hasn't authored and wasn't last to contribute to - the pool onAddToSubmission
     * picks from.
     */
    public List<TextSubmission> getAvailableSubmissions(String gameCode, String playerId, int requestedCount, boolean showNewSubmissions) {
        TextSubmission[] result = restTemplate.getForObject(
                "/collaborativeText/available?gameCode={gameCode}&playerId={playerId}&requestedCount={requestedCount}&showNewSubmissions={showNewSubmissions}",
                TextSubmission[].class, gameCode, playerId, requestedCount, showNewSubmissions);
        return result == null ? List.of() : List.of(result);
    }

    /**
     * Submits a fresh root TextAddition. outcomeTypeWithLabel should already be shaped the way the
     * real client builds it (see FullGameScenario.narrowToChosenSubType, mirroring
     * onSelectSubType) - the full parent id/label/clarifier with subTypes narrowed to just the one
     * chosen child, not the child alone.
     */
    public CollaborativeTextPhase submitTextAddition(String gameCode, String authorId, OutcomeType outcomeTypeWithLabel,
                                                       String text, String repercussionType, String repercussionSubmission) {
        TextAddition addition = new TextAddition();
        addition.setAuthorId(authorId);
        addition.setAddedText(text);
        if (!repercussionType.isEmpty() && !repercussionSubmission.isEmpty()) {
            addition.setRepercussion(new Repercussion(repercussionType, repercussionSubmission));
        }
        addition.setOutcomeTypeWithLabel(outcomeTypeWithLabel);
        return restTemplate.postForObject("/collaborativeText?gameCode={gameCode}", addition, CollaborativeTextPhase.class, gameCode);
    }

    /**
     * Mirrors the real player-client's onAddToSubmission (collaborative-text.component.ts):
     * posts a TextAddition with submissionId set to an EXISTING submission (possibly authored by
     * someone else), triggering CollaborativeTextHelper.createBranchedSubmission server-side
     * instead of a fresh root submission. When two different authors each branch directly off the
     * same parent submissionId, the result is two sibling submissions - the mechanism that
     * produces a second OutcomeFork on the same Option.
     */
    public CollaborativeTextPhase addToSubmission(String gameCode, String authorId, String parentSubmissionId,
                                                    String text, String repercussionType, String repercussionSubmission) {
        TextAddition addition = new TextAddition();
        addition.setAuthorId(authorId);
        addition.setAddedText(text);
        addition.setSubmissionId(parentSubmissionId);
        if (!repercussionType.isEmpty() && !repercussionSubmission.isEmpty()) {
            addition.setRepercussion(new Repercussion(repercussionType, repercussionSubmission));
        }
        return restTemplate.postForObject("/collaborativeText?gameCode={gameCode}", addition, CollaborativeTextPhase.class, gameCode);
    }

    public void submitVote(String gameCode, String playerId, String submissionId) {
        PlayerVote vote = new PlayerVote(null, playerId, submissionId, 1);
        restTemplate.postForObject("/collaborativeText/votes?gameCode={gameCode}", List.of(vote), CollaborativeTextPhase.class, gameCode);
    }

    // ===== Calls the real clients make that the scenario doesn't need the result of =====
    // Bodies are read as raw Strings: these exist so the traffic (and its latency - see LatencyLog)
    // matches a real game, not for their content, so there's no point deserializing them.

    /** Player-client collaborative-text.component loadCollaborativePhase. */
    public String getCollaborativeTextPhaseRaw(String gameCode) {
        return restTemplate.getForObject("/collaborativeText?gameCode={gameCode}", String.class, gameCode);
    }

    /** Player-client collaborative-text.component loadStoryForOutcomeType / onRepercussionChange. */
    public String getStoryRaw(String gameCode, String storyId) {
        return restTemplate.getForObject("/story?gameCode={gameCode}&storyId={storyId}", String.class, gameCode, storyId);
    }

    /** Player-client collaborative-text.component WRITE_EPILOGUES assigned/epilogue player lookups. */
    public String getPlayerByAuthorIdRaw(String gameCode, String authorId) {
        return restTemplate.getForObject("/player/authorId?gameCode={gameCode}&authorId={authorId}", String.class, gameCode, authorId);
    }

    /** Game-client player-progress.component loadPlayers, on every gameState change. */
    public String getPlayersRaw(String gameCode) {
        return restTemplate.getForObject("/player?gameCode={gameCode}", String.class, gameCode);
    }

    /** Game-client game-board.component, shown when phaseInfo.showGameBoard. */
    public String getGameBoardRaw(String gameCode) {
        return restTemplate.getForObject("/game-board?gameCode={gameCode}", String.class, gameCode);
    }

    /** Game-client collaborative-text.component loadGameSessionDisplay. */
    public String getGameSessionDisplayRaw(String gameCode) {
        return restTemplate.getForObject("/display?gameCode={gameCode}", String.class, gameCode);
    }

    /** Game-client collaborative-text.component loadWinningSubmission, in WINNING phases. */
    public String getWinningSubmissionsRaw(String gameCode) {
        return restTemplate.getForObject("/collaborativeText/winner?gameCode={gameCode}", String.class, gameCode);
    }

    /**
     * Game-client timer.service setContributionPhaseActive (startTimer + contributionPhaseActive)
     * / setWriteTimerDone - the same partial ActivePlayerSession body the TV sends when a phase
     * timer runs out. Server-side this re-reads and re-writes the whole game session document
     * (ActiveSessionDAO.updateActivePlayerSession). Sent as a Map with exactly the keys the
     * TypeScript client sends, not a full ActivePlayerSession, so omitted fields deserialize to
     * the server's defaults the same way they do for the real client.
     */
    public void setContributionPhaseActive(String gameCode) {
        restTemplate.exchange("/activePlayerSession", HttpMethod.PUT,
                new HttpEntity<>(Map.of("gameCode", gameCode, "startTimer", true, "contributionPhaseActive", true)), String.class);
    }

    /** Game-client timer.service setWriteTimerDone - see setContributionPhaseActive. */
    public void setWriteTimerDone(String gameCode) {
        restTemplate.exchange("/activePlayerSession", HttpMethod.PUT,
                new HttpEntity<>(Map.of("gameCode", gameCode, "writeTimerDone", true)), String.class);
    }

    /**
     * Deletes the entire gameSessions/{gameCode} Firestore document (players, stories, endings,
     * collaborativeTextPhases, etc. all live as fields on that one document - see
     * AdminController.deleteGame / GameSessionDAO.deleteGame). Intended as end-of-test cleanup so
     * repeated runs don't leave junk games in the live deployment.
     */
    public void deleteGame(String gameCode) {
        restTemplate.delete("/admin/game?gameCode={gameCode}", gameCode);
    }
}
