package client.nowhere.model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TextSubmission {
    private String submissionId;
    private String authorId;
    private String originalText;
    private String currentText; // This gets modified as players add to it
    private List<TextAddition> additions;
    private LocalDateTime createdAt;
    private LocalDateTime lastModified;
    private boolean isFinalized;
    private int totalVotes;
    private double averageRanking;

    public TextSubmission() {
        this.additions = new ArrayList<>();
        this.createdAt = LocalDateTime.now();
        this.lastModified = LocalDateTime.now();
        this.isFinalized = false;
        this.totalVotes = 0;
        this.averageRanking = 0.0;
    }

    public TextSubmission(String submissionId, String authorId, String originalText) {
        this();
        this.submissionId = submissionId;
        this.authorId = authorId;
        this.originalText = originalText;
        this.currentText = originalText;
    }

    // Getters and Setters
    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getOriginalText() { return originalText; }
    public void setOriginalText(String originalText) { this.originalText = originalText; }

    public String getCurrentText() { return currentText; }
    public void setCurrentText(String currentText) { 
        this.currentText = currentText; 
        this.lastModified = LocalDateTime.now();
    }

    public List<TextAddition> getAdditions() { return additions; }
    public void setAdditions(List<TextAddition> additions) { this.additions = additions; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getLastModified() { return lastModified; }
    public void setLastModified(LocalDateTime lastModified) { this.lastModified = lastModified; }

    public boolean isFinalized() { return isFinalized; }
    public void setFinalized(boolean finalized) { isFinalized = finalized; }

    public int getTotalVotes() { return totalVotes; }
    public void setTotalVotes(int totalVotes) { this.totalVotes = totalVotes; }

    public double getAverageRanking() { return averageRanking; }
    public void setAverageRanking(double averageRanking) { this.averageRanking = averageRanking; }

    // Helper methods
    public void addTextAddition(TextAddition addition) {
        this.additions.add(addition);
        this.currentText += " " + addition.getAddedText();
        this.lastModified = LocalDateTime.now();
    }

    public void incrementVotes() {
        this.totalVotes++;
    }
}
