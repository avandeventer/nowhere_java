package client.nowhere.dao;

import client.nowhere.exception.ResourceException;
import client.nowhere.model.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
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

    public List<Story> getSaveGameSequelStories(String userProfileId, String adventureId, String saveGameId, List<String> storyIds) {
        UserProfile userProfile = this.get(userProfileId);

        List<Story> saveGameStories = userProfile.getMaps()
                .get(adventureId)
                .getSaveGameById(saveGameId)
                .getGlobalStories();

        return saveGameStories.stream().filter(story -> storyIds.contains(story.getPrequelStoryId())).collect(Collectors.toList());
    }

    public List<Story> getSaveGameStories(GameSession gameSession, int locationId) {
        String userProfileId = gameSession.getUserProfileId();
        String adventureId = gameSession.getAdventureMap().getAdventureId();
        String saveGameId = gameSession.getSaveGameId();

        UserProfile userProfile = this.get(userProfileId);

        List<Story> saveGameStories = userProfile.getMaps()
                .get(adventureId)
                .getSaveGameById(saveGameId)
                .getGlobalStories();

        return saveGameStories.stream().filter(story -> locationId == story.getLocation().getLocationId())
                .collect(Collectors.toList());
    }

}
