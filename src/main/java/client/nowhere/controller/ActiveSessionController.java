package client.nowhere.controller;

import client.nowhere.helper.ActiveSessionHelper;
import client.nowhere.model.ActiveGameStateSession;
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

    @PutMapping("/activePlayerSession/next")
    @ResponseBody
    public ActivePlayerSession nextPlayerTurn(@RequestParam String gameCode) {
        return this.activeSessionHelper.nextPlayerTurn(gameCode);
    }

    @PutMapping("/activeGameStateSession")
    @ResponseBody
    public ActiveGameStateSession updateGameStateSession(@RequestParam String gameCode,
                                                         @RequestParam String authorId,
                                                         @RequestParam boolean isDone,
                                                         @RequestParam boolean isDoneWithTurn) {
        return this.activeSessionHelper.update(gameCode, authorId, isDone, isDoneWithTurn);
    }
}
