package client.nowhere.model;

import lombok.Getter;

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
            "Player will receive this Encounter as a Trait",
            "#24bf86"
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
}
