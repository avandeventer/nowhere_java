package client.nowhere.controller;

import client.nowhere.helper.EndingHelper;
import client.nowhere.model.Ending;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(maxAge = 3600)
@RestController
public class EndingController {

    private final EndingHelper endingHelper;

    @Autowired
    public EndingController(EndingHelper endingHelper) { this.endingHelper = endingHelper; }

    @GetMapping("/ending")
    public Ending getAuthorEnding(
            @RequestParam String gameCode,
            @RequestParam String authorId
    ) {
        return endingHelper.getAuthorEnding(gameCode, authorId);
    }

    @PutMapping("/adventure-ending")
    public Ending getPlayerEnding (
            @RequestParam String gameCode,
            @RequestParam String playerId
    ) {
        return endingHelper.getPlayerEnding(gameCode, playerId);
    }
}
