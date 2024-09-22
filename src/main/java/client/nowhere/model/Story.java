package client.nowhere.model;

import java.util.ArrayList;

public class Story {

    private String prompt;
    private ArrayList<Option> options;
    private String gameCode;
    private ArrayList<String> sequelStoryIds;
    private ArrayList<String> prequelStoryIds;

    public Story() {}

    public Story(String prompt) {
        this.prompt = prompt;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(String prompt) {
        this.prompt = prompt;
    }

    public ArrayList<Option> getOptions() {
        return options;
    }

    public void setOptions(ArrayList<Option> options) {
        this.options = options;
    }

    public String getGameCode() {
        return gameCode;
    }

    public void setGameCode(String gameCode) {
        this.gameCode = gameCode;
    }

    public ArrayList<String> getSequelStoryIds() {
        return sequelStoryIds;
    }

    public void setSequelStoryIds(ArrayList<String> sequelStoryIds) {
        this.sequelStoryIds = sequelStoryIds;
    }

    public ArrayList<String> getPrequelStoryIds() {
        return prequelStoryIds;
    }

    public void setPrequelStoryIds(ArrayList<String> prequelStoryIds) {
        this.prequelStoryIds = prequelStoryIds;
    }

}
