package client.nowhere.controller;

import client.nowhere.helper.GameSessionHelper;
import client.nowhere.helper.StoryHelper;
import client.nowhere.model.GameSession;
import client.nowhere.model.ResponseObject;
import client.nowhere.model.Story;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(maxAge = 3600)
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final StoryHelper storyHelper;
    private final GameSessionHelper gameSessionHelper;

    @Autowired
    public AdminController(StoryHelper storyHelper, GameSessionHelper gameSessionHelper) {
        this.storyHelper = storyHelper;
        this.gameSessionHelper = gameSessionHelper;
    }

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

    @PutMapping("/game")
    @ResponseBody
    public GameSession update(@RequestBody GameSession gameSession) {
        return this.gameSessionHelper.adminUpdateGameSession(gameSession);
    }
}
