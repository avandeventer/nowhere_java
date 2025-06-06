package client.nowhere.model;

public class PlayerStat {

    StatType statType;
    int value;

    public PlayerStat() {
        this.statType = new StatType();
        this.value = 0;
    }

    public PlayerStat(StatType statType, int value) {
        this.statType = statType;
        this.value = value;
    }

    public StatType getStatType() {
        return statType;
    }

    public void setStatType(StatType statType) {
        this.statType = statType;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int value) {
        this.value = value;
    }

}
