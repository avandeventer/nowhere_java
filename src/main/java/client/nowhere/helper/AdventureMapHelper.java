package client.nowhere.helper;

import client.nowhere.dao.AdventureMapDAO;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class AdventureMapHelper {
    private final AdventureMapDAO adventureMapDAO;
    private final GameSessionDAO gameSessionDAO;
    private final StoryHelper storyHelper;

    @Autowired
    public AdventureMapHelper(
            AdventureMapDAO adventureMapDAO,
            GameSessionDAO gameSessionDAO,
            StoryHelper storyHelper
    ) {
        this.adventureMapDAO = adventureMapDAO;
        this.gameSessionDAO = gameSessionDAO;
        this.storyHelper = storyHelper;
    }

    public List<Location> getGameLocations(String gameCode) {
        return this.adventureMapDAO.getLocations(gameCode);
    }

    public GameSessionDisplay getGameSessionDisplay(String gameCode) {
        GameSessionDisplay gameSessionDisplay = this.adventureMapDAO.getGameSessionDisplay(gameCode);
        if (gameSessionDisplay.getEntity() == null || gameSessionDisplay.getEntity().isEmpty()) {
            GameSession gameSession = gameSessionDAO.getGame(gameCode);
            if (gameSession.getAdventureMap() != null) {
                Optional<StatType> favorStatOpt = gameSession.getAdventureMap().getStatTypes().stream().filter(StatType::isFavorType).findAny();
                favorStatOpt.ifPresent(statType -> gameSessionDisplay.setEntity(statType.getFavorEntity()));
                adventureMapDAO.updateGameSessionDisplay(gameCode, gameSessionDisplay);
            }
        }
        return gameSessionDisplay;
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
        return this.adventureMapDAO.addStatTypeGlobal(userProfileId, adventureId, statType);
    }

    public AdventureMap addRitualOption(String userProfileId, String adventureId, Option option) {
        return this.adventureMapDAO.addRitualOption(userProfileId, adventureId, option);
    }

    public AdventureMap get(String userProfileId, String adventureId) {
        return this.adventureMapDAO.get(userProfileId, adventureId);
    }

    public void delete(String userProfileId, String adventureId) {
        this.adventureMapDAO.delete(userProfileId, adventureId);
    }

    public List<Location> addLocation(String gameCode, Location location) {
        return this.adventureMapDAO.addLocation(gameCode, location);
    }

    public Location updateLocation(String gameCode, Location location) {
        return this.adventureMapDAO.updateLocation(gameCode, location);
    }

    public List<Location> getLocationByAuthor(String gameCode, String authorId) {
        return this.adventureMapDAO.getLocationByAuthor(gameCode, authorId);
    }

    public List<Location> getLocationByOutcomeAuthor(String gameCode, String outcomeAuthorId) {
        return this.adventureMapDAO.getLocationByOutcomeAuthor(gameCode, outcomeAuthorId);
    }

    public List<String> getLocationImages() {
        return this.adventureMapDAO.getLocationImages();
    }

    public List<Location> getLocationsByPlayerId(String gameCode, String playerId) {
        GameSession gameSession = gameSessionDAO.getGame(gameCode);

        List<Story> existingUnwrittenPlayerStories = gameSession.getStories() == null || gameSession.getStories().isEmpty()
        ? new ArrayList<>()
        : gameSession.getStories().stream()
        .filter(story ->
                story.getPlayerId().equals(playerId)
                        && story.getSelectedOptionId().isBlank()
                        && story.getAuthorId().isBlank()
        ).collect(Collectors.toList());

        List<Location> locations = this.adventureMapDAO.getLocations(gameCode);
        
        if (existingUnwrittenPlayerStories.size() >= gameSession.getStoriesToWritePerRound()) {
            List<Location> selectableLocations = new ArrayList<>();
            for (Location location : locations) {
                Story playerStory = storyHelper.getSaveGameStoryForPlayer(gameCode, playerId, location.getId(), gameSession);
                if (playerStory != null) {
                    selectableLocations.add(location);
                }
            }
            locations = selectableLocations;
        }

        return locations;
    }

}
