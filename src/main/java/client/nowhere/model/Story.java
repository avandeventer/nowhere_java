package client.nowhere.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class Story {

    private String storyId = "";
    private String prompt = "";
    private String authorId = "";
    private String outcomeAuthorId = "";
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
}
