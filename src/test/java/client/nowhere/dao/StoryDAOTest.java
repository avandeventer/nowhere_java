package client.nowhere.dao;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.AdventureMap;
import client.nowhere.model.Story;
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
            StoryDAO storyDAO = new StoryDAO(mockDb, objectMapper);

            DocumentReference mockGameSessionRef = setupFirestoreStoryMocks(
                    mockDb,
                    objectMapper,
                    gameCode,
                    "src/test/resources/ROUND1_stories.json");

            // Create a story object to update
            Story storyToUpdate = new Story();
            storyToUpdate.setGameCode(gameCode);
            storyToUpdate.setStoryId(storyIdToUpdate);
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

                File rawUpdatedStories = new File("src/test/resources/ROUND1_stories_updated.json");
                List<Story> expectedStories = objectMapper.readValue(
                        rawUpdatedStories,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, Story.class)
                );
                verify(mockGameSessionRef, times(1)).update(eq("stories"), eq(expectedStories));
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
        StoryDAO storyDAO = new StoryDAO(mockDb, objectMapper);

        DocumentReference mockGameSessionRef = setupFirestoreStoryMocks(
                mockDb,
                objectMapper,
                gameCode,
                "src/test/resources/WRITE_OPTIONS_AGAIN_stories.json"
        );

        // Create a story object to update
        Story storyToUpdate = new Story();
        storyToUpdate.setGameCode(gameCode);
        storyToUpdate.setStoryId(storyIdToUpdate);
        storyToUpdate.setLocation(new AdventureMap().getLocations().get(3));
        storyToUpdate.setVisited(true);
        storyToUpdate.setPlayerId(playerId);

        // Verify interactions
        try {
            // Act
            Story updatedStory = storyDAO.updateStory(storyToUpdate);

            // Assert
            assertNotNull(updatedStory);
            assertEquals(false, updatedStory.isPlayerSucceeded());
            assertEquals(playerId, updatedStory.getPlayerId());
            assertEquals(new AdventureMap().getLocations().get(3), updatedStory.getLocation());

            File rawUpdatedStories = new File("src/test/resources/WRITE_OPTIONS_AGAIN_stories_updated.json");
            List<Story> expectedStories = objectMapper.readValue(
                    rawUpdatedStories,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, Story.class)
            );
            verify(mockGameSessionRef, times(1)).update(eq("stories"), eq(expectedStories));
        } catch (IOException e) {
            System.out.println("File failed to process" + e.getMessage());
        }
    }

    private DocumentReference setupFirestoreStoryMocks(
            Firestore mockDb,
            ObjectMapper objectMapper,
            String gameCode,
            String filePath
    ) {
            // Mock Firestore and related components
            DocumentSnapshot mockGameSessionSnapshot = mock(DocumentSnapshot.class);
            DocumentReference mockGameSessionRef = mock(DocumentReference.class);
            CollectionReference mockCollectionRef = mock(CollectionReference.class);


            // Mock Firestore reference and behavior
            when(mockDb.collection("gameSessions")).thenReturn(mockCollectionRef);
            when(mockCollectionRef.document(gameCode)).thenReturn(mockGameSessionRef);
            when(mockGameSessionRef.get()).thenReturn(ApiFutures.immediateFuture(mockGameSessionSnapshot));
            when(mockGameSessionSnapshot.exists()).thenReturn(true);

            // Load game session JSON and set it as the return for the mock snapshot
            File jsonFile = new File(filePath);

            try {
                Object rawStories = objectMapper.readValue(
                        jsonFile,
                        Object.class
                );

                when(mockGameSessionSnapshot.get("stories")).thenReturn(rawStories);
                ApiFuture<WriteResult> mockApiFuture = mock(ApiFuture.class);
                WriteResult writeResult = mock(WriteResult.class);
                when(mockGameSessionRef.update(eq("stories"), any())).thenReturn(mockApiFuture);
                when(mockApiFuture.get()).thenReturn(writeResult);
                when(writeResult.toString()).thenReturn("Now");
            } catch (IOException | ExecutionException | InterruptedException exception) {
                throw new ResourceException("There was an issue setting up the DAO mocks", exception);
            }

            return mockGameSessionRef;
        }

        @Test
        void testUpdateStory_storyNotFound() throws Exception {
//            // Arrange
//            String gameCode = "GAME123";
//            String storyId = "NON_EXISTENT_STORY";
//
//            Story updatedStory = new Story();
//            updatedStory.setGameCode(gameCode);
//            updatedStory.setStoryId(storyId);
//
//            when(db.collection("gameSessions").document(gameCode)).thenReturn(documentReference);
//            when(documentReference.get()).thenReturn(apiFuture);
//            when(apiFuture.get()).thenReturn(documentSnapshot);
//            when(documentSnapshot.exists()).thenReturn(true);
//            when(documentSnapshot.get("stories", List.class)).thenReturn(List.of());
//
//            // Act & Assert
//            Exception exception = assertThrows(ResourceException.class, () -> {
//                storyDAO.updateStory(updatedStory);
//            });
//
//            assertTrue(exception.getMessage().contains("There was an issue updating the story"));
//            verify(documentReference, never()).update(eq("stories"), any());
        }
}