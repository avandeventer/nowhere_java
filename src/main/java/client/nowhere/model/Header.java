package client.nowhere.model;

import lombok.Getter;
import lombok.Setter;

public class Header {
    @Getter @Setter
    String label;

    @Getter @Setter
    String color;

    public Header() {}

    public Header(String label) {
        this.label = label;
        this.color = "#4caf50";
    }

    public Header(String label, String color) {
        this.label = label;
        this.color = color;
    }
}
