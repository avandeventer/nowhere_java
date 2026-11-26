package client.nowhere.helper;

import client.nowhere.dao.AdventureMapDAO;
import client.nowhere.dao.UserProfileDAO;
import client.nowhere.model.AdventureMap;
import client.nowhere.model.ProfileAdventureMap;
import client.nowhere.model.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Keeps the key ("master") adventure maps in step across profiles.
 *
 * A master profile edits a master map through the /user-profile/{id}/adventure-map endpoints;
 * publish copies the result to the global /maps/{adventureId} document with a higher version.
 * When any profile logs in, syncOnLogin replaces that profile's copy of each master map whose
 * version is behind the global one, or adds the map if the profile doesn't have it. Only
 * maps.{adventureId}.adventureMap is written, so save games and the profile's other maps are kept.
 *
 * Master profiles are synced on login too: with several masters, each must start from the others'
 * latest edits, or publishing from an old copy would undo them. A master's own copy never holds
 * unpublished edits (every master edit publishes), so the sync can't overwrite their work.
 */
@Component
public class MasterAdventureMapSyncHelper {

    private static final Logger logger = LoggerFactory.getLogger(MasterAdventureMapSyncHelper.class);

    private final AdventureMapDAO adventureMapDAO;
    private final UserProfileDAO userProfileDAO;
    private final List<String> masterProfileIds;
    private final List<String> masterAdventureIds;

    @Autowired
    public MasterAdventureMapSyncHelper(
            AdventureMapDAO adventureMapDAO,
            UserProfileDAO userProfileDAO,
            @Value("${nowhere.master-maps.master-profile-ids:}") String masterProfileIds,
            @Value("${nowhere.master-maps.adventure-ids:}") String masterAdventureIds
    ) {
        this.adventureMapDAO = adventureMapDAO;
        this.userProfileDAO = userProfileDAO;
        this.masterProfileIds = parseIds(masterProfileIds);
        this.masterAdventureIds = parseIds(masterAdventureIds);
    }

    /** A comma-separated property value as a list of trimmed, non-empty ids. */
    private static List<String> parseIds(String commaSeparatedIds) {
        if (commaSeparatedIds == null) {
            return List.of();
        }
        return Arrays.stream(commaSeparatedIds.split(","))
                .map(String::trim)
                .filter(id -> !id.isEmpty())
                .toList();
    }

    /** True when a master profile is editing one of the master maps. */
    public boolean isMasterEdit(String userProfileId, String adventureId) {
        return masterProfileIds.contains(userProfileId)
                && masterAdventureIds.contains(adventureId);
    }

    /**
     * Writes a master profile's copy of a master map to /maps with the next version, and the
     * same versioned map back to the master profile so its copy counts as current.
     * Returns the versioned map.
     */
    public AdventureMap publish(String masterUserProfileId, AdventureMap profileMap) {
        AdventureMap globalMap = adventureMapDAO.findGlobal(profileMap.getAdventureId());
        long globalVersion = globalMap == null ? 0 : globalMap.getVersion();
        profileMap.setVersion(Math.max(globalVersion, profileMap.getVersion()) + 1);

        adventureMapDAO.setGlobal(profileMap);
        userProfileDAO.replaceProfileAdventureMap(masterUserProfileId, profileMap);
        logger.info("Published master adventure map {} at version {}", profileMap.getAdventureId(), profileMap.getVersion());
        return profileMap;
    }

    /**
     * Brings userProfile's copies of the master maps up to the global versions, in Firestore and
     * in the userProfile object itself so the caller returns the updated maps. A failure is logged
     * and skipped: it never stops the user from logging in.
     */
    public void syncOnLogin(UserProfile userProfile) {
        if (userProfile == null) {
            return;
        }

        for (String adventureId : masterAdventureIds) {
            try {
                syncMap(userProfile, adventureId);
            } catch (RuntimeException e) {
                logger.error("Could not sync master adventure map {} to profile {}", adventureId, userProfile.getId(), e);
            }
        }
    }

    private void syncMap(UserProfile userProfile, String adventureId) {
        AdventureMap globalMap = adventureMapDAO.findGlobal(adventureId);
        if (globalMap == null) {
            logger.warn("Master adventure map {} has no global document; skipping sync", adventureId);
            return;
        }

        if (userProfile.getMaps() == null) {
            userProfile.setMaps(new HashMap<>());
        }
        ProfileAdventureMap profileMap = userProfile.getMaps().get(adventureId);

        if (profileMap == null) {
            ProfileAdventureMap added = userProfileDAO.addAdventureMap(userProfile.getId(), globalMap);
            userProfile.upsertProfileAdventureMap(added);
            return;
        }

        AdventureMap existing = profileMap.getAdventureMap();
        if (existing != null && existing.getVersion() >= globalMap.getVersion()) {
            return;
        }

        userProfileDAO.replaceProfileAdventureMap(userProfile.getId(), globalMap);
        profileMap.setAdventureMap(globalMap);
    }
}
