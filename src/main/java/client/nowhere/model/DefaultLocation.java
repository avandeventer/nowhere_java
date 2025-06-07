package client.nowhere.model;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public enum DefaultLocation {
    TAVERN( "0",
            "Tavern",
            Arrays.asList(
                new Option(
                        "Drink",
                        "You spend the week drinking. You gain some friends and build a tolerance.",
                        Arrays.asList(
                                new OutcomeStat(new PlayerStat(Stat.STRENGTH.getStatType(), 1)),
                                new OutcomeStat(new PlayerStat(Stat.CHARISMA.getStatType(), 1))
                        )),
                new Option(
                        "Bartend",
                        "You spend the week serving mead. You are charming and are tipped well!",
                        Arrays.asList(
                                new OutcomeStat(new PlayerStat(Stat.CHARISMA.getStatType(), 1)),
                                new OutcomeStat(new PlayerStat(Stat.WEALTH.getStatType(), 1))
                        ))
            ),
            "https://storage.googleapis.com/nowhere_images/location_icons/Tavern.png"
    ),TOWN_SQUARE("1",
            "Town Square",
            Arrays.asList(
                    new Option(
                            "Perform",
                            "You dance in the center of the town square for coin. People enjoy your energy and some even drop some coin!",
                            Arrays.asList(
                                    new OutcomeStat(new PlayerStat(Stat.CHARISMA.getStatType(), 1)),
                                    new OutcomeStat(new PlayerStat(Stat.WEALTH.getStatType(), 1))
                            )),
                    new Option(
                            "Barter",
                            "You spend the week serving mead to the locals. You make some coin and put in some hard work.",
                            Arrays.asList(
                                    new OutcomeStat(new PlayerStat(Stat.DEXTERITY.getStatType(), 1)),
                                    new OutcomeStat(new PlayerStat(Stat.WEALTH.getStatType(), 1))
                            ))
            ),
            "https://storage.googleapis.com/nowhere_images/location_icons/Tavern.png"
    ),
    APOTHECARY("2",
            "Apothecary",
            Arrays.asList(
                    new Option(
                            "Apprentice",
                            "You spend the week training under the masters of the old magics. You learn a lot and feel your connection to the world increase.",
                            Arrays.asList(
                                    new OutcomeStat(new PlayerStat(Stat.MAGIC.getStatType(), 1)),
                                    new OutcomeStat(new PlayerStat(Stat.INTELLECT.getStatType(), 1))
                            )),
                    new Option(
                            "Experiment",
                            "You spend the week trying out spells you haven't tried before. You grow your familiarity with magic and see some unexpected results.",
                            Arrays.asList(
                                    new OutcomeStat(new PlayerStat(Stat.MAGIC.getStatType(), 1)),
                                    new OutcomeStat(new PlayerStat(
                                            Stat.values()[ThreadLocalRandom.current().nextInt(Stat.values().length)].getStatType(), 1))
                            ))
            ),
            "https://storage.googleapis.com/nowhere_images/location_icons/Tavern.png"
    ),
    THE_WILDS("3",
            "Wilds",
            Arrays.asList(
                    new Option(
                            "Adventure",
                            "You spend the week wandering in the wilds and avoiding danger. Your footing improves and you even find some treasure.",
                            Arrays.asList(
                                    new OutcomeStat(new PlayerStat(Stat.DEXTERITY.getStatType(), 1)),
                                    new OutcomeStat(new PlayerStat(Stat.WEALTH.getStatType(), 1))
                            )),
                    new Option(
                            "Forage",
                            "You spend the week looking for resources for the town. You learn a lot about the forest surrounding your town!",
                            Arrays.asList(
                                    new OutcomeStat(new PlayerStat(Stat.INTELLECT.getStatType(), 1)),
                                    new OutcomeStat(new PlayerStat(Stat.DEXTERITY.getStatType(), 1))
                            ))
            ),
            "https://storage.googleapis.com/nowhere_images/location_icons/Tavern.png"
    ),
    RITUAL_GROUNDS("4",
            "Ritual Grounds",
            Arrays.asList(
                    new Option(
                            "Pray",
                            "You spend the week in pious reverie. You feel the glow and self-satisfaction of knowing you are in good standing with your god.",
                            Arrays.asList(
                                    new OutcomeStat(new PlayerStat(Stat.FAVOR.getStatType(), 1)),
                                    new OutcomeStat(new PlayerStat(Stat.MAGIC.getStatType(), 1))
                            )),
                    new Option(
                            "Help Prepare for the Ritual",
                            "You spend the week helping to erect the display for the harvest ritual. Your body is hardened by the good work and your soul is enriched.",
                            Arrays.asList(
                                    new OutcomeStat(new PlayerStat(Stat.STRENGTH.getStatType(), 1)),
                                    new OutcomeStat(new PlayerStat(Stat.FAVOR.getStatType(), 1))
                            ))
            ),
            "https://storage.googleapis.com/nowhere_images/location_icons/Tavern.png"
    ),
    DISCUSSION_PARLOR("5",
            "Discussion Parlor",
            Arrays.asList(
                    new Option(
                            "Hold a Debate",
                            "You spend the week contributing to the town's systems of governance. The rigorous discussion improves your ability to make your voice heard.",
                            Arrays.asList(
                                    new OutcomeStat(new PlayerStat(Stat.INTELLECT.getStatType(), 1)),
                                    new OutcomeStat(new PlayerStat(Stat.CHARISMA.getStatType(), 1))
                            )),
                    new Option(
                            "Research",
                            "You spend the week learning about the town's history. You feel better prepared for the road ahead.",
                            Arrays.asList(
                                    new OutcomeStat(new PlayerStat(Stat.MAGIC.getStatType(), 1)),
                                    new OutcomeStat(new PlayerStat(Stat.INTELLECT.getStatType(), 1))
                            ))
            ),
            "https://storage.googleapis.com/nowhere_images/location_icons/Tavern.png"
    ),
    FARMLANDS("6",
            "Farmlands",
            Arrays.asList(
                    new Option(
                            "Till the Earth",
                            "You spend the week planting and caring for the plant life. Your standing with nature improves.",
                            Arrays.asList(
                                    new OutcomeStat(new PlayerStat(Stat.FAVOR.getStatType(), 1)),
                                    new OutcomeStat(new PlayerStat(Stat.STRENGTH.getStatType(), 1))
                            )),
                    new Option(
                            "Tame the Beasts",
                            "You spend week the tending to the beasts and retrieving their pelts and milks. Your body hardens and you get to pet lots of cute critters.",
                            Arrays.asList(
                                    new OutcomeStat(new PlayerStat(Stat.STRENGTH.getStatType(), 1)),
                                    new OutcomeStat(new PlayerStat(Stat.DEXTERITY.getStatType(), 1))
                            ))
            ),
            "https://storage.googleapis.com/nowhere_images/location_icons/Tavern.png"
    );

    private final String id;
    private final String label;
    private final String iconDirectory;
    private final List<Option> defaultOptions;

    DefaultLocation(String id, String label, List<Option> defaultOptions, String iconDirectory) {
        this.id = id;
        this.label = label;
        this.defaultOptions = defaultOptions;
        this.iconDirectory = iconDirectory;
    }

    public String getId() {
        return id;
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
