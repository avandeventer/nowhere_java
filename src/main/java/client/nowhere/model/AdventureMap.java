package client.nowhere.model;

import java.util.ArrayList;
import java.util.List;

public class AdventureMap {

    String name;
    String adventureId;
    List<Location> locations;

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

}
