package client.nowhere.helper;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

import client.nowhere.constants.AuthorConstants;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.dao.UserProfileDAO;
import client.nowhere.factory.MutexFactory;
import client.nowhere.model.*;
import com.google.cloud.firestore.Transaction;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.springframework.beans.factory.annotation.Autowired;

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

    @Spy
    private MutexFactory<String> mutexFactory = new MutexFactory<>();

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

        when(gameSessionDAO.runInTransaction(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Transaction.Function<Story> function = invocation.getArgument(0);
            return function.updateCallback(mock(Transaction.class));
        });

        when(gameSessionDAO.getGameInTransaction(eq(gameCode), any()))
                .thenReturn(mockGameSession);
        when(storyDAO.getStories(gameCode)).thenReturn(new ArrayList<>()); // No game session stories
        when(storyDAO.getPlayerStories(gameCode, playerId, locationId)).thenReturn(new ArrayList<>()); // No player stories
        when(userProfileDAO.getRegularSaveGameStories(mockGameSession, locationId)).thenReturn(new ArrayList<>()); // No global stories

        Story result = storyHelper.storePlayerStory(gameCode, playerId, locationId);

        assertNotNull(result);
        assertEquals(gameCode, result.getGameCode());
        assertEquals(locationId, result.getLocation().getLocationId());

        List<Story> expectedUpdatedStories = mockGameSession.getStories();
        expectedUpdatedStories.add(result);
        verify(gameSessionDAO).updateStoriesInTransaction(eq(gameCode), eq(expectedUpdatedStories), any());
    }

    @ParameterizedTest
    @MethodSource("provideSequelStoryScenarios")
    void testGetPlayerStory_whenSequelStoriesExist_returnsFirstSequel(
            List<Story> gameSessionStories,
            List<Story> globalSequelPlayerStories,
            String expectedStoryId,
            boolean expectSearchForSaveGameSequels,
            List<Story> regularSaveGameStories,
            boolean expectNewSequelStory,
            boolean expectNewSequelStoryFiltering
    ) {
        // Arrange
        String gameCode = "GAME123";
        String playerId = "PLAYER123";
        int locationId = 1;

        GameSession mockGameSession = new GameSession(gameCode);
        mockGameSession.setUserProfileId("USER_PROFILE_ID");
        mockGameSession.setSaveGameId("SAVE_GAME_ID");
        mockGameSession.setStories(gameSessionStories);

        when(gameSessionDAO.runInTransaction(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            Transaction.Function<Story> function = invocation.getArgument(0);
            return function.updateCallback(mock(Transaction.class));
        });

        when(gameSessionDAO.getGameInTransaction(eq(gameCode), any()))
                .thenReturn(mockGameSession);
        when(storyDAO.getStories(gameCode)).thenReturn(gameSessionStories);
        when(userProfileDAO.getSaveGameSequelStories(
                eq(mockGameSession.getUserProfileId()),
                eq(mockGameSession.getAdventureMap().getAdventureId()),
                eq(mockGameSession.getSaveGameId()),
                any()
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
            verify(userProfileDAO, times(1)).getSaveGameSequelStories(
                    mockGameSession.getUserProfileId(),
                    mockGameSession.getAdventureMap().getAdventureId(),
                    mockGameSession.getSaveGameId(),
                    mockGameSession.getStories()
            );
        } else if (expectNewSequelStoryFiltering) {
            verify(userProfileDAO, times(1)).getSaveGameSequelStories(
                    eq(mockGameSession.getUserProfileId()),
                    eq(mockGameSession.getAdventureMap().getAdventureId()),
                    eq(mockGameSession.getSaveGameId()),
                    any()
            );
        } else {
            verify(userProfileDAO, never()).getSaveGameSequelStories(any(), any(), any(), any());
        }

        if (expectNewSequelStory && !result.getPrequelStoryId().isEmpty()) {
            assertTrue(result.isSequelStory());
            assertEquals("selectedOptionId", result.getPrequelStorySelectedOptionId());
            if (!result.getPrequelStoryPlayerId().isEmpty()) {
                assertEquals(result.getPlayerId(), result.getPrequelStoryPlayerId());
            }
        } else {
            assertEquals("", result.getPrequelStoryPlayerId());
            assertFalse(result.isSequelStory());
            assertEquals("", result.getPrequelStorySelectedOptionId());
        }

        if (regularSaveGameStories.size() > 0) {
            verify(userProfileDAO).getRegularSaveGameStories(mockGameSession, locationId);
        }
        assertEquals(1, result.getLocation().getLocationId());
        List<Story> expectedUpdatedStories = new ArrayList<>(gameSessionStories);
        expectedUpdatedStories.add(result);
        verify(gameSessionDAO).updateStoriesInTransaction(eq(gameCode), eq(expectedUpdatedStories), any());
    }

    static Stream<Arguments> provideSequelStoryScenarios() {
        String playerId = "PLAYER123";

        return Stream.of(
                // Case 1: Single sequel story exists
                Arguments.of(
                        createStories(
                                createVisitedStory(playerId, 1,  "VISITED", new SequelKey("selectedOptionId", false)),
                                createUnwrittenStory(playerId)
                        ),
                        List.of(createPlayerSequelStory(new SequelKey("selectedOptionId", false))),
                        "PLAYER_SEQUEL",
                        true,
                        new ArrayList<>(),
                        true,
                        false
                ),
                // Case 2: Location AND player sequel exist, pick player sequel
                Arguments.of(
                        createStories(
                                createVisitedStory(playerId, 1, "VISITED", new SequelKey("selectedOptionId", false)),
                                createUnwrittenStory(playerId)
                        ),
                        List.of(
                                createLocationSequelStory(new SequelKey("selectedOptionId", false)),
                                createPlayerSequelStory(new SequelKey("selectedOptionId", false))
                        ),
                        "PLAYER_SEQUEL",
                        true,
                        new ArrayList<>(),
                        true,
                        false
                ),
                // Case 3: Only location sequel exists
                Arguments.of(
                        createStories(
                                createVisitedStory(playerId, 1, "VISITED", new SequelKey("selectedOptionId", false)),
                                createUnwrittenStory(playerId)
                        ),
                        List.of(createLocationSequelStory(new SequelKey("selectedOptionId", false))),
                        "LOCATION_SEQUEL",
                        true,
                        new ArrayList<>(),
                        true,
                        false
                ),
                // Case 4: Only player sequel exists
                Arguments.of(
                        createStories(
                                createVisitedStory(playerId, 1, "VISITED", new SequelKey("selectedOptionId", false)),
                                createUnwrittenStory(playerId)
                        ),
                        List.of(createPlayerSequelStory(new SequelKey("selectedOptionId", false))),
                        "PLAYER_SEQUEL",
                        true,
                        new ArrayList<>(),
                        true,
                        false
                ),
                // Case 5: Unwritten stories at max, no sequels at all, defaults to creating another unwritten story
                Arguments.of(
                        createStories(
                                createVisitedStory(playerId, 1, "VISITED", new SequelKey("selectedOptionId", false)),
                                createUnwrittenStory(playerId)
                        ),
                        List.of(),
                        "",
                        true,
                        new ArrayList<>(),
                        true,
                        false
                ),
                // Case 6: Unwritten stories at max, no sequels available, search for regular save game story
                Arguments.of(
                        createStories(createUnwrittenStory(playerId), createVisitedStory(playerId, 1, "VISITED", new SequelKey("selectedOptionId", false))),
                        List.of(),
                        "REGULAR_SAVE_GAME",
                        true,
                        List.of(createRegularSaveGameStory(1), createRegularSaveGameStory(1)),
                        false,
                        false
                ),
                // Case 7: No unwritten stories for player yet, new story generated
                Arguments.of(
                        createStories(createLocationSequelStory(new SequelKey("selectedOptionId", true))),
                        List.of(),
                        "",
                        false,
                        new ArrayList<>(),
                        true,
                        false
                ),
                // Case 8: No unwritten stories for player yet, new story generated, in game sequel filtered and save game sequel filtered
                Arguments.of(
                        createStories(
                                createVisitedStory("someOtherPlayer", 1, "VISITED1", new SequelKey("selectedOptionId1", false)),
                                createVisitedStory("someOtherPlayer", 1, "VISITED", new SequelKey("selectedOptionId2", false)),
                                createVisitedStory(playerId, 2, "VISITED2", new SequelKey("selectedOptionId", true)),
                                createLocationSequelStory(new SequelKey("selectedOptionId2", false))
                        ),
                        List.of(
                                createLocationSequelStory(new SequelKey("selectedOptionId2", true)),
                                createPlayerSequelStory(new SequelKey("selectedOptionId1", false))
                        ),
                        "",
                        false,
                        new ArrayList<>(),
                        true,
                        true
                ),
                // Case 9: No unwritten stories for player yet, new story generated, all prequels already used
                Arguments.of(
                        createStories(
                                createVisitedStory("someOtherPlayer", 1, "VISITED", new SequelKey("selectedOptionId", false)),
                                createVisitedStory(playerId, 2, "VISITED", new SequelKey("selectedOptionId", true)),
                                createLocationSequelStory(new SequelKey("selectedOptionId", true))
                        ),
                        List.of(),
                        "",
                        false,
                        new ArrayList<>(),
                        false,
                        false
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

    private static Story createVisitedStory(String playerId, int locationId, String storyId, SequelKey sequelKey) {
        Story story = new Story();
        story.setStoryId("VISITED");
        if (!storyId.isEmpty()) {
            story.setStoryId(storyId);
        }
        story.setPrequelStorySucceeded(sequelKey.isSucceeded());
        story.setPlayerId(playerId);
        story.setLocation(new AdventureMap().getLocations().get(locationId));
        story.setSelectedOptionId(sequelKey.getSelectedOptionId());
        story.setVisited(true);
        return story;
    }

    private static Story createPlayerSequelStory(SequelKey prequelKey) {
        Story story = new Story();
        story.setStoryId("PLAYER_SEQUEL");
        story.setPrequelStoryPlayerId(AuthorConstants.GLOBAL_PLAYER_SEQUEL);
        story.setPrequelStorySelectedOptionId(prequelKey.getSelectedOptionId());
        story.setPrequelStorySucceeded(prequelKey.isSucceeded());
        story.setPrequelStoryId("VISITED");
        return story;
    }

    private static Story createLocationSequelStory(SequelKey prequelKey) {
        Story story = new Story();
        story.setStoryId("LOCATION_SEQUEL");
        story.setLocation(new AdventureMap().getLocations().get(1));
        story.setPrequelStoryId("VISITED");
        story.setPrequelStorySelectedOptionId(prequelKey.getSelectedOptionId());
        story.setPrequelStorySucceeded(prequelKey.isSucceeded());
        story.setOptions(Arrays.asList(new Option(), new Option()));
        story.setSelectedOptionId(story.getOptions().get(0).getOptionId());
        return story;
    }
}
