package client.nowhere.model;

import java.util.UUID;

public class PlayerStat {

    private String id;
    private String label;
    private Integer value;

    public PlayerStat() {
        this.label = "";
        this.value = 4;
        this.id = UUID.randomUUID().toString();
    }

    public PlayerStat(String label, Integer value) {
        this.label = label;
        this.value = value;
        this.id = UUID.randomUUID().toString();
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

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }
}
