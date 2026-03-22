package client.nowhere.model;

import java.util.UUID;

public class Trait {
    public String traitId;
    public String traitLabel;
    public TraitType traitType;
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

    public Trait(Repercussion repercussion) {
        this.traitId = UUID.randomUUID().toString();
        this.traitLabel = repercussion.getRepercussionSubmission();
        if (repercussion.getRepercussionType().equals(RepercussionType.TITLE.getName())) {
            this.traitType = TraitType.TITLE;
        } else {
            this.traitType = TraitType.STANDARD;
        }
    }


    public Trait(String traitId, String traitLabel) {
        this.traitId = traitId;
        this.traitLabel = traitLabel;
        this.traitType = TraitType.STANDARD;
    }

    public Trait(String traitLabel, TraitType type) {
        this.traitId = UUID.randomUUID().toString();
        this.traitLabel = traitLabel;
        this.traitType = type;
    }

    public Trait(String traitLabel, TextSubmission textSubmission) {
        this.traitId = UUID.randomUUID().toString();
        this.traitLabel = traitLabel;
        this.textSubmission = textSubmission;
        this.traitType = TraitType.STANDARD;
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

    public TraitType getTraitType() { return traitType; }
}
