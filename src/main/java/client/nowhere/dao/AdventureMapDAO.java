package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.*;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
public class AdventureMapDAO {

    private static final Logger logger = LoggerFactory.getLogger(AdventureMapDAO.class);
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

    public AdventureMap addStatTypeGlobal(String userProfileId, String adventureId, StatType statType) {
        AdventureMap existingAdventureMap = get(userProfileId, adventureId);
        existingAdventureMap.getStatTypes().add(statType);
        update(userProfileId, existingAdventureMap);
        return existingAdventureMap;
    }

    public AdventureMap addStatTypes(String gameCode, List<StatType> statTypes) {
        AdventureMap existingAdventureMap = get(gameCode);
        existingAdventureMap.getStatTypes().addAll(statTypes);
        updateSessionAdventureMap(gameCode, existingAdventureMap);
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

    public AdventureMap get(String gameCode) {
        DocumentReference gameSessionAdventureMapRef = db.collection("gameSessions").document(gameCode);
        GameSession gameSession = FirestoreDAOUtil.mapDatabaseObject(gameSessionAdventureMapRef, GameSession.class);
        AdventureMap adventureMap = gameSession.getAdventureMap();
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

    public AdventureMap updateSessionAdventureMap(String gameCode, AdventureMap updatedAdventureMap) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            GameSession gameSession = FirestoreDAOUtil.mapDatabaseObject(gameSessionRef, GameSession.class);
            gameSession.setAdventureMap(updatedAdventureMap);
            ApiFuture<WriteResult> result = gameSessionRef.set(gameSession);
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

    public List<Location> addLocation(String gameCode, Location location) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
        GameSession gameSession = FirestoreDAOUtil.mapDatabaseObject(gameSessionRef, GameSession.class);
        
        if (gameSession.getAdventureMap().getLocations() != null 
            && location.getLabel() != null
            && !location.getLabel().isEmpty()
            && gameSession.getAdventureMap().getLocations().stream()
            .map(Location::getLabel).toList().contains(location.getLabel())
        ) {
            System.out.println("Location already exists: " + location.getLabel());
            return gameSession.getAdventureMap().getLocations();
        }

        try {
            // Use a transaction to safely update only the locations list
            ApiFuture<Void> result = db.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(gameSessionRef).get();
                if (!snapshot.exists()) {
                    throw new ResourceException("Game session does not exist");
                }
                GameSession currentGameSession = snapshot.toObject(GameSession.class);
                
                if (currentGameSession.getAdventureMap().getLocations() == null) {
                    currentGameSession.getAdventureMap().setLocations(new ArrayList<>());
                }
                currentGameSession.getAdventureMap().getLocations().add(location);
                
                // Update only the adventureMap.locations field
                Map<String, Object> updates = new HashMap<>();
                updates.put("adventureMap.locations", currentGameSession.getAdventureMap().getLocations());
                transaction.update(gameSessionRef, updates);
                
                return null;
            });
            
            result.get();
            System.out.println("Location added successfully");
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue adding the location", e);
        }
        return gameSession.getAdventureMap().getLocations();
    }

    public List<Location> getLocationByAuthor(String gameCode, String authorId) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
        GameSession gameSession = FirestoreDAOUtil.mapDatabaseObject(gameSessionRef, GameSession.class);
        
        return gameSession.getAdventureMap().getLocations().stream()
                .filter(location -> authorId.equals(location.getAuthorId()))
                .collect(Collectors.toList());
    }

    public List<Location> getLocationByOutcomeAuthor(String gameCode, String outcomeAuthorId) {
        DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
        GameSession gameSession = FirestoreDAOUtil.mapDatabaseObject(gameSessionRef, GameSession.class);
        
        return gameSession.getAdventureMap().getLocations().stream()
                .filter(location -> location.getOptions() != null)
                .filter(location -> location.getOptions().stream()
                        .anyMatch(option -> outcomeAuthorId.equals(option.getOutcomeAuthorId())))
                .collect(Collectors.toList());
    }

    public Location updateLocation(String gameCode, Location location) {
        try {
            DocumentReference gameSessionRef = db.collection("gameSessions").document(gameCode);
            GameSession gameSession = FirestoreDAOUtil.mapDatabaseObject(gameSessionRef, GameSession.class);
            List<Location> locations = gameSession.getAdventureMap().getLocations();

            for (int i = 0; i < locations.size(); i++) {
                Location locationToUpdate = locations.get(i);
                if (locationToUpdate.getId().equals(location.getId())) {
                    // Update Location fields
                    if (!location.getAuthorId().isEmpty()) {
                        locationToUpdate.setAuthorId(location.getAuthorId());
                    }

                    if (location.getLabel() != null && !location.getLabel().isEmpty()) {
                        locationToUpdate.setLabel(location.getLabel());
                    }

                    if (location.getIconDirectory() != null && !location.getIconDirectory().isEmpty()) {
                        locationToUpdate.setIconDirectory(location.getIconDirectory());
                    }

                    // Update Options
                    if (location.getOptions() != null && !location.getOptions().isEmpty()) {
                        List<Option> optionsToUpdate = new ArrayList<>();

                        for (Option resultOption : locationToUpdate.getOptions()) {
                            for (Option inputOption : location.getOptions()) {
                                if (resultOption.getOptionId().equals(inputOption.getOptionId())) {
                                    Option optionToUpdate = new Option(
                                            resultOption.getOptionId(),
                                            !inputOption.getOptionText().isEmpty() ?
                                                    inputOption.getOptionText() :
                                                    resultOption.getOptionText(),
                                            !inputOption.getOptionText().isEmpty() ?
                                                    inputOption.getOptionText() :
                                                    resultOption.getOptionText(),
                                            !inputOption.getSuccessText().isEmpty() ?
                                                    inputOption.getSuccessText() :
                                                    resultOption.getSuccessText(),
                                            resultOption.getSuccessResults(),
                                            !inputOption.getFailureText().isEmpty() ?
                                                    inputOption.getFailureText() :
                                                    resultOption.getFailureText(),
                                            resultOption.getFailureResults(),
                                            !inputOption.getOutcomeAuthorId().isEmpty() ?
                                                    inputOption.getOutcomeAuthorId() :
                                                    resultOption.getOutcomeAuthorId(),
                                            resultOption.getPlayerStatDCs()
                                    );
                                    
                                    // Update attemptText if provided
                                    if (!inputOption.getAttemptText().isEmpty()) {
                                        optionToUpdate.setAttemptText(inputOption.getAttemptText());
                                    } else {
                                        optionToUpdate.setAttemptText(resultOption.getAttemptText());
                                    }
                                    
                                    optionsToUpdate.add(optionToUpdate);
                                }
                            }
                        }

                        if (!optionsToUpdate.isEmpty()) {
                            locationToUpdate.setOptions(optionsToUpdate);
                        }
                    }
                    
                    location = locationToUpdate;
                    locations.set(i, locationToUpdate);
                    break;
                }
            }

            Map<String, Object> updates = new HashMap<>();
            updates.put("adventureMap.locations", locations);
            
            ApiFuture<WriteResult> result = gameSessionRef.update(updates);
            WriteResult asyncResponse = result.get();
            System.out.println("Location updated successfully: " + location.getLabel());
            return location;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue updating the location", e);
        }
    }

    public List<String> getLocationImages() {
        try {
            logger.info("Fetching location images from Google Cloud Storage");
            Storage storage = StorageOptions.getDefaultInstance().getService();
            
            String bucketName = "nowhere_images";
            String prefix = "location_icons/";
            
            List<String> images = new ArrayList<>();
            for (com.google.cloud.storage.Blob blob : storage.list(bucketName, Storage.BlobListOption.prefix(prefix)).iterateAll()) {
                String name = blob.getName().toLowerCase();
                if (name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg")) {
                    images.add("https://storage.googleapis.com/" + bucketName + "/" + blob.getName());
                }
            }
            images.sort(String::compareTo);
                
            logger.info("Successfully fetched {} location images", images.size());
            return images;
            
        } catch (Exception e) {
            logger.error("Error fetching location images from Google Cloud Storage", e);
            logger.info("Falling back to hardcoded image list");
            return getHardcodedImages();
        }
    }

    private List<String> getHardcodedImages() {
        List<String> images = new ArrayList<>();
        images.add("https://storage.googleapis.com/nowhere_images/location_icons/Tavern.png");
        images.add("https://storage.googleapis.com/nowhere_images/location_icons/Castle.png");
        images.add("https://storage.googleapis.com/nowhere_images/location_icons/Forest.png");
        images.add("https://storage.googleapis.com/nowhere_images/location_icons/Dungeon.png");
        images.add("https://storage.googleapis.com/nowhere_images/location_icons/Village.png");
        images.add("https://storage.googleapis.com/nowhere_images/location_icons/Mountain.png");
        images.add("https://storage.googleapis.com/nowhere_images/location_icons/Desert.png");
        images.add("https://storage.googleapis.com/nowhere_images/location_icons/Ocean.png");
        images.add("https://storage.googleapis.com/nowhere_images/location_icons/Cave.png");
        images.add("https://storage.googleapis.com/nowhere_images/location_icons/Temple.png");
        images.add("https://storage.googleapis.com/nowhere_images/location_icons/Library.png");
        images.add("https://storage.googleapis.com/nowhere_images/location_icons/Market.png");
        images.add("https://storage.googleapis.com/nowhere_images/location_icons/Inn.png");
        images.add("https://storage.googleapis.com/nowhere_images/location_icons/Bridge.png");
        images.add("https://storage.googleapis.com/nowhere_images/location_icons/Tower.png");
        return images;
    }
}
