package client.nowhere.model;

import java.util.List;
import java.util.stream.Collectors;

public class RitualOption extends Option {

    private List<StatRequirement> statRequirements;

    private String selectedByPlayerId;

    public RitualOption() { }

    public RitualOption (String optionId,
                   String optionText,
                   String attemptText,
                   List<Stat> stats,
                   List<Integer> difficultyValues,
                   String successText, String failureText) {
        this.optionId = optionId;
        this.optionText = optionText;
        this.attemptText = attemptText;
        setStatRequirements(stats, difficultyValues);
        this.successText = successText;
        this.failureText = failureText;
    }

    public void setStatRequirements(List<Stat> stats, List<Integer> difficultyValues) {
        this.statRequirements = stats.stream()
                .flatMap(stat -> difficultyValues.stream()
                        .map(difficulty -> new StatRequirement(stat, difficulty)))
                .collect(Collectors.toList());
    }

    public RitualOption(String optionId,
                           String optionText,
                           String attemptText,
                           List<StatRequirement> statRequirements,
                           int statDC,
                           String successText,
                           List<OutcomeStat> successResults,
                           String failureText,
                           List<OutcomeStat> failureResults,
                           String outcomeAuthorId) {
        super(optionId, optionText, attemptText, null, statDC, successText, successResults, failureText, failureResults, outcomeAuthorId);
        this.statRequirements = statRequirements;
    }

    public List<StatRequirement> getStatRequirements() {
        return statRequirements;
    }

    public void setStatRequirements(List<StatRequirement> statRequirements) {
        this.statRequirements = statRequirements;
    }

    public String getSelectedByPlayerId() {
        return selectedByPlayerId;
    }

    public void setSelectedByPlayerId(String selectedByPlayerId) {
        this.selectedByPlayerId = selectedByPlayerId;
    }

    @Override
    public String toString() {
        return "RitualOption{" +
                "optionId='" + optionId + '\'' +
                ", optionText='" + optionText + '\'' +
                ", attemptText='" + attemptText + '\'' +
                ", statRequirement=" + statRequirement +
                ", statDC=" + statDC +
                ", successText='" + successText + '\'' +
                ", outcomeAuthorId='" + outcomeAuthorId + '\'' +
                ", successResults=" + successResults +
                ", failureText='" + failureText + '\'' +
                ", failureResults=" + failureResults +
                ", statRequirements=" + statRequirements +
                '}';
    }
}