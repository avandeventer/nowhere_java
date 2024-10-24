package client.nowhere.helper;

import client.nowhere.model.AdventureMap;
import client.nowhere.model.Location;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LocationHelper {

    private final AdventureMap adventureMap;

    @Autowired
    public LocationHelper() {
        this.adventureMap = new AdventureMap();
    }

    public List<Location> getGameLocations(String gameCode) {
        return this.adventureMap.getLocations();
    }
}
