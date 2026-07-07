package client.nowhere.model;

public class DefiningTrait {

    private String traitName;
    private String category;
    private String type;
    private String sourceStoryId;

    public DefiningTrait() { }

    public DefiningTrait(String traitName, String category, String type, String sourceStoryId) {
        this.traitName = traitName;
        this.category = category;
        this.type = type;
        this.sourceStoryId = sourceStoryId;
    }

    public String getTraitName() { return traitName; }
    public void setTraitName(String traitName) { this.traitName = traitName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getSourceStoryId() { return sourceStoryId; }
    public void setSourceStoryId(String sourceStoryId) { this.sourceStoryId = sourceStoryId; }
}
