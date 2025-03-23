package client.nowhere.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SaveGame {
    String id;
    List<Story> globalStories;

    public SaveGame() {
        this.id = UUID.randomUUID().toString();
        this.globalStories = new ArrayList<>();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<Story> getGlobalStories() {
        return globalStories;
    }

    public void setGlobalStories(List<Story> globalStories) {
        this.globalStories = globalStories;
    }
}
