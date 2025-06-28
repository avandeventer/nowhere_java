package client.nowhere.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AdventureMapTest {

    @Test
    void shouldUpdateOnlyNonEmptyAdventureMapDisplayFields() {
        AdventureMap map = new AdventureMap();
        GameSessionDisplay initialDisplay = new GameSessionDisplay();
        initialDisplay.setMapDescription("Old desc");
        initialDisplay.setGoalDescription("Keep this");
        map.setGameSessionDisplay(initialDisplay);

        AdventureMap updates = new AdventureMap();
        updates.setName("New Name");
        GameSessionDisplay newDisplay = new GameSessionDisplay();
        newDisplay.setMapDescription("New desc");
        newDisplay.setGoalDescription("");
        updates.setGameSessionDisplay(newDisplay);

        map.updateAdventureMapDisplay(updates);

        assertEquals("New Name", map.getName());
        assertEquals("New desc", map.getGameSessionDisplay().getMapDescription());
        assertEquals("Keep this", map.getGameSessionDisplay().getGoalDescription());
    }

    @Test
    void shouldReplaceOrAddStatTypesById() {
        StatType oldStat = new StatType("s1", "Dex");
        StatType preserved = new StatType("s2", "Wis");
        AdventureMap map = new AdventureMap();
        map.setStatTypes(new ArrayList<>(List.of(oldStat, preserved)));

        List<StatType> updates = List.of(
                new StatType("s1", "Dex (updated)"), // replaces
                new StatType("s3", "Cha")           // adds
        );

        map.updateStatTypes(updates);

        List<String> labels = map.getStatTypes().stream()
                .map(StatType::getLabel).collect(Collectors.toList());

        assertEquals(3, map.getStatTypes().size());
        assertTrue(labels.contains("Dex (updated)"));
        assertTrue(labels.contains("Wis"));
        assertTrue(labels.contains("Cha"));
    }

    @Test
    void shouldUpdateLocationsById() {
        Location loc1 = new Location("loc1", "Old Tower");
        Location loc2 = new Location("loc2", "Forest");
        AdventureMap map = new AdventureMap();
        map.setLocations(new ArrayList<>(List.of(loc1, loc2)));

        List<Location> updates = List.of(
                new Location("loc1", "New Tower"),
                new Location("loc3", "Bridge")
        );

        map.updateLocations(updates);

        List<String> labels = map.getLocations().stream()
                .map(Location::getLabel).collect(Collectors.toList());

        assertEquals(3, map.getLocations().size());
        assertTrue(labels.contains("New Tower"));
        assertTrue(labels.contains("Forest"));
        assertTrue(labels.contains("Bridge"));
    }
}
