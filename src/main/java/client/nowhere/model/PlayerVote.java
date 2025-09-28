package client.nowhere.model;

import java.time.LocalDateTime;

public class PlayerVote {
    private String voteId;
    private String playerId;
    private String submissionId;
    private int ranking; // 1 = best, 2 = second, 3 = third
    private LocalDateTime votedAt;

    public PlayerVote() {
        this.votedAt = LocalDateTime.now();
    }

    public PlayerVote(String voteId, String playerId, String submissionId, int ranking) {
        this();
        this.voteId = voteId;
        this.playerId = playerId;
        this.submissionId = submissionId;
        this.ranking = ranking;
    }

    // Getters and Setters
    public String getVoteId() { return voteId; }
    public void setVoteId(String voteId) { this.voteId = voteId; }

    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }

    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }

    public int getRanking() { return ranking; }
    public void setRanking(int ranking) { this.ranking = ranking; }

    public LocalDateTime getVotedAt() { return votedAt; }
    public void setVotedAt(LocalDateTime votedAt) { this.votedAt = votedAt; }
}
