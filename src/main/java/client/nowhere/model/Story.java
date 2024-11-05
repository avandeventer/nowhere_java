package client.nowhere.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Story extends Object {

    private String storyId = "";
    private boolean visited = false;
    private String prompt = "";
    private String authorId = "";
    private String outcomeAuthorId = "";
    private String playerId = "";
    private String selectedOptionId = "";
    private boolean playerSucceeded = false;
    private boolean prequelStorySucceeded = false;
    private String prequelStoryId = "";
    private String prequelStoryPlayerId = "";
    private Location location;
    private List<Option> options;
    private String gameCode = "";
    private List<Repercussion> successRepercussions;
    private List<Repercussion> failureRepercussions;

    public Story () {
        this.storyId = UUID.randomUUID().toString();
    }

    public Story(String gameCode) {
        this.gameCode = gameCode;
        this.storyId = UUID.randomUUID().toString();
    }

    public Story(
            String gameSessionCode,
            Location location,
            String outcomeAuthorId,
            String playerAuthorId
    ) {
        this.gameCode = gameSessionCode;
        this.storyId = UUID.randomUUID().toString();
        randomizeNewStory();
        this.location = location;
        this.authorId = playerAuthorId;
        this.outcomeAuthorId = outcomeAuthorId;
    }

    public Story(
            String gameSessionCode,
            Location location,
            String outcomeAuthorId,
            String playerAuthorId,
            String prequelStoryId,
            String playerPrequelStoryId,
            boolean prequelStorySucceeded
    ) {
        this.gameCode = gameSessionCode;
        this.storyId = UUID.randomUUID().toString();
        randomizeNewStory();
        this.location = location;
        this.authorId = playerAuthorId;
        this.outcomeAuthorId = outcomeAuthorId;
        this.prequelStoryId = prequelStoryId;
        this.prequelStorySucceeded = prequelStorySucceeded;
    }

    public void randomizeNewStory() {
        this.prompt = "";
        int minDC = 1;
        int maxDC = 10;

        Option optionOne = new Option();
        optionOne.randomizeOptionStats(minDC, maxDC);

        if(optionOne.getStatDC() >= 7) {
            maxDC = 6;
        }

        if(optionOne.getStatDC() <= 3) {
            minDC = 3;
        }

        Option optionTwo = new Option();
        optionTwo.randomizeOptionStats(minDC, maxDC);
        this.options = new ArrayList<>();
        options.add(optionOne);
        options.add(optionTwo);
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
}
