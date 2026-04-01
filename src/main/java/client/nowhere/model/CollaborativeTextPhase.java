package client.nowhere.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class CollaborativeTextPhase {
    private String phaseId;
    private String question; // e.g., "Where are we?", "Who are we?", "What is our goal?"
    private PhaseType phaseType; // SUBMISSION, VOTING, or WINNING
    private List<TextSubmission> submissions;
    private Map<String, List<PlayerVote>> playerVotes; // playerId -> list of their votes
    private List<String> playersWhoSubmitted;
    private List<String> playersWhoVoted;
    private String finalResult; // The winning text that goes to GameSessionDisplay
    private Map<String, List<String>> submissionViews; // submissionId -> List of playerIds who viewed it // Key: playerId_submissionId
    private boolean isComplete;

    public CollaborativeTextPhase() {
        this.submissions = new ArrayList<>();
        this.playerVotes = new HashMap<>();
        this.playersWhoSubmitted = new ArrayList<>();
        this.playersWhoVoted = new ArrayList<>();
        this.submissionViews = new HashMap<>();
        this.isComplete = false;
    }

    public CollaborativeTextPhase(String phaseId, String question, PhaseType phaseType) {
        this();
        this.phaseId = phaseId;
        this.question = question;
        this.phaseType = phaseType;
    }

    // Getters and Setters
    public String getPhaseId() { return phaseId; }
    public void setPhaseId(String phaseId) { this.phaseId = phaseId; }

    public String getQuestion() { return question; }
    public void setQuestion(String question) { this.question = question; }

    public PhaseType getPhaseType() { return phaseType; }
    public void setPhaseType(PhaseType phaseType) { this.phaseType = phaseType; }

    public List<TextSubmission> getSubmissions() { return submissions; }
    public void setSubmissions(List<TextSubmission> submissions) { this.submissions = submissions; }

    public Map<String, List<PlayerVote>> getPlayerVotes() { return playerVotes; }
    public void setPlayerVotes(Map<String, List<PlayerVote>> playerVotes) { this.playerVotes = playerVotes; }

    public List<String> getPlayersWhoSubmitted() { return playersWhoSubmitted; }
    public void setPlayersWhoSubmitted(List<String> playersWhoSubmitted) { this.playersWhoSubmitted = playersWhoSubmitted; }

    public List<String> getPlayersWhoVoted() { return playersWhoVoted; }
    public void setPlayersWhoVoted(List<String> playersWhoVoted) { this.playersWhoVoted = playersWhoVoted; }

    public String getFinalResult() { return finalResult; }
    public void setFinalResult(String finalResult) { this.finalResult = finalResult; }

    public boolean isComplete() { return isComplete; }
    public void setComplete(boolean complete) { isComplete = complete; }

    public Map<String, List<String>> getSubmissionViews() { return submissionViews; }
    public void setSubmissionViews(Map<String, List<String>> submissionViews) { this.submissionViews = submissionViews; }

    // Helper methods
    public void addSubmission(TextSubmission submission) {
        this.submissions.add(submission);
        if (!this.playersWhoSubmitted.contains(submission.getAuthorId())) {
            this.playersWhoSubmitted.add(submission.getAuthorId());
        }
    }

    /**
     * Removes a submission by its ID from the phase.
     * @param submissionId The ID of the submission to remove
     * @return true if the submission was removed, false if it wasn't found
     */
    public boolean removeSubmissionById(String submissionId) {
        boolean removed = this.submissions.removeIf(s -> s.getSubmissionId().equals(submissionId));
        // Also remove from submissionViews if present
        if (removed) {
            this.submissionViews.remove(submissionId);
        }
        return removed;
    }

    public void addPlayerVote(PlayerVote vote) {
        this.playerVotes.computeIfAbsent(vote.getPlayerId(), k -> new ArrayList<>()).add(vote);
        if (!this.playersWhoVoted.contains(vote.getPlayerId())) {
            this.playersWhoVoted.add(vote.getPlayerId());
        }
    }

    public void transitionToVotingPhase() {
        this.phaseType = PhaseType.VOTING;
        this.playersWhoVoted.clear();
    }

    public TextSubmission getSubmissionById(String submissionId) {
        return submissions.stream()
                .filter(s -> s.getSubmissionId().equals(submissionId))
                .findFirst()
                .orElse(null);
    }


    /**
     * Gets submissions that a player can still view (not exhausted).
     * @param playerId The player requesting submissions
     * @param outcomeTypeId Optional outcome type ID to filter by (for WHAT_WILL_BECOME_OF_US phase)
     * @return List of submissions available to the player
     */
    public List<TextSubmission> getAvailableSubmissionsForPlayer(String playerId, String outcomeTypeId) {
        return submissions.stream()
                .filter(submission -> {
                    // Don't show player's own submissions
                    if (submission.getAuthorId().equals(playerId)) {
                        return false;
                    }
                    
                    // Filter by outcome type if provided (for WHAT_WILL_BECOME_OF_US phase)
                    if (outcomeTypeId != null && !outcomeTypeId.isEmpty()) {
                        String submissionOutcomeType = submission.getOutcomeType();
                        if (submissionOutcomeType == null || !outcomeTypeId.equals(submissionOutcomeType)) {
                            return false;
                        }
                    }
                    
                    String submissionId = submission.getSubmissionId();
                    List<String> viewers = submissionViews.getOrDefault(submissionId, new ArrayList<>());
                    
                    // If a player is currently viewing this submission, it's unavailable
                    return viewers.size() < 1;
                })
                .toList();
    }

    /**
     * Records a view for a submission by a player.
     * @param submissionId The submission being viewed
     * @param playerId The player viewing the submission
     * @return true if the view was recorded, false if the player already viewed this submission
     */
    public boolean recordSubmissionView(String submissionId, String playerId) {
        List<String> viewers = submissionViews.computeIfAbsent(submissionId, k -> new ArrayList<>());
        
        // Check if player already viewed this submission
        if (viewers.contains(playerId)) {
            return false; // Already viewed
        }
        
        // Add player to viewers list
        viewers.add(playerId);
        return true; // View recorded
    }

    /**
     * Clears all views for a specific player across all submissions.
     * @param playerId The player whose views should be cleared
     */
    public void clearViewsForPlayer(String playerId) {
        for (List<String> viewers : submissionViews.values()) {
            viewers.remove(playerId);
        }
    }

    /**
     * Filters out submissions that have been iterated on (parent submissions).
     * A submission is considered a parent if it is referenced in any other submission's additions.
     * This method modifies the submissions list in place.
     */
    public List<TextSubmission> getSubmissionsWithoutParentSubmissions() {
        // Collect all submissionIds that are referenced in other submissions' additions
        Set<String> iteratedSubmissionIds = submissions.stream()
                .flatMap(submission -> submission.getAdditions() != null 
                        ? submission.getAdditions().stream() 
                        : java.util.stream.Stream.empty())
                .map(TextAddition::getSubmissionId)
                .filter(submissionId -> submissionId != null && !submissionId.trim().isEmpty())
                .collect(Collectors.toSet());
        
        // Filter out submissions that have been iterated on
        return submissions.stream()
                .filter(submission -> !iteratedSubmissionIds.contains(submission.getSubmissionId()))
                .collect(Collectors.toList());
    }

    /**
     * Resets all submissions and votes from the phase.
     * This clears submissions, votes, player tracking lists, and submission views.
     */
    public void resetAll() {
        this.submissions = new ArrayList<>();
        this.playerVotes = new HashMap<>();
        this.playersWhoSubmitted = new ArrayList<>();
        this.playersWhoVoted = new ArrayList<>();
        this.submissionViews = new HashMap<>();
    }

    /**
     * Resets votes and related tracking data, but keeps submissions.
     * This clears votes, player tracking lists, and submission views.
     */
    public void resetVotes() {
        this.playerVotes = new HashMap<>();
        this.playersWhoSubmitted = new ArrayList<>();
        this.playersWhoVoted = new ArrayList<>();
        this.submissionViews = new HashMap<>();
    }
}
