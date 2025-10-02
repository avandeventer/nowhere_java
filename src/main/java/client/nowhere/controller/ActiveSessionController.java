package client.nowhere.controller;

import client.nowhere.helper.ActiveSessionHelper;
import client.nowhere.helper.CollaborativeTextHelper;
import client.nowhere.model.*;
import client.nowhere.exception.GameStateException;

import java.util.List;

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
    public ActivePlayerSession nextPlayerTurn(
            @RequestParam String gameCode,
            @RequestParam String currentTurnPlayerId
    ) {
        return this.activeSessionHelper.nextPlayerTurn(gameCode, currentTurnPlayerId);
    }

    @PutMapping("/activeGameStateSession")
    @ResponseBody
    public void updateGameStateSession(@RequestParam String gameCode,
                                                         @RequestParam String gamePhase,
                                                         @RequestParam String authorId,
                                                         @RequestParam boolean isDone) {
        if (gamePhase == null || gamePhase.trim().isEmpty()) {
            throw new GameStateException("Game phase parameter cannot be null or empty");
        }
        
        try {
            GameState gameStateEnum = GameState.valueOf(gamePhase.toUpperCase().trim());
            System.out.println("Processing player done update - Game: " + gameCode + ", Phase: " + gameStateEnum + ", Player: " + authorId + ", Done: " + isDone);
            this.activeSessionHelper.update(gameCode, gameStateEnum, authorId, isDone);
        } catch (IllegalArgumentException e) {
            throw new GameStateException("Invalid game phase: " + gamePhase + ". Valid phases: " + 
                java.util.Arrays.toString(GameState.values()));
        }
    }
}