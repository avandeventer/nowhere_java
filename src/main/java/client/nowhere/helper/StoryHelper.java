package client.nowhere.helper;

import client.nowhere.dao.StoryDAO;
import client.nowhere.model.Story;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class StoryHelper {

    private final StoryDAO storyDAO;

    @Autowired
    public StoryHelper(StoryDAO storyDAO) {
        this.storyDAO = storyDAO;
    }

    public Story createPrompt(Story story) {
        return storyDAO.createStory(story);
    }
}
