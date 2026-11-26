package client.nowhere.helper;

import client.nowhere.dao.AdventureMapDAO;
import client.nowhere.dao.UserProfileDAO;
import client.nowhere.exception.ResourceException;
import client.nowhere.model.AdventureMap;
import client.nowhere.model.ProfileAdventureMap;
import client.nowhere.model.SaveGame;
import client.nowhere.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class MasterAdventureMapSyncHelperTest {

    private static final String MASTER_ID = "master-profile";
    private static final String SECOND_MASTER_ID = "second-master-profile";
    private static final String MASTER_MAP_ID = "master-map";
    private static final String USER_ID = "user-1";

    @Mock
    private AdventureMapDAO adventureMapDAO;

    @Mock
    private UserProfileDAO userProfileDAO;

    private MasterAdventureMapSyncHelper helper;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
        helper = new MasterAdventureMapSyncHelper(adventureMapDAO, userProfileDAO, MASTER_ID + ", " + SECOND_MASTER_ID, " " + MASTER_MAP_ID + " , other-map");
    }

    @Test
    void isMasterEdit_onlyForMasterProfilesAndConfiguredMaps() {
        assertTrue(helper.isMasterEdit(MASTER_ID, MASTER_MAP_ID));
        assertTrue(helper.isMasterEdit(SECOND_MASTER_ID, MASTER_MAP_ID));
        assertTrue(helper.isMasterEdit(MASTER_ID, "other-map"));
        assertFalse(helper.isMasterEdit(USER_ID, MASTER_MAP_ID));
        assertFalse(helper.isMasterEdit(MASTER_ID, "unlisted-map"));
    }

    @Test
    void isMasterEdit_falseWhenNoMasterConfigured() {
        MasterAdventureMapSyncHelper unconfigured = new MasterAdventureMapSyncHelper(adventureMapDAO, userProfileDAO, "", "");
        assertFalse(unconfigured.isMasterEdit("", ""));
        assertFalse(unconfigured.isMasterEdit(MASTER_ID, MASTER_MAP_ID));
    }

    @Test
    void publish_bumpsVersionPastGlobalAndWritesGlobalAndMasterProfile() {
        when(adventureMapDAO.findGlobal(MASTER_MAP_ID)).thenReturn(map(MASTER_MAP_ID, 4));
        AdventureMap edited = map(MASTER_MAP_ID, 2);

        AdventureMap published = helper.publish(MASTER_ID, edited);

        assertEquals(5, published.getVersion());
        verify(adventureMapDAO).setGlobal(edited);
        verify(userProfileDAO).replaceProfileAdventureMap(MASTER_ID, edited);
    }

    @Test
    void publish_startsAtOneWhenNoGlobalMapExists() {
        when(adventureMapDAO.findGlobal(MASTER_MAP_ID)).thenReturn(null);

        AdventureMap published = helper.publish(MASTER_ID, map(MASTER_MAP_ID, 0));

        assertEquals(1, published.getVersion());
    }

    @Test
    void syncOnLogin_replacesOlderCopyAndKeepsSaveGames() {
        AdventureMap globalMap = map(MASTER_MAP_ID, 3);
        globalMap.setName("New name");
        when(adventureMapDAO.findGlobal(MASTER_MAP_ID)).thenReturn(globalMap);
        UserProfile profile = profileWith(MASTER_MAP_ID, 1);
        SaveGame saveGame = profile.getMaps().get(MASTER_MAP_ID).getSaveGames().values().iterator().next();
        profile.getMaps().put("custom-map", new ProfileAdventureMap(map("custom-map", 0)));

        helper.syncOnLogin(profile);

        verify(userProfileDAO).replaceProfileAdventureMap(USER_ID, globalMap);
        ProfileAdventureMap synced = profile.getMaps().get(MASTER_MAP_ID);
        assertSame(globalMap, synced.getAdventureMap());
        assertEquals(1, synced.getSaveGames().size());
        assertSame(saveGame, synced.getSaveGames().get(saveGame.getId()));
        assertTrue(profile.getMaps().containsKey("custom-map"));
        verify(userProfileDAO, never()).addAdventureMap(anyString(), any());
    }

    @Test
    void syncOnLogin_noWriteWhenCopyIsCurrent() {
        when(adventureMapDAO.findGlobal(MASTER_MAP_ID)).thenReturn(map(MASTER_MAP_ID, 3));
        UserProfile profile = profileWith(MASTER_MAP_ID, 3);

        helper.syncOnLogin(profile);

        verify(userProfileDAO, never()).replaceProfileAdventureMap(anyString(), any());
        verify(userProfileDAO, never()).addAdventureMap(anyString(), any());
    }

    @Test
    void syncOnLogin_addsMapWhenProfileHasNone() {
        AdventureMap globalMap = map(MASTER_MAP_ID, 2);
        when(adventureMapDAO.findGlobal(MASTER_MAP_ID)).thenReturn(globalMap);
        ProfileAdventureMap added = new ProfileAdventureMap(globalMap);
        when(userProfileDAO.addAdventureMap(USER_ID, globalMap)).thenReturn(added);
        UserProfile profile = new UserProfile();
        profile.setId(USER_ID);

        helper.syncOnLogin(profile);

        verify(userProfileDAO).addAdventureMap(USER_ID, globalMap);
        assertSame(added, profile.getMaps().get(MASTER_MAP_ID));
    }

    @Test
    void syncOnLogin_bringsMasterUpToAnotherMastersEdits() {
        AdventureMap globalMap = map(MASTER_MAP_ID, 5);
        when(adventureMapDAO.findGlobal(MASTER_MAP_ID)).thenReturn(globalMap);
        UserProfile master = profileWith(MASTER_MAP_ID, 4);
        master.setId(SECOND_MASTER_ID);

        helper.syncOnLogin(master);

        verify(userProfileDAO).replaceProfileAdventureMap(SECOND_MASTER_ID, globalMap);
    }

    @Test
    void syncOnLogin_skipsMissingGlobalMap() {
        when(adventureMapDAO.findGlobal(anyString())).thenReturn(null);

        helper.syncOnLogin(profileWith(MASTER_MAP_ID, 0));

        verifyNoInteractions(userProfileDAO);
    }

    @Test
    void syncOnLogin_failureOnOneMapDoesNotStopLoginOrOtherMaps() {
        when(adventureMapDAO.findGlobal(MASTER_MAP_ID)).thenThrow(new ResourceException("boom"));
        AdventureMap otherGlobal = map("other-map", 1);
        when(adventureMapDAO.findGlobal("other-map")).thenReturn(otherGlobal);
        when(userProfileDAO.addAdventureMap(eq(USER_ID), any())).thenReturn(new ProfileAdventureMap(otherGlobal));
        UserProfile profile = profileWith(MASTER_MAP_ID, 0);

        assertDoesNotThrow(() -> helper.syncOnLogin(profile));

        verify(userProfileDAO).addAdventureMap(USER_ID, otherGlobal);
    }

    @Test
    void syncOnLogin_nullProfileIsIgnored() {
        assertDoesNotThrow(() -> helper.syncOnLogin(null));
        verifyNoInteractions(adventureMapDAO, userProfileDAO);
    }

    private static AdventureMap map(String adventureId, long version) {
        AdventureMap adventureMap = new AdventureMap();
        adventureMap.setAdventureId(adventureId);
        adventureMap.setVersion(version);
        return adventureMap;
    }

    private static UserProfile profileWith(String adventureId, long version) {
        UserProfile profile = new UserProfile();
        profile.setId(USER_ID);
        profile.getMaps().put(adventureId, new ProfileAdventureMap(map(adventureId, version)));
        return profile;
    }
}
