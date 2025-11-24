package client.nowhere.model;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AdventureMap {
    String name;
    String adventureId;
    List<StatType> statTypes;
    GameSessionDisplay gameSessionDisplay;
    List<Location> locations;
    List<Location> unusedLocations;
    Story ritual;
    List<EncounterLabel> encounterLabels;

    public AdventureMap() {
        this.name = "";

        this.adventureId = UUID.randomUUID().toString();

        this.locations = new ArrayList<>();
//        int locationId = 0;
//        for(DefaultLocation location : DefaultLocation.values()) {
//            Location townLocale = new Location(location.name(), location.getDescription(), locationId, Integer.toString(locationId), location.getDefaultOptions(), location.getLabel(), location.getIconDirectory());
//            locationId++;
//            this.locations.add(townLocale);
//        }

//        generateDefaultGameSessionDisplay();
//        generateDefaultRitual();
        this.ritual = new Story();

        this.statTypes = new ArrayList<>();
//        for (Stat defaultStat: Stat.values()) {
//            this.statTypes.add(defaultStat.getStatType());
//        }
        this.encounterLabels = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Location> getLocations() {
        return locations;
    }

    public void setLocations(List<Location> locations) {
        this.locations = locations;
    }

    public void generateDefaultRitual() {
        List<Integer> statGradient = Arrays.asList(9, 6);

        List<Option> ritualOptions = Arrays.asList(
            new Option("0", "Holder of the Chant", "You lead the rhythmic chants, setting the ritual's tone.", Arrays.asList(Stat.CHARISMA.getStatType(), Stat.STRENGTH.getStatType()), statGradient, "Your powerful voice resonates, guiding the ritual to new heights!", "Your voice wavers, and the chant loses its rhythm."),
            new Option("1", "Lead Dancer", "You work to gather the other dancers around the pyre.", Arrays.asList(Stat.CHARISMA.getStatType(), Stat.DEXTERITY.getStatType()), statGradient, "Your sense of flow and natural athletic artistry spurs the town into a magnificent choreographed display!", "The other dancers appreciate your efforts, but there is only so many times you can step on other people's toes before they move on to other tasks."),
            new Option("2", "Ceremony Herald", "You announce the phases of the ceremony with confidence.", Arrays.asList(Stat.CHARISMA.getStatType(), Stat.INTELLECT.getStatType()), statGradient, "Your eloquence ensures the ritual runs smoothly and inspires awe!", "Your words falter, and the crowd grows restless."),
            new Option("3", "King of Fools", "You embrace chaos, playing the trickster within the ritual.", Arrays.asList(Stat.CHARISMA.getStatType(), Stat.WEALTH.getStatType()), statGradient, "Your antics bring unexpected wisdom through humor!", "Your jokes fall flat, and confusion ensues."),
            new Option("4", "Seer", "You peer into the flames, divining the ritual's deeper meaning.", Arrays.asList(Stat.CHARISMA.getStatType(), Stat.MAGIC.getStatType()), statGradient, "A clear vision emerges, guiding the town's future.", "Your vision is clouded, leaving uncertainty."),
            new Option("5", "Lord of Misrule", "You orchestrate playful disruption to challenge expectations.", Arrays.asList(Stat.DEXTERITY.getStatType(), Stat.INTELLECT.getStatType()), statGradient, "Your cunning and agility keep the ceremony lively!", "Your antics cause more chaos than intended."),
            new Option("6", "Head of Ritual Consecration", "You ensure the sacred space is properly prepared.", Arrays.asList(Stat.DEXTERITY.getStatType(), Stat.WEALTH.getStatType()), statGradient, "The ritual ground radiates energy, amplifying the spellwork.", "The space remains mundane, the energy unshaped."),
            new Option("7", "Spirit Walker", "You move between the seen and unseen, guiding the spirits.", Arrays.asList(Stat.DEXTERITY.getStatType(), Stat.MAGIC.getStatType()), statGradient, "You dance the border of worlds, bridging the realms.", "The spirits remain distant, their whispers unheard."),
            new Option("8", "Fire Keeper", "You tend the sacred fire, ensuring its steady glow.", Arrays.asList(Stat.STRENGTH.getStatType(), Stat.INTELLECT.getStatType()), statGradient, "The flames blaze high, illuminating the ritual with power.", "The fire sputters, casting uncertain shadows."),
            new Option("9", "Stone Circle Builder", "You work to place and empower the sacred stones.", Arrays.asList(Stat.STRENGTH.getStatType(), Stat.WEALTH.getStatType()), statGradient, "The stones resonate with energy, forming a potent circle.", "The placements feel unbalanced, weakening the ritual."),
            new Option("10", "Rune Carver", "You inscribe sacred runes to channel energy.", Arrays.asList(Stat.STRENGTH.getStatType(), Stat.MAGIC.getStatType()), statGradient, "The runes glow with power, enhancing the ritual’s intent.", "The symbols lack clarity, and their power falters."),
            new Option("11", "Feast Lord", "You oversee the sacred meal, ensuring offerings are honored.", Arrays.asList(Stat.INTELLECT.getStatType(), Stat.WEALTH.getStatType()), statGradient, "The feast is perfectly balanced, nourishing both body and spirit.", "The offerings feel lacking, diminishing the ritual’s blessing."),
            new Option("12", "Erysus Evoker", "You call upon the presence of the god Erysus.", Arrays.asList(Stat.INTELLECT.getStatType(), Stat.MAGIC.getStatType()), statGradient, "The god's presence fills the space, empowering all present.", "Your voice falters, and the connection feels weak.")
        );

        this.ritual = new Story(ritualOptions);
    }

    public String getAdventureId() {
        return adventureId;
    }

    public void setAdventureId(String adventureId) {
        this.adventureId = adventureId;
        if (this.adventureId == null || this.adventureId.isEmpty()) {
            this.adventureId = UUID.randomUUID().toString();
        }
    }

    public Story getRitual() {
        return ritual;
    }

    public void setRitual(Story ritual) {
        this.ritual = ritual;
    }

    private void generateDefaultGameSessionDisplay() {
        GameSessionDisplay gameSessionDisplay = new GameSessionDisplay(
                "The season of harvest has arrived and we all must spend the coming weeks preparing for the ritual to our patron God of Harvest, Erysus. Our very survival is dependent on her whims.",
                "The ritual at the end of the season will determine whether we will live or die this year. Those who are invested in the well-being of our little hamlet will make choices that please Erysus, but there will still be those among us who choose frivolity.",
                "Behold! The harvest ritual arrives! Have you prepared appropriately? Choose how you believe you can best impress Erysus."
        );

        gameSessionDisplay.playerTitle = "The Nobodies";
        gameSessionDisplay.playerDescription = "We are the inhabitants of a small farming village. We spend our days toiling in the fields in hopes of a fruitful season for our children and the nights praying to Erysus. We are devout followers of a God who annually appears to demand that we perform her rites.";

        gameSessionDisplay.successText = "And so the ritual lasted long into the night just as it always does. All of the citizens of our little hamlet in the middle of Nowhere did their best to impress Erysus lest she deliver a season where they could provide for their families. " +
                "After a night of colors and feasting and prayer, we awaken to fields abundant with new sprouts. The winter has ended and Erysus has looked kindly upon us. " +
                "We hug and we cry and we begin to work again once more in the fields and those among us who believe set upon their task of preparing to pray again next year before the god who sees our faith and shows us mercy. The almighty Erysus!";

        gameSessionDisplay.neutralText = "And so the ritual lasted long into the night just as it always does. All of the citizens of our little hamlet in the middle of Nowhere did their best to impress Erysus lest she deliver a season where they could provide for their families. " +
                "It was a long night and some things went better than others and as the sun rises it is unclear how we did. The wind is hollow and the dirt does not seem to bare fruit. Just as the townspeople are preparing to pack up their things and scramble to the local trading post in " +
                "anticipation of a bare season, someone notices a small patch of crop peaking up from behind the pyre. We may not have excelled, but the glimmer of Erysus' eye shines down upon us. Perhaps we will not starve after all.";

        gameSessionDisplay.failureText = "And so the ritual lasted long into the night just as it always does. All of the citizens of our little hamlet in the middle of Nowhere did their best to impress Erysus lest she deliver a season where they could provide for their families. " +
                "However. The night is filled with folly and, despite the best efforts of the faithful, Erysus seems to have abandoned us come morning. " +
                "The fields do not sprout, the air remains cold, and children go hungry. The season of winter has ended, but we do not see any change. " +
                "Those who are faithful begin their task of convincing the community that our ritual next year should be grander, but many in the community lose faith altogether. It will be a year of despair.";

        this.gameSessionDisplay = gameSessionDisplay;
    }

    public void updateAdventureMapDisplay(AdventureMap adventureMapUpdates) {
        if (!adventureMapUpdates.getName().isEmpty()) {
            setName(adventureMapUpdates.getName());
        }

        if (adventureMapUpdates.getGameSessionDisplay() != null) {
            GameSessionDisplay gameSessionDisplayUpdates = adventureMapUpdates.getGameSessionDisplay();
            GameSessionDisplay existingDisplay = getGameSessionDisplay();

            if (!gameSessionDisplayUpdates.getMapDescription().isEmpty()) {
                existingDisplay.setMapDescription(gameSessionDisplayUpdates.getMapDescription());
            }
            if (!gameSessionDisplayUpdates.getGoalDescription().isEmpty()) {
                existingDisplay.setGoalDescription(gameSessionDisplayUpdates.getGoalDescription());
            }
            if (!gameSessionDisplayUpdates.getPlayerTitle().isEmpty()) {
                existingDisplay.setPlayerTitle(gameSessionDisplayUpdates.getPlayerTitle());
            }
            if (!gameSessionDisplayUpdates.getPlayerDescription().isEmpty()) {
                existingDisplay.setPlayerDescription(gameSessionDisplayUpdates.getPlayerDescription());
            }
            if (!gameSessionDisplayUpdates.getEndingDescription().isEmpty()) {
                existingDisplay.setEndingDescription(gameSessionDisplayUpdates.getEndingDescription());
            }
            if (!gameSessionDisplayUpdates.getSuccessText().isEmpty()) {
                existingDisplay.setSuccessText(gameSessionDisplayUpdates.getSuccessText());
            }
            if (!gameSessionDisplayUpdates.getNeutralText().isEmpty()) {
                existingDisplay.setNeutralText(gameSessionDisplayUpdates.getNeutralText());
            }
            if (!gameSessionDisplayUpdates.getFailureText().isEmpty()) {
                existingDisplay.setFailureText(gameSessionDisplayUpdates.getFailureText());
            }
        }
    }

    public GameSessionDisplay getGameSessionDisplay() {
        return gameSessionDisplay;
    }

    public void setGameSessionDisplay(GameSessionDisplay gameSessionDisplay) {
        this.gameSessionDisplay = gameSessionDisplay;
    }

    public List<StatType> getStatTypes() {
        return statTypes;
    }

    public void setStatTypes(List<StatType> statTypes) {
        this.statTypes = statTypes;
    }

    public void updateStatTypes(List<StatType> statTypes) {
        Map<String, StatType> statTypeMap = this.statTypes.stream()
                .collect(Collectors.toMap(StatType::getId, Function.identity()));

        for (StatType statType : statTypes) {
            if (!statType.getId().isEmpty() && !statType.getLabel().isEmpty()) {
                statTypeMap.put(statType.getId(), statType);
            }
        }

        this.statTypes = new ArrayList<>(statTypeMap.values());
    }

    public void updateLocations(List<Location> locations) {
        Map<String, Location> locationMap = this.locations.stream()
                .collect(Collectors.toMap(Location::getId, Function.identity()));

        for (Location location : locations) {
            if (!location.getId().isEmpty() && !location.getLabel().isEmpty()) {
                locationMap.put(location.getId(), location);
            }
        }

        this.locations = new ArrayList<>(locationMap.values());
    }

    public void updateRitualOptions(Story ritual) {
        if (this.ritual.getOptions() == null) {
            this.ritual.setOptions(new ArrayList<>());
        }
        Map<String, Option> ritualOptionMap = this.ritual.getOptions().stream()
                .collect(Collectors.toMap(Option::getOptionId, Function.identity()));

        for (Option option : ritual.getOptions()) {
            if (!option.getOptionId().isEmpty() && !option.getOptionText().isEmpty()) {
                ritualOptionMap.put(option.getOptionId(), option);
            }
        }

        this.ritual.setOptions(new ArrayList<>(ritualOptionMap.values()));
    }

    public List<Location> getUnusedLocations() {
        return unusedLocations;
    }

    public void setUnusedLocations(List<Location> unusedLocations) {
        this.unusedLocations = unusedLocations;
    }

    public List<EncounterLabel> getEncounterLabels() {
        return encounterLabels;
    }

    public void setEncounterLabels(List<EncounterLabel> encounterLabels) {
        this.encounterLabels = encounterLabels;
    }

    @Override
    public String toString() {
        return "AdventureMap{" +
                "name='" + name + '\'' +
                ", adventureId='" + adventureId + '\'' +
                ", locations=" + locations +
                ", ritual=" + ritual +
                '}';
    }
}
