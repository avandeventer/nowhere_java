package client.nowhere.model;

import com.google.cloud.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class TextSubmission {
    private String submissionId;
    private String authorId;
    private String originalText;
    private String currentText; // This gets modified as players add to it
    private List<TextAddition> additions;
    private Timestamp createdAt;
    private Timestamp lastModified;
    private boolean isFinalized;
    private int totalVotes;
    private double averageRanking;

    public TextSubmission() {
        this.additions = new ArrayList<>();
        this.createdAt = Timestamp.now();
        this.lastModified = Timestamp.now();
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
        this.lastModified = Timestamp.now();
    }

    public List<TextAddition> getAdditions() { return additions; }
    public void setAdditions(List<TextAddition> additions) { this.additions = additions; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getLastModified() { return lastModified; }
    public void setLastModified(Timestamp lastModified) { this.lastModified = lastModified; }

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
        this.lastModified = Timestamp.now();
    }

    public void incrementVotes() {
        this.totalVotes++;
    }
}
