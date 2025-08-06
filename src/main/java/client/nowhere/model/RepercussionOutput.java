package client.nowhere.model;

public class RepercussionOutput {
    Story ending;
    Location locationDestroyed;

    public RepercussionOutput() { }

    public Story getEnding() {
        return ending;
    }

    public void setEnding(Story ending) {
        this.ending = ending;
    }

    public Location getLocationDestroyed() {
        return locationDestroyed;
    }

    public void setLocationDestroyed(Location locationDestroyed) {
        this.locationDestroyed = locationDestroyed;
    }
}
