package client.nowhere.dao;

import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;

class GameSessionDAOTest {

    private static final String GAME_CODE = "GAME12";

    private Firestore db;
    private WriteBatch batch;
    private DocumentReference gameSessionRef;

    @BeforeEach
    void setUp() {
        db = mock(Firestore.class);
        batch = mock(WriteBatch.class);
        gameSessionRef = mock(DocumentReference.class);

        CollectionReference gameSessions = mock(CollectionReference.class);
        when(db.collection("gameSessions")).thenReturn(gameSessions);
        when(gameSessions.document(GAME_CODE)).thenReturn(gameSessionRef);
        when(gameSessionRef.delete()).thenReturn(ApiFutures.immediateFuture(mock(WriteResult.class)));
        when(db.batch()).thenReturn(batch);
        when(batch.commit()).thenReturn(ApiFutures.immediateFuture(List.of()));
    }

    @Test
    void deleteGame_deletesEveryDocumentInThePhasePathsLayoutThenTheGame() {
        DocumentReference story = mock(DocumentReference.class);
        DocumentReference submission = mock(DocumentReference.class);
        DocumentReference encounterVote = mock(DocumentReference.class);
        DocumentReference unknownDocument = mock(DocumentReference.class);

        // rounds/0/stories/{story}
        CollectionReference stories = leafCollection("stories", story);
        // rounds/0/phases/WHAT_HAPPENS_HERE/submissions/{submission}
        CollectionReference phases = parentCollection("phases",
                parentDocument(leafCollection("submissions", submission)));
        // rounds/0/encounters/x1y0/phases/MAKE_OUTCOME_CHOICE_VOTING/votes/{vote}
        CollectionReference encounterPhases = parentCollection("phases",
                parentDocument(leafCollection("votes", encounterVote)));
        CollectionReference encounters = parentCollection("encounters", parentDocument(encounterPhases));
        CollectionReference rounds = parentCollection("rounds", parentDocument(stories, phases, encounters));
        // Not part of the layout: logged, not deleted
        CollectionReference unknown = leafCollection("adventureMap", unknownDocument);
        when(gameSessionRef.listCollections()).thenReturn(List.of(rounds, unknown));

        new GameSessionDAO(db, mock(StoryDAO.class)).deleteGame(GAME_CODE);

        verify(batch).delete(story);
        verify(batch).delete(submission);
        verify(batch).delete(encounterVote);
        verify(batch, never()).delete(unknownDocument);
        verify(unknown, never()).select(ArgumentMatchers.<FieldPath>any());
        var order = inOrder(batch, gameSessionRef);
        order.verify(batch).commit();
        order.verify(gameSessionRef).delete();
    }

    @Test
    void deleteGame_withNothingUnderTheGame_deletesOnlyTheGameDocument() {
        when(gameSessionRef.listCollections()).thenReturn(List.of());

        new GameSessionDAO(db, mock(StoryDAO.class)).deleteGame(GAME_CODE);

        verify(batch, never()).delete(any(DocumentReference.class));
        verify(gameSessionRef).delete();
    }

    // A never-written parent document (round, encounter, phase) with these subcollections
    private static DocumentReference parentDocument(CollectionReference... collections) {
        DocumentReference document = mock(DocumentReference.class);
        when(document.listCollections()).thenReturn(Arrays.asList(collections));
        return document;
    }

    private static CollectionReference parentCollection(String id, DocumentReference... documents) {
        CollectionReference collection = mock(CollectionReference.class);
        when(collection.getId()).thenReturn(id);
        when(collection.listDocuments()).thenReturn(Arrays.asList(documents));
        return collection;
    }

    // A collection of real documents, found by an ID-only query
    private static CollectionReference leafCollection(String id, DocumentReference... documents) {
        CollectionReference collection = mock(CollectionReference.class);
        when(collection.getId()).thenReturn(id);
        when(collection.getPath()).thenReturn("gameSessions/" + GAME_CODE + "/" + id);
        Query idQuery = mock(Query.class);
        when(collection.select(ArgumentMatchers.<FieldPath>any())).thenReturn(idQuery);
        List<QueryDocumentSnapshot> snapshots = new ArrayList<>();
        for (DocumentReference document : documents) {
            QueryDocumentSnapshot snapshot = mock(QueryDocumentSnapshot.class);
            when(snapshot.getReference()).thenReturn(document);
            snapshots.add(snapshot);
        }
        QuerySnapshot querySnapshot = mock(QuerySnapshot.class);
        when(querySnapshot.getDocuments()).thenReturn(snapshots);
        when(idQuery.get()).thenReturn(ApiFutures.immediateFuture(querySnapshot));
        return collection;
    }
}
