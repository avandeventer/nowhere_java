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
    public GameSession create(
                @RequestParam(required = false, defaultValue = "d0e5fa1e-e22a-4609-8274-e34df7f7c302") String userProfileId,//"c8d068ae-e180-44c9-940c-011ba632cba4") String userProfileId,
                @RequestParam(required = false) String adventureId,
                @RequestParam(required = false, defaultValue = "d9cb0595-86fe-4aac-bfce-918e212a4508") String saveGameId,//"4b8a146a-ccf6-41cf-961a-65096d70bf82") String saveGameId,
                @RequestParam(required = false, defaultValue = "1") Integer storiesToWritePerRound,
                @RequestParam(required = false, defaultValue =  "1") Integer storiesToPlayPerRound
    ) {
        GameSession game = this.gameSessionHelper.createGameSession(userProfileId, adventureId, saveGameId, storiesToWritePerRound, storiesToPlayPerRound);
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
