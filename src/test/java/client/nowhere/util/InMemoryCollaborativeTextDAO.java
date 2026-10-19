package client.nowhere.util;

import client.nowhere.dao.CollaborativeTextDAO;
import client.nowhere.helper.PhaseKeys;
import client.nowhere.model.CollaborativeTextPhase;
import client.nowhere.model.GameSession;
import client.nowhere.model.GameState;
import client.nowhere.model.PlayerVote;
import client.nowhere.model.TextSubmission;

import java.io.IOException;
import java.util.*;

/**
 * In-memory stand-in for CollaborativeTextDAO. Stores and returns the same TextSubmission instances
 * it was given, so tests can compare winners against fixture submissions by identity.
 *
 * Deliberately scope-blind: phases are stored by phaseId only (the _r{round}... suffix of the
 * phaseKey is dropped) and votes ignore voteScope. That matches the old embedded
 * collaborativeTextPhases map the fixtures were captured with, so handler tests keep working when
 * they move the player's coordinates after loading a fixture. PhaseKeysTest covers the scoping rules.
 */
public class InMemoryCollaborativeTextDAO extends CollaborativeTextDAO {

    private final Map<String, LinkedHashMap<String, TextSubmission>> submissionsByPhase = new HashMap<>();
    private final Map<String, Map<String, List<PlayerVote>>> votesByPhase = new HashMap<>();
    private final Map<String, Map<String, List<String>>> viewsByPhase = new HashMap<>();
    private long nextInsertOrder = 1;

    public InMemoryCollaborativeTextDAO() {
        super(null);
    }

    /** Seeds every phase embedded in a captured game session fixture. */
    public void seedFromFixture(GameSession gameSession, String fixtureFileName) throws IOException {
        for (Map.Entry<String, CollaborativeTextPhase> entry : TestJsonLoader.loadPhasesFromJson(fixtureFileName).entrySet()) {
            seedPhase(gameSession, GameState.valueOf(entry.getKey()), entry.getValue());
        }
    }

    public void seedPhase(GameSession gameSession, GameState phaseId, CollaborativeTextPhase phase) {
        String phaseKey = PhaseKeys.phaseKey(gameSession, phaseId);
        if (phase.getSubmissions() != null) {
            putSubmissions(gameSession.getGameCode(), phaseKey, phase.getSubmissions());
        }
        if (phase.getPlayerVotes() != null) {
            String voteScope = PhaseKeys.voteScope(gameSession);
            phase.getPlayerVotes().forEach((playerId, votes) ->
                    putVotes(gameSession.getGameCode(), phaseKey, voteScope, playerId, votes));
        }
        if (phase.getSubmissionViews() != null) {
            Map<String, List<String>> views = viewsByPhase.computeIfAbsent(phaseOf(phaseKey), k -> new HashMap<>());
            phase.getSubmissionViews().forEach((submissionId, playerIds) ->
                    playerIds.forEach(playerId -> views.computeIfAbsent(playerId, k -> new ArrayList<>()).add(submissionId)));
        }
    }

    /** The phase as the helpers would load it for gameSession. */
    public CollaborativeTextPhase phase(GameSession gameSession, GameState phaseId) {
        return loadPhase(gameSession.getGameCode(), PhaseKeys.phaseKey(gameSession, phaseId), phaseId.name(), PhaseKeys.voteScope(gameSession));
    }

    @Override
    public CollaborativeTextPhase loadPhase(String gameCode, String phaseKey, String phaseId, String voteScope) {
        CollaborativeTextPhase phase = new CollaborativeTextPhase();
        phase.setPhaseId(phaseId);
        phase.setSubmissions(getSubmissions(gameCode, phaseKey));
        phase.setPlayerVotes(getVotes(gameCode, phaseKey, voteScope));
        return phase;
    }

    @Override
    public CollaborativeTextPhase loadPhaseWithViews(String gameCode, String phaseKey, String phaseId) {
        CollaborativeTextPhase phase = new CollaborativeTextPhase();
        phase.setPhaseId(phaseId);
        phase.setSubmissions(getSubmissions(gameCode, phaseKey));
        Map<String, List<String>> viewersBySubmission = new HashMap<>();
        viewsByPhase.getOrDefault(phaseOf(phaseKey), Map.of()).forEach((playerId, submissionIds) ->
                submissionIds.forEach(submissionId ->
                        viewersBySubmission.computeIfAbsent(submissionId, k -> new ArrayList<>()).add(playerId)));
        phase.setSubmissionViews(viewersBySubmission);
        return phase;
    }

    @Override
    public List<TextSubmission> getSubmissions(String gameCode, String phaseKey) {
        return new ArrayList<>(submissionsByPhase.getOrDefault(phaseOf(phaseKey), new LinkedHashMap<>()).values());
    }

    @Override
    public TextSubmission getSubmission(String gameCode, String phaseKey, String submissionId) {
        return submissionsByPhase.getOrDefault(phaseOf(phaseKey), new LinkedHashMap<>()).get(submissionId);
    }

    @Override
    public List<TextSubmission> getSubmissionsByAuthor(String gameCode, String phaseKey, String authorId) {
        return getSubmissions(gameCode, phaseKey).stream()
                .filter(submission -> authorId.equals(submission.getAuthorId()))
                .toList();
    }

    @Override
    public TextSubmission createSubmission(String gameCode, String phaseKey, TextSubmission submission) {
        putSubmissions(gameCode, phaseKey, List.of(submission));
        return submission;
    }

    @Override
    public void putSubmissions(String gameCode, String phaseKey, List<TextSubmission> submissions) {
        LinkedHashMap<String, TextSubmission> phaseSubmissions = submissionsByPhase.computeIfAbsent(phaseOf(phaseKey), k -> new LinkedHashMap<>());
        for (TextSubmission submission : submissions) {
            // An overwrite gets a new insertOrder in Firestore, so it moves to the end here too.
            phaseSubmissions.remove(submission.getSubmissionId());
            submission.setInsertOrder(nextInsertOrder++);
            phaseSubmissions.put(submission.getSubmissionId(), submission);
        }
    }

    @Override
    public void deleteSubmissions(String gameCode, String phaseKey, Collection<String> submissionIds) {
        LinkedHashMap<String, TextSubmission> phaseSubmissions = submissionsByPhase.get(phaseOf(phaseKey));
        if (phaseSubmissions != null) {
            submissionIds.forEach(phaseSubmissions::remove);
        }
    }

    @Override
    public void putVotes(String gameCode, String phaseKey, String voteScope, String playerId, List<PlayerVote> playerVotes) {
        votesByPhase.computeIfAbsent(phaseOf(phaseKey), k -> new HashMap<>())
                .put(playerId, new ArrayList<>(playerVotes));
    }

    @Override
    public Map<String, List<PlayerVote>> getVotes(String gameCode, String phaseKey, String voteScope) {
        return new HashMap<>(votesByPhase.getOrDefault(phaseOf(phaseKey), Map.of()));
    }

    /** Votes cast so far in a phase, across every vote scope. */
    public Map<String, List<PlayerVote>> allVotes(GameState phaseId) {
        return new HashMap<>(votesByPhase.getOrDefault(phaseId.name(), Map.of()));
    }

    private static String phaseOf(String phaseKey) {
        return phaseKey.replaceFirst("_r\\d+.*$", "");
    }

    @Override
    public void putViews(String gameCode, String phaseKey, String playerId, List<String> submissionIds) {
        viewsByPhase.computeIfAbsent(phaseOf(phaseKey), k -> new HashMap<>()).put(playerId, new ArrayList<>(submissionIds));
    }
}
