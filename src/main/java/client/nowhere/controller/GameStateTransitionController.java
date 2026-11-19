package client.nowhere.controller;

import client.nowhere.exception.ResourceException;
import client.nowhere.helper.GameStateTransitionHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Called only by the Cloud Tasks queue (CloudTasksTransitionQueue) to run a game state transition
 * that a done-flag request locked. Not for clients: requests without the queue's token are refused.
 */
@RestController
public class GameStateTransitionController {

    private final GameStateTransitionHelper gameStateTransitionHelper;

    @Autowired
    public GameStateTransitionController(GameStateTransitionHelper gameStateTransitionHelper) {
        this.gameStateTransitionHelper = gameStateTransitionHelper;
    }

    @PostMapping("/internal/transitions")
    public ResponseEntity<Void> runTransition(@RequestParam String gameCode,
                                              @RequestParam String lockId,
                                              @RequestHeader(value = "Authorization", required = false) String authorization) {
        try {
            gameStateTransitionHelper.verifyQueuedRequest(authorization);
        } catch (ResourceException | IllegalStateException e) {
            System.out.println("Refused /internal/transitions for game " + gameCode + ": " + e.getMessage());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        // Always 200 once verified: a failed transition releases its lock, so a retry would do
        // nothing anyway, and PUT /game/next can take over
        gameStateTransitionHelper.runQueued(gameCode, lockId);
        return ResponseEntity.ok().build();
    }
}
