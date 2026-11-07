package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.helper.PhasePaths;
import client.nowhere.model.*;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.SetOptions;
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
        AdventureMap adventureMap = get(gameCode);
        return adventureMap == null ? new ArrayList<>() : adventureMap.getLocations();
    }

    /** The game's display text, plus the map's name (adventureMapName, not stored) for the clients' start screens. */
    public GameSessionDisplay getGameSessionDisplay(String gameCode) {
        AdventureMap adventureMap = get(gameCode);
        if (adventureMap == null) {
            return new GameSessionDisplay();
        }
        GameSessionDisplay display = adventureMap.getGameSessionDisplay() == null
                ? new GameSessionDisplay()
                : adventureMap.getGameSessionDisplay();
        display.setAdventureMapName(adventureMap.getName() == null ? "" : adventureMap.getName());
        return display;
    }

    public void updateGameSessionDisplay(String gameCode, GameSessionDisplay display) {
        try {
            // Targeted field update of the map document: no read, nothing else rewritten
            ApiFuture<WriteResult> result = gameSessionMapRef(gameCode).set(Map.of("gameSessionDisplay", display), SetOptions.merge());
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
        existingAdventureMap.updateFrom(adventureMapUpdates);
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

    /**
     * The game's own copy of its adventure map (gameSessions/{code}/adventureMap/map, see
     * PhasePaths), or null for a game created without one.
     */
    public AdventureMap get(String gameCode) {
        try {
            DocumentSnapshot snapshot = gameSessionMapRef(gameCode).get().get();
            return snapshot.exists() ? snapshot.toObject(AdventureMap.class) : null;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue reading the game's adventure map", e);
        }
    }

    public DocumentReference gameSessionMapRef(String gameCode) {
        return db.document(PhasePaths.adventureMapPath(gameCode));
    }

    /**
     * Updates the AdventureMap in a game session with data from another AdventureMap.
     * Retrieves the existing AdventureMap from the game session, applies updates, and saves it back.
     * @param gameCode The game code identifying the game session
     * @param adventureMapUpdates The AdventureMap containing the updates to apply
     * @return The updated AdventureMap
     */
    public AdventureMap updateGameSessionAdventureMap(String gameCode, AdventureMap adventureMapUpdates) {
        AdventureMap existingAdventureMap = get(gameCode);
        existingAdventureMap.updateFrom(adventureMapUpdates);
        updateSessionAdventureMap(gameCode, existingAdventureMap);
        return existingAdventureMap;
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

    public void updateSessionAdventureMap(String gameCode, AdventureMap updatedAdventureMap) {
        try {
            // The map is its own document, so replacing it doesn't touch the game document
            ApiFuture<WriteResult> result = gameSessionMapRef(gameCode).set(updatedAdventureMap);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
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
        DocumentReference mapRef = gameSessionMapRef(gameCode);
        try {
            // Transaction on the map document: the duplicate check and the append see the same list
            return db.runTransaction(transaction -> {
                DocumentSnapshot snapshot = transaction.get(mapRef).get();
                if (!snapshot.exists()) {
                    throw new ResourceException("Game " + gameCode + " has no adventure map");
                }
                AdventureMap adventureMap = snapshot.toObject(AdventureMap.class);
                if (adventureMap.getLocations() == null) {
                    adventureMap.setLocations(new ArrayList<>());
                }

                if (location.getLabel() != null
                        && !location.getLabel().isEmpty()
                        && adventureMap.getLocations().stream().map(Location::getLabel).toList().contains(location.getLabel())) {
                    System.out.println("Location already exists: " + location.getLabel());
                    return adventureMap.getLocations();
                }

                adventureMap.getLocations().add(location);
                transaction.update(mapRef, "locations", adventureMap.getLocations());
                return adventureMap.getLocations();
            }).get();
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue adding the location", e);
        }
    }

    public List<Location> getLocationByAuthor(String gameCode, String authorId) {
        return getLocations(gameCode).stream()
                .filter(location -> authorId.equals(location.getAuthorId()))
                .collect(Collectors.toList());
    }

    public List<Location> getLocationByOutcomeAuthor(String gameCode, String outcomeAuthorId) {
        return getLocations(gameCode).stream()
                .filter(location -> location.getOptions() != null)
                .filter(location -> location.getOptions().stream()
                        .anyMatch(option -> outcomeAuthorId.equals(option.getOutcomeAuthorId())))
                .collect(Collectors.toList());
    }

    public Location updateLocation(String gameCode, Location location) {
        try {
            List<Location> locations = getLocations(gameCode);

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

            ApiFuture<WriteResult> result = gameSessionMapRef(gameCode).update("locations", locations);
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
