package client.nowhere.model;

import java.util.UUID;

public class StatType {

    private String id;
    private String label;

    public StatType() {
        this.label = "";
        this.id = UUID.randomUUID().toString();
    }

    public StatType(String label) {
        this.label = label;
        this.id = UUID.randomUUID().toString();
    }

    public StatType(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
