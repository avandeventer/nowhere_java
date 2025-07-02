package client.nowhere.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Location {

    private int locationId;
    private String description;
    private int locationIndex;

    @JsonIgnore
    private String locationName;
    private String id;
    private String label;
    private List<Option> options;
    private String iconDirectory;

    public Location () {
        this.id = UUID.randomUUID().toString();
    }

    public Location(String id, String label, String description, List<Option> options, String iconDirectory) {
        this.id = id;
        this.description = description;
        if (id.isEmpty()){
            this.id = UUID.randomUUID().toString();
        }
        this.options = options;
        this.label = label;
        this.iconDirectory = iconDirectory;
    }

    public Location(String id, String label) {
        this.id = id;
        if (id.isEmpty()){
            this.id = UUID.randomUUID().toString();
        }
        this.label = label;
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
        if (this.iconDirectory == null || this.iconDirectory.isEmpty()) {
            setIconDirectory("https://storage.googleapis.com/nowhere_images/location_icons/Tavern.png");
        }
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @JsonIgnore
    public String getLocationName() {
        return locationName;
    }

    @JsonProperty
    public void setLocationName(String locationName) {
        this.locationName = locationName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return locationId == location.locationId && Objects.equals(label, location.label) && Objects.equals(options, location.options);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationId, label, options);
    }
}
