package client.nowhere.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CollaborativeTextPhase {
    private String phaseId;
    private String question; // e.g., "Where are we?", "Who are we?", "What is our goal?"
    private PhaseType phaseType; // SUBMISSION or VOTING
    private List<TextSubmission> submissions;
    private Map<String, List<PlayerVote>> playerVotes; // playerId -> list of their votes
    private List<String> playersWhoSubmitted;
    private List<String> playersWhoVoted;
    private String finalResult; // The winning text that goes to GameSessionDisplay
    private Map<String, List<String>> submissionViews; // submissionId -> List of playerIds who viewed it // Key: playerId_submissionId
    private boolean isComplete;

    public enum PhaseType {
        SUBMISSION, // Players submit and elaborate on text
        VOTING      // Players vote on final submissions
    }

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
     * @param requestedCount Number of submissions requested
     * @return List of submissions available to the player
     */
    public List<TextSubmission> getAvailableSubmissionsForPlayer(String playerId, int requestedCount) {
        return submissions.stream()
                .filter(submission -> {
                    // Don't show player's own submissions
                    if (submission.getAuthorId().equals(playerId)) {
                        return false;
                    }
                    
                    String submissionId = submission.getSubmissionId();
                    List<String> viewers = submissionViews.getOrDefault(submissionId, new ArrayList<>());
                    
                    // If 2 or more players have viewed this submission, it's exhausted
                    return viewers.size() < 2;
                })
                .limit(requestedCount)
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
}
