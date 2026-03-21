package client.nowhere.model;

public class Repercussion {
    String repercussionType;
    String repercussionSubmission;

    public String getRepercussionSubmission() {
        return repercussionSubmission;
    }

    public void setRepercussionSubmission(String repercussionSubmission) {
        this.repercussionSubmission = repercussionSubmission;
    }

    public String getRepercussionType() {
        return repercussionType;
    }

    public void setRepercussionType(String repercussionType) {
        this.repercussionType = repercussionType;
    }

    public String getColor() {
        if (repercussionType == null) return null;
        try {
            return RepercussionType.valueOf(repercussionType.toUpperCase()).getColor();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
