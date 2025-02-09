package client.nowhere.model;

import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

public class StatRequirement {

    Stat dcStat;
    int dcValue;

    public StatRequirement() { }

    public StatRequirement(Stat dcStat, int dcValue) {
        this.dcStat = dcStat;
        this.dcValue = dcValue;
    }

    public Stat getDcStat() {
        return dcStat;
    }

    public void setDcStat(Stat dcStat) {
        this.dcStat = dcStat;
    }

    public int getDcValue() {
        return dcValue;
    }

    public void setDcValue(int dcValue) {
        this.dcValue = dcValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StatRequirement that = (StatRequirement) o;
        return dcValue == that.dcValue && dcStat == that.dcStat;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dcStat, dcValue);
    }
}
