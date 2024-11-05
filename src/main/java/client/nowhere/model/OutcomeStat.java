package client.nowhere.model;

import java.util.concurrent.ThreadLocalRandom;

public class OutcomeStat {

    Stat impactedStat;
    int statChange;

    public OutcomeStat () { }

    public OutcomeStat (Stat impactedStat, int statChange) {
        this.impactedStat = impactedStat;
        this.statChange = statChange;
    }

    public void randomizeOutcomeStat (int min, int max) {
        this.impactedStat = Stat.values()[ThreadLocalRandom.current().nextInt(Stat.values().length)];
        this.statChange = ThreadLocalRandom.current().nextInt(min, max + 1);
    }

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
}
