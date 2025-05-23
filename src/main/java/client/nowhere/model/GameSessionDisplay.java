package client.nowhere.model;

public class GameSessionDisplay {
    String mapDescription;
    String goalDescription;
    String endingDescription;
    String successText;
    String neutralText;
    String failureText;

    public GameSessionDisplay() { }

    public GameSessionDisplay(
            String mapDescription,
            String goalDescription,
            String endingDescription
    ) {
        this.mapDescription = mapDescription;
        this.goalDescription = goalDescription;
        this.endingDescription = endingDescription;
    }

    public String getMapDescription() {
        return mapDescription;
    }

    public void setMapDescription(String mapDescription) {
        this.mapDescription = mapDescription;
    }

    public String getSuccessText() {
        return successText;
    }

    public void setSuccessText(String successText) {
        this.successText = successText;
    }

    public String getNeutralText() {
        return neutralText;
    }

    public void setNeutralText(String neutralText) {
        this.neutralText = neutralText;
    }

    public String getFailureText() {
        return failureText;
    }

    public void setFailureText(String failureText) {
        this.failureText = failureText;
    }

    public String getGoalDescription() {
        return goalDescription;
    }

    public void setGoalDescription(String goalDescription) {
        this.goalDescription = goalDescription;
    }

}
