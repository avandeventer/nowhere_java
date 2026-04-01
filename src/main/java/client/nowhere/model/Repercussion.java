package client.nowhere.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Repercussion {
    String repercussionType;
    String repercussionSubmission;

    public Repercussion() {
        this.repercussionType = "";
        this.repercussionSubmission = "";
    }

    public Repercussion(String repercussionType, String repercussionSubmission) {
        this.repercussionType = repercussionType;
        this.repercussionSubmission = repercussionSubmission;
    }

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
        for (RepercussionType type : RepercussionType.values()) {
            if (type.getName().equals(repercussionType)) {
                return type.getColor();
            }
        }
        return null;
    }
}
