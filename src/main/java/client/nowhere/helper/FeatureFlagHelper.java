package client.nowhere.helper;

import client.nowhere.dao.FeatureFlagDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FeatureFlagHelper {

    // Flags are checked on most collaborative-text requests, and each check was a Firestore read
    // (~17ms). A changed flag reaches every instance within this long.
    static final Duration CACHE_TTL = Duration.ofSeconds(60);

    private final FeatureFlagDAO featureFlagDAO;
    private final Clock clock;
    private final Map<String, CachedFlag> cache = new ConcurrentHashMap<>();

    @Autowired
    public FeatureFlagHelper(FeatureFlagDAO featureFlagDAO) {
        this(featureFlagDAO, Clock.systemUTC());
    }

    FeatureFlagHelper(FeatureFlagDAO featureFlagDAO, Clock clock) {
        this.featureFlagDAO = featureFlagDAO;
        this.clock = clock;
    }

    /**
     * Gets the value of a feature flag, from this instance's cache when it was read less than
     * CACHE_TTL ago.
     * @param flagName The name of the feature flag
     * @return The boolean value of the flag
     */
    public boolean getFlagValue(String flagName) {
        Instant now = clock.instant();
        CachedFlag cached = cache.get(flagName);
        if (cached != null && now.isBefore(cached.expiresAt())) {
            return cached.value();
        }
        boolean value = Boolean.TRUE.equals(featureFlagDAO.getFlagValue(flagName));
        cache.put(flagName, new CachedFlag(value, now.plus(CACHE_TTL)));
        return value;
    }

    private record CachedFlag(boolean value, Instant expiresAt) { }
}
