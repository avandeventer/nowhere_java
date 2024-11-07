package client.nowhere.helper;

import client.nowhere.dao.StoryDAO;
import client.nowhere.model.Option;
import client.nowhere.model.Story;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

@Component
public class StoryHelper {

    private final StoryDAO storyDAO;

    @Autowired
    public StoryHelper(StoryDAO storyDAO) {
        this.storyDAO = storyDAO;
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
        List<Story> gameSessionStories = storyDAO.getStories(gameCode);
        List<Story> sequelStories = getSequelStories(gameSessionStories, playerId, locationId);
        List<String> gameSessionStoryIds =
                gameSessionStories.stream().map(Story::getStoryId).collect(Collectors.toList());

        Story story = new Story();
        if(sequelStories.size() == 0) {
            List<Story> playerStories = storyDAO.getPlayerStories(gameCode, playerId, locationId);

            if(playerStories.size() == 0) {
                List<Story> globalStories = storyDAO.getGlobalStories(locationId);

                if(globalStories.size() == 0) {
                    story.defaultStory(gameCode, locationId);
                    storyDAO.createStory(story);
                } else {
                    boolean storySelected = false;
                    for(Story globalStory : globalStories) {
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
            story = sequelStories.get(0);
            if(gameSessionStoryIds.contains(story.getStoryId())) {
                storyDAO.createStory(story);
            }
        }

        story.setVisited(true);
        storyDAO.updateStory(story);
        return story;
    }

    public List<Story> getSequelStories(List<Story> gameSessionStories, String playerId, int locationId) {

        List<String> visitedStoryIds = gameSessionStories.stream()
                .filter(gameSessionStory -> isVisitedByPlayer(playerId, gameSessionStory))
                .map(Story::getStoryId)
                .collect(Collectors.toList());

        if (visitedStoryIds.size() == 0) {
            return new ArrayList<>();
        }

        List<Story> globalSequelPlayerStories = storyDAO.getGlobalSequelPlayerStories(visitedStoryIds);
        gameSessionStories.addAll(globalSequelPlayerStories);

        return gameSessionStories.stream()
                .filter(gameSessionStory -> isPlayerSequel(playerId, locationId, gameSessionStory)
                        && visitedStoryIds.contains(gameSessionStory.getPrequelStoryId()))
                .collect(Collectors.toList());
    }

    private boolean isVisitedByPlayer(String playerId, Story gameSessionStory) {
        return gameSessionStory.isVisited()
                && !gameSessionStory.getSelectedOptionId().isEmpty()
                && gameSessionStory.getStoryId().equals(playerId);
    }

    private boolean isPlayerSequel(String playerId, int locationId, Story gameSessionStory) {
        return !gameSessionStory.isVisited()
                && gameSessionStory.getSelectedOptionId().isEmpty()
                && !gameSessionStory.getPrequelStoryId().isEmpty()
                && (gameSessionStory.getPrequelStoryPlayerId().equals(playerId)
                || gameSessionStory.getLocation().getLocationId() == locationId);
    }

    public Story createGlobalStory(Story story) {
        story.setAuthorId("ADMIN");
        story.setOutcomeAuthorId("ADMIN");

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
}
