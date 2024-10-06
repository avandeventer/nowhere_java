package client.nowhere.model;

public class Repercussion {

    String sequelPlayerId;
    String sequelLocationId;
    Event event;

    public String getSequelPlayerId() {
        return sequelPlayerId;
    }

    public void setSequelPlayerId(String sequelPlayerId) {
        this.sequelPlayerId = sequelPlayerId;
    }

    public String getSequelLocationId() {
        return sequelLocationId;
    }

    public void setSequelLocationId(String sequelLocationId) {
        this.sequelLocationId = sequelLocationId;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

}
