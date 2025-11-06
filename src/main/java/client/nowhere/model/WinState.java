package client.nowhere.model;

public class WinState {
    private String outcomeText;
    private String outcomeImage;
    private String state;

    public WinState(String outcomeText, String outcomeImage, String state) {
        this.outcomeText = outcomeText;
        this.outcomeImage = outcomeImage;
        this.state = state;
    }

    public String getOutcomeText() {
        return outcomeText;
    }

    public String getOutcomeImage() {
        return outcomeImage;
    }

    public void setOutcomeText(String outcomeText) {
        this.outcomeText = outcomeText;
    }

    public void setOutcomeImage(String outcomeImage) {
        this.outcomeImage = outcomeImage;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
