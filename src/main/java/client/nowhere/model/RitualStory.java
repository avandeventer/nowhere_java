package client.nowhere.model;

import java.util.List;

public class RitualStory extends Story {

    List<RitualOption> ritualOptions;

    public RitualStory(List<RitualOption> ritualOptions) {
        this.ritualOptions = ritualOptions;
    }

    public RitualStory() { }

    public List<RitualOption> getRitualOptions() {
        return ritualOptions;
    }

    public void setRitualOptions(List<RitualOption> ritualOptions) {
        this.ritualOptions = ritualOptions;
    }

    @Override
    public String toString() {
        return "RitualStory{" +
                "ritualOptions=" + ritualOptions +
                '}';
    }
}
