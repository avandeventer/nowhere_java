package client.nowhere.model;

import java.util.ArrayList;

public class Option {

    String optionText;
    Stat statRequirement;
    int statDC;
    ArrayList<Repercussion> successResults;
    ArrayList<Repercussion> failureResults;

    public String getOptionText() {
        return optionText;
    }

    public void setOptionText(String optionText) {
        this.optionText = optionText;
    }

    public Stat getStatRequirement() {
        return statRequirement;
    }

    public void setStatRequirement(Stat statRequirement) {
        this.statRequirement = statRequirement;
    }

    public int getStatDC() {
        return statDC;
    }

    public void setStatDC(int statDC) {
        this.statDC = statDC;
    }

    public ArrayList<Repercussion> getSuccessResults() {
        return successResults;
    }

    public void setSuccessResults(ArrayList<Repercussion> successResults) {
        this.successResults = successResults;
    }

    public ArrayList<Repercussion> getFailureResults() {
        return failureResults;
    }

    public void setFailureResults(ArrayList<Repercussion> failureResults) {
        this.failureResults = failureResults;
    }
}