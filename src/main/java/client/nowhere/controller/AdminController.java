package client.nowhere.controller;

import client.nowhere.helper.StoryHelper;
import client.nowhere.model.ResponseObject;
import client.nowhere.model.Story;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final StoryHelper storyHelper;

    @Autowired
    public AdminController(StoryHelper storyHelper) { this.storyHelper = storyHelper; }

    @PostMapping("/story")
    @ResponseBody
    public Story create(@RequestBody Story story) {
        return this.storyHelper.createGlobalStory(story);
    }

    @PutMapping("/story/all")
    @ResponseBody
    public List<Story> saveAllGameSessionStories(@RequestParam String gameCode) {
        return this.storyHelper.saveAllGameSessionStories(gameCode);
    }

}
