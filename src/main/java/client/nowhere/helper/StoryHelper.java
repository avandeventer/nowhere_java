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

        List<Story> existingUnwrittenPlayerStories = gameSession.getStories().stream()
                    .filter(story ->
                            story.getPlayerId().equals(playerId)
                            && story.getSelectedOptionId().isBlank()
                    ).collect(Collectors.toList());

        if(existingUnwrittenPlayerStories.size() >= gameSession.getStoriesToWritePerRound()) {
            return getPlayerStory(gameCode, playerId, locationId, gameSession);
        }

        Optional<Location> location = gameSession.getAdventureMap().getLocations().stream().filter(
                gameSessionLocation ->
                gameSessionLocation.getLocationId() == locationId).findFirst();

        if (location.isEmpty()) {
            throw new ResourceException("A location at " + locationId + " does not exist.");
        }

        Story newStory = new Story(gameCode, location.get(), playerId);
        newStory.setVisited(true);
        return storyDAO.createStory(newStory);
    }

    public Story getPlayerStory(String gameCode, String playerId, int locationId, GameSession gameSession) {
        List<Story> gameSessionStories = gameSession.getStories();
        Story selectedStory = getSequelSaveGameStory(gameSession, playerId, locationId);

        if (selectedStory == null) {
            List<Story> saveGameStories = userProfileDAO.getSaveGameStories(gameSession, locationId);

            if (!saveGameStories.isEmpty()) {
                for (Story saveGameStory : saveGameStories) {
                    List<String> gameSessionStoryIds =
                            gameSessionStories.stream().map(Story::getStoryId).collect(Collectors.toList());
                    if (!gameSessionStoryIds.contains(saveGameStory.getStoryId())) {
                        selectedStory = saveGameStory;
                        selectedStory.setGameCode(gameCode);
                        break;
                    }
                }
            }
        }

        if (selectedStory == null) {
            selectedStory = new Story();
            selectedStory.defaultStory(gameCode, locationId);
        }

        List<String> gameSessionStoryIds =
                gameSessionStories.stream().map(Story::getStoryId).collect(Collectors.toList());

        if (!gameSessionStoryIds.contains(selectedStory.getStoryId())) {
            storyDAO.createStory(selectedStory);
        } else {
            getPlayerStory(gameCode, playerId, locationId, gameSession);
        }

        selectedStory.setVisited(true);
        selectedStory.setPlayerId(playerId);
        storyDAO.updateStory(selectedStory);
        return selectedStory;
    }

    public Story getSequelSaveGameStory(GameSession gameSession, String playerId, int locationId) {
        List<String> allVisitedStoryIds = gameSession.getStories().stream()
                .filter(gameSessionStory -> !gameSessionStory.getSelectedOptionId().isEmpty())
                .map(Story::getStoryId)
                .collect(Collectors.toList());

        if (allVisitedStoryIds.size() == 0) {
            return new Story();
        }

        List<Story> saveGameSequelStories = userProfileDAO.getSaveGameSequelStories(
                gameSession.getUserProfileId(),
                gameSession.getAdventureMap().getAdventureId(),
                gameSession.getSaveGameId(),
                allVisitedStoryIds
        );

        List<String> playerVisitedStoryIds = gameSession.getStories().stream()
                .filter(gameSessionStory -> isVisitedByPlayer(playerId, gameSessionStory))
                .map(Story::getStoryId)
                .collect(Collectors.toList());

        List<String> allGameSessionStoryIds = gameSession.getStories().stream()
                .map(Story::getStoryId)
                .collect(Collectors.toList());

        List<Story> sequels = saveGameSequelStories.stream()
                .filter(saveGameSequelStory -> isPlayerSequelRelevantToThisPlayer(saveGameSequelStory, playerVisitedStoryIds, allGameSessionStoryIds))
                .collect(Collectors.toList());

        Story selectedSequelStory = sequels.size() > 0 ? sequels.get(0) : null;

        if (selectedSequelStory == null) {
            List<Story> locationSequels = saveGameSequelStories.stream()
                    .filter(saveGameSequelStory -> isLocationSequelRelevantToThisPlayer(locationId, saveGameSequelStory, allGameSessionStoryIds))
                    .collect(Collectors.toList());
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
