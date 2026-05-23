package client.nowhere.model;

import lombok.Getter;
import lombok.Setter;
import java.util.ArrayList;
import java.util.List;

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

    @Getter
    @Setter
    List<OutcomeType> subTypes;

    @Getter
    @Setter
    String header;

    public OutcomeType () {
        this.id = "";
        this.label = "";
        this.clarifier = "";
        this.subTypes = new ArrayList<>();
        this.header = "";
    }

    public OutcomeType(String id, String label) {
        this.id = id;
        this.label = label;
        this.clarifier = "";
        this.subTypes = new ArrayList<>();
        this.header = "";
    }

    public OutcomeType(String id, String label, String clarifier) {
        this.id = id;
        this.label = label;
        this.clarifier = clarifier;
        this.subTypes = new ArrayList<>();
        this.header = "";
    }
}
