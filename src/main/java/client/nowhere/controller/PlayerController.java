package client.nowhere.controller;

import client.nowhere.helper.GameSessionHelper;
import client.nowhere.model.Player;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(maxAge = 3600)
@RestController
public class PlayerController {

    private final GameSessionHelper gameSessionHelper;

    @Autowired
    public PlayerController(GameSessionHelper gameSessionHelper) {
        this.gameSessionHelper = gameSessionHelper;
    }

    @PostMapping(value = "/player", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Player create(@RequestBody Player player) throws Exception {
        if(player == null) {
            throw new Exception("Must include user name and game code");
        }

        this.gameSessionHelper.joinPlayer(player);
        return player;
    }

}
