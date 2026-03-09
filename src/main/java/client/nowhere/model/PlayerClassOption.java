package client.nowhere.model;

import java.util.List;

public class PlayerClassOption {
    private String name;
    private String description;
    private List<String> repercussionTypes;

    public PlayerClassOption() {}

    public PlayerClassOption(String name, String description, List<String> repercussionTypes) {
        this.name = name;
        this.description = description;
        this.repercussionTypes = repercussionTypes;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public List<String> getRepercussionTypes() { return repercussionTypes; }
    public void setRepercussionTypes(List<String> repercussionTypes) { this.repercussionTypes = repercussionTypes; }
}
