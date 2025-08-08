package client.nowhere.model;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SaveGame {
    String id;
    String name;

    List<Story> globalStories;
    List<Story> globalRituals;

    public SaveGame() {
        this.id = UUID.randomUUID().toString();
        this.globalStories = new ArrayList<>();
    }

    public SaveGame(String name) {
        this.id = UUID.randomUUID().toString();
        this.globalStories = new ArrayList<>();
        this.name = name;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Story> getGlobalRituals() {
        return globalRituals;
    }

    public void setGlobalRituals(List<Story> globalRituals) {
        this.globalRituals = globalRituals;
    }
}
