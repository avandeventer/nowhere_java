package client.nowhere.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Tracks how many times a player has viewed a specific submission.
 * This helps ensure fair distribution of submissions across players.
 */
public class SubmissionView {
    private String viewId;
    private String playerId;
    private String submissionId;
    private LocalDateTime firstViewedAt;
    private LocalDateTime lastViewedAt;
    private int viewCount;
    private boolean isExhausted; // true when this player has seen this submission enough times

    public SubmissionView() {}

    public SubmissionView(String playerId, String submissionId) {
        this.playerId = playerId;
        this.submissionId = submissionId;
        this.firstViewedAt = LocalDateTime.now();
        this.lastViewedAt = LocalDateTime.now();
        this.viewCount = 1;
        this.isExhausted = false;
    }

    // Getters and Setters
    public String getViewId() {
        return viewId;
    }

    public void setViewId(String viewId) {
        this.viewId = viewId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getSubmissionId() {
        return submissionId;
    }

    public void setSubmissionId(String submissionId) {
        this.submissionId = submissionId;
    }

    public LocalDateTime getFirstViewedAt() {
        return firstViewedAt;
    }

    public void setFirstViewedAt(LocalDateTime firstViewedAt) {
        this.firstViewedAt = firstViewedAt;
    }

    public LocalDateTime getLastViewedAt() {
        return lastViewedAt;
    }

    public void setLastViewedAt(LocalDateTime lastViewedAt) {
        this.lastViewedAt = lastViewedAt;
    }

    public int getViewCount() {
        return viewCount;
    }

    public void setViewCount(int viewCount) {
        this.viewCount = viewCount;
    }

    public boolean isExhausted() {
        return isExhausted;
    }

    public void setExhausted(boolean exhausted) {
        isExhausted = exhausted;
    }

    /**
     * Increments the view count and updates the last viewed timestamp.
     * Marks as exhausted if the view count reaches the maximum allowed views.
     */
    public void recordView(int maxViews) {
        this.viewCount++;
        this.lastViewedAt = LocalDateTime.now();
        this.isExhausted = this.viewCount >= maxViews;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SubmissionView that = (SubmissionView) o;
        return Objects.equals(playerId, that.playerId) && 
               Objects.equals(submissionId, that.submissionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(playerId, submissionId);
    }

    @Override
    public String toString() {
        return "SubmissionView{" +
                "viewId='" + viewId + '\'' +
                ", playerId='" + playerId + '\'' +
                ", submissionId='" + submissionId + '\'' +
                ", viewCount=" + viewCount +
                ", isExhausted=" + isExhausted +
                '}';
    }
}
