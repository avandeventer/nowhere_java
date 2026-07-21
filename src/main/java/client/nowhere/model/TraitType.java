package client.nowhere.model;

import lombok.Getter;

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
}
