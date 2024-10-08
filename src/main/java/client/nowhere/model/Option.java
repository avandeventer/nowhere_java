package client.nowhere.model;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class Option {

    String optionId = "";
    String optionText = "";
    String attemptText = "";
    Stat statRequirement;
    int statDC;
    String successText = "";
    ArrayList<OutcomeStat> successResults;

    String failureText = "";
    ArrayList<OutcomeStat> failureResults;

    public Option () {
        this.optionId = UUID.randomUUID().toString();
    }

    public Option(String optionId,
                  String optionText,
                  String attemptText,
                  Stat statRequirement,
                  int statDC,
                  String successText,
                  ArrayList<OutcomeStat> successResults,
                  String failureText,
                  ArrayList<OutcomeStat> failureResults) {
        this.optionId = optionId;
        this.optionText = optionText;
        this.statRequirement = statRequirement;
        this.statDC = statDC;
        this.attemptText = attemptText;
        this.successText = successText;
        this.successResults = successResults;
        this.failureText = failureText;
        this.failureResults = failureResults;
    }

    public void randomizeOptionStats (int minDC, int maxDC) {
        this.optionText = "";
        this.statRequirement = Stat.values()[ThreadLocalRandom.current().nextInt(Stat.values().length)];
        this.statDC = ThreadLocalRandom.current().nextInt(minDC, maxDC + 1);

        OutcomeStat successStat = new OutcomeStat();
        successStat.randomizeOutcomeStat(1, 2);
        this.successResults = new ArrayList<>();
        this.successResults.add(successStat);

        OutcomeStat failureStat = new OutcomeStat();
        failureStat.randomizeOutcomeStat(1, 2);
        this.failureResults = new ArrayList<>();
        this.failureResults.add(failureStat);
    }

    public String getOptionId() {
        return optionId;
    }

    public void setOptionId(String optionId) {
        this.optionId = optionId;
    }

    public String getOptionText() {
        return optionText;
    }

    public void setOptionText(String optionText) {
        this.optionText = optionText;
    }

    public String getAttemptText() {
        return attemptText;
    }

    public void setAttemptText(String attemptText) {
        this.attemptText = attemptText;
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

    public String getSuccessText() {
        return successText;
    }

    public void setSuccessText(String successText) {
        this.successText = successText;
    }

    public ArrayList<OutcomeStat> getSuccessResults() {
        return successResults;
    }

    public void setSuccessResults(ArrayList<OutcomeStat> successResults) {
        this.successResults = successResults;
    }

    public String getFailureText() {
        return failureText;
    }

    public void setFailureText(String failureText) {
        this.failureText = failureText;
    }

    public ArrayList<OutcomeStat> getFailureResults() {
        return failureResults;
    }

    public void setFailureResults(ArrayList<OutcomeStat> failureResults) {
        this.failureResults = failureResults;
    }
}