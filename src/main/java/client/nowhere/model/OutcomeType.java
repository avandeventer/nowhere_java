package client.nowhere.model;

import com.fasterxml.jackson.annotation.JsonProperty;
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

    List<Header> headers;

    public List<Header> getHeaders() {
        return headers;
    }

    public void setHeaders(List<Header> headers) {
        this.headers = headers;
    }

    public OutcomeType() {
        this.id = "";
        this.label = "";
        this.clarifier = "";
        this.subTypes = new ArrayList<>();
        this.headers = new ArrayList<>();
    }

    public OutcomeType(String id, String label) {
        this.id = id;
        this.label = label;
        this.clarifier = "";
        this.subTypes = new ArrayList<>();
        this.headers = new ArrayList<>();
    }

    public OutcomeType(String id, String label, String clarifier, List<Header> headers) {
        this.id = id;
        this.label = label;
        this.clarifier = clarifier;
        this.subTypes = new ArrayList<>();
        this.headers = headers;
    }
}
