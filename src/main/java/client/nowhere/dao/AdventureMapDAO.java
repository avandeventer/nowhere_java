package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.*;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
            return gameSession.getAdventureMap() == null
                    ? new GameSessionDisplay()
                    : gameSession.getAdventureMap().getGameSessionDisplay();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue reading the game session for locations", e);
        }
    }

    public void updateGameSessionDisplay(String gameCode, GameSessionDisplay display) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
        try {
            // Get the current game session
            GameSession gameSession = FirestoreDAOUtil.mapGameSession(FirestoreDAOUtil.getGameSession(gameSessionRef));
            
            // Update the GameSessionDisplay in the AdventureMap
            if (gameSession.getAdventureMap() == null) {
                gameSession.setAdventureMap(new AdventureMap());
            }
            gameSession.getAdventureMap().setGameSessionDisplay(display);
            
            // Update the game session in Firestore
            ApiFuture<WriteResult> result = gameSessionRef.set(gameSession);
            result.get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the game session display", e);
        }
    }

    public AdventureMap createGlobal(AdventureMap adventureMap) {
        try {
            DocumentReference globalAdventureMapRef = db.collection("maps").document(adventureMap.getAdventureId());
            ApiFuture<WriteResult> result = globalAdventureMapRef.set(adventureMap);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return adventureMap;
    }

    public AdventureMap updateGlobal(AdventureMap adventureMapUpdates) {
        try {
            DocumentReference globalAdventureMapRef = db.collection("maps").document(adventureMapUpdates.getAdventureId());
            AdventureMap existingAdventureMap = FirestoreDAOUtil.mapDatabaseObject(globalAdventureMapRef, AdventureMap.class);
            existingAdventureMap.updateAdventureMapDisplay(adventureMapUpdates);

            ApiFuture<WriteResult> result = globalAdventureMapRef.set(adventureMapUpdates);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return adventureMapUpdates;
    }

    public AdventureMap updateAdventureMap(String userProfileId, AdventureMap adventureMapUpdates) {
        AdventureMap existingAdventureMap = get(userProfileId, adventureMapUpdates.getAdventureId());
        existingAdventureMap.updateAdventureMapDisplay(adventureMapUpdates);
        existingAdventureMap.updateStatTypes(adventureMapUpdates.getStatTypes());
        existingAdventureMap.updateLocations(adventureMapUpdates.getLocations());
        update(userProfileId, existingAdventureMap);
        return existingAdventureMap;
    }

    public AdventureMap addLocation(String userProfileId, String adventureId, Location newLocation) {
        AdventureMap existingAdventureMap = get(userProfileId, adventureId);
        existingAdventureMap.getLocations().add(newLocation);
        update(userProfileId, existingAdventureMap);
        return existingAdventureMap;
    }

    public AdventureMap addStatType(String userProfileId, String adventureId, StatType statType) {
        AdventureMap existingAdventureMap = get(userProfileId, adventureId);
        existingAdventureMap.getStatTypes().add(statType);
        update(userProfileId, existingAdventureMap);
        return existingAdventureMap;
    }

    public AdventureMap addRitualOption(String userProfileId, String adventureId, Option ritualOption) {
        AdventureMap existingAdventureMap = get(userProfileId, adventureId);
        existingAdventureMap.getRitual().getOptions().add(ritualOption);
        update(userProfileId, existingAdventureMap);
        return existingAdventureMap;
    }

    public AdventureMap getGlobal(String adventureId) {
        DocumentReference globalAdventureMapRef = db.collection("maps").document(adventureId);
        AdventureMap adventureMap = FirestoreDAOUtil.mapDatabaseObject(globalAdventureMapRef, AdventureMap.class);
        System.out.println("Update time : " + adventureMap.toString());
        return adventureMap;
    }

    public AdventureMap get(String userProfileId, String adventureId) {
        DocumentReference globalAdventureMapRef = db.collection("userProfiles").document(userProfileId);
        UserProfile userProfile = FirestoreDAOUtil.mapDatabaseObject(globalAdventureMapRef, UserProfile.class);
        AdventureMap adventureMap = userProfile.getMaps().get(adventureId).getAdventureMap();
        System.out.println("Update time : " + adventureMap.toString());
        return adventureMap;
    }

    public AdventureMap update(String userProfileId, AdventureMap updatedAdventureMap) {
        try {
            DocumentReference globalAdventureMapRef = db.collection("userProfiles").document(userProfileId);
            UserProfile userProfile = FirestoreDAOUtil.mapDatabaseObject(globalAdventureMapRef, UserProfile.class);
            userProfile.getMaps().get(updatedAdventureMap.getAdventureId()).setAdventureMap(updatedAdventureMap);
            ApiFuture<WriteResult> result = globalAdventureMapRef.set(userProfile);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
            return updatedAdventureMap;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
    }

    public AdventureMap create(String userProfileId, AdventureMap adventureMap) {
        try {
            DocumentReference globalAdventureMapRef = db.collection("userProfiles").document(userProfileId);
            UserProfile userProfile = FirestoreDAOUtil.mapDatabaseObject(globalAdventureMapRef, UserProfile.class);
            ProfileAdventureMap profileAdventureMap = new ProfileAdventureMap(adventureMap);
            userProfile.getMaps().put(adventureMap.getAdventureId(), profileAdventureMap);
            ApiFuture<WriteResult> result = globalAdventureMapRef.set(userProfile);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return adventureMap;
    }

    public List<AdventureMap> getAllGlobal() {
        CollectionReference globalAdventureMapRef = db.collection("maps");
        Iterable<DocumentReference> globalMapRefs = globalAdventureMapRef.listDocuments();

        List<AdventureMap> globalAdventureMaps = new ArrayList<>();

        for ( DocumentReference globalMapRef : globalMapRefs) {
            AdventureMap globalAdventureMap = FirestoreDAOUtil.mapDatabaseObject(globalMapRef, AdventureMap.class);
            globalAdventureMaps.add(globalAdventureMap);
        }

        return globalAdventureMaps;
    }

    public void delete(String userProfileId, String adventureId) {
        try {
            DocumentReference globalAdventureMapRef = db.collection("userProfiles").document(userProfileId);
            UserProfile userProfile = FirestoreDAOUtil.mapDatabaseObject(globalAdventureMapRef, UserProfile.class);

            if (userProfile.getMaps() != null && userProfile.getMaps().containsKey(adventureId)) {
                userProfile.getMaps().remove(adventureId);

                ApiFuture<WriteResult> result = globalAdventureMapRef.set(userProfile);
                WriteResult asyncResponse = result.get();
                System.out.println("Deleted adventure map with ID: " + adventureId +
                        ", Update time: " + asyncResponse.getUpdateTime());
            } else {
                System.out.println("No adventure map found with ID: " + adventureId);
            }
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue deleting the adventure map", e);
        }
    }

}
