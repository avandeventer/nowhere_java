package client.nowhere.helper;

import client.nowhere.dao.AdventureMapDAO;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AdventureMapHelper {
    private final AdventureMapDAO adventureMapDAO;

    @Autowired
    public AdventureMapHelper(AdventureMapDAO adventureMapDAO) {
        this.adventureMapDAO = adventureMapDAO;
    }

    public List<Location> getGameLocations(String gameCode) {
        return this.adventureMapDAO.getLocations(gameCode);
    }

    public GameSessionDisplay getGameSessionDisplay(String gameCode) {
        return this.adventureMapDAO.getGameSessionDisplay(gameCode);
    }

    //Global Adventure Map Functions
    public AdventureMap createGlobal(AdventureMap adventureMap) {
        if (adventureMap.getAdventureId() == null || adventureMap.getAdventureId().isEmpty()) {
            adventureMap = new AdventureMap();
        }
        return this.adventureMapDAO.createGlobal(adventureMap);
    }

    public AdventureMap getGlobal(String adventureId) {
        return this.adventureMapDAO.getGlobal(adventureId);
    }

    public AdventureMap updateGlobal(AdventureMap adventureMap) {
        return this.adventureMapDAO.updateGlobal(adventureMap);
    }

    //User Profile Adventure Map Functions
    public AdventureMap create(String userProfileId, AdventureMap adventureMap) {
        return this.adventureMapDAO.create(userProfileId, adventureMap);
    }

    public AdventureMap updateAdventureMap(String userProfileId, AdventureMap adventureMap) {
        return this.adventureMapDAO.updateAdventureMap(userProfileId, adventureMap);
    }

    public AdventureMap addLocation(String userProfileId, String adventureId, Location location) {
        return this.adventureMapDAO.addLocation(userProfileId, adventureId, location);
    }

    public AdventureMap addStatType(String userProfileId, String adventureId, StatType statType) {
        return this.adventureMapDAO.addStatType(userProfileId, adventureId, statType);
    }

    public AdventureMap addRitualOption(String userProfileId, String adventureId, Option option) {
        return this.adventureMapDAO.addRitualOption(userProfileId, adventureId, option);
    }

    public AdventureMap get(String userProfileId, String adventureId) {
        return this.adventureMapDAO.get(userProfileId, adventureId);
    }
}
