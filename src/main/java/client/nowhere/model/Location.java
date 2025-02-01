package client.nowhere.model;

import java.util.List;
import java.util.Objects;

public class Location {

    private int locationId;
    private String locationName;
    private String label;
    private List<Option> options;
    private String iconDirectory;

    public Location () {}

    public Location(String locationName, int locationId, List<Option> options, String label, String iconDirectory) {
        this.locationName = locationName;
        this.locationId = locationId;
        this.options = options;
        this.label = label;
        this.iconDirectory = iconDirectory;
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

    public String getIconDirectory() {
        return iconDirectory;
    }

    public void setIconDirectory(String iconDirectory) {
        this.iconDirectory = iconDirectory;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return locationId == location.locationId && Objects.equals(locationName, location.locationName) && Objects.equals(label, location.label) && Objects.equals(options, location.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationId, locationName, label, options);
    }
}
