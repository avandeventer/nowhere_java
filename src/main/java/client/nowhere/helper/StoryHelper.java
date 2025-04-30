package client.nowhere.helper;

import client.nowhere.constants.AuthorConstants;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.dao.UserProfileDAO;
import client.nowhere.exception.ResourceException;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class StoryHelper {

    private final StoryDAO storyDAO;
    private final GameSessionDAO gameSessionDAO;
    private final UserProfileDAO userProfileDAO;

    @Autowired
    public StoryHelper(StoryDAO storyDAO, GameSessionDAO gameSessionDAO, UserProfileDAO userProfileDAO) {
        this.storyDAO = storyDAO;
        this.gameSessionDAO = gameSessionDAO;
        this.userProfileDAO = userProfileDAO;
    }

    public Story createStory(Story story) {
        return storyDAO.createStory(story);
    }

    public Story updateStory(Story story) {
        return storyDAO.updateStory(story);
    }

    public List<Story> getAuthorStories(String gameCode, String authorId) {
        return storyDAO.getAuthorStories(gameCode, authorId);
    }

    public List<Story> getAuthorStoriesByOutcomeAuthorId(String gameCode, String outcomeAuthorId) {
        return storyDAO.getAuthorStoriesByOutcomeAuthorId(gameCode, outcomeAuthorId);
    }

    public List<Story> getAuthorStoriesByStoryId(String gameCode, String storyId) {
        return storyDAO.getAuthorStoriesByStoryId(gameCode, storyId);
    }

    public Story storePlayerStory(String gameCode, String playerId, int locationId) {
        GameSession gameSession = gameSessionDAO.getGame(gameCode);

        List<Story> existingUnwrittenPlayerStories = gameSession.getStories() == null || gameSession.getStories().isEmpty()
                ? new ArrayList<>()
                : gameSession.getStories().stream()
                    .filter(story ->
                            story.getPlayerId().equals(playerId)
                            && story.getSelectedOptionId().isBlank()
                            && story.getAuthorId().isBlank()
                    ).collect(Collectors.toList());

        Story playerStory = null;
        if(existingUnwrittenPlayerStories.size() >= gameSession.getStoriesToWritePerRound()) {
            playerStory = getSaveGameStoryForPlayer(gameCode, playerId, locationId, gameSession);
        }

        if (playerStory == null) {
            playerStory = createNewPlayerStory(gameCode, locationId, gameSession);
        }

        playerStory.setPlayerId(playerId);
        playerStory.setVisited(true);
        storyDAO.createStory(playerStory);
        return playerStory;
    }

    private Story createNewPlayerStory(String gameCode, int locationId, GameSession gameSession) {
        Story playerStory;
        Optional<Location> location = gameSession.getAdventureMap().getLocations().stream().filter(
                gameSessionLocation ->
                        gameSessionLocation.getLocationId() == locationId).findFirst();

        if (location.isEmpty()) {
            throw new ResourceException("A location at " + locationId + " does not exist.");
        }

        playerStory = new Story(gameCode, location.get());
        return playerStory;
    }

    public Story getSaveGameStoryForPlayer(String gameCode, String playerId, int locationId, GameSession gameSession) {
        Story selectedStory = getSequelSaveGameStory(gameSession, playerId, locationId);

        if (selectedStory == null) {
            selectedStory = getRegularSaveGameStory(gameCode, locationId, gameSession);
        }

        if (selectedStory == null) {
            selectedStory = new Story();
            selectedStory.defaultStory(gameCode, locationId);
        }

        return selectedStory;
    }

    private Story getRegularSaveGameStory(String gameCode, int locationId, GameSession gameSession) {
        List<Story> saveGameStories = userProfileDAO.getRegularSaveGameStories(gameSession, locationId);

        Story selectedSaveGameStory = null;
        if (!saveGameStories.isEmpty()) {
            Random randomGenerator = new Random();
            int randomSequelStoryIndex = randomGenerator.nextInt(saveGameStories.size());
            selectedSaveGameStory = saveGameStories.get(randomSequelStoryIndex);
            selectedSaveGameStory.setGameCode(gameCode);
        }
        return selectedSaveGameStory;
    }

    public Story getSequelSaveGameStory(GameSession gameSession, String playerId, int locationId) {
        Map<String, Boolean> allSelectedOptionOutcomes = gameSession.getStories().stream()
                .filter(gameSessionStory -> !gameSessionStory.getSelectedOptionId().isEmpty())
                .collect(Collectors.toMap(Story::getSelectedOptionId, Story::isPlayerSucceeded));

        if (allSelectedOptionOutcomes.size() == 0) {
            return null;
        }

        List<Story> saveGameSequelStories = userProfileDAO.getSaveGameSequelStories(
                gameSession.getUserProfileId(),
                gameSession.getAdventureMap().getAdventureId(),
                gameSession.getSaveGameId(),
                allSelectedOptionOutcomes
        );

        List<String> allGameSessionStoryIds = gameSession.getStories().stream()
                .map(Story::getStoryId)
                .collect(Collectors.toList());

        Story selectedSequelStory = getSaveGamePlayerSequelStory(gameSession, playerId, saveGameSequelStories, allGameSessionStoryIds, locationId);

        if (selectedSequelStory == null) {
            selectedSequelStory = getSaveGameLocationSequelStory(gameSession, playerId, locationId, saveGameSequelStories, allGameSessionStoryIds);
        }

        return selectedSequelStory;
    }

    private Story getSaveGameLocationSequelStory(GameSession gameSession, String playerId, int locationId, List<Story> saveGameSequelStories, List<String> allGameSessionStoryIds) {
        Story selectedSequelStory = null;
        List<Story> locationSequels = saveGameSequelStories.stream()
                .filter(saveGameSequelStory -> isLocationSequelRelevantToThisPlayer(locationId, saveGameSequelStory, allGameSessionStoryIds))
                .collect(Collectors.toList());

        if (locationSequels.size() > 0) {
            Random randomGenerator = new Random();
            int randomSequelStoryIndex = randomGenerator.nextInt(locationSequels.size());
            selectedSequelStory = locationSequels.get(randomSequelStoryIndex);
            selectedSequelStory.setLocation(gameSession.getAdventureMap().getLocations().get(locationId));
            if (selectedSequelStory.getPrequelStoryPlayerId().equals(AuthorConstants.GLOBAL_PLAYER_SEQUEL)) {
                selectedSequelStory.setPrequelStoryPlayerId(playerId);
            }
        }

        return selectedSequelStory;
    }

    private Story getSaveGamePlayerSequelStory(GameSession gameSession, String playerId, List<Story> saveGameSequelStories, List<String> allGameSessionStoryIds, int locationId) {
        List<String> playerVisitedStoryIds = gameSession.getStories().stream()
                .filter(gameSessionStory -> isVisitedByPlayer(playerId, gameSessionStory))
                .map(Story::getStoryId)
                .collect(Collectors.toList());

        List<Story> sequels = saveGameSequelStories.stream()
                .filter(saveGameSequelStory -> isPlayerSequelRelevantToThisPlayer(saveGameSequelStory, playerVisitedStoryIds, allGameSessionStoryIds))
                .collect(Collectors.toList());

        Story selectedSequelStory = sequels.size() > 0 ? sequels.get(0) : null;
        if (selectedSequelStory != null) {
            Optional<Location> locationOptional = gameSession.getAdventureMap()
                    .getLocations().stream().filter(existingLocation -> existingLocation.getLocationId() == locationId)
                    .findFirst();

            locationOptional.ifPresent(selectedSequelStory::setLocation);
        }

        return selectedSequelStory;
    }

    private boolean isVisitedByPlayer(String playerId, Story gameSessionStory) {
        return gameSessionStory.getPlayerId().equals(playerId)
                && !gameSessionStory.getSelectedOptionId().isEmpty();
    }

    private boolean isPlayerSequelRelevantToThisPlayer(Story saveGameSequelStory, List<String> storyIdsPlayedByPlayer, List<String> existingGameSessionStoryIds) {
        if (existingGameSessionStoryIds.contains(saveGameSequelStory.getStoryId())) {
            return false;
        }

        boolean isASequelForThisPlayer = saveGameSequelStory.getPrequelStoryPlayerId().equals(AuthorConstants.GLOBAL_PLAYER_SEQUEL)
                && storyIdsPlayedByPlayer.contains(saveGameSequelStory.getPrequelStoryId());

        return isASequelForThisPlayer;
    }

    private boolean isLocationSequelRelevantToThisPlayer(int locationId, Story saveGameSequelStory, List<String> existingGameSessionStoryIds) {
        if (existingGameSessionStoryIds.contains(saveGameSequelStory.getStoryId())) {
            return false;
        }

        boolean isASequelForThisLocation = saveGameSequelStory.getLocation() != null && saveGameSequelStory.getLocation().getLocationId() == locationId;
        return isASequelForThisLocation;
    }

    public Story createGlobalStory(Story story) {
        story.setAuthorId(AuthorConstants.GAME_DEV);
        story.setOutcomeAuthorId(AuthorConstants.GAME_DEV);

        if(story.getStoryId().isEmpty()) {
            String storyId = UUID.randomUUID().toString();
            story.setStoryId(storyId);
        }

        for (Option option : story.getOptions()) {
            if(option.getOptionId().isEmpty()) {
                String optionId = UUID.randomUUID().toString();
                option.setOptionId(optionId);
            }
        }

        return storyDAO.createGlobalStory(story);
    }

    public List<Story> getPlayedStoriesForAdventure(String gameCode, String playerId) {
        return storyDAO.getPlayedStoriesForAdventure(gameCode, playerId);
    }

    public List<Story> saveAllGameSessionStories(String gameCode) {
        GameSession gameSession = gameSessionDAO.getGame(gameCode);
        String adventureId = gameSession.getAdventureMap().getAdventureId();
        String userProfileId = gameSession.getUserProfileId();
        String saveGameId = gameSession.getSaveGameId();

        gameSession.getStories()
            .stream()
            .forEach(Story::resetPlayerVariables);

        List<Story> gameSessionStories = gameSession.getStories()
                .stream()
                .filter(gameSessionStory -> !gameSessionStory.getAuthorId().equals(AuthorConstants.DEFAULT))
                .collect(Collectors.toList());

        return userProfileDAO.saveGameToUserProfile(gameSessionStories, userProfileId, adventureId, saveGameId);
    }
}
