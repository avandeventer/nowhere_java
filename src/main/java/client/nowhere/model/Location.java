package client.nowhere.model;

public class Location {

    private int locationId;
    private String locationName;

    public Location () {}

    public Location (String locationName, int locationId) {
        this.locationName = locationName;
        this.locationId = locationId;
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

}
