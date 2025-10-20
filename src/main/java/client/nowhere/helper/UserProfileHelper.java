package client.nowhere.helper;

import client.nowhere.dao.*;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

@Component
public class UserProfileHelper {

    UserProfileDAO userProfileDAO;
    AdventureMapDAO adventureMapDAO;
    GameSessionDAO gameSessionDAO;

    @Autowired
    public UserProfileHelper(UserProfileDAO userProfileDAO, AdventureMapDAO adventureMapDAO, GameSessionDAO gameSessionDAO) {
        this.userProfileDAO = userProfileDAO;
        this.adventureMapDAO = adventureMapDAO;
        this.gameSessionDAO = gameSessionDAO;
    }

    public UserProfile create(UserProfile userProfile) {
        List<AdventureMap> adventureMaps = adventureMapDAO.getAllGlobal();

        HashMap<String, ProfileAdventureMap> profileAdventureHashMap = new HashMap<>();
        for (AdventureMap adventureMap : adventureMaps) {
            ProfileAdventureMap profileAdventureMap = new ProfileAdventureMap(adventureMap);
            profileAdventureHashMap.put(profileAdventureMap.getAdventureMap().getAdventureId(), profileAdventureMap);
        }

        userProfile.setMaps(profileAdventureHashMap);
        return this.userProfileDAO.create(userProfile);
    }

    public String saveGameSessionAdventureMapToUserProfile(String gameCode) {
        GameSession gameSession = gameSessionDAO.getGame(gameCode);
        String saveGameId = saveGameSessionAdventureMapToUserProfile(gameSession);

        if (!saveGameId.isEmpty()) {
            gameSession.setSaveGameId(saveGameId);
            gameSessionDAO.updateGameSession(gameSession);
        }

        return saveGameId;
    }

    public String saveGameSessionAdventureMapToUserProfile(GameSession gameSession) {
        UserProfile userProfile = userProfileDAO.get(gameSession.getUserProfileId());
        String adventureId = gameSession.getAdventureMap().getAdventureId();

        String saveGameId = "";
        if (!userProfile.getMaps().containsKey(adventureId)) {
            AdventureMap adventureMap = gameSession.getAdventureMap();
            if (adventureMap.getName().isEmpty()) {
                adventureMap.setName("Your new map [Name me]");
            }

            ProfileAdventureMap profileAdventureMap = userProfileDAO.addAdventureMap(
                    gameSession.getUserProfileId(),
                    gameSession.getAdventureMap()
            );

            if (!profileAdventureMap.getSaveGames().isEmpty()) {
                saveGameId = profileAdventureMap.getSaveGames().keySet().iterator().next();
            }
        }
        return saveGameId;
    }

    public UserProfile get(String email, String password) {
        return this.userProfileDAO.get(email, password);
    }

    public SaveGame upsertSaveGame(String userProfileId, String adventureId, SaveGame saveGame) {
        return this.userProfileDAO.upsertSaveGame(userProfileId, adventureId, saveGame);
    }

    public SaveGame getSaveGame(String email, String password, String adventureId, String saveGameId) {
        return this.userProfileDAO.getSaveGame(email, password, adventureId, saveGameId);
    }

    public void deleteSaveGame(String userProfileId, String adventureId, String saveGameId) {
        this.userProfileDAO.deleteSaveGame(userProfileId, adventureId, saveGameId);
    }
}
