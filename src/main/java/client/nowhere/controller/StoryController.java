package client.nowhere.controller;

import client.nowhere.helper.StoryHelper;
import client.nowhere.model.Story;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(maxAge = 3600)
@RestController
public class StoryController {

    private final StoryHelper storyHelper;

    @Autowired
    public StoryController(StoryHelper storyHelper) {
        this.storyHelper = storyHelper;
    }

    @PostMapping("/story")
    @ResponseBody
    public Story create(@RequestBody Story story) {
        story.randomizeNewStory();
        Story updatedStory = this.storyHelper.createStory(story);
        return updatedStory;
    }

    @PutMapping("/story")
    @ResponseBody
    public Story update(@RequestBody Story story) {
        Story updatedStory = this.storyHelper.updateStory(story);
        return updatedStory;
    }

    @GetMapping("/story")
    @ResponseBody
    public List<Story> getPlayerStories(
            @RequestParam String gameCode,
            @RequestParam(required = false) String authorId,
            @RequestParam(required = false) String outcomeAuthorId) {

        if (authorId != null) {
            return this.storyHelper.getPlayerStories(gameCode, authorId);
        } else if (outcomeAuthorId != null) {
            return this.storyHelper.getPlayerStoriesByOutcomeAuthorId(gameCode, outcomeAuthorId);
        } else {
            throw new IllegalArgumentException("Either authorId or outcomeAuthorId must be provided.");
        }
    }

}
