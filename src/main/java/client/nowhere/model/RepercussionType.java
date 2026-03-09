package client.nowhere.model;

import lombok.Getter;

public enum RepercussionType {
    SPREAD (
            "Spread",
            "",
            "Spread outcome effects to all players",
            "Outcome effects will spread to all players"
    ),
    TITLE (
            "Title",
            "Add title",
            "Give a title to the player who chooses this",
            "Player will receive this title"
    ),
    TRAIT (
            "Trait",
            "Add player trait",
            "Give a trait to the player who chooses this",
            "Player will receive this trait"
    ),
    SEQUEL (
            "Sequel",
            "",
            "Trigger sequel",
            "This outcome sets up a sequel"
    );

    @Getter
    public final String name;

    @Getter
    public final String label; //Add text instruction

    @Getter
    public final String instruction; //Toggle off

    @Getter
    public final String description; //Toggle on

    RepercussionType(String name, String label, String instruction, String description) {
        this.name = name;
        this.label = label;
        this.instruction = instruction;
        this.description = description;
    }
}
