package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.GameSession;
import client.nowhere.model.GameSessionDisplay;
import client.nowhere.model.Location;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Component
public class AdventureMapDAO {

    private final Firestore db;

    @Autowired
    public AdventureMapDAO(Firestore db) {
        this.db = db;
    }

    public List<Location> getLocations(String gameCode) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
        try {
            GameSession gameSession = FirestoreDAOUtil.mapGameSession(FirestoreDAOUtil.getGameSession(gameSessionRef));
            return gameSession.getAdventureMap().getLocations();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue reading the game session for locations", e);
        }
    }

    public GameSessionDisplay getGameSessionDisplay(String gameCode) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
        try {
            GameSession gameSession = FirestoreDAOUtil.mapGameSession(FirestoreDAOUtil.getGameSession(gameSessionRef));
            return gameSession.getAdventureMap().getGameSessionDisplay();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue reading the game session for locations", e);
        }
    }
}
