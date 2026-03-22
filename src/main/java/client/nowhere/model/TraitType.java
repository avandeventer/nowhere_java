package client.nowhere.model;

import lombok.Getter;

public enum TraitType {

    STANDARD (
            "Standard"
    ),
    TITLE (
            "Title"
    );

    @Getter
    final String name;

    TraitType (String name) {
        this.name = name;
    }
}
