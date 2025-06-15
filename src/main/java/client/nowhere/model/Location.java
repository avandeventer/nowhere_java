package client.nowhere.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Location {

    private int locationId;
    private int locationIndex;
    private String id;
    private String locationName;
    private String label;
    private List<Option> options;
    private String iconDirectory;

    public Location () {}

    public Location(String locationName, int locationId, String id, List<Option> options, String label, String iconDirectory) {
        this.id = id;
        if (id.isEmpty()){
            this.id = UUID.randomUUID().toString();
        }
        this.locationName = locationName;
        this.locationId = locationId;
        this.options = options;
        this.label = label;
        this.iconDirectory = iconDirectory;
    }

    public String getId() {
        if (id == null && locationId != 0) {
            return Integer.toString(locationId);
        }

        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public int getLocationIndex() {
        return locationIndex;
    }

    public void setLocationIndex(int locationIndex) {
        this.locationIndex = locationIndex;
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
