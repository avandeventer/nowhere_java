package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.helper.PhasePaths;
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
 * Stores collaborative text phases outside the GameSession document, under a phase document whose
 * path comes from PhasePaths (e.g. gameSessions/{code}/rounds/{round}/phases/{phaseId}):
 *
 * {phasePath}/submissions/{submissionId}       one TextSubmission each
 * {phasePath}/votes/{voteScope}_{playerId}     one PlayerBallot each
 * {phasePath}/views/{playerId}                 { playerId, submissionIds }
 *
 * Every document is written by exactly one request (a player's own submission, ballot or views, or
 * a phase initializer's batch), so nothing here needs a transaction. voteScope also comes from
 * PhasePaths. No round, encounter or phase document is written: Firestore subcollections don't
 * need their parent document to exist.
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
    public CollaborativeTextPhase loadPhase(String phasePath, String phaseId, String voteScope) {
        ApiFuture<QuerySnapshot> submissionsFuture = submissions(phasePath).orderBy("insertOrder").get();
        ApiFuture<QuerySnapshot> votesFuture = votes(phasePath).whereEqualTo("voteScope", voteScope).get();

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
    public CollaborativeTextPhase loadPhaseWithViews(String phasePath, String phaseId) {
        ApiFuture<QuerySnapshot> submissionsFuture = submissions(phasePath).orderBy("insertOrder").get();
        ApiFuture<QuerySnapshot> viewsFuture = views(phasePath).get();

        CollaborativeTextPhase phase = emptyPhase(phaseId);
        phase.setSubmissions(toSubmissions(await(submissionsFuture)));
        phase.setSubmissionViews(toViewersBySubmission(await(viewsFuture)));
        phase.setPlayersWhoSubmitted(authorIds(phase.getSubmissions()));
        return phase;
    }

    public List<TextSubmission> getSubmissions(String phasePath) {
        return toSubmissions(await(submissions(phasePath).orderBy("insertOrder").get()));
    }

    public TextSubmission getSubmission(String phasePath, String submissionId) {
        DocumentSnapshot snapshot = await(submissions(phasePath).document(submissionId).get());
        return snapshot.exists() ? snapshot.toObject(TextSubmission.class) : null;
    }

    public List<TextSubmission> getSubmissionsByAuthor(String phasePath, String authorId) {
        // Sorted in memory: whereEqualTo + orderBy on different fields would need a composite index.
        List<TextSubmission> authorSubmissions = new ArrayList<>(
                toSubmissions(await(submissions(phasePath).whereEqualTo("authorId", authorId).get())));
        authorSubmissions.sort(Comparator.comparingLong(TextSubmission::getInsertOrder));
        return authorSubmissions;
    }

    public TextSubmission createSubmission(String phasePath, TextSubmission submission) {
        submission.setInsertOrder(nextInsertOrder());
        await(submissions(phasePath).document(submission.getSubmissionId()).set(submission));
        return submission;
    }

    /**
     * Writes submissions in as few batched commits as possible, keeping list order. Submissions
     * with an existing ID are overwritten, so phase initializers are safe to re-run.
     */
    public void putSubmissions(String phasePath, List<TextSubmission> submissionsToPut) {
        if (submissionsToPut == null || submissionsToPut.isEmpty()) {
            return;
        }
        long baseOrder = nextInsertOrder();
        CollectionReference submissionsRef = submissions(phasePath);
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

    public void deleteSubmissions(String phasePath, Collection<String> submissionIds) {
        if (submissionIds == null || submissionIds.isEmpty()) {
            return;
        }
        CollectionReference submissionsRef = submissions(phasePath);
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
    public void putVotes(String phasePath, String voteScope, String playerId, List<PlayerVote> playerVotes) {
        PlayerBallot ballot = new PlayerBallot(playerId, voteScope, new ArrayList<>(playerVotes));
        await(votes(phasePath).document(voteScope + "_" + playerId).set(ballot));
    }

    /**
     * @return playerId -> that player's votes, for one vote
     */
    public Map<String, List<PlayerVote>> getVotes(String phasePath, String voteScope) {
        return toVotesByPlayer(await(votes(phasePath).whereEqualTo("voteScope", voteScope).get()));
    }

    /**
     * Replaces the list of submissions a player is currently viewing. One blind write, no read.
     */
    public void putViews(String phasePath, String playerId, List<String> submissionIds) {
        Map<String, Object> viewsDocument = new HashMap<>();
        viewsDocument.put("playerId", playerId);
        viewsDocument.put("submissionIds", new ArrayList<>(submissionIds));
        await(views(phasePath).document(playerId).set(viewsDocument));
    }

    private CollectionReference submissions(String phasePath) {
        return db.document(phasePath).collection(PhasePaths.SUBMISSIONS);
    }

    private CollectionReference votes(String phasePath) {
        return db.document(phasePath).collection(PhasePaths.VOTES);
    }

    private CollectionReference views(String phasePath) {
        return db.document(phasePath).collection(PhasePaths.VIEWS);
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
