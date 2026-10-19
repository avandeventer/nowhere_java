package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.CollaborativeTextPhase;
import client.nowhere.model.PlayerBallot;
import client.nowhere.model.PlayerVote;
import client.nowhere.model.TextSubmission;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ExecutionException;

/**
 * Stores collaborative text phases outside the GameSession document:
 *
 * gameSessions/{code}/phases/{phaseKey}/submissions/{submissionId}  one TextSubmission each
 * gameSessions/{code}/phases/{phaseKey}/votes/{voteScope}_{playerId} one PlayerBallot each
 * gameSessions/{code}/phases/{phaseKey}/views/{playerId}             { playerId, submissionIds }
 *
 * Every document is written by exactly one request (a player's own submission, ballot or views, or
 * a phase initializer's batch), so nothing here needs a transaction. phaseKey and voteScope come
 * from PhaseKeys. No parent phase document is written: Firestore subcollections don't need one.
 */
@Component
public class CollaborativeTextDAO {

    private static final int MAX_BATCH_WRITES = 500;

    private final Firestore db;

    @Autowired
    public CollaborativeTextDAO(Firestore db) {
        this.db = db;
    }

    /**
     * Loads a phase's submissions and the votes for one voteScope, as an in-memory
     * CollaborativeTextPhase so the model's winner/filter methods work unchanged.
     * Returns an empty phase (never null) when nothing has been written yet.
     */
    public CollaborativeTextPhase loadPhase(String gameCode, String phaseKey, String phaseId, String voteScope) {
        ApiFuture<QuerySnapshot> submissionsFuture = submissions(gameCode, phaseKey).orderBy("insertOrder").get();
        ApiFuture<QuerySnapshot> votesFuture = votes(gameCode, phaseKey).whereEqualTo("voteScope", voteScope).get();

        CollaborativeTextPhase phase = emptyPhase(phaseId);
        phase.setSubmissions(toSubmissions(await(submissionsFuture)));
        phase.setPlayerVotes(toVotesByPlayer(await(votesFuture)));
        phase.setPlayersWhoSubmitted(authorIds(phase.getSubmissions()));
        phase.setPlayersWhoVoted(new ArrayList<>(phase.getPlayerVotes().keySet()));
        return phase;
    }

    /**
     * Loads a phase's submissions and every player's current views, for /available.
     */
    public CollaborativeTextPhase loadPhaseWithViews(String gameCode, String phaseKey, String phaseId) {
        ApiFuture<QuerySnapshot> submissionsFuture = submissions(gameCode, phaseKey).orderBy("insertOrder").get();
        ApiFuture<QuerySnapshot> viewsFuture = views(gameCode, phaseKey).get();

        CollaborativeTextPhase phase = emptyPhase(phaseId);
        phase.setSubmissions(toSubmissions(await(submissionsFuture)));
        phase.setSubmissionViews(toViewersBySubmission(await(viewsFuture)));
        phase.setPlayersWhoSubmitted(authorIds(phase.getSubmissions()));
        return phase;
    }

    public List<TextSubmission> getSubmissions(String gameCode, String phaseKey) {
        return toSubmissions(await(submissions(gameCode, phaseKey).orderBy("insertOrder").get()));
    }

    public TextSubmission getSubmission(String gameCode, String phaseKey, String submissionId) {
        DocumentSnapshot snapshot = await(submissions(gameCode, phaseKey).document(submissionId).get());
        return snapshot.exists() ? snapshot.toObject(TextSubmission.class) : null;
    }

    public List<TextSubmission> getSubmissionsByAuthor(String gameCode, String phaseKey, String authorId) {
        // Sorted in memory: whereEqualTo + orderBy on different fields would need a composite index.
        List<TextSubmission> authorSubmissions = new ArrayList<>(
                toSubmissions(await(submissions(gameCode, phaseKey).whereEqualTo("authorId", authorId).get())));
        authorSubmissions.sort(Comparator.comparingLong(TextSubmission::getInsertOrder));
        return authorSubmissions;
    }

    public TextSubmission createSubmission(String gameCode, String phaseKey, TextSubmission submission) {
        submission.setInsertOrder(nextInsertOrder());
        await(submissions(gameCode, phaseKey).document(submission.getSubmissionId()).set(submission));
        return submission;
    }

    /**
     * Writes submissions in as few batched commits as possible, keeping list order. Submissions
     * with an existing ID are overwritten, so phase initializers are safe to re-run.
     */
    public void putSubmissions(String gameCode, String phaseKey, List<TextSubmission> submissionsToPut) {
        if (submissionsToPut == null || submissionsToPut.isEmpty()) {
            return;
        }
        long baseOrder = nextInsertOrder();
        CollectionReference submissionsRef = submissions(gameCode, phaseKey);
        List<WriteBatch> batches = new ArrayList<>();
        for (int i = 0; i < submissionsToPut.size(); i++) {
            if (i % MAX_BATCH_WRITES == 0) {
                batches.add(db.batch());
            }
            TextSubmission submission = submissionsToPut.get(i);
            submission.setInsertOrder(baseOrder + i);
            batches.getLast().set(submissionsRef.document(submission.getSubmissionId()), submission);
        }
        commitAll(batches);
    }

    public void deleteSubmissions(String gameCode, String phaseKey, Collection<String> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return;
        }
        CollectionReference submissionsRef = submissions(gameCode, phaseKey);
        List<WriteBatch> batches = new ArrayList<>();
        int i = 0;
        for (String submissionId : submissionIds) {
            if (i++ % MAX_BATCH_WRITES == 0) {
                batches.add(db.batch());
            }
            batches.getLast().delete(submissionsRef.document(submissionId));
        }
        commitAll(batches);
    }

    /**
     * Replaces a player's ballot for one vote. One blind write, no read.
     */
    public void putVotes(String gameCode, String phaseKey, String voteScope, String playerId, List<PlayerVote> playerVotes) {
        PlayerBallot ballot = new PlayerBallot(playerId, voteScope, new ArrayList<>(playerVotes));
        await(votes(gameCode, phaseKey).document(voteScope + "_" + playerId).set(ballot));
    }

    /**
     * @return playerId -> that player's votes, for one vote
     */
    public Map<String, List<PlayerVote>> getVotes(String gameCode, String phaseKey, String voteScope) {
        return toVotesByPlayer(await(votes(gameCode, phaseKey).whereEqualTo("voteScope", voteScope).get()));
    }

    /**
     * Replaces the list of submissions a player is currently viewing. One blind write, no read.
     */
    public void putViews(String gameCode, String phaseKey, String playerId, List<String> submissionIds) {
        Map<String, Object> viewsDocument = new HashMap<>();
        viewsDocument.put("playerId", playerId);
        viewsDocument.put("submissionIds", new ArrayList<>(submissionIds));
        await(views(gameCode, phaseKey).document(playerId).set(viewsDocument));
    }

    private CollectionReference phases(String gameCode) {
        return db.collection("gameSessions").document(gameCode).collection("phases");
    }

    private CollectionReference submissions(String gameCode, String phaseKey) {
        return phases(gameCode).document(phaseKey).collection("submissions");
    }

    private CollectionReference votes(String gameCode, String phaseKey) {
        return phases(gameCode).document(phaseKey).collection("votes");
    }

    private CollectionReference views(String gameCode, String phaseKey) {
        return phases(gameCode).document(phaseKey).collection("views");
    }

    private static CollaborativeTextPhase emptyPhase(String phaseId) {
        CollaborativeTextPhase phase = new CollaborativeTextPhase();
        phase.setPhaseId(phaseId);
        return phase;
    }

    private static List<TextSubmission> toSubmissions(QuerySnapshot snapshot) {
        List<TextSubmission> submissionList = new ArrayList<>();
        for (QueryDocumentSnapshot document : snapshot.getDocuments()) {
            submissionList.add(document.toObject(TextSubmission.class));
        }
        return submissionList;
    }

    private static Map<String, List<PlayerVote>> toVotesByPlayer(QuerySnapshot snapshot) {
        Map<String, List<PlayerVote>> votesByPlayer = new HashMap<>();
        for (QueryDocumentSnapshot document : snapshot.getDocuments()) {
            PlayerBallot ballot = document.toObject(PlayerBallot.class);
            votesByPlayer.put(ballot.getPlayerId(), ballot.getVotes() != null ? ballot.getVotes() : new ArrayList<>());
        }
        return votesByPlayer;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<String>> toViewersBySubmission(QuerySnapshot snapshot) {
        Map<String, List<String>> viewersBySubmission = new HashMap<>();
        for (QueryDocumentSnapshot document : snapshot.getDocuments()) {
            String playerId = document.getString("playerId");
            List<String> submissionIds = (List<String>) document.get("submissionIds");
            if (playerId == null || submissionIds == null) {
                continue;
            }
            for (String submissionId : submissionIds) {
                viewersBySubmission.computeIfAbsent(submissionId, k -> new ArrayList<>()).add(playerId);
            }
        }
        return viewersBySubmission;
    }

    private static List<String> authorIds(List<TextSubmission> submissionList) {
        return submissionList.stream()
                .map(TextSubmission::getAuthorId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    /** Microseconds since epoch; leaves room for a batch's per-item offset. */
    private static long nextInsertOrder() {
        return System.currentTimeMillis() * 1000;
    }

    private static void commitAll(List<WriteBatch> batches) {
        List<ApiFuture<List<WriteResult>>> commits = batches.stream().map(WriteBatch::commit).toList();
        commits.forEach(CollaborativeTextDAO::await);
    }

    private static <T> T await(ApiFuture<T> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResourceException("Interrupted while accessing collaborative text phase data", e);
        } catch (ExecutionException e) {
            throw new ResourceException("Failed to access collaborative text phase data", e);
        }
    }
}
