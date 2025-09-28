package client.nowhere.model;

import com.google.cloud.Timestamp;

public class TextAddition {
    private String additionId;
    private String authorId;
    private String addedText;
    private Timestamp addedAt;
    private String submissionId; // Reference to the original submission

    public TextAddition() {
        this.addedAt = Timestamp.now();
    }

    public TextAddition(String additionId, String authorId, String addedText, String submissionId) {
        this();
        this.additionId = additionId;
        this.authorId = authorId;
        this.addedText = addedText;
        this.submissionId = submissionId;
    }

    // Getters and Setters
    public String getAdditionId() { return additionId; }
    public void setAdditionId(String additionId) { this.additionId = additionId; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getAddedText() { return addedText; }
    public void setAddedText(String addedText) { this.addedText = addedText; }

    public Timestamp getAddedAt() { return addedAt; }
    public void setAddedAt(Timestamp addedAt) { this.addedAt = addedAt; }

    public String getSubmissionId() { return submissionId; }
    public void setSubmissionId(String submissionId) { this.submissionId = submissionId; }
}
