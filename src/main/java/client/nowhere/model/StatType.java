package client.nowhere.model;

import java.util.UUID;

public class StatType {

    private String id;
    private String label;
    boolean isFavorType = false;
    String description;
    String favorEntity;

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

    public StatType(String id, String label, boolean isFavorType, String favorEntity) {
        this.id = id;
        this.label = label;
        this.isFavorType = isFavorType;
        this.favorEntity = favorEntity;
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

    public boolean isFavorType() {
        return isFavorType;
    }

    public void setFavorType(boolean favorType) {
        isFavorType = favorType;
    }

    public String getFavorEntity() {
        return favorEntity;
    }

    public void setFavorEntity(String favorEntity) {
        this.favorEntity = favorEntity;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
