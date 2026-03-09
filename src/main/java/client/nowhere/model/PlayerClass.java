package client.nowhere.model;

import java.util.List;

public enum PlayerClass {
    Bard(
            "Bard",
            "Your skill with spreading a good tale allows you to spread story outcomes to all players, including yourself",
            List.of (RepercussionType.SPREAD)
    ),
    Noble(
            "Noble",
            "Your rank allows you to give new titles to other players",
            List.of(RepercussionType.TITLE)
    ),
    Poet(
            "Poet",
            "You weave small tales into great epics allowing you to designate which Encounters should appear again in the future",
            List.of(RepercussionType.SEQUEL)
    ),
    Historian(
            "Historian",
            "Your fondness for recording the histories of adventurers allows you to add Traits to other players",
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
