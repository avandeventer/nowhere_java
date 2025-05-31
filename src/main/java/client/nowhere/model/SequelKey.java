package client.nowhere.model;

import java.util.Objects;

public class SequelKey {
    private final String selectedOptionId;
    private final boolean succeeded;

    public SequelKey(String selectedOptionId, boolean succeeded) {
        this.selectedOptionId = selectedOptionId;
        this.succeeded = succeeded;
    }

    public String getSelectedOptionId() {
        return selectedOptionId;
    }

    public boolean isSucceeded() {
        return succeeded;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SequelKey sequelKey)) return false;
        return succeeded == sequelKey.succeeded && selectedOptionId.equals(sequelKey.selectedOptionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(selectedOptionId, succeeded);
    }
}