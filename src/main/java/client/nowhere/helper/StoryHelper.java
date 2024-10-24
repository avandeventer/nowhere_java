package client.nowhere.helper;

import client.nowhere.dao.StoryDAO;
import client.nowhere.model.Story;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

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

    public Story getPlayerStory(String gameCode, String playerId, int locationId) {
        List<Story> playerStories = storyDAO.getPlayerStories(gameCode, playerId, locationId);

        Story story = new Story();
        if(playerStories.size() > 0) {
            story = playerStories.get(0);
            story.setVisited(true);
            storyDAO.updateStory(story);
        }
        return story;
    }
}
