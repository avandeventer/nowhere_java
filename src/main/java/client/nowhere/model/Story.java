package client.nowhere.model;

import client.nowhere.constants.AuthorConstants;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import com.google.cloud.Timestamp;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Story {

    private String storyId = "";
    private String prompt = "";
    private String authorId = "";
    private String outcomeAuthorId = "";
    private boolean prequelStorySucceeded = false;
    private String prequelStoryId = "";
    private String prequelStorySelectedOptionId = "";
    private String gameCode = "";
    private boolean mainPlotStory = false;

    //Temporary player variables
    private boolean visited = false;
    private String playerId = "";
    private String selectedOptionId = "";
    private boolean playerSucceeded = false;
    private String prequelStoryPlayerId = "";

    private List<Repercussion> successRepercussions;
    private List<Repercussion> failureRepercussions;
    private List<String> prequelOutcomeDisplay;
    private boolean sequelStory;
    private boolean saveGameStory;
    private List<Option> options;

    @Setter
    @Getter
    private EncounterLabel encounterLabel;

    @JsonIgnore
    private List<Option> ritualOptions;
    private Location location;
    private String optionType;
    private Timestamp createdAt;

    public Story () {
        this.storyId = UUID.randomUUID().toString();
    }

    public void setNewStoryId() {
        this.storyId = UUID.randomUUID().toString();
    }

    public Story (List<Option> options) {
        this.options = options;
    }

    public Story(
            String gameSessionCode,
            Location location,
            List<StatType> statTypes
    ) {
        this.gameCode = gameSessionCode;
        this.storyId = UUID.randomUUID().toString();
        randomizeNewStory(statTypes);
        this.location = location;
    }

    public void randomizeNewStory(List<StatType> statTypes) {
        this.prompt = "";
        int minDC = 1;
        int maxDC = 10;

        List<StatType> nonFavorAdventureMapStatTypes = statTypes.stream().filter(statType -> !statType.isFavorType).collect(Collectors.toList());

        this.options = new ArrayList<>();
        Option optionOne = new Option();
        optionOne.randomizeStatDCs(minDC, maxDC, nonFavorAdventureMapStatTypes);
        optionOne.randomizeOptionOutcomes(nonFavorAdventureMapStatTypes);

        if(optionOne.getPlayerStatDCs().get(0).getValue() >= 7) {
            maxDC = 6;
        }

        if(optionOne.getPlayerStatDCs().get(0).getValue() <= 3) {
            minDC = 3;
        }
        options.add(optionOne);

        Option optionTwo = new Option();

        optionTwo.randomizeOptionOutcomes(nonFavorAdventureMapStatTypes);

        do {
            optionTwo.randomizeStatDCs(minDC, maxDC, nonFavorAdventureMapStatTypes);
        } while (optionTwo.getPlayerStatDCs().get(0).getStatType() == optionOne.getPlayerStatDCs().get(0).getStatType());

        options.add(optionTwo);
    }

    public void setToMainPlotStory(List<StatType> playerStats) {
        this.setMainPlotStory(true);
        Optional<StatType> favorStatOptional = playerStats.stream().filter(StatType::isFavorType).findFirst();
        favorStatOptional.ifPresent(statType ->  {
            this.getOptions().get(0).randomizeFavorOutcomes(statType, true);
            this.getOptions().get(1).randomizeFavorOutcomes(statType, false);
        });
    }

    public String getStoryId() {
        return storyId;
    }

    public void setStoryId(String storyId) {
        this.storyId = storyId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public List<Option> getOptions() {
        if (options == null && ritualOptions != null) {
            return ritualOptions;
        }

        return options;
    }

    public void setOptions(List<Option> options) {
        this.options = options;
    }

    public String getGameCode() {
        return gameCode;
    }

    public void setGameCode(String gameCode) {
        this.gameCode = gameCode;
    }

    public List<Repercussion> getSuccessRepercussions() {
        return successRepercussions;
    }

    public void setSuccessRepercussions(List<Repercussion> successRepercussions) {
        this.successRepercussions = successRepercussions;
    }

    public List<Repercussion> getFailureRepercussions() {
        return failureRepercussions;
    }

    public void setFailureRepercussions(List<Repercussion> failureRepercussions) {
        this.failureRepercussions = failureRepercussions;
    }

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getOutcomeAuthorId() {
        return this.outcomeAuthorId;
    }

    public void setOutcomeAuthorId(String outcomeAuthorId) { this.outcomeAuthorId = outcomeAuthorId; }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public String getSelectedOptionId() {
        return selectedOptionId;
    }

    public void setSelectedOptionId(String selectedOptionId) {
        this.selectedOptionId = selectedOptionId;
    }

    public boolean isVisited() {
        return visited;
    }

    public void setVisited(boolean visited) {
        this.visited = visited;
    }

    public String getPrequelStoryId() {
        return prequelStoryId;
    }

    public void setPrequelStoryId(String prequelStoryId) {
        this.prequelStoryId = prequelStoryId;
    }

    public boolean isPlayerSucceeded() {
        return playerSucceeded;
    }

    public void setPlayerSucceeded(boolean playerSucceeded) {
        this.playerSucceeded = playerSucceeded;
    }

    public boolean isPrequelStorySucceeded() {
        return prequelStorySucceeded;
    }

    public void setPrequelStorySucceeded(boolean prequelStorySucceeded) {
        this.prequelStorySucceeded = prequelStorySucceeded;
    }

    public String getPrequelStoryPlayerId() {
        return prequelStoryPlayerId;
    }

    public void setPrequelStoryPlayerId(String prequelStoryPlayerId) {
        this.prequelStoryPlayerId = prequelStoryPlayerId;
    }

    public List<String> getPrequelOutcomeDisplay() {
        return prequelOutcomeDisplay;
    }

    public void setPrequelOutcomeDisplay(List<String> prequelOutcomeDisplay) {
        this.prequelOutcomeDisplay = prequelOutcomeDisplay;
    }

    public String getPrequelStorySelectedOptionId() {
        return prequelStorySelectedOptionId;
    }

    public void setPrequelStorySelectedOptionId(String prequelStorySelectedOptionId) {
        this.prequelStorySelectedOptionId = prequelStorySelectedOptionId;
    }

    public boolean isSequelStory() {
        return sequelStory;
    }

    public void setSequelStory(boolean sequelStory) {
        this.sequelStory = sequelStory;
    }

    public boolean isSaveGameStory() {
        return saveGameStory;
    }

    public void setSaveGameStory(boolean saveGameStory) {
        this.saveGameStory = saveGameStory;
    }

    @JsonProperty
    public void setRitualOptions(List<Option> ritualOptions) {
        this.ritualOptions = ritualOptions;
    }

    //Temporary to correctly deserialize from the old RitualStory object
    @JsonIgnore
    public List<Option> getRitualOptions() {
        return ritualOptions;
    }

    //Temporary to correctly deserialize from the old RitualStory object
    @JsonIgnore
    public String getOptionType() {
        return optionType;
    }

    public void setOptionType(String optionType) {
        this.optionType = optionType;
    }

    @JsonIgnore
    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public boolean isMainPlotStory() {
        return mainPlotStory;
    }

    public void setMainPlotStory(boolean isMainPlotStory) {
        this.mainPlotStory = isMainPlotStory;
    }

    @JsonIgnore
    public SequelKey getSequelKey() {
        return new SequelKey(selectedOptionId, playerSucceeded);
    }

    @JsonIgnore
    public SequelKey getPrequelKey() {
        return new SequelKey(prequelStorySelectedOptionId, prequelStorySucceeded);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Story story = (Story) obj;
        return Objects.equals(storyId, story.storyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(storyId);
    }

    public void resetPlayerVariables() {
        setPlayerId("");
        setVisited(false);
        setPlayerSucceeded(false);
        setSelectedOptionId("");
        if (!this.prequelStoryPlayerId.isBlank()) {
            setPrequelStoryPlayerId(AuthorConstants.GLOBAL_PLAYER_SEQUEL);
            setLocation(new Location());
        }
    }

    public void makeSequel(String storyId, boolean playerSucceeded, String selectedOptionId) {
        setPrequelStoryId(storyId);
        setPrequelStorySucceeded(playerSucceeded);
        setPrequelStorySelectedOptionId(selectedOptionId);
        setSequelStory(true);
    }

    @JsonIgnore
    public boolean isAFavorStory() {
        if (this.options == null || this.options.size() == 0) {
            return false;
        }

        return options.stream()
                .flatMap(option -> Stream.concat(
                        option.getSuccessResults().stream(),
                        option.getFailureResults().stream()
                ))
                .anyMatch(outcomeStat -> outcomeStat.getPlayerStat().getStatType().isFavorType());
    }

}
