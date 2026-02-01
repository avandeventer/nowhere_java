package client.nowhere.model;

import lombok.Getter;
import lombok.Setter;

public class OutcomeFork {
    @Getter
    @Setter
    TextSubmission textSubmission;

    public OutcomeFork() { }

    public OutcomeFork(TextSubmission textSubmission) {
        this.textSubmission = textSubmission;
    }
}
