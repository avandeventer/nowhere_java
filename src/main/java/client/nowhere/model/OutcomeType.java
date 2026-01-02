package client.nowhere.model;

import lombok.Getter;
import lombok.Setter;

public class OutcomeType {
    @Getter
    @Setter
    String id;

    @Getter
    @Setter
    String label;

    @Getter
    @Setter
    String clarifier;

    public OutcomeType () {
        this.id = "";
        this.label = "";
        this.clarifier = "";
    }

    public OutcomeType(String id, String label) {
        this.id = id;
        this.label = label;
        this.clarifier = "";
    }

    public OutcomeType(String id, String label, String clarifier) {
        this.id = id;
        this.label = label;
        this.clarifier = clarifier;
    }
}
