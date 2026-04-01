package client.nowhere.model;

import java.util.List;

public enum PlayerClass {
    Bard(
            "Bard",
            "Your skill with spreading a good tale allows you to spread story outcomes to all players, including yourself",
            List.of (RepercussionType.ALL_PLAYERS)
    ),
    Herald(
            "Herald",
            "Your rank allows you to give new titles to other players",
            List.of(RepercussionType.TITLE)
    ),
    Scribe(
            "Scribe",
            "Your fondness for recording the histories of other adventurers allows you to add Traits to other players",
            List.of(RepercussionType.TRAIT)
    );

    public final String name;
    public final String description;
    public final List<RepercussionType> repercussionTypes;

    PlayerClass(String name, String description, List<RepercussionType> repercussionTypes) {
        this.name = name;
        this.description = description;
        this.repercussionTypes = repercussionTypes;
    }
}
