package client.nowhere.controller;

import client.nowhere.helper.StoryHelper;
import client.nowhere.model.Story;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StoryController {

    private final StoryHelper storyHelper;

    @Autowired
    public StoryController(StoryHelper storyHelper) {
        this.storyHelper = storyHelper;
    }

    @PostMapping("/story")
    public String create(@RequestBody Story story) {
        Story updatedStory = this.storyHelper.createPrompt(story);
        return story.getPrompt();
    }

}
