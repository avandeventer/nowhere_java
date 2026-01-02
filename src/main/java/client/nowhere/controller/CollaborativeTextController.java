package client.nowhere.controller;

import client.nowhere.helper.CollaborativeTextHelper;
import client.nowhere.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@CrossOrigin(maxAge = 3600)
@RestController
public class CollaborativeTextController {

    private final CollaborativeTextHelper collaborativeTextHelper;

    @Autowired
    public CollaborativeTextController(CollaborativeTextHelper collaborativeTextHelper) {
        this.collaborativeTextHelper = collaborativeTextHelper;
    }

    @PostMapping("/collaborativeText")
    @ResponseBody
    public CollaborativeTextPhase submitTextAddition(@RequestBody TextAddition textAddition, @RequestParam String gameCode) {
        return this.collaborativeTextHelper.submitTextAddition(gameCode, textAddition);
    }

    @PostMapping("/collaborativeText/votes")
    @ResponseBody
    public CollaborativeTextPhase submitPlayerVotes(@RequestBody List<PlayerVote> playerVotes, @RequestParam String gameCode) {
        return this.collaborativeTextHelper.submitPlayerVotes(gameCode, playerVotes);
    }

    @GetMapping("/collaborativeText")
    @ResponseBody
    public CollaborativeTextPhase getCollaborativeTextPhase(@RequestParam String gameCode) {
        return this.collaborativeTextHelper.getCollaborativeTextPhase(gameCode);
    }

    @GetMapping("/collaborativeText/winner")
    @ResponseBody
    public List<TextSubmission> getWinningSubmission(@RequestParam String gameCode) {
        return this.collaborativeTextHelper.calculateWinningSubmission(gameCode);
    }

    @GetMapping("/collaborativeText/available")
    @ResponseBody
    public List<TextSubmission> getAvailableSubmissions(@RequestParam String gameCode, @RequestParam String playerId, @RequestParam(defaultValue = "2") int requestedCount, @RequestParam (required = false) String outcomeTypeId) {
        return this.collaborativeTextHelper.getAvailableSubmissionsForPlayer(gameCode, playerId, requestedCount, outcomeTypeId);
    }

    @GetMapping("/collaborativeText/voting")
    @ResponseBody
    public List<TextSubmission> getVotingSubmissions(@RequestParam String gameCode, @RequestParam String playerId) {
        return this.collaborativeTextHelper.getVotingSubmissionsForPlayer(gameCode, playerId);
    }

    @GetMapping("/collaborativeText/outcomeType")
    @ResponseBody
    public OutcomeType getOutcomeTypeForPlayer(@RequestParam String gameCode, @RequestParam String playerId) {
        return this.collaborativeTextHelper.getOutcomeTypeForPlayer(gameCode, playerId);
    }

    @GetMapping("/collaborativeText/phaseInfo")
    @ResponseBody
    public CollaborativeTextPhaseInfo getCollaborativeTextPhaseInfo(@RequestParam String gameCode) {
        return this.collaborativeTextHelper.getCollaborativeTextPhaseInfo(gameCode);
    }

    @GetMapping("/collaborativeText/outcomeTypes")
    @ResponseBody
    public List<OutcomeType> getOutcomeTypes(@RequestParam String gameCode, @RequestParam String playerId) {
        return this.collaborativeTextHelper.getOutcomeTypes(gameCode, playerId);
    }
}
