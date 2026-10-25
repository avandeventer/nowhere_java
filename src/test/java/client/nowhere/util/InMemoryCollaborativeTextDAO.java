package client.nowhere.util;

import client.nowhere.dao.CollaborativeTextDAO;
import client.nowhere.helper.PhasePaths;
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
 * Scope-blind by default: phases are stored by phaseId only (the rounds/... and encounters/...
 * parts of the phasePath are dropped) and votes ignore voteScope. That matches the old embedded
 * collaborativeTextPhases map the fixtures were captured with, so handler tests keep working when
 * they move the player's coordinates or round after loading a fixture. scoped() keeps every
 * phasePath and voteScope apart, like Firestore does; PhasePathsTest covers the path rules.
 */
public class InMemoryCollaborativeTextDAO extends CollaborativeTextDAO {

    private final boolean scoped;
    private final Map<String, LinkedHashMap<String, TextSubmission>> submissionsByPhase = new HashMap<>();
    private final Map<String, Map<String, List<PlayerVote>>> votesByPhase = new HashMap<>();
    private final Map<String, Map<String, List<String>>> viewsByPhase = new HashMap<>();
    private long nextInsertOrder = 1;

    public InMemoryCollaborativeTextDAO() {
        this(false);
    }

    private InMemoryCollaborativeTextDAO(boolean scoped) {
        super(null);
        this.scoped = scoped;
    }

    /** Keeps rounds, encounters and vote scopes apart, as Firestore does. */
    public static InMemoryCollaborativeTextDAO scoped() {
        return new InMemoryCollaborativeTextDAO(true);
    }

    /** Seeds every phase embedded in a captured game session fixture. */
    public void seedFromFixture(GameSession gameSession, String fixtureFileName) throws IOException {
        for (Map.Entry<String, CollaborativeTextPhase> entry : TestJsonLoader.loadPhasesFromJson(fixtureFileName).entrySet()) {
            seedPhase(gameSession, GameState.valueOf(entry.getKey()), entry.getValue());
        }
    }

    public void seedPhase(GameSession gameSession, GameState phaseId, CollaborativeTextPhase phase) {
        String phasePath = PhasePaths.phasePath(gameSession, phaseId);
        if (phase.getSubmissions() != null) {
            putSubmissions(phasePath, phase.getSubmissions());
        }
        if (phase.getPlayerVotes() != null) {
            String voteScope = PhasePaths.voteScope(gameSession);
            phase.getPlayerVotes().forEach((playerId, votes) ->
                    putVotes(phasePath, voteScope, playerId, votes));
        }
        if (phase.getSubmissionViews() != null) {
            Map<String, List<String>> views = viewsByPhase.computeIfAbsent(phaseOf(phasePath), k -> new HashMap<>());
            phase.getSubmissionViews().forEach((submissionId, playerIds) ->
                    playerIds.forEach(playerId -> views.computeIfAbsent(playerId, k -> new ArrayList<>()).add(submissionId)));
        }
    }

    /** The phase as the helpers would load it for gameSession. */
    public CollaborativeTextPhase phase(GameSession gameSession, GameState phaseId) {
        return loadPhase(PhasePaths.phasePath(gameSession, phaseId), phaseId.name(), PhasePaths.voteScope(gameSession));
    }

    @Override
    public CollaborativeTextPhase loadPhase(String phasePath, String phaseId, String voteScope) {
        CollaborativeTextPhase phase = new CollaborativeTextPhase();
        phase.setPhaseId(phaseId);
        phase.setSubmissions(getSubmissions(phasePath));
        phase.setPlayerVotes(getVotes(phasePath, voteScope));
        return phase;
    }

    @Override
    public CollaborativeTextPhase loadPhaseWithViews(String phasePath, String phaseId) {
        CollaborativeTextPhase phase = new CollaborativeTextPhase();
        phase.setPhaseId(phaseId);
        phase.setSubmissions(getSubmissions(phasePath));
        Map<String, List<String>> viewersBySubmission = new HashMap<>();
        viewsByPhase.getOrDefault(phaseOf(phasePath), Map.of()).forEach((playerId, submissionIds) ->
                submissionIds.forEach(submissionId ->
                        viewersBySubmission.computeIfAbsent(submissionId, k -> new ArrayList<>()).add(playerId)));
        phase.setSubmissionViews(viewersBySubmission);
        return phase;
    }

    @Override
    public List<TextSubmission> getSubmissions(String phasePath) {
        return new ArrayList<>(submissionsByPhase.getOrDefault(phaseOf(phasePath), new LinkedHashMap<>()).values());
    }

    @Override
    public TextSubmission getSubmission(String phasePath, String submissionId) {
        return submissionsByPhase.getOrDefault(phaseOf(phasePath), new LinkedHashMap<>()).get(submissionId);
    }

    @Override
    public List<TextSubmission> getSubmissionsByAuthor(String phasePath, String authorId) {
        return getSubmissions(phasePath).stream()
                .filter(submission -> authorId.equals(submission.getAuthorId()))
                .toList();
    }

    @Override
    public TextSubmission createSubmission(String phasePath, TextSubmission submission) {
        putSubmissions(phasePath, List.of(submission));
        return submission;
    }

    @Override
    public void putSubmissions(String phasePath, List<TextSubmission> submissions) {
        LinkedHashMap<String, TextSubmission> phaseSubmissions = submissionsByPhase.computeIfAbsent(phaseOf(phasePath), k -> new LinkedHashMap<>());
        for (TextSubmission submission : submissions) {
            // An overwrite gets a new insertOrder in Firestore, so it moves to the end here too.
            phaseSubmissions.remove(submission.getSubmissionId());
            submission.setInsertOrder(nextInsertOrder++);
            phaseSubmissions.put(submission.getSubmissionId(), submission);
        }
    }

    @Override
    public void deleteSubmissions(String phasePath, Collection<String> submissionIds) {
        LinkedHashMap<String, TextSubmission> phaseSubmissions = submissionsByPhase.get(phaseOf(phasePath));
        if (phaseSubmissions != null) {
            submissionIds.forEach(phaseSubmissions::remove);
        }
    }

    @Override
    public void putVotes(String phasePath, String voteScope, String playerId, List<PlayerVote> playerVotes) {
        votesByPhase.computeIfAbsent(votesOf(phasePath, voteScope), k -> new HashMap<>())
                .put(playerId, new ArrayList<>(playerVotes));
    }

    @Override
    public Map<String, List<PlayerVote>> getVotes(String phasePath, String voteScope) {
        return new HashMap<>(votesByPhase.getOrDefault(votesOf(phasePath, voteScope), Map.of()));
    }

    /** Votes cast so far in a phase, across every round, encounter and vote scope. */
    public Map<String, List<PlayerVote>> allVotes(GameState phaseId) {
        Map<String, List<PlayerVote>> allVotes = new HashMap<>();
        votesByPhase.forEach((key, votes) -> {
            if (lastSegment(key.split("\\|")[0]).equals(phaseId.name())) {
                allVotes.putAll(votes);
            }
        });
        return allVotes;
    }

    @Override
    public void putViews(String phasePath, String playerId, List<String> submissionIds) {
        viewsByPhase.computeIfAbsent(phaseOf(phasePath), k -> new HashMap<>()).put(playerId, new ArrayList<>(submissionIds));
    }

    private String phaseOf(String phasePath) {
        return scoped ? phasePath : lastSegment(phasePath);
    }

    private String votesOf(String phasePath, String voteScope) {
        return scoped ? phasePath + "|" + voteScope : lastSegment(phasePath);
    }

    private static String lastSegment(String phasePath) {
        return phasePath.substring(phasePath.lastIndexOf('/') + 1);
    }
}
