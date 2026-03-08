package client.nowhere.model;

import java.util.UUID;

public class Trait {
    public String traitId;
    public String traitLabel;
    public TextSubmission textSubmission;

    public Trait() {
        this.traitId = UUID.randomUUID().toString();
        this.traitLabel = "";
        this.textSubmission = new TextSubmission();
    }

    public Trait(String traitLabel) {
        this.traitId = UUID.randomUUID().toString();
        this.traitLabel = traitLabel;
    }

    public Trait(String traitLabel, TextSubmission textSubmission) {
        this.traitId = UUID.randomUUID().toString();
        this.traitLabel = traitLabel;
        this.textSubmission = textSubmission;
    }

    public String getTraitId() {
        return traitId;
    }

    public String getTraitLabel() {
        return traitLabel;
    }

    public TextSubmission getTextSubmission() {
        return textSubmission;
    }
}
