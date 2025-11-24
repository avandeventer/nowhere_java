package client.nowhere.model;

import java.util.UUID;

public class EncounterLabel {
    public String encounterId;
    public String encounterLabel;
    public TextSubmission textSubmission;

    public EncounterLabel(String encounterLabel, TextSubmission textSubmission, Story story) {
        this.encounterId = UUID.randomUUID().toString();
        this.encounterLabel = encounterLabel;
        this.textSubmission = textSubmission;
    }

    public EncounterLabel(String encounterLabel, TextSubmission textSubmission) {
        this.encounterId = UUID.randomUUID().toString();
        this.encounterLabel = encounterLabel;
        this.textSubmission = textSubmission;
    }

    public String getEncounterId() {
        return encounterId;
    }

    public String getEncounterLabel() {
        return encounterLabel;
    }

    public TextSubmission getTextSubmission() {
        return textSubmission;
    }
}
