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
    private final CollaborativeTextHelper collaborativeTextHelper;

    @Autowired
    public ActiveSessionController(ActiveSessionHelper activeSessionHelper, CollaborativeTextHelper collaborativeTextHelper) {
        this.activeSessionHelper = activeSessionHelper;
        this.collaborativeTextHelper = collaborativeTextHelper;
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

    // ===== COLLABORATIVE TEXT ENDPOINTS =====

    /**
     * Unified endpoint for text submissions - accepts only TextAddition objects
     * - If submissionId is provided: creates a new submission branching from the parent
     * - If submissionId is null/empty: creates a new submission with empty originalText
     */
    @PostMapping("/collaborativeText")
    @ResponseBody
    public CollaborativeTextPhase submitTextAddition(@RequestBody TextAddition textAddition, @RequestParam String gameCode) {
        return this.collaborativeTextHelper.submitTextAddition(gameCode, textAddition);
    }

    @PostMapping("/collaborativeText/vote")
    @ResponseBody
    public CollaborativeTextPhase submitPlayerVote(@RequestBody PlayerVote playerVote, @RequestParam String gameCode) {
        return this.collaborativeTextHelper.submitPlayerVote(gameCode, playerVote);
    }

    @GetMapping("/collaborativeText")
    @ResponseBody
    public CollaborativeTextPhase getCollaborativeTextPhase(@RequestParam String gameCode) {
        return this.collaborativeTextHelper.getCollaborativeTextPhase(gameCode);
    }

    @GetMapping("/collaborativeText/winner")
    @ResponseBody
    public String getWinningSubmission(@RequestParam String gameCode) {
        return this.collaborativeTextHelper.calculateWinningSubmission(gameCode);
    }

    @GetMapping("/collaborativeText/available")
    @ResponseBody
    public List<TextSubmission> getAvailableSubmissions(@RequestParam String gameCode, @RequestParam String playerId) {
        return this.collaborativeTextHelper.getAvailableSubmissionsForPlayer(gameCode, playerId);
    }

    @PostMapping("/collaborativeText/view")
    @ResponseBody
    public void recordSubmissionView(@RequestParam String gameCode, @RequestParam String playerId, @RequestParam String submissionId) {
        this.collaborativeTextHelper.recordSubmissionView(gameCode, playerId, submissionId);
    }
}