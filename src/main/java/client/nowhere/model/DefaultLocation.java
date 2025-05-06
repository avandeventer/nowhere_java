package client.nowhere.model;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public enum DefaultLocation {
    TAVERN( "Tavern",
            Arrays.asList(
            new Option(
                    "Drink",
                    "You spend the week drinking. You gain some friends and build a tolerance.",
                    Arrays.asList(
                            new OutcomeStat(Stat.STRENGTH, 1),
                            new OutcomeStat(Stat.CHARISMA, 1)
                    )),
            new Option(
                    "Bartend",
                    "You spend the week serving mead. You are charming and are tipped well!",
                    Arrays.asList(
                            new OutcomeStat(Stat.CHARISMA, 1),
                            new OutcomeStat(Stat.WEALTH, 1)
                    ))
            ),
            "https://storage.googleapis.com/nowhere_images/location_icons/Tavern.png"
    ),
    TOWN_SQUARE( "Town Square",
            Arrays.asList(
                    new Option(
                            "Perform",
                            "You dance in the center of the town square for coin. People enjoy your energy and some even drop some coin!",
                            Arrays.asList(
                                    new OutcomeStat(Stat.CHARISMA, 1),
                                    new OutcomeStat(Stat.WEALTH, 1)
                            )),
                    new Option(
                            "Barter",
                            "You spend the week serving mead to the locals. You make some coin and put in some hard work.",
                            Arrays.asList(
                                    new OutcomeStat(Stat.DEXTERITY, 1),
                                    new OutcomeStat(Stat.WEALTH, 1)
                            ))
            ),
            "https://storage.googleapis.com/nowhere_images/location_icons/Tavern.png"
    ),
    APOTHECARY( "Apothecary",
            Arrays.asList(
                    new Option(
                            "Apprentice",
                            "You spend the week training under the masters of the old magics. You learn a lot and feel your connection to the world increase.",
                            Arrays.asList(
                                    new OutcomeStat(Stat.MAGIC, 1),
                                    new OutcomeStat(Stat.INTELLECT, 1)
                            )),
                    new Option(
                            "Experiment",
                            "You spend the week trying out spells you haven't tried before. You grow your familiarity with magic and see some unexpected results.",
                            Arrays.asList(
                                    new OutcomeStat(Stat.MAGIC, 1),
                                    new OutcomeStat(Stat.values()[ThreadLocalRandom.current().nextInt(Stat.values().length)], 1)
                            ))
            ),
            "https://storage.googleapis.com/nowhere_images/location_icons/Tavern.png"
    ),
    THE_WILDS( "Wilds",
            Arrays.asList(
                    new Option(
                            "Adventure",
                            "You spend the week wandering in the wilds and avoiding danger. Your footing improves and you even find some treasure.",
                            Arrays.asList(
                                    new OutcomeStat(Stat.DEXTERITY, 1),
                                    new OutcomeStat(Stat.WEALTH, 1)
                            )),
                    new Option(
                            "Forage",
                            "You spend the week looking for resources for the town. You learn a lot about the forest surrounding your town!",
                            Arrays.asList(
                                    new OutcomeStat(Stat.INTELLECT, 1),
                                    new OutcomeStat(Stat.DEXTERITY, 1)
                            ))
            ),
            "https://storage.googleapis.com/nowhere_images/location_icons/Tavern.png"
    ),
    RITUAL_GROUNDS( "Ritual Grounds",
            Arrays.asList(
                    new Option(
                            "Pray",
                            "You spend the week in pious reverie. You feel the glow and self-satisfaction of knowing you are in good standing with your god.",
                            Arrays.asList(
                                    new OutcomeStat(Stat.FAVOR, 1),
                                    new OutcomeStat(Stat.MAGIC, 1)
                            )),
                    new Option(
                            "Help Prepare for the Ritual",
                            "You spend the week helping to erect the display for the harvest ritual. Your body is hardened by the good work and your soul is enriched.",
                            Arrays.asList(
                                    new OutcomeStat(Stat.STRENGTH, 1),
                                    new OutcomeStat(Stat.FAVOR, 1)
                            ))
            ),
            "https://storage.googleapis.com/nowhere_images/location_icons/Tavern.png"
    ),
    DISCUSSION_PARLOR( "Discussion Parlor",
            Arrays.asList(
                    new Option(
                            "Hold a Debate",
                            "You spend the week contributing to the town's systems of governance. The rigorous discussion improves your ability to make your voice heard.",
                            Arrays.asList(
                                    new OutcomeStat(Stat.INTELLECT, 1),
                                    new OutcomeStat(Stat.CHARISMA, 1)
                            )),
                    new Option(
                            "Research",
                            "You spend the week learning about the town's history. You feel better prepared for the road ahead.",
                            Arrays.asList(
                                    new OutcomeStat(Stat.MAGIC, 1),
                                    new OutcomeStat(Stat.INTELLECT, 1)
                            ))
            ),
            "https://storage.googleapis.com/nowhere_images/location_icons/Tavern.png"
    ),
    FARMLANDS( "Farmlands",
            Arrays.asList(
                    new Option(
                            "Till the Earth",
                            "You spend the week planting and caring for the plant life. Your standing with nature improves.",
                            Arrays.asList(
                                    new OutcomeStat(Stat.FAVOR, 1),
                                    new OutcomeStat(Stat.STRENGTH, 1)
                            )),
                    new Option(
                            "Tame the Beasts",
                            "You spend week the tending to the beasts and retrieving their pelts and milks. Your body hardens and you get to pet lots of cute critters.",
                            Arrays.asList(
                                    new OutcomeStat(Stat.STRENGTH, 1),
                                    new OutcomeStat(Stat.DEXTERITY, 1)
                            ))
            ),
            "https://storage.googleapis.com/nowhere_images/location_icons/Tavern.png"
    );

    private final String label;
    private final String iconDirectory;
    private final List<Option> defaultOptions;

    DefaultLocation(String label, List<Option> defaultOptions, String iconDirectory) {
        this.label = label;
        this.defaultOptions = defaultOptions;
        this.iconDirectory = iconDirectory;
    }

    public List<Option> getDefaultOptions() {
        return defaultOptions;
    }

    public String getLabel() {
        return label;
    }

    public String getIconDirectory() {
        return iconDirectory;
    }
}
