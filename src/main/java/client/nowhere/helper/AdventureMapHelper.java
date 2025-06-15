package client.nowhere.helper;

import client.nowhere.dao.AdventureMapDAO;
import client.nowhere.model.AdventureMap;
import client.nowhere.model.GameSessionDisplay;
import client.nowhere.model.Location;
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

    public AdventureMap create(AdventureMap adventureMap) {
        if (adventureMap.getAdventureId() == null || adventureMap.getAdventureId().isEmpty()) {
            adventureMap = new AdventureMap();
        }
        return this.adventureMapDAO.createAdventureMap(adventureMap);
    }
}
