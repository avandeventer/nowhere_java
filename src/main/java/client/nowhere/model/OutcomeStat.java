package client.nowhere.model;

import java.util.List;
import java.util.Objects;

public class OutcomeStat {
    PlayerStat playerStat;
    Stat impactedStat;
    int statChange;

    public OutcomeStat () { }

    public OutcomeStat (PlayerStat playerStat) {
        this.playerStat = playerStat;
    }

    //This constructor randomizes player stats
    public OutcomeStat (StatType statType, int min, int max, boolean sideWith) {
        this.playerStat = new PlayerStat(statType, min, max, sideWith);
    }

    //This constructor randomizes player stats
    public OutcomeStat (List<StatType> adventureMapStatTypes, int min, int max) {
        this.playerStat = new PlayerStat(adventureMapStatTypes, min, max);
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
