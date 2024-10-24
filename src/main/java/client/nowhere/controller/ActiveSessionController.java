package client.nowhere.controller;

import client.nowhere.helper.ActiveSessionHelper;
import client.nowhere.helper.StoryHelper;
import client.nowhere.model.ActivePlayerSession;
import client.nowhere.model.Story;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(maxAge = 3600)
@RestController
public class ActiveSessionController {

    private final ActiveSessionHelper activeSessionHelper;

    @Autowired
    public ActiveSessionController(ActiveSessionHelper storyHelper) {
        this.activeSessionHelper = activeSessionHelper;
    }

    @PutMapping("/activeSession")
    @ResponseBody
    public ActivePlayerSession update(@RequestBody ActivePlayerSession activeSession) {
        return this.activeSessionHelper.update(activeSession);
    }

}
