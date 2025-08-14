package client.nowhere.model;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class PlayerStat {

    StatType statType;
    int value;

    public PlayerStat() {
        this.statType = new StatType();
        this.value = 0;
    }

    //Randomize stat type and value
    public PlayerStat(List<StatType> statTypes, int min, int max) {
        this.statType = statTypes.get(ThreadLocalRandom.current().nextInt(statTypes.size()));
        this.value = ThreadLocalRandom.current().nextInt(min, max + 1);
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
