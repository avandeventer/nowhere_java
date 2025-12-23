package client.nowhere.controller;

import client.nowhere.helper.FeatureFlagHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(maxAge = 3600)
@RestController
@RequestMapping("/featureFlags")
public class FeatureFlagController {

    private final FeatureFlagHelper featureFlagHelper;

    @Autowired
    public FeatureFlagController(FeatureFlagHelper featureFlagHelper) {
        this.featureFlagHelper = featureFlagHelper;
    }

    /**
     * Gets the value of a feature flag.
     * @param flagName The name of the feature flag
     * @return The boolean value of the flag
     */
    @GetMapping("/value")
    @ResponseBody
    public boolean getFlagValue(@RequestParam String flagName) {
        return featureFlagHelper.getFlagValue(flagName);
    }
}

