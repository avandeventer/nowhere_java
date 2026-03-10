package client.nowhere.model;

public class RepercussionTypeOption {
    private String name;
    private String label;
    private String instruction;
    private String description;

    public RepercussionTypeOption() {}

    public RepercussionTypeOption(String name, String label, String instruction, String description) {
        this.name = name;
        this.label = label;
        this.instruction = instruction;
        this.description = description;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
