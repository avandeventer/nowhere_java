package client.nowhere.model;

public class RepercussionTypeOption {
    private String name;
    private String label;
    private String instruction;
    private String description;
    private String color;

    public RepercussionTypeOption() {}

    public RepercussionTypeOption(String name, String label, String instruction, String description, String color) {
        this.name = name;
        this.label = label;
        this.instruction = instruction;
        this.description = description;
        this.color = color;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
