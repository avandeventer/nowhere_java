package client.nowhere.controller;

import client.nowhere.helper.GameSessionHelper;
import client.nowhere.model.GameSession;
import client.nowhere.model.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(maxAge = 3600)
@RestController
public class GameSessionController {

    private final GameSessionHelper gameSessionHelper;

    @Autowired
    public GameSessionController(GameSessionHelper gameSessionHelper) {
        this.gameSessionHelper = gameSessionHelper;
    }

    @GetMapping("/game")
    @ResponseBody
    public GameSession get(@RequestParam String gameCode) {
        return this.gameSessionHelper.getGame(gameCode);
    }

    @PostMapping(value = "/game", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public GameSession create() {
        GameSession game = this.gameSessionHelper.createGameSession();
        return game;
    }

    @PutMapping(value = "/game", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public GameSession update(@RequestBody GameSession gameSession) {
        GameSession game = this.gameSessionHelper.updateGameSession(gameSession);
        return game;
    }

}
