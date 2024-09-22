package client.nowhere.model;

public class Repercussion {

    Stat impactedStat;
    int statChange;
    String sequelPlayerId;
    String sequelLocationId;

    public Stat getImpactedStat() {
        return impactedStat;
    }

    public void setImpactedStat(Stat impactedStat) {
        this.impactedStat = impactedStat;
    }

    public int getStatChange() {
        return statChange;
    }

    public void setStatChange(int statChange) {
        this.statChange = statChange;
    }

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
}
