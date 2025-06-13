package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.*;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
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

    public AdventureMap createAdventureMap(AdventureMap adventureMap) {
        try {
            DocumentReference globalAdventureMapRef = db.collection("stories").document(adventureMap.getAdventureId());
            ApiFuture<WriteResult> result = globalAdventureMapRef.set(adventureMap);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return adventureMap;
    }

}
