package client.nowhere.controller;

import client.nowhere.helper.GameSessionHelper;
import client.nowhere.model.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PlayerController {

    private final GameSessionHelper gameSessionHelper;

    @Autowired
    public PlayerController(GameSessionHelper gameSessionHelper) {
        this.gameSessionHelper = gameSessionHelper;
    }

    @PostMapping("/player")
    public String create(@RequestParam String gameCode,
                         @RequestParam String playerFirstName,
                         @RequestParam String playerLastName) {
        Player player = this.gameSessionHelper.joinPlayer(gameCode, playerFirstName, playerLastName);
        return player.getFirstName();
    }

}
