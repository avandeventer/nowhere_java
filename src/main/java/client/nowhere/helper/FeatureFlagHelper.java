package client.nowhere.helper;

import client.nowhere.dao.FeatureFlagDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FeatureFlagHelper {

    private final FeatureFlagDAO featureFlagDAO;

    @Autowired
    public FeatureFlagHelper(FeatureFlagDAO featureFlagDAO) {
        this.featureFlagDAO = featureFlagDAO;
    }

    /**
     * Gets the value of a feature flag.
     * @param flagName The name of the feature flag
     * @return The boolean value of the flag
     */
    public boolean getFlagValue(String flagName) {
        return featureFlagDAO.getFlagValue(flagName);
    }
}

