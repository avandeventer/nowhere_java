package client.nowhere.controller;

import client.nowhere.helper.ActiveSessionHelper;
import client.nowhere.model.ActivePlayerSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(maxAge = 3600)
@RestController
public class ActiveSessionController {

    private final ActiveSessionHelper activeSessionHelper;

    @Autowired
    public ActiveSessionController(ActiveSessionHelper activeSessionHelper) {
        this.activeSessionHelper = activeSessionHelper;
    }

    @PutMapping("/activePlayerSession")
    @ResponseBody
    public ActivePlayerSession update(@RequestBody ActivePlayerSession activeSession) {
        return this.activeSessionHelper.update(activeSession);
    }

}
