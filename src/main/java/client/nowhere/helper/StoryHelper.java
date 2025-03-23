package client.nowhere.helper;

import client.nowhere.constants.AuthorConstants;
import client.nowhere.dao.GameSessionDAO;
import client.nowhere.dao.StoryDAO;
import client.nowhere.dao.UserProfileDAO;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    public Story getPlayerStory(String gameCode, String playerId, int locationId) {
        GameSession gameSession = gameSessionDAO.getGame(gameCode);
        List<Story> gameSessionStories = gameSession.getStories();
        List<Story> sequelStories = getSequelStories(gameSession, playerId, locationId);
        List<String> gameSessionStoryIds =
                gameSessionStories.stream().map(Story::getStoryId).collect(Collectors.toList());

        Story story = new Story();
        if(sequelStories.size() == 0) {
            List<Story> playerStories = storyDAO.getPlayerStories(gameCode, playerId, locationId);

            if(playerStories.size() == 0) {
//                List<Story> globalStories = storyDAO.getGlobalStories(locationId);
                List<Story> saveGameStories = userProfileDAO.getSaveGameStories(gameSession, locationId);

                if(saveGameStories.size() == 0) {
                    story.defaultStory(gameCode, locationId);
                    storyDAO.createStory(story);
                } else {
                    boolean storySelected = false;
                    for(Story globalStory : saveGameStories) {
                        if (!gameSessionStoryIds.contains(globalStory.getStoryId())) {
                            story = globalStory;
                            story.setGameCode(gameCode);
                            storyDAO.createStory(story);
                            storySelected = true;
                            break;
                        };
                    }

                    if(!storySelected) {
                        story.defaultStory(gameCode, locationId);
                        storyDAO.createStory(story);
                    }
                }
            } else {
                story = playerStories.get(0);
            }
        } else {
            Optional<Story> playerSequelStoryExists = sequelStories.stream()
                    .filter(sequelStory -> sequelStory.getPrequelStoryPlayerId().equals(playerId) ||
                            sequelStory.getPrequelStoryPlayerId().equals(AuthorConstants.GLOBAL_PLAYER_SEQUEL)
                    ).findAny();

            if(playerSequelStoryExists.isPresent()) {
                story = playerSequelStoryExists.get();
                AdventureMap adventureMap = new AdventureMap();
                story.setLocation(adventureMap.getLocations().get(locationId));
                if (story.getPrequelStoryPlayerId().equals(AuthorConstants.GLOBAL_PLAYER_SEQUEL)) {
                    story.setPrequelStoryPlayerId(playerId);
                }
            } else {
                story = sequelStories.get(0);
            }

            if(!gameSessionStoryIds.contains(story.getStoryId())) {
                storyDAO.createStory(story);
            }
        }

        story.setVisited(true);
        story.setPlayerId(playerId);
        storyDAO.updateStory(story);
        return story;
    }

    public List<Story> getSequelStories(GameSession gameSession, String playerId, int locationId) {
        List<String> visitedStoryIds = gameSession.getStories().stream()
                .filter(gameSessionStory -> isVisitedByPlayer(playerId, gameSessionStory))
                .map(Story::getStoryId)
                .collect(Collectors.toList());

        if (visitedStoryIds.size() == 0) {
            return new ArrayList<>();
        }
        //TODO: Might want to readd this in the event I want to set prewritten db stories for all users
        //List<Story> globalSequelPlayerStories = storyDAO.getGlobalSequelPlayerStories(visitedStoryIds);
        List<Story> saveGameSequelStories = userProfileDAO.getSaveGameSequelStories(
                gameSession.getUserProfileId(),
                gameSession.getAdventureMap().getAdventureId(),
                gameSession.getSaveGameId(),
                visitedStoryIds
        );

        List<Story> sequelStoriesToSearch = Stream.concat(gameSession.getStories().stream(),
                saveGameSequelStories.stream()).toList();

        return sequelStoriesToSearch.stream()
                .filter(gameSessionStory -> isPlayerSequel(playerId, locationId, gameSessionStory)
                        && visitedStoryIds.contains(gameSessionStory.getPrequelStoryId()))
                .collect(Collectors.toList());
    }

    private boolean isVisitedByPlayer(String playerId, Story gameSessionStory) {
        return gameSessionStory.getPlayerId().equals(playerId)
                && !gameSessionStory.getSelectedOptionId().isEmpty();
    }

    private boolean isPlayerSequel(String playerId, int locationId, Story gameSessionStory) {
        boolean hasNotBeenAssigned = gameSessionStory.getPlayerId().isEmpty();
        boolean hasNotBeenPlayed = gameSessionStory.getSelectedOptionId().isEmpty();
        boolean hasAPrequel = !gameSessionStory.getPrequelStoryId().isEmpty();
        boolean isASequelForThisPlayer = gameSessionStory.getPrequelStoryPlayerId().equals(playerId)
                || gameSessionStory.getPrequelStoryPlayerId().equals(AuthorConstants.GLOBAL_PLAYER_SEQUEL);
        boolean isASequelForThisLocation = gameSessionStory.getLocation() != null && gameSessionStory.getLocation().getLocationId() == locationId;

        return hasNotBeenAssigned
                && hasNotBeenPlayed
                && hasAPrequel
                && (isASequelForThisPlayer || isASequelForThisLocation);
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
