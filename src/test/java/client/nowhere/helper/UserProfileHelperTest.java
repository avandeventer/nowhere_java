package client.nowhere.helper;

import java.util.HashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import client.nowhere.dao.UserProfileDAO;
import client.nowhere.exception.ResourceException;
import client.nowhere.model.AdventureMap;
import client.nowhere.model.GameSession;
import client.nowhere.model.ProfileAdventureMap;
import client.nowhere.model.UserProfile;

public class UserProfileHelperTest {

    @Mock
    private UserProfileDAO userProfileDAO;

    @InjectMocks
    private UserProfileHelper userProfileHelper;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @ParameterizedTest
    @MethodSource("provideSaveGameSessionAdventureMapScenarios")
    void testSaveGameSessionAdventureMapToUserProfile(
            String scenarioName,
            String adventureId,
            String userId,
            boolean adventureMapExists,
            String expectedSaveGameId
    ) {
        // Arrange
        GameSession gameSession = createGameSession(adventureId, userId);
        UserProfile userProfile = createUserProfile(userId, adventureId, adventureMapExists);

        when(userProfileDAO.get(userId)).thenReturn(userProfile);
        
        if (!adventureMapExists) {
            ProfileAdventureMap expectedProfileAdventureMap = new ProfileAdventureMap(gameSession.getAdventureMap());
            when(userProfileDAO.addAdventureMap(eq(userId), any(AdventureMap.class)))
                .thenReturn(expectedProfileAdventureMap);
        }

        // Act
        String result = userProfileHelper.saveGameSessionAdventureMapToUserProfile(gameSession);

        // Assert
        if (!adventureMapExists) {
            verify(userProfileDAO).addAdventureMap(eq(userId), any(AdventureMap.class));
            assertNotNull(result);
            // Don't check for exact match since the save game ID is generated
            // Just verify it's not null and not empty
            assertTrue(!result.isEmpty());
        } else {
            verify(userProfileDAO, never()).addAdventureMap(anyString(), any(AdventureMap.class));
            assertEquals("", result);
        }
    }

    @Test
    void testSaveGameSessionAdventureMapToUserProfile_NullAdventureMap() {
        // Arrange
        GameSession gameSession = createGameSession("test-adventure", "user-123");
        gameSession.setAdventureMap(null);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            userProfileHelper.saveGameSessionAdventureMapToUserProfile(gameSession);
        });
    }

    @Test
    void testSaveGameSessionAdventureMapToUserProfile_NullUserProfileId() {
        // Arrange
        GameSession gameSession = createGameSession("test-adventure", null);

        // Act & Assert
        assertThrows(NullPointerException.class, () -> {
            userProfileHelper.saveGameSessionAdventureMapToUserProfile(gameSession);
        });
    }

    @Test
    void testSaveGameSessionAdventureMapToUserProfile_EmptyUserProfileId() {
        // Arrange
        GameSession gameSession = createGameSession("test-adventure", "");
        UserProfile userProfile = createUserProfile("", "test-adventure", false);
        ProfileAdventureMap expectedProfileAdventureMap = new ProfileAdventureMap(gameSession.getAdventureMap());
        
        when(userProfileDAO.get("")).thenReturn(userProfile);
        when(userProfileDAO.addAdventureMap(eq(""), any(AdventureMap.class))).thenReturn(expectedProfileAdventureMap);

        // Act
        String result = userProfileHelper.saveGameSessionAdventureMapToUserProfile(gameSession);

        // Assert
        // Empty string is valid, so it should call the DAO and return a save game ID
        verify(userProfileDAO).get("");
        verify(userProfileDAO).addAdventureMap(eq(""), any(AdventureMap.class));
        assertNotNull(result);
        assertTrue(!result.isEmpty());
    }

    @Test
    void testSaveGameSessionAdventureMapToUserProfile_UserProfileNotFound() {
        // Arrange
        GameSession gameSession = createGameSession("test-adventure", "user-123");
        
        when(userProfileDAO.get("user-123")).thenThrow(new ResourceException("User profile not found"));

        // Act & Assert
        assertThrows(ResourceException.class, () -> {
            userProfileHelper.saveGameSessionAdventureMapToUserProfile(gameSession);
        });
    }

    private static Stream<Arguments> provideSaveGameSessionAdventureMapScenarios() {
        return Stream.of(
            Arguments.of(
                "Adventure map does NOT exist in user profile",
                "test-adventure-123",
                "user-123",
                false, // adventureMapExists
                "any-save-game-id" // expectedSaveGameId (not used for exact comparison)
            ),
            Arguments.of(
                "Adventure map DOES exist in user profile",
                "existing-adventure-456",
                "user-456",
                true, // adventureMapExists
                ""  // expectedSaveGameId (empty string when not added)
            )
        );
    }

    private GameSession createGameSession(String adventureId, String userId) {
        GameSession gameSession = new GameSession();
        gameSession.setGameCode("test123");
        gameSession.setUserProfileId(userId);
        
        AdventureMap adventureMap = new AdventureMap();
        adventureMap.setAdventureId(adventureId);
        gameSession.setAdventureMap(adventureMap);
        
        return gameSession;
    }

    private UserProfile createUserProfile(String userId, String adventureId, boolean adventureMapExists) {
        UserProfile userProfile = new UserProfile();
        userProfile.setId(userId);
        userProfile.setMaps(new HashMap<>());
        
        if (adventureMapExists) {
            AdventureMap adventureMap = new AdventureMap();
            adventureMap.setAdventureId(adventureId);
            ProfileAdventureMap profileAdventureMap = new ProfileAdventureMap(adventureMap);
            userProfile.getMaps().put(adventureId, profileAdventureMap);
        }
        
        return userProfile;
    }
}
