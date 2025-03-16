package client.nowhere.model;

import java.util.List;

public class Ending {

    String playerId;
    List<Story> associatedStories;
    String associatedLocationId;
    RitualOption associatedRitualOption;
    String authorId;
    String endingBody;
    boolean didWeSucceed;

    public Ending() { }

    public Ending(String authorId, String playerId) {
        this.authorId = authorId;
        this.playerId = playerId;
    }

    public String getPlayerId() {
        return playerId;
    }

    public void setPlayerId(String playerId) {
        this.playerId = playerId;
    }

    public List<Story> getAssociatedStories() {
        return associatedStories;
    }

    public void setAssociatedStories(List<Story> associatedStories) {
        this.associatedStories = associatedStories;
    }

    public String getAssociatedLocationId() {
        return associatedLocationId;
    }

    public void setAssociatedLocationId(String associatedLocationId) {
        this.associatedLocationId = associatedLocationId;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getEndingBody() {
        return endingBody;
    }

    public void setEndingBody(String endingBody) {
        this.endingBody = endingBody;
    }

    public RitualOption getAssociatedRitualOption() {
        return associatedRitualOption;
    }

    public void setAssociatedRitualOption(RitualOption associatedRitualOption) {
        this.associatedRitualOption = associatedRitualOption;
    }

    public boolean isDidWeSucceed() {
        return didWeSucceed;
    }

    public void setDidWeSucceed(boolean didWeSucceed) {
        this.didWeSucceed = didWeSucceed;
    }
}
