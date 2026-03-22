package client.nowhere.model;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

public class OutcomeFork {
    @Getter
    @Setter
    TextSubmission textSubmission;

    @Getter
    @Setter
    List<Repercussion> repercussions;

    public OutcomeFork() { }

    public OutcomeFork(TextSubmission textSubmission) {
        this.textSubmission = textSubmission;
    }
}
