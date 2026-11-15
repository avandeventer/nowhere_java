package client.nowhere.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class StoryDAOTest {

        @Mock
        private Firestore db;

        @Mock
        private DocumentReference documentReference;

        @Mock
        private DocumentSnapshot documentSnapshot;

        @Mock
        private ApiFuture<WriteResult> apiFuture;

        @Mock
        private WriteResult writeResult;

        @InjectMocks
        private StoryDAO storyDAO; // Assuming the method is in this service

        @BeforeEach
        void setUp() {
            MockitoAnnotations.openMocks(this);
        }

        @Test
        void testUpdateStory_PlayerSucceedsAdventure() {
            // Arrange
            String gameCode = "GAME123";
            String storyIdToUpdate = "ab02d44f-1fa5-4aef-a248-a741a6a7b198";
            String selectedOptionId = "fc98977d-1ea2-4fb2-bdeb-dc974f90ec07";

            ObjectMapper objectMapper = new ObjectMapper();
            Firestore mockDb = mock(Firestore.class);
            StoryDAO storyDAO = new StoryDAO(mockDb);

            Transaction mockTransaction = setupFirestoreStoryMocks(
                    mockDb,
                    objectMapper,
                    gameCode,
                    storyIdToUpdate,
                    "src/test/resources/ROUND1_stories.json");

            // Create a story object to update
            Story storyToUpdate = new Story();
            storyToUpdate.setGameCode(gameCode);
            storyToUpdate.setStoryId(storyIdToUpdate);
            storyToUpdate.setRoundNumber(STORY_ROUND);
            storyToUpdate.setPlayerSucceeded(true);
            storyToUpdate.setSelectedOptionId(selectedOptionId);

            // Verify interactions
            try {
                // Act
                Story updatedStory = storyDAO.updateStory(storyToUpdate);

                // Assert
                assertNotNull(updatedStory);
                assertEquals(true, updatedStory.isPlayerSucceeded());
                assertEquals(selectedOptionId, updatedStory.getSelectedOptionId());

                Story expectedStory = storyFromFixture(objectMapper, "src/test/resources/ROUND1_stories_updated.json", storyIdToUpdate);
                verify(mockTransaction, times(1)).set(any(DocumentReference.class), eq(expectedStory));
            } catch (IOException e) {
                System.out.println("File failed to process" + e.getMessage());
            }
        }

    @Test
    void testUpdateStory_PlayerSequelStoryPicked() {
        // Arrange
        String gameCode = "GAME123";
        String storyIdToUpdate = "028d3e27-b6cc-410d-a278-f0febd4d7b97";
        String playerId = "PLAYER123";

        ObjectMapper objectMapper = new ObjectMapper();
        Firestore mockDb = mock(Firestore.class);
        StoryDAO storyDAO = new StoryDAO(mockDb);

        Transaction mockTransaction = setupFirestoreStoryMocks(
                mockDb,
                objectMapper,
                gameCode,
                storyIdToUpdate,
                "src/test/resources/WRITE_OPTIONS_AGAIN_stories.json"
        );

        // Create a story object to update
        Story storyToUpdate = new Story();
        storyToUpdate.setGameCode(gameCode);
        storyToUpdate.setStoryId(storyIdToUpdate);
        storyToUpdate.setRoundNumber(STORY_ROUND);

        Location townLocale = DefaultLocation.valueOf("APOTHECARY").getLocation();

        storyToUpdate.setLocation(townLocale);
        storyToUpdate.setVisited(true);
        List<Repercussion> repercussions = List.of(new Repercussion("TRAIT", "Good at Coding"));
        storyToUpdate.setRepercussions(repercussions);
        storyToUpdate.setSelectedOptionId("d274a490-52ff-48df-bf2a-4086c2b86e83");
        storyToUpdate.setPlayerId(playerId);
        Option optionOne = new Option();
        optionOne.setOptionId("d274a490-52ff-48df-bf2a-4086c2b86e83");
        optionOne.setOutcomeForks(List.of(new OutcomeFork(new TextSubmission("a80ef30c-e3fb-4a6c-99fc-bc9a39684b05", "player1", "Test test"))));
        optionOne.setSelectedForkId("a80ef30c-e3fb-4a6c-99fc-bc9a39684b05");
        Option optionTwo = new Option();
        optionTwo.setOptionId("cf70f59c-75f1-428b-a784-af1e84d2819e");

        storyToUpdate.setOptions(List.of(optionOne, optionTwo));

        // Verify interactions
        try {
            // Act
            Story updatedStory = storyDAO.updateStory(storyToUpdate);

            // Assert
            assertNotNull(updatedStory);
            assertFalse(updatedStory.isPlayerSucceeded());
            assertEquals(playerId, updatedStory.getPlayerId());

            assertEquals(townLocale, updatedStory.getLocation());
            assertEquals(repercussions, updatedStory.getRepercussions());

            assertEquals("a80ef30c-e3fb-4a6c-99fc-bc9a39684b05", updatedStory.getSelectedOption().getSelectedOutcomeFork().getTextSubmission().getSubmissionId());

            Story expectedStory = storyFromFixture(objectMapper, "src/test/resources/WRITE_OPTIONS_AGAIN_stories_updated.json", storyIdToUpdate);
            verify(mockTransaction, times(1)).set(any(DocumentReference.class), eq(expectedStory));
        } catch (IOException e) {
            System.out.println("File failed to process" + e.getMessage());
        }
    }

    @Test
    void updateStories_writesEachStoryOnceWithItsLatestVersionInOneTransaction() {
        String gameCode = "GAME123";
        Firestore mockDb = mock(Firestore.class);
        CollectionReference mockStoriesRef = mock(CollectionReference.class);
        DocumentReference firstRef = mock(DocumentReference.class);
        DocumentReference secondRef = mock(DocumentReference.class);
        when(mockDb.collection("gameSessions/" + gameCode + "/rounds/" + STORY_ROUND + "/stories")).thenReturn(mockStoriesRef);
        when(mockStoriesRef.document("first")).thenReturn(firstRef);
        when(mockStoriesRef.document("second")).thenReturn(secondRef);

        DocumentSnapshot firstSnapshot = mock(DocumentSnapshot.class);
        DocumentSnapshot secondSnapshot = mock(DocumentSnapshot.class);
        when(firstSnapshot.toObject(Story.class)).thenReturn(story(gameCode, "first", ""));
        when(secondSnapshot.toObject(Story.class)).thenReturn(story(gameCode, "second", ""));
        Transaction mockTransaction = mock(Transaction.class);
        when(mockTransaction.getAll(firstRef, secondRef)).thenReturn(ApiFutures.immediateFuture(List.of(firstSnapshot, secondSnapshot)));
        when(mockDb.runTransaction(any())).thenAnswer(invocation -> {
            Transaction.Function<?> function = invocation.getArgument(0);
            return ApiFutures.immediateFuture(function.updateCallback(mockTransaction));
        });

        // "first" is changed twice in one request; only its last version is written
        new StoryDAO(mockDb).updateStories(List.of(
                story(gameCode, "first", "PLAYER_A"),
                story(gameCode, "second", "PLAYER_B"),
                story(gameCode, "first", "PLAYER_C")));

        verify(mockDb, times(1)).runTransaction(any());
        verify(mockTransaction, times(1)).set(eq(firstRef), argThat((Story s) -> "PLAYER_C".equals(s.getPlayerId())));
        verify(mockTransaction, times(1)).set(eq(secondRef), argThat((Story s) -> "PLAYER_B".equals(s.getPlayerId())));
    }

    private static Story story(String gameCode, String storyId, String playerId) {
        Story story = new Story();
        story.setGameCode(gameCode);
        story.setStoryId(storyId);
        story.setRoundNumber(STORY_ROUND);
        story.setPlayerId(playerId);
        return story;
    }

    private static final int STORY_ROUND = 1;

    /**
     * Mocks the story's own document at gameSessions/{gameCode}/rounds/1/stories/{storyId}, holding
     * that story from the fixture, and runs StoryDAO's transaction against a mock Transaction.
     */
    private Transaction setupFirestoreStoryMocks(
            Firestore mockDb,
            ObjectMapper objectMapper,
            String gameCode,
            String storyId,
            String filePath
    ) {
        CollectionReference mockStoriesRef = mock(CollectionReference.class);
        DocumentReference mockStoryRef = mock(DocumentReference.class);
        DocumentSnapshot mockStorySnapshot = mock(DocumentSnapshot.class);
        Transaction mockTransaction = mock(Transaction.class);

        when(mockDb.collection("gameSessions/" + gameCode + "/rounds/" + STORY_ROUND + "/stories")).thenReturn(mockStoriesRef);
        when(mockStoriesRef.document(storyId)).thenReturn(mockStoryRef);
        when(mockTransaction.get(mockStoryRef)).thenReturn(ApiFutures.immediateFuture(mockStorySnapshot));

        try {
            Story storedStory = storyFromFixture(objectMapper, filePath, storyId);
            when(mockStorySnapshot.toObject(Story.class)).thenReturn(storedStory);
        } catch (IOException exception) {
            throw new ResourceException("There was an issue setting up the DAO mocks", exception);
        }

        when(mockDb.runTransaction(any())).thenAnswer(invocation -> {
            Transaction.Function<?> function = invocation.getArgument(0);
            return ApiFutures.immediateFuture(function.updateCallback(mockTransaction));
        });
        return mockTransaction;
    }

    private static Story storyFromFixture(ObjectMapper objectMapper, String filePath, String storyId) throws IOException {
        List<Story> stories = objectMapper.readValue(
                new File(filePath),
                objectMapper.getTypeFactory().constructCollectionType(List.class, Story.class)
        );
        return stories.stream()
                .filter(story -> story.getStoryId().equals(storyId))
                .findFirst()
                .orElseThrow();
    }
}