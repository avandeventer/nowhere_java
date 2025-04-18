package client.nowhere.helper;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import client.nowhere.constants.AuthorConstants;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.dao.UserProfileDAO;
import client.nowhere.model.AdventureMap;
import client.nowhere.model.GameSession;
import client.nowhere.model.Story;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.*;
import java.util.stream.Collectors;
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
        when(userProfileDAO.getSaveGameStories(mockGameSession, locationId)).thenReturn(new ArrayList<>()); // No global stories

        Story result = storyHelper.getPlayerStory(gameCode, playerId, locationId, mockGameSession);

        assertNotNull(result);
        assertEquals(gameCode, result.getGameCode());
        assertEquals(locationId, result.getLocation().getLocationId());
        verify(storyDAO).createStory(any(Story.class)); // Ensure a default story was created
        verify(storyDAO).updateStory(result); // Ensure the story was updated
    }

    @ParameterizedTest
    @MethodSource("provideSequelStoryScenarios")
    void testGetPlayerStory_whenSequelStoriesExist_returnsFirstSequel(
            List<Story> gameSessionStories,
            List<Story> globalSequelPlayerStories,
            String expectedStoryId,
            boolean expectCreateStory
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
                gameSessionStories.stream()
                        .filter(gameSessionStory -> !gameSessionStory.getSelectedOptionId().isEmpty())
                        .map(Story::getStoryId)
                        .collect(Collectors.toList()))
        ).thenReturn(globalSequelPlayerStories);

        // Act
        Story result = storyHelper.getPlayerStory(gameCode, playerId, locationId, mockGameSession);

        // Assert
        assertNotNull(result);
        if(!expectedStoryId.equals("DEFAULT")) {
            assertEquals(expectedStoryId, result.getStoryId());
        }

        if (expectCreateStory) {
            verify(storyDAO).createStory(any(Story.class));
        } else {
            verify(storyDAO, never()).createStory(any(Story.class));
        }
        assertEquals(1, result.getLocation().getLocationId());
        verify(storyDAO).updateStory(result);
    }

    static Stream<Arguments> provideSequelStoryScenarios() {
        String playerId = "PLAYER123";

        return Stream.of(
                // Case 1: Single sequel story exists
                Arguments.of(
                        createStories(
                                createVisitedStory(playerId),
                                createPlayerSequelStory(playerId)
                        ),
                        List.of(),
                        "PLAYER_SEQUEL",
                        false
                ),
                // Case 2: Location AND player sequel exist, pick player sequel
                Arguments.of(
                        createStories(
                                createVisitedStory(playerId),
                                createLocationSequelStory(),
                                createPlayerSequelStory(playerId)
                        ),
                        List.of(),
                        "PLAYER_SEQUEL",
                        false
                ),
                // Case 3: No sequels in game session, but sequel in global
                Arguments.of(
                        createStories(
                                createVisitedStory(playerId)
                        ),
                        List.of(createLocationSequelStory()),
                        "LOCATION_SEQUEL",
                        true
                ),
                // Case 4: No sequels in game session, player sequel in global with GLOBAL player author constant
                Arguments.of(
                        createStories(
                                createVisitedStory(playerId)
                        ),
                        List.of(createPlayerSequelStory(AuthorConstants.GLOBAL_PLAYER_SEQUEL)),
                        "PLAYER_SEQUEL",
                        true
                ),
                // Case 5: No sequels at all, return a default story
                Arguments.of(
                        createStories(
                                createVisitedStory(playerId)
                        ),
                        List.of(),
                        "DEFAULT",
                        true
                )
        );
    }

    private static List<Story> createStories(Story... stories) {
        return Arrays.asList(stories);
    }

    private static Story createVisitedStory(String playerId) {
        Story story = new Story();
        story.setStoryId("VISITED");
        story.setPlayerId(playerId);
        story.setSelectedOptionId("OPTION2");
        story.setVisited(true);
        return story;
    }

    private static Story createPlayerSequelStory(String playerId) {
        Story story = new Story();
        story.setStoryId("PLAYER_SEQUEL");
        story.setPrequelStoryPlayerId(playerId);
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
