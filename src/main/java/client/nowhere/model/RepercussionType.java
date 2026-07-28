package client.nowhere.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum RepercussionType {
    ALL_PLAYERS(
            "All Players",
            "",
            "Spread outcome effects to all players",
            "Outcome effects will spread to all players",
            "#f57c00"
    ),
    TITLE (
            "Title",
            "Add title",
            "Give a title to the player who chooses this",
            "Player will receive this title",
            "#7b1fa2"
    ),
    TRAIT (
            "Trait",
            "Add player trait",
            "Give a trait to the player who chooses this",
            "Player will receive this trait",
            "#0288d1"
    ),
    SEQUEL (
            "Sequel",
            "",
            "Trigger sequel",
            "This outcome sets up a sequel",
            "#388e3c"
    ),
    COMPANION(
            "Companion",
            "",
            "Encountered entity joins you",
            "Player will receive this Encounter as a Companion",
            "#E60000"
    ),
    DESTINY(
            "Destiny",
            "Add destiny trait",
            "Give a destiny trait to the player who chooses this",
            "Player will receive this as their Destiny",
            "#FFD700"
    );

    @Getter
    public final String name;

    @Getter
    public final String label; //Add text instruction

    @Getter
    public final String instruction; //Toggle off

    @Getter
    public final String description; //Toggle on

    @Getter
    public final String color;

    RepercussionType(String name, String label, String instruction, String description, String color) {
        this.name = name;
        this.label = label;
        this.instruction = instruction;
        this.description = description;
        this.color = color;
    }

    @JsonCreator
    public static RepercussionType fromJson(JsonNode node) {
        String value = node.isObject() ? node.path("name").asText() : node.asText();
        for (RepercussionType type : values()) {
            if (type.name().equalsIgnoreCase(value) || type.name.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown RepercussionType: " + value);
    }
}
