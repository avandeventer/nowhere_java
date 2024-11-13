package client.nowhere.model;

import java.util.List;

public class Location {

    private int locationId;
    private String locationName;
    private String label;
    private List<Option> options;

    public Location () {}

    public Location(String locationName, int locationId, List<Option> options, String label) {
        this.locationName = locationName;
        this.locationId = locationId;
        this.options = options;
        this.label = label;
    }

    public int getLocationId() {
        return locationId;
    }

    public void setLocationId(int locationId) {
        this.locationId = locationId;
    }

    public String getLocationName() {
        return locationName;
    }

    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    public List<Option> getOptions() {
        return options;
    }

    public void setOptions(List<Option> options) {
        this.options = options;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
