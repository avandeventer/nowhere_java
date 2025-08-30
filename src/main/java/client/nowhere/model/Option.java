package client.nowhere.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.*;
import java.util.stream.Collectors;

public class Option {
    String optionId = "";
    String optionText = "";
    String attemptText = "";
    Stat statRequirement;
    List<StatRequirement> statRequirements = new ArrayList<>();
    int statDC = 0;
    List<PlayerStat> playerStatDCs = new ArrayList<>();
    String successText = "";
    String outcomeAuthorId = "";
    List<OutcomeStat> successResults = new ArrayList<>();
    String failureText = "";
    List<OutcomeStat> failureResults = new ArrayList<>();
    private String selectedByPlayerId;
    private boolean playerSucceeded = false;
    private Integer pointsRewarded = 0;
    private String successMarginText = "";

    public Option () {
        this.optionId = UUID.randomUUID().toString();
        this.playerStatDCs = new ArrayList<>();
        this.successResults = new ArrayList<>();
        this.failureResults = new ArrayList<>();
    }

    public Option (String optionText,
                   String attemptText,
                   List<OutcomeStat> successResults) {
        this.optionText = optionText;
        this.attemptText = attemptText;
        this.successResults = successResults;
    }

    public Option(String optionId,
                  String optionText,
                  String attemptText,
                  String successText,
                  List<OutcomeStat> successResults,
                  String failureText,
                  List<OutcomeStat> failureResults,
                  String outcomeAuthorId,
                  List<PlayerStat> playerStatDCs
    ) {
        this.optionId = optionId;
        this.optionText = optionText;
        this.attemptText = attemptText;
        this.successText = successText;
        this.successResults = successResults;
        this.failureText = failureText;
        this.failureResults = failureResults;
        this.outcomeAuthorId = outcomeAuthorId;
        this.playerStatDCs = playerStatDCs;
    }

    public Option (String optionId,
                         String optionText,
                         String attemptText,
                         List<StatType> stats,
                         List<Integer> difficultyValues,
                         String successText,
                         String failureText) {
        this.optionId = optionId;
        this.optionText = optionText;
        this.attemptText = attemptText;
        setStatRequirements(stats, difficultyValues);
        this.successText = successText;
        this.failureText = failureText;
    }

    public Option (
            List<StatType> stats,
            List<Integer> difficultyValues
    ) {
        this.optionId = UUID.randomUUID().toString();
        setStatRequirements(stats, difficultyValues);
    }

    public void randomizeStatDCs(int minDC, int maxDC, List<StatType> statTypes) {
        this.optionText = "";

        this.playerStatDCs = Collections.singletonList(
                new PlayerStat(statTypes, minDC, maxDC)
        );
    }


    public void randomizeOptionOutcomes (List<StatType> statTypes) {
        OutcomeStat successStat = new OutcomeStat(statTypes, 1, 2);
        this.successResults = new ArrayList<>();
        this.successResults.add(successStat);
        OutcomeStat failureStat = new OutcomeStat(statTypes, 1, 2);
        this.failureResults = new ArrayList<>();
        this.failureResults.add(failureStat);
    }

    public void randomizeFavorOutcomes(List<StatType> nonFavorAdventureMapStatTypes, StatType favorStat, boolean sideWithFavorEntity) {
        this.successResults = new ArrayList<>();

        int minStatChange = sideWithFavorEntity ? 1 : -1;
        int maxStatChange = sideWithFavorEntity ? 2 : -2;
        this.successResults.add(new OutcomeStat(favorStat, minStatChange, maxStatChange));
        this.successResults.add(new OutcomeStat(nonFavorAdventureMapStatTypes, 1, 2));

        this.failureResults = new ArrayList<>();
        this.failureResults.add(new OutcomeStat(favorStat, minStatChange, maxStatChange));
        this.failureResults.add(new OutcomeStat(nonFavorAdventureMapStatTypes, 1, 2));
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

    public List<OutcomeStat> getSuccessResults() {
        if (this.successResults == null) {
            return new ArrayList<>();
        }
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

    public List<OutcomeStat> getFailureResults() {
        if (this.failureResults == null) {
            return new ArrayList<>();
        }
        return failureResults;
    }

    public void setFailureResults(ArrayList<OutcomeStat> failureResults) {
        this.failureResults = failureResults;
    }

    public String getOutcomeAuthorId() {
        return outcomeAuthorId;
    }

    public void setOutcomeAuthorId(String outcomeAuthorId) {
        this.outcomeAuthorId = outcomeAuthorId;
    }

    public List<PlayerStat> getPlayerStatDCs() {
        if (this.playerStatDCs == null || this.playerStatDCs.isEmpty()) {
            if (this.statRequirement != null) {
                return Collections.singletonList(new PlayerStat(this.statRequirement.getStatType(), this.statDC));
            }

            if (this.statRequirements != null && !this.statRequirements.isEmpty()) {
                return this.statRequirements.stream()
                        .map(statRequirement -> new PlayerStat(statRequirement.dcStat.getStatType(), statRequirement.dcValue))
                        .collect(Collectors.toList());
            }
        }
        return playerStatDCs;
    }

    public void setPlayerStatDCs(List<PlayerStat> playerStatDCs) {
        this.playerStatDCs = playerStatDCs;
    }

    public Stat getStatRequirement() {
        return statRequirement;
    }

    public void setStatRequirement(Stat statRequirement) {
        this.statRequirement = statRequirement;
    }

    public void setStatRequirements(List<StatType> stats, List<Integer> difficultyValues) {
        this.playerStatDCs = stats.stream()
                .flatMap(statType -> difficultyValues.stream()
                        .map(difficulty -> new PlayerStat(statType, difficulty)))
                .collect(Collectors.toList());
    }

    public String getSelectedByPlayerId() {
        return selectedByPlayerId;
    }

    public void setSelectedByPlayerId(String selectedByPlayerId) {
        this.selectedByPlayerId = selectedByPlayerId;
    }

    public boolean isPlayerSucceeded() {
        return playerSucceeded;
    }

    public void setPlayerSucceeded(boolean playerSucceeded) {
        this.playerSucceeded = playerSucceeded;
    }

    public Integer getPointsRewarded() {
        return pointsRewarded;
    }

    public void setPointsRewarded(Integer pointsRewarded) {
        this.pointsRewarded = pointsRewarded;
    }

    public String getSuccessMarginText() {
        return successMarginText;
    }

    public void setSuccessMarginText(String successMarginText) {
        this.successMarginText = successMarginText;
    }

    @JsonIgnore
    public List<StatRequirement> getStatRequirements() {
        return statRequirements;
    }

    public void setStatRequirements(List<StatRequirement> statRequirements) {
        this.statRequirements = statRequirements;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Option)) return false;
        Option option = (Option) o;
        return statDC == option.statDC && playerSucceeded == option.playerSucceeded && optionId.equals(option.optionId) && Objects.equals(optionText, option.optionText) && Objects.equals(attemptText, option.attemptText) && statRequirement == option.statRequirement && Objects.equals(playerStatDCs, option.playerStatDCs) && Objects.equals(successText, option.successText) && Objects.equals(outcomeAuthorId, option.outcomeAuthorId) && Objects.equals(successResults, option.successResults) && Objects.equals(failureText, option.failureText) && Objects.equals(failureResults, option.failureResults) && Objects.equals(selectedByPlayerId, option.selectedByPlayerId) && Objects.equals(pointsRewarded, option.pointsRewarded) && Objects.equals(successMarginText, option.successMarginText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(optionId, optionText, attemptText, statRequirement, statDC, playerStatDCs, successText, outcomeAuthorId, successResults, failureText, failureResults, selectedByPlayerId, playerSucceeded, pointsRewarded, successMarginText);
    }
}