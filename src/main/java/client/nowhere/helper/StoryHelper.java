package client.nowhere.helper;

import client.nowhere.constants.AuthorConstants;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.dao.UserProfileDAO;
import client.nowhere.exception.ResourceException;
import client.nowhere.factory.MutexFactory;
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
        GameSession gameSession = gameSessionDAO.getGame(story.getGameCode());
        story.randomizeNewStory(gameSession.getAdventureMap().getStatTypes());
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
        return gameSessionDAO.runInTransaction(txn -> {
            Story playerStory = null;
            GameSession gameSession = gameSessionDAO.getGameInTransaction(gameCode, txn);
            List<Story> stories = gameSession.getStories() != null ? new ArrayList<>(gameSession.getStories()) : new ArrayList<>();

            List<Story> existingUnwrittenPlayerStories = gameSession.getStories() == null || gameSession.getStories().isEmpty()
                    ? new ArrayList<>()
                    : gameSession.getStories().stream()
                    .filter(story ->
                            story.getPlayerId().equals(playerId)
                                    && story.getSelectedOptionId().isBlank()
                                    && story.getAuthorId().isBlank()
                    ).collect(Collectors.toList());

            if (existingUnwrittenPlayerStories.size() >= gameSession.getStoriesToWritePerRound()) {
                playerStory = getSaveGameStoryForPlayer(gameCode, playerId, locationId, gameSession);
            }

            if (playerStory == null) {
                playerStory = createNewPlayerStory(gameCode, locationId, gameSession, playerId);
            }

            playerStory.setPlayerId(playerId);
            playerStory.setVisited(true);
            stories.add(playerStory);
            gameSessionDAO.updateStoriesInTransaction(gameCode, stories, txn);
            return playerStory;
        });
    }

    private Story createNewPlayerStory(String gameCode, int locationId, GameSession gameSession, String playerId) {
        Optional<Location> location = gameSession.getAdventureMap().getLocations().stream().filter(
                gameSessionLocation ->
                        gameSessionLocation.getLocationId() == locationId).findFirst();

        if (location.isEmpty()) {
            throw new ResourceException("A location at " + locationId + " does not exist.");
        }

        List<StatType> playerStats = gameSession.getAdventureMap().getStatTypes();
        Story playerStory = new Story(gameCode, location.get(), playerStats);

        List<Story> stories = gameSession.getStories() != null ? gameSession.getStories() : Collections.emptyList();

        List<Story> playedStories = new ArrayList<>();
        if (!stories.isEmpty()) {
            playedStories = getPlayedStories(gameSession, stories);
        }

        if (!playedStories.isEmpty()) {
            Optional<Story> prequelStory = getPrequelStory(locationId, playerId, playedStories);

            prequelStory.ifPresent(foundPrequelStory -> {
                playerStory.makeSequel(foundPrequelStory.getStoryId(), foundPrequelStory.isPlayerSucceeded(), foundPrequelStory.getSelectedOptionId());

                if (foundPrequelStory.getPlayerId().equals(playerId)
                        && foundPrequelStory.getLocation().getLocationId() != locationId) {
                    playerStory.setPrequelStoryPlayerId(foundPrequelStory.getPlayerId());
                }
            });
        }

        return playerStory;
    }

    private Optional<Story> getPrequelStory(int locationId, String playerId, List<Story> playedStories) {
        Random rand = new Random();
        int coinFlip = rand.nextInt(3);
        boolean getSequel = coinFlip > 0;

        Optional<Story> prequelStory = Optional.empty();

        if (getSequel) {
            prequelStory = findLocationMatch(playedStories, locationId, playerId)
                    .or(() -> findPlayerMatch(playedStories, playerId));
        }

        return prequelStory;
    }

    private List<Story> getPlayedStories(GameSession gameSession, List<Story> stories) {
        List<String> existingSequelIds = stories.stream()
                .map(Story::getPrequelStoryId)
                .collect(Collectors.toList());

        List<Story> playedStories = stories.stream()
                .filter(story ->
                        !story.getPlayerId().isEmpty()
                                && !story.getSelectedOptionId().isEmpty()
                                && !existingSequelIds.contains(story.getStoryId()))
                .collect(Collectors.toList());

        return playedStories.isEmpty()
                ? playedStories : filterOutExistingUserProfileSequels(gameSession, playedStories);
    }

    private Optional<Story> findPlayerMatch(List<Story> stories, String playerId) {
        return stories.stream()
                .filter(story -> story.getPlayerId().equals(playerId))
                .findFirst();
    }

    private Optional<Story> findLocationMatch(List<Story> stories, int locationId, String playerId) {
        return stories.stream()
                .filter(story -> story.getLocation().getLocationId() == locationId)
                .findFirst();
    }

    public Story getSaveGameStoryForPlayer(String gameCode, String playerId, int locationId, GameSession gameSession) {
        Story selectedStory = getSequelSaveGameStory(gameSession, playerId, locationId);

        if (selectedStory == null) {
            selectedStory = getRegularSaveGameStory(gameCode, locationId, gameSession);
        }

        if (selectedStory != null) {
            selectedStory.setSaveGameStory(true);
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
        String userProfileId = gameSession.getUserProfileId();
        String adventureId = gameSession.getAdventureMap().getAdventureId();
        String saveGameId = gameSession.getSaveGameId();

        List<Story> saveGameSequelStories = userProfileDAO.getSaveGameSequelStories(
                userProfileId,
                adventureId,
                saveGameId,
                gameSession.getStories()
        );

        if (saveGameSequelStories.size() == 0) {
            return null;
        }

        List<String> allGameSessionStoryIds = gameSession.getStories().stream()
                .map(Story::getStoryId)
                .collect(Collectors.toList());

        Story selectedSequelStory = getSaveGamePlayerSequelStory(gameSession, playerId, saveGameSequelStories, allGameSessionStoryIds, locationId);

        if (selectedSequelStory == null) {
            selectedSequelStory = getSaveGameLocationSequelStory(locationId, saveGameSequelStories, allGameSessionStoryIds);
        }

        if (selectedSequelStory != null) {
            selectedSequelStory.setSequelStory(true);
        }

        return selectedSequelStory;
    }

    private Story getSaveGameLocationSequelStory(int locationId, List<Story> saveGameSequelStories, List<String> allGameSessionStoryIds) {
        Story selectedSequelStory = null;
        List<Story> locationSequels = saveGameSequelStories.stream()
                .filter(saveGameSequelStory -> isLocationSequelRelevantToThisPlayer(locationId, saveGameSequelStory, allGameSessionStoryIds))
                .collect(Collectors.toList());

        if (locationSequels.size() > 0) {
            Random randomGenerator = new Random();
            int randomSequelStoryIndex = randomGenerator.nextInt(locationSequels.size());
            selectedSequelStory = locationSequels.get(randomSequelStoryIndex);
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
            selectedSequelStory.setPrequelStoryPlayerId(playerId);
        }

        return selectedSequelStory;
    }

    private List<Story> filterOutExistingUserProfileSequels(GameSession gameSession, List<Story> playedStories) {
        List<Story> saveGameSequelStories = userProfileDAO.getSaveGameSequelStories(
                gameSession.getUserProfileId(),
                gameSession.getAdventureMap().getAdventureId(),
                gameSession.getSaveGameId(),
                playedStories
        );

        Set<SequelKey> existingSaveGameSequelOutcomes = saveGameSequelStories.stream()
                .filter(story -> !story.getPrequelStorySelectedOptionId().isEmpty())
                .map(Story::getPrequelKey)
                .collect(Collectors.toSet());

        return playedStories.stream()
                .filter(story -> !existingSaveGameSequelOutcomes.contains(story.getSequelKey()))
                .collect(Collectors.toList());

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
