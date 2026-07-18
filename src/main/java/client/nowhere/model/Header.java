package client.nowhere.model;

import lombok.Getter;
import lombok.Setter;

public class Header {
    @Getter @Setter
    String label;

    @Getter @Setter
    String color;

    public Header() {}

    public Header(String label, String color) {
        this.label = label;
        this.color = color;
    }
}
