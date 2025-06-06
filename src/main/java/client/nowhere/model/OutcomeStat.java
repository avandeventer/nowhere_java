package client.nowhere.model;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class OutcomeStat {
    PlayerStat playerStat;
    Stat impactedStat;
    int statChange;

    public OutcomeStat () { }

    public OutcomeStat (PlayerStat playerStat) {
        this.playerStat = playerStat;
    }

    public void randomizeOutcomeStat (List<StatType> adventureMapStatTypes, int min, int max) {
        this.playerStat = new PlayerStat(
                adventureMapStatTypes.get(ThreadLocalRandom.current().nextInt(adventureMapStatTypes.size())),
                ThreadLocalRandom.current().nextInt(min, max + 1)
        );
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

    public PlayerStat getPlayerStat() {
        if (
            playerStat == null
            && impactedStat != null
        ) {
            return new PlayerStat(impactedStat.getStatType(), statChange);
        }

        return playerStat;
    }

    public void setPlayerStat(PlayerStat playerStat) {
        this.playerStat = playerStat;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OutcomeStat that = (OutcomeStat) o;
        return statChange == that.statChange && impactedStat == that.impactedStat;
    }

    @Override
    public int hashCode() {
        return Objects.hash(impactedStat, statChange);
    }
}
