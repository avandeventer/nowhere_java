package client.nowhere.controller;

import client.nowhere.helper.GameSessionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GameSessionController {

    private final GameSessionHelper gameSessionHelper;

    @Autowired
    public GameSessionController(GameSessionHelper gameSessionHelper) {
        this.gameSessionHelper = gameSessionHelper;
    }

    @PostMapping("/game")
    public String create() {
        String sessionCode = this.gameSessionHelper.createGameSession();
        return sessionCode;
    }

}
