package client.nowhere.controller;

import client.nowhere.helper.GameSessionHelper;
import client.nowhere.model.GameSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

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
    public GameSession create(@RequestParam(required = false, defaultValue = "c8d068ae-e180-44c9-940c-011ba632cba4") String userProfileId,
                              @RequestParam(required = false, defaultValue = "a6a6e1ab-de29-4ffb-9028-7c4f90f9d008") String adventureId,
                              @RequestParam(required = false, defaultValue = "4b8a146a-ccf6-41cf-961a-65096d70bf82\n") String saveGameId) {
        GameSession game = this.gameSessionHelper.createGameSession(userProfileId);
        return game;
    }

    @PutMapping(value = "/game", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public GameSession update(@RequestParam(required = false) boolean isTestMode,
                              @RequestBody GameSession gameSession) {
        GameSession game = this.gameSessionHelper.updateGameSession(gameSession, isTestMode);
        return game;
    }

    @PutMapping(value = "/game/next", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public GameSession nextGameState(@RequestParam String gameCode) {
        GameSession game = this.gameSessionHelper.updateToNextGameState(gameCode);
        return game;
    }

}
