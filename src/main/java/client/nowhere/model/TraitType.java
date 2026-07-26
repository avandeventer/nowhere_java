package client.nowhere.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.Getter;

@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum TraitType {

    STANDARD (
            "Trait",
            "#0288d1"
    ),
    TITLE (
            "Title",
            "#7b1fa2"
    ),
    COMPANION (
            "Companion",
            "#E60000"
    ),
    RELATIONSHIP (
            "Relationship",
            "E981AE"
    ),
    DESTINY (
            "Destiny",
            "#FFD700"
    );

    @Getter
    final String name;

    @Getter
    public final String color;

    TraitType (String name, String color) {
        this.name = name;
        this.color = color;
    }

    @JsonCreator
    public static TraitType fromJson(JsonNode node) {
        String value = node.isObject() ? node.path("name").asText() : node.asText();
        for (TraitType type : values()) {
            if (type.name().equalsIgnoreCase(value) || type.name.equalsIgnoreCase(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TraitType: " + value);
    }
}
