package client.nowhere.helper;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import client.nowhere.constants.AuthorConstants;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.dao.UserProfileDAO;
import client.nowhere.model.AdventureMap;
import client.nowhere.model.GameSession;
import client.nowhere.model.Location;
import client.nowhere.model.Story;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.stream.Stream;

class StoryHelperTest {

    @Mock
    private StoryDAO storyDAO;

    @Mock
    private GameSessionDAO gameSessionDAO;

    @Mock
    private UserProfileDAO userProfileDAO;

    @InjectMocks
    private StoryHelper storyHelper;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetPlayerStory_whenNoSequelOrPlayerOrGlobalStories_createsDefaultStory() {
        String gameCode = "GAME123";
        String playerId = "PLAYER123";
        int locationId = 1;

        GameSession mockGameSession = new GameSession(gameCode);
        mockGameSession.setUserProfileId("USER_PROFILE_ID");
        mockGameSession.setSaveGameId("SAVE_GAME_ID");
        mockGameSession.setStories(new ArrayList<>());

        when(gameSessionDAO.getGame(gameCode)).thenReturn(mockGameSession);
        when(storyDAO.getStories(gameCode)).thenReturn(new ArrayList<>()); // No game session stories
        when(storyDAO.getPlayerStories(gameCode, playerId, locationId)).thenReturn(new ArrayList<>()); // No player stories
        when(userProfileDAO.getRegularSaveGameStories(mockGameSession, locationId)).thenReturn(new ArrayList<>()); // No global stories

        Story result = storyHelper.storePlayerStory(gameCode, playerId, locationId);

        assertNotNull(result);
        assertEquals(gameCode, result.getGameCode());
        assertEquals(locationId, result.getLocation().getLocationId());
        verify(storyDAO).createStory(any(Story.class)); // Ensure a default story was created
    }

    @ParameterizedTest
    @MethodSource("provideSequelStoryScenarios")
    void testGetPlayerStory_whenSequelStoriesExist_returnsFirstSequel(
            List<Story> gameSessionStories,
            List<Story> globalSequelPlayerStories,
            String expectedStoryId,
            boolean expectSearchForSaveGameSequels,
            List<Story> regularSaveGameStories
    ) {
        // Arrange
        String gameCode = "GAME123";
        String playerId = "PLAYER123";
        int locationId = 1;

        GameSession mockGameSession = new GameSession(gameCode);
        mockGameSession.setUserProfileId("USER_PROFILE_ID");
        mockGameSession.setSaveGameId("SAVE_GAME_ID");
        mockGameSession.setStories(gameSessionStories);

        when(gameSessionDAO.getGame(gameCode)).thenReturn(mockGameSession);
        when(storyDAO.getStories(gameCode)).thenReturn(gameSessionStories);
        when(userProfileDAO.getSaveGameSequelStories(
                mockGameSession.getUserProfileId(),
                mockGameSession.getAdventureMap().getAdventureId(),
                mockGameSession.getSaveGameId(),
                mockGameSession.getStories()
            )).thenReturn(globalSequelPlayerStories);

        when(userProfileDAO.getRegularSaveGameStories
                (mockGameSession, locationId)
            ).thenReturn(regularSaveGameStories);

        // Act
        Story result = storyHelper.storePlayerStory(gameCode, playerId, locationId);

        // Assert
        assertNotNull(result);

        if(expectedStoryId.isBlank() || expectedStoryId.equals("DEFAULT")) {
            assertEquals(4, StringUtils.countMatches(result.getStoryId(), "-"));
        } else {
            assertEquals(expectedStoryId, result.getStoryId());
        }

        if (expectedStoryId.equals("DEFAULT")) {
            assertEquals(AuthorConstants.DEFAULT, result.getAuthorId());
        }

        if (expectSearchForSaveGameSequels) {
            verify(userProfileDAO).getSaveGameSequelStories(
                    mockGameSession.getUserProfileId(),
                    mockGameSession.getAdventureMap().getAdventureId(),
                    mockGameSession.getSaveGameId(),
                    mockGameSession.getStories()
            );
        } else {
            verify(userProfileDAO, never()).getSaveGameSequelStories(any(), any(), any(), any());
        }

        if (regularSaveGameStories.size() > 0) {
            verify(userProfileDAO).getRegularSaveGameStories(mockGameSession, locationId);
        }
        assertEquals(1, result.getLocation().getLocationId());
    }

    static Stream<Arguments> provideSequelStoryScenarios() {
        String playerId = "PLAYER123";

        return Stream.of(
                // Case 1: Single sequel story exists
                Arguments.of(
                        createStories(
                                createVisitedStory(playerId),
                                createUnwrittenStory(playerId)
                        ),
                        List.of(createPlayerSequelStory()),
                        "PLAYER_SEQUEL",
                        true,
                        new ArrayList<>()
                ),
                // Case 2: Location AND player sequel exist, pick player sequel
                Arguments.of(
                        createStories(
                                createVisitedStory(playerId),
                                createUnwrittenStory(playerId)
                        ),
                        List.of(
                                createLocationSequelStory(),
                                createPlayerSequelStory()
                        ),
                        "PLAYER_SEQUEL",
                        true,
                        new ArrayList<>()
                ),
                // Case 3: Only location sequel exists
                Arguments.of(
                        createStories(
                                createVisitedStory(playerId),
                                createUnwrittenStory(playerId)
                        ),
                        List.of(createLocationSequelStory()),
                        "LOCATION_SEQUEL",
                        true,
                        new ArrayList<>()
                ),
                // Case 4: Only player sequel exists
                Arguments.of(
                        createStories(
                                createVisitedStory(playerId),
                                createUnwrittenStory(playerId)
                        ),
                        List.of(createPlayerSequelStory()),
                        "PLAYER_SEQUEL",
                        true,
                        new ArrayList<>()
                ),
                // Case 5: Unwritten stories at max, no sequels at all, defaults to creating another unwritten story
                Arguments.of(
                        createStories(
                                createVisitedStory(playerId),
                                createUnwrittenStory(playerId)
                        ),
                        List.of(),
                        "",
                        true,
                        new ArrayList<>()
                ),
                // Case 6: No unwritten stories for player yet, new story generated
                Arguments.of(
                        createStories(createLocationSequelStory()),
                        List.of(),
                        "",
                        false,
                        new ArrayList<>()
                ),
                // Case 7: Unwritten stories at max, no sequels available, search for regular save game story
                Arguments.of(
                        createStories(createUnwrittenStory(playerId), createVisitedStory(playerId)),
                        List.of(),
                        "REGULAR_SAVE_GAME",
                        true,
                        List.of(createRegularSaveGameStory(1), createRegularSaveGameStory(1))
                )
        );
    }

    private static List<Story> createStories(Story... stories) {
        return Arrays.asList(stories);
    }

    private static Story createRegularSaveGameStory(int locationId) {
        AdventureMap adventureMap = new AdventureMap();
        Location defaultLocation = adventureMap.getLocations().stream().filter(location -> location.getLocationId() == locationId).findFirst().get();
        Story story = new Story();
        story.setStoryId("REGULAR_SAVE_GAME");
        story.setLocation(defaultLocation);
        return story;
    }

    private static Story createUnwrittenStory(String playerId) {
        Story story = new Story();
        story.setStoryId("UNWRITTEN");
        story.setPlayerId(playerId);
        story.setVisited(true);
        return story;
    }

    private static Story createVisitedStory(String playerId) {
        Story story = new Story();
        story.setStoryId("VISITED");
        story.setPlayerId(playerId);
        story.setSelectedOptionId("OPTION2");
        story.setVisited(true);
        return story;
    }

    private static Story createPlayerSequelStory() {
        Story story = new Story();
        story.setStoryId("PLAYER_SEQUEL");
        story.setPrequelStoryPlayerId(AuthorConstants.GLOBAL_PLAYER_SEQUEL);
        story.setPrequelStoryId("VISITED");
        return story;
    }

    private static Story createLocationSequelStory() {
        Story story = new Story();
        story.setStoryId("LOCATION_SEQUEL");
        story.setLocation(new AdventureMap().getLocations().get(1));
        story.setPrequelStoryId("VISITED");
        return story;
    }
}
