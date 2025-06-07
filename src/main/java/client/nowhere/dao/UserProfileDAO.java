package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.exception.ValidationException;
import client.nowhere.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import at.favre.lib.crypto.bcrypt.BCrypt;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Component
public class UserProfileDAO {

    private final Firestore db;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    public UserProfileDAO(Firestore db, ObjectMapper objectMapper) {
        this.db = db;
        this.objectMapper = objectMapper;
    }

    public UserProfile create(UserProfile userProfile) {
        DocumentReference docRef = db.collection("userProfiles").document(userProfile.getId());

        String hashedPassword = BCrypt.withDefaults().hashToString(12, userProfile.getPassword().toCharArray());
        userProfile.setPassword(hashedPassword);

        try {
            ApiFuture<WriteResult> result = docRef.set(userProfile);
            WriteResult asyncResponse = result.get();
            System.out.println("Update time : " + result.get().toString());
            System.out.println("Object " + result.get().toString());
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("There was an issue creating the game session", e);
        }
        return userProfile;
    }

    public UserProfile get(String userProfileId) {
        UserProfile userProfile = new UserProfile();
        try {
            DocumentReference userProfileRef = db.collection("userProfiles").document(userProfileId);
            DocumentSnapshot gameSessionSnapshot = FirestoreDAOUtil.getDocumentSnapshot(userProfileRef);
            userProfile = FirestoreDAOUtil.mapUserProfile(gameSessionSnapshot);
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("There was an issue retrieving the game session " + e.getMessage());
        }
        return userProfile;
    }

    public UserProfile get(String email, String inputPassword) {
        UserProfile userProfile = null;
        try {
            CollectionReference userProfilesRef = db.collection("userProfiles");

            // First, query by email
            ApiFuture<QuerySnapshot> query = userProfilesRef
                    .whereEqualTo("email", email)
                    .get();

            QuerySnapshot querySnapshot = query.get();

            if (querySnapshot.isEmpty()) {
                throw new ValidationException("No profile with that email exists");
            }

            String hashedPassword = BCrypt.withDefaults().hashToString(12, inputPassword.toCharArray());

            DocumentSnapshot document = querySnapshot.getDocuments().get(0);
            String storedHashedPassword = document.getString("password");

            BCrypt.Result result = BCrypt.verifyer().verify(inputPassword.toCharArray(), storedHashedPassword);

            if (!result.verified) {
                throw new ValidationException("Incorrect Password");
            }

            userProfile = FirestoreDAOUtil.mapUserProfile(document);
        } catch (InterruptedException | ExecutionException e) {
            System.out.println("There was an issue retrieving the user profile: " + e.getMessage());
        }
        return userProfile;
    }

    public List<Story> saveGameToUserProfile(List<Story> gameSessionStories, String userProfileId, String adventureId, String saveGameId) {
        try {
            // Fetch user profile from Firestore
            DocumentReference userProfileRef = db.collection("userProfiles").document(userProfileId);
            DocumentSnapshot userProfileSnapshot = FirestoreDAOUtil.getDocumentSnapshot(userProfileRef);
            UserProfile userProfile = FirestoreDAOUtil.mapUserProfile(userProfileSnapshot);

            // Get ProfileAdventureMap and SaveGame
            ProfileAdventureMap profileAdventureMap = userProfile.getMaps().get(adventureId);
            if (profileAdventureMap == null) {
                throw new ResourceException("Adventure map " + adventureId + " not found for user " + userProfileId);
            }

            SaveGame saveGame = profileAdventureMap.getSaveGames().get(saveGameId);
            if (saveGame == null) {
                throw new ResourceException("Save game " + saveGameId + " not found in adventure " + adventureId);
            }

            // Deduplicate and add new stories
            List<Story> uniqueGameSessionStories = getDedupedGameSessionStories(gameSessionStories, saveGame);
            saveGame.getGlobalStories().addAll(uniqueGameSessionStories);

            // Save updated objects
            profileAdventureMap.upsertSaveGame(saveGame);
            userProfile.upsertProfileAdventureMap(profileAdventureMap);

            // Update Firestore
            ApiFuture<WriteResult> result = userProfileRef.update("maps", userProfile.getMaps());
            result.get(); // Ensure update completes before returning
            System.out.println("Update time: " + result.get().toString());

            return uniqueGameSessionStories;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("Error storing stories for user " + userProfileId + ", adventure " + adventureId + ", save game " + saveGameId);
        }
    }

    private List<Story> getDedupedGameSessionStories(List<Story> gameSessionStories,
                                                     SaveGame saveGame) {
        Set<String> existingGlobalStoryIdSet = saveGame.getGlobalStories()
                .stream().map(Story::getStoryId).collect(Collectors.toSet());

        List<Story> dedupedGameSessionStories = gameSessionStories
                .stream()
                .filter(story -> !existingGlobalStoryIdSet.contains(story.getStoryId()))
                .collect(Collectors.toList());

        return dedupedGameSessionStories;
    }

    public List<Story> getSaveGameSequelStories(
            String userProfileId,
            String adventureId,
            String saveGameId,
            List<Story> existingGameSessionStories
    ) {
        Set<SequelKey> allSelectedOptionOutcomes = existingGameSessionStories.stream()
                .filter(story -> !story.getSelectedOptionId().isEmpty())
                .map(Story::getSequelKey)
                .collect(Collectors.toSet());

        if (allSelectedOptionOutcomes.size() == 0) {
            return new ArrayList<>();
        }

        UserProfile userProfile = this.get(userProfileId);

        List<Story> saveGameStories = userProfile.getMaps()
                .get(adventureId)
                .getSaveGameById(saveGameId)
                .getGlobalStories();

        return saveGameStories.stream()
                .filter(story -> allSelectedOptionOutcomes.contains(story.getPrequelKey()))
                .collect(Collectors.toList());
    }

    public List<Story> getRegularSaveGameStories(GameSession gameSession, String locationId) {
        String userProfileId = gameSession.getUserProfileId();
        String adventureId = gameSession.getAdventureMap().getAdventureId();
        String saveGameId = gameSession.getSaveGameId();

        UserProfile userProfile = this.get(userProfileId);

        List<Story> saveGameStories = userProfile.getMaps()
                .get(adventureId)
                .getSaveGameById(saveGameId)
                .getGlobalStories();

        List<String> allGameSessionStoryIds = gameSession.getStories().stream()
                .map(Story::getStoryId)
                .collect(Collectors.toList());

        return saveGameStories.stream().filter(story -> locationId == story.getLocation().getLocationId()
                && story.getPrequelStoryId().isBlank()
                && !allGameSessionStoryIds.contains(story.getStoryId()))
                .collect(Collectors.toList());
    }

    public SaveGame upsertSaveGame(String userProfileId, String adventureId, SaveGame saveGame) {

        try {
            // Fetch user profile from Firestore
            DocumentReference userProfileRef = db.collection("userProfiles").document(userProfileId);
            DocumentSnapshot userProfileSnapshot = FirestoreDAOUtil.getDocumentSnapshot(userProfileRef);
            UserProfile userProfile = FirestoreDAOUtil.mapUserProfile(userProfileSnapshot);

            // Get ProfileAdventureMap and SaveGame
            ProfileAdventureMap profileAdventureMap = userProfile.getMaps().get(adventureId);
            if (profileAdventureMap == null) {
                throw new ResourceException("Adventure map " + adventureId + " not found for user " + userProfileId);
            }

            SaveGame saveGameToUpsert = new SaveGame(saveGame.getName());
            if (!saveGame.getId().isEmpty() && profileAdventureMap.getSaveGames().containsKey(saveGame.getId())) {
                SaveGame existingSaveGame = profileAdventureMap.getSaveGames().get(saveGame.getId());
                existingSaveGame.setName(saveGame.getName());
                saveGameToUpsert = existingSaveGame;
            }

            profileAdventureMap.upsertSaveGame(saveGameToUpsert);

            userProfile.upsertProfileAdventureMap(profileAdventureMap);

            ApiFuture<WriteResult> result = userProfileRef.update("maps", userProfile.getMaps());
            result.get();

            return saveGameToUpsert;
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            throw new ResourceException("Error saving game to profile. User Profile ID: " + userProfileId + ", adventure " + adventureId + ", save game " + saveGame.getId());
        }
    }

    public SaveGame getSaveGame(String email, String password, String adventureId, String saveGameId) {
        UserProfile userProfile = this.get(email, password);
        return userProfile.getMaps().get(adventureId).getSaveGameById(saveGameId);
    }
}
