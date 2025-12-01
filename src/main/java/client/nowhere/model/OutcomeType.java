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

    public OutcomeType () {
        this.id = "";
        this.label = "";
    }

    public OutcomeType(String id, String label) {
        this.id = id;
        this.label = label;
    }
}
