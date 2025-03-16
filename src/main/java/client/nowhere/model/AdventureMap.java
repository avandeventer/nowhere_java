package client.nowhere.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdventureMap {

    String name;
    String adventureId;
    List<Location> locations;
    RitualStory ritual;

    public AdventureMap() {
        this.name = "Nowhere";
        this.adventureId = "a6a6e1ab-de29-4ffb-9028-7c4f90f9d008";

        this.locations = new ArrayList<>();
        int locationId = 0;
        for(DefaultLocation location : DefaultLocation.values()) {
            Location townLocale = new Location(location.name(), locationId, location.getDefaultOptions(), location.getLabel(), location.getIconDirectory());
            locationId++;
            this.locations.add(townLocale);
        }

        generateDefaultRitual();
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

        List<RitualOption> ritualOptions = Arrays.asList(
                new RitualOption("0", "Holder of the Chant", "You lead the rhythmic chants, setting the ritual's tone.", Arrays.asList(Stat.CHARISMA, Stat.STRENGTH), statGradient, "Your powerful voice resonates, guiding the ritual to new heights!", "Your voice wavers, and the chant loses its rhythm."),
                new RitualOption("1", "Lead Dancer", "You work to gather the other dancers around the pyre.", Arrays.asList(Stat.CHARISMA, Stat.DEXTERITY), statGradient, "Your sense of flow and natural athletic artistry spurs the town into a magnificent choreographed display!", "The other dancers appreciate your efforts, but there is only so many times you can step on other people's toes before they move on to other tasks."),
                new RitualOption("2", "Ceremony Herald", "You announce the phases of the ceremony with confidence.", Arrays.asList(Stat.CHARISMA, Stat.INTELLECT), statGradient, "Your eloquence ensures the ritual runs smoothly and inspires awe!", "Your words falter, and the crowd grows restless."),
                new RitualOption("3", "King of Fools", "You embrace chaos, playing the trickster within the ritual.", Arrays.asList(Stat.CHARISMA, Stat.WEALTH), statGradient, "Your antics bring unexpected wisdom through humor!", "Your jokes fall flat, and confusion ensues."),
                new RitualOption("4", "Seer", "You peer into the flames, divining the ritual's deeper meaning.", Arrays.asList(Stat.CHARISMA, Stat.MAGIC), statGradient, "A clear vision emerges, guiding the town's future.", "Your vision is clouded, leaving uncertainty."),
                new RitualOption("5", "Lord of Misrule", "You orchestrate playful disruption to challenge expectations.", Arrays.asList(Stat.DEXTERITY, Stat.INTELLECT), statGradient, "Your cunning and agility keep the ceremony lively!", "Your antics cause more chaos than intended."),
                new RitualOption("6", "Head of Ritual Consecration", "You ensure the sacred space is properly prepared.", Arrays.asList(Stat.DEXTERITY, Stat.WEALTH), statGradient, "The ritual ground radiates energy, amplifying the spellwork.", "The space remains mundane, the energy unshaped."),
                new RitualOption("7", "Spirit Walker", "You move between the seen and unseen, guiding the spirits.", Arrays.asList(Stat.DEXTERITY, Stat.MAGIC), statGradient, "You dance the border of worlds, bridging the realms.", "The spirits remain distant, their whispers unheard."),
                new RitualOption("8", "Fire Keeper", "You tend the sacred fire, ensuring its steady glow.", Arrays.asList(Stat.STRENGTH, Stat.INTELLECT), statGradient, "The flames blaze high, illuminating the ritual with power.", "The fire sputters, casting uncertain shadows."),
                new RitualOption("9", "Stone Circle Builder", "You work to place and empower the sacred stones.", Arrays.asList(Stat.STRENGTH, Stat.WEALTH), statGradient, "The stones resonate with energy, forming a potent circle.", "The placements feel unbalanced, weakening the ritual."),
                new RitualOption("10", "Rune Carver", "You inscribe sacred runes to channel energy.", Arrays.asList(Stat.STRENGTH, Stat.MAGIC), statGradient, "The runes glow with power, enhancing the ritual’s intent.", "The symbols lack clarity, and their power falters."),
                new RitualOption("11", "Feast Lord", "You oversee the sacred meal, ensuring offerings are honored.", Arrays.asList(Stat.INTELLECT, Stat.WEALTH), statGradient, "The feast is perfectly balanced, nourishing both body and spirit.", "The offerings feel lacking, diminishing the ritual’s blessing."),
                new RitualOption("12", "Erysus Evoker", "You call upon the presence of the god Erysus.", Arrays.asList(Stat.INTELLECT, Stat.MAGIC), statGradient, "The god's presence fills the space, empowering all present.", "Your voice falters, and the connection feels weak.")
        );

        this.ritual = new RitualStory(ritualOptions);
    }

    public String getAdventureId() {
        return adventureId;
    }

    public void setAdventureId(String adventureId) {
        this.adventureId = adventureId;
    }

    public RitualStory getRitual() {
        return ritual;
    }

    public void setRitual(RitualStory ritual) {
        this.ritual = ritual;
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
