package client.nowhere.dao;

import client.nowhere.model.AdventureMap;
import client.nowhere.model.GameSessionDisplay;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.annotation.Exclude;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdventureMapDAOTest {

    private static final String GAME_CODE = "GAME12";

    @Test
    void getGameSessionDisplay_addsTheMapNameForTheClients() {
        AdventureMap adventureMap = new AdventureMap();
        adventureMap.setName("The Vale");
        GameSessionDisplay display = new GameSessionDisplay();
        display.setMapDescription("A misty valley");
        adventureMap.setGameSessionDisplay(display);

        GameSessionDisplay result = daoWithMap(adventureMap).getGameSessionDisplay(GAME_CODE);

        assertEquals("The Vale", result.getAdventureMapName());
        assertEquals("A misty valley", result.getMapDescription());
    }

    @Test
    void getGameSessionDisplay_withoutAMap_returnsAnEmptyDisplay() {
        GameSessionDisplay result = daoWithMap(null).getGameSessionDisplay(GAME_CODE);

        assertEquals("", result.getAdventureMapName());
        assertEquals("", result.getMapDescription());
    }

    @Test
    void adventureMapName_isNotWrittenToFirestore() throws NoSuchMethodException {
        // Firestore's mapper skips @Exclude'd properties, so the name never lands on the map document
        assertNotNull(GameSessionDisplay.class.getMethod("getAdventureMapName").getAnnotation(Exclude.class));
        assertNotNull(GameSessionDisplay.class.getMethod("setAdventureMapName", String.class).getAnnotation(Exclude.class));
    }

    private static AdventureMapDAO daoWithMap(AdventureMap adventureMap) {
        Firestore db = mock(Firestore.class);
        DocumentReference mapRef = mock(DocumentReference.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
        when(db.document("gameSessions/" + GAME_CODE + "/adventureMap/map")).thenReturn(mapRef);
        when(mapRef.get()).thenReturn(ApiFutures.immediateFuture(snapshot));
        when(snapshot.exists()).thenReturn(adventureMap != null);
        when(snapshot.toObject(AdventureMap.class)).thenReturn(adventureMap);
        return new AdventureMapDAO(db);
    }
}
