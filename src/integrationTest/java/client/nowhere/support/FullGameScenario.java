package client.nowhere.support;

import client.nowhere.model.*;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plays one entire DUNGEON_MODE game end-to-end over HTTP, the same way the Angular clients
 * would: create a game against a known-good, already-authored adventure map, join N players
 * (assigning up to one of each PlayerClass - Scribe/Bard/Herald/Fabulist - in join order), then
 * drive every phase transition until the game reaches ENDING.
 *
 * Deliberately does NOT author its own AdventureMap/Location/startingStories content - building
 * that content correctly (locations, starting stories, options, outcome forks) is a real content-
 * authoring task, not something worth re-deriving in a test fixture. Instead this reuses a real,
 * already-populated adventure map (see LIVE_USER_PROFILE_ID / LIVE_ADVENTURE_ID) the same way
 * POST /game does for any real game.
 *
 * Round 1's HOW_DOES_THIS_RESOLVE is not left generic: every naturally-assigned player submits
 * their default root resolution (as every other phase does), and then real repercission scenarios
 * are layered on top - one per available PlayerClass, each targeting a DIFFERENT player's story
 * (never a player's own) via GameFlowClient.addToSubmission, mirroring the real player-client's
 * onAddToSubmission (see nowhere-player-client's collaborative-text.component.ts). This is real
 * branching onto an existing submission, not two independent root submissions - see
 * src/integrationTest/README.md for why that distinction matters and how it was corrected
 * mid-session.
 *
 * Every vote afterwards (MAKE_CHOICE_VOTING / MAKE_OUTCOME_CHOICE_VOTING) checks a small set of
 * "preferred" submission/option ids collected while building those scenarios: if a candidate
 * matches, every voter votes for it, guaranteeing a specific fork/option wins instead of leaving
 * it to candidate order. This is what makes each PlayerClass's effect deterministically provable
 * (not a coin flip) and also lets the Title scenario deliberately prove real, multi-candidate
 * MAKE_CHOICE_VOTING / MAKE_OUTCOME_CHOICE_VOTING voting resolves correctly rather than only ever
 * exercising the single-candidate auto-select path.
 *
 * Effects are asserted the first time round 2's WHAT_HAPPENS_HERE is observed (round 1 is fully
 * resolved by then, round 2 hasn't created any new stories yet) - see assertRoundOneEffects.
 */
public class FullGameScenario {

    private static final int MAX_ITERATIONS = 400;

    // Known-good, already-authored profile/adventure map with real starting locations/stories.
    private static final String LIVE_USER_PROFILE_ID = "c8d068ae-e180-44c9-940c-011ba632cba4";
    private static final String LIVE_ADVENTURE_ID = "b371256a-015a-4a72-8cc2-8f5ad6b40cd4";

    // One PlayerClass per player, in join order, up to however many of the four exist for the
    // requested player count. Player counts below 4 simply cover fewer classes; counts above 4
    // leave the extra players class-less, which doubles as a "no repercussion" control case.
    private static final List<String> CLASS_NAMES_IN_JOIN_ORDER = List.of("Scribe", "Bard", "Herald", "Fabulist");

    private static final String TRAIT_ALONE_LABEL = "Steadfast";
    private static final String COMBINED_TRAIT_LABEL = "Battle-Scarred";
    private static final String TITLE_LABEL = "Voice of the Vale";
    private static final String ALL_PLAYERS_REPERCUSSION_NAME = "All Players";
    private static final String WHAT_HAPPENS_HERE_TRAIT_LABEL = "Watchful";

    private final GameFlowClient client;
    private final List<String> playerAuthorIds = new ArrayList<>();
    private final Map<String, String> classNameByAuthorId = new LinkedHashMap<>();
    // userName (Player1, Player2, ...) instead of the raw authorId UUID in any text sent to the
    // server, so live game data is readable by eye when debugging a failed run.
    private final Map<String, String> userNameByAuthorId = new LinkedHashMap<>();

    // Round-1 HOW_DOES_THIS_RESOLVE bookkeeping, built once and read by the round-2 assertions.
    private final Map<String, ResolvedOption> rootByResolver = new LinkedHashMap<>();
    private final Set<String> knownSubmissionIds = new HashSet<>();
    private final Set<String> preferredWinningSubmissionIds = new HashSet<>();
    private ScenarioTarget companionTarget;
    private ScenarioTarget traitAloneTarget;
    private ScenarioTarget combinedTarget;
    private ScenarioTarget titleTarget;
    private boolean roundOneScenariosBuilt = false;
    private boolean roundOneEffectsAsserted = false;

    // Round-1 WHAT_HAPPENS_HERE bookkeeping - see .CLASSES.md, "WHAT_HAPPENS_HERE-time
    // repercussions stack with the winning fork's own". Fields below are authors, not resolvers,
    // since no Story/storyId exists yet at WHAT_HAPPENS_HERE time (.PLAYER_LIFECYCLE.md, "WHAT_CAN_WE_TRY").
    private final Map<String, String> whatHappensHereRootByAuthor = new LinkedHashMap<>();
    private boolean whatHappensHereScenariosBuilt = false;
    private String bardWhatHappensHereStackAuthorId;
    private String scribeWhatHappensHereSoloAuthorId;
    private ScenarioTarget bardStackTarget;

    // Opt-in alternate mode: instead of the 4-class scenario above, builds a single ALL_PLAYERS-
    // alone repercussion and splits round 2's LOCATION_VOTING so players end up at different
    // locations - needed to test that a round-2 "sequel" story's playerIds are scoped to players
    // at the sequel's own location (CollaborativeTextHelper.handleWhatHappensHereStreamlined's
    // clarifier/prequelStory branch), not every player in the game.
    private boolean sequelLocationScopeMode = false;
    private ScenarioTarget allPlayersAloneTarget;
    private boolean sequelLocationScopeAsserted = false;

    /** A player's root HOW_DOES_THIS_RESOLVE submission for their naturally assigned story. */
    private record ResolvedOption(String storyId, String optionId, String rootSubmissionId) {}

    /** Where a repercussion scenario landed, and which fork it expects to have won. */
    private record ScenarioTarget(String storyId, String optionId, String preferredForkSubmissionId) {}

    public FullGameScenario(RestTemplate restTemplate) {
        this.client = new GameFlowClient(restTemplate);
    }

    public GameSession play(int playerCount) {
        return play(playerCount, false);
    }

    public GameSession play(int playerCount, boolean sequelLocationScopeMode) {
        this.sequelLocationScopeMode = sequelLocationScopeMode;
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        GameSession game = client.createGame(LIVE_USER_PROFILE_ID, LIVE_ADVENTURE_ID);
        String gameCode = game.getGameCode();
        System.out.println("[FullGameScenario] created gameCode=" + gameCode);

        String startingLocationId = game.getAdventureMap().getLocations().stream()
                .filter(Location::isStartingLocation)
                .map(Location::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Adventure map has no startingLocation=true entry"));

        joinPlayers(gameCode, playerCount, suffix, startingLocationId);

        for (String authorId : playerAuthorIds) {
            client.markPhaseDone(gameCode, "INIT", authorId);
        }

        return runMainLoop(gameCode);
    }

    public List<String> getPlayerAuthorIds() {
        return playerAuthorIds;
    }

    // ===== Setup =====

    private void joinPlayers(String gameCode, int playerCount, String suffix, String startingLocationId) {
        for (int i = 0; i < playerCount; i++) {
            Player player = client.joinPlayer(gameCode, "Player" + (i + 1));
            String authorId = player.getAuthorId();
            playerAuthorIds.add(authorId);
            userNameByAuthorId.put(authorId, player.getUserName());

            // Order matters here: Player.updatePlayer null-guards selectedLocationId (only
            // overwrites if the incoming body has one) but NOT playerClass (always overwrites,
            // even with null). A fresh Player() sent by selectStartingLocation has playerClass
            // unset, so calling it AFTER selectPlayerClass would silently wipe the class
            // selection back to null. Doing location first, then class, means whichever call
            // sets playerClass runs last and sticks, while location (guarded) survives either way.
            client.selectStartingLocation(gameCode, authorId, startingLocationId);

            if (i < CLASS_NAMES_IN_JOIN_ORDER.size()) {
                String desiredClassName = CLASS_NAMES_IN_JOIN_ORDER.get(i);
                PlayerClassOption chosenClass = client.getPlayerClasses().stream()
                        .filter(playerClass -> desiredClassName.equals(playerClass.getName()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                "No PlayerClass named '" + desiredClassName + "' returned by GET /player/classes"));
                client.selectPlayerClass(gameCode, authorId, chosenClass);
                classNameByAuthorId.put(authorId, desiredClassName);
            }
        }
    }

    // ===== Main loop =====

    private GameSession runMainLoop(String gameCode) {
        GameSession finalState = null;
        GameSession lastSeenState = null;
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            GameSession game = client.getGame(gameCode);
            lastSeenState = game;
            GameState gameState = game.getGameState();
            int roundNumber = game.getRoundNumber() == null ? 0 : game.getRoundNumber();
            int xCoordinate = -1;
            int yCoordinate = -1;
            String encounterLabel = "none";
            if (game.getGameBoard() != null && game.getGameBoard().getPlayerCoordinates() != null) {
                xCoordinate = game.getGameBoard().getPlayerCoordinates().getxCoordinate();
                yCoordinate = game.getGameBoard().getPlayerCoordinates().getyCoordinate();
                Encounter currentEncounter = game.getGameBoard().getEncounterAtPlayerCoordinates();
                if (currentEncounter != null && currentEncounter.getEncounterLabel() != null) {
                    encounterLabel = currentEncounter.getEncounterLabel().getEncounterLabel();
                }
            }

            System.out.println("[FullGameScenario] gameCode=" + gameCode + " iteration=" + iteration
                    + " gameState=" + gameState + " roundNumber=" + roundNumber + " " + " playerCoordinates=(" + xCoordinate + "," + yCoordinate + ")" + " encounterLabel=" + encounterLabel);

            // ENDING is a terminal display state too: its PhaseBaseInfo currently misreports
            // phaseType VOTING (see src/integrationTest/README.md, "Known bug ... ENDING
            // misclassified as a voting phase"), and the ENDING/EPILOGUE mechanic is being
            // reworked, so treat reaching it as the game being over rather than attempting phase
            // actions on it.
            if (gameState == GameState.FINALE || gameState == GameState.ENDING) {
                finalState = game;
                break;
            }

            if (!roundOneEffectsAsserted && gameState == GameState.WHAT_HAPPENS_HERE && roundNumber == 2) {
                assertRoundOneEffects(gameCode);
                roundOneEffectsAsserted = true;
            }

            // Runs one iteration later than the check above: round 2's WHAT_HAPPENS_HERE needs to
            // have actually resolved (creating the sequel story) before there's anything to assert.
            if (sequelLocationScopeMode && !sequelLocationScopeAsserted
                    && gameState == GameState.WHAT_CAN_WE_TRY && roundNumber == 2) {
                assertSequelLocationScopeEffects(gameCode);
                sequelLocationScopeAsserted = true;
            }

            try {
                runPhaseActions(gameCode, gameState, roundNumber);
                // The server can auto-advance gameState as a side effect of the last player's
                // action (see ActiveSessionHelper.update(): when the final player is marked done,
                // it calls GameSessionHelper.updateToNextGameState() itself, inside that same
                // request). Calling PUT /game/next unconditionally after that would advance an
                // already-advanced state a second time. Re-check before calling next().
                GameState stateAfterActions = client.getGame(gameCode).getGameState();
                if (gameState == stateAfterActions) {
                    client.nextGameState(gameCode);
                }
            } catch (RuntimeException e) {
                System.out.println("[FullGameScenario] FAILED gameCode=" + gameCode
                        + " at gameState=" + gameState + " iteration=" + iteration + ": " + e.getMessage());
                throw e;
            }
        }

        if (finalState == null) {
            GameState lastState = lastSeenState == null ? null : lastSeenState.getGameState();
            throw new IllegalStateException(
                    "Game did not reach FINALE/ENDING within " + MAX_ITERATIONS + " iterations. Last state: " + lastState);
        }
        return finalState;
    }

    private void runPhaseActions(String gameCode, GameState gameState, int roundNumber) {
        if (!sequelLocationScopeMode && gameState == GameState.WHAT_HAPPENS_HERE && roundNumber == 1) {
            buildWhatHappensHereRepercussionScenarios(gameCode);
            return;
        }

        if (gameState == GameState.HOW_DOES_THIS_RESOLVE && roundNumber == 1) {
            if (sequelLocationScopeMode) {
                buildSequelLocationScopeRepercussion(gameCode);
            } else {
                buildRoundOneRepercussionScenarios(gameCode);
            }
            return;
        }

        if (sequelLocationScopeMode && gameState == GameState.LOCATION_VOTING && roundNumber == 2) {
            runSplitLocationVote(gameCode);
            return;
        }

        CollaborativeTextPhaseInfo phaseInfo = client.getPhaseInfo(gameCode);
        if (phaseInfo == null) {
            return; // no-op / display-only state (PREAMBLE, ENDING_PREAMBLE, ENDING, NAVIGATE_WINNER, ...)
        }

        PhaseType phaseType = phaseInfo.phaseType();
        if (phaseType == null) {
            return;
        }

        switch (phaseType) {
            case SUBMISSION -> runGenericSubmissionPhase(gameCode, gameState);
            case VOTING -> runVotingPhase(gameCode, gameState);
            case WINNING -> { /* computed server-side already, nothing to do */ }
        }
    }

    private void runGenericSubmissionPhase(String gameCode, GameState gameState) {
        for (String authorId : playerAuthorIds) {
            List<OutcomeType> outcomeTypes = client.getOutcomeTypes(gameCode, authorId);
            if (outcomeTypes.isEmpty()) {
                continue;
            }
            OutcomeType parent = outcomeTypes.get(0);
            List<OutcomeType> subTypes = parent.getSubTypes();
            String text = "Integration test submission for " + gameState + " by " + userNameByAuthorId.get(authorId);
            if (subTypes != null && !subTypes.isEmpty()) {
                // Mirror the real player-client: keep the parent wrapper, narrow subTypes to the
                // one chosen child, rather than flattening to the child alone (see
                // narrowToChosenSubType for why this matters).
                client.submitTextAddition(gameCode, authorId, narrowToChosenSubType(parent, subTypes.get(0)), text, "", "");
            } else {
                client.submitTextAddition(gameCode, authorId, parent, text, "", "");
            }
        }
    }

    private void runVotingPhase(String gameCode, GameState gameState) {
        boolean choosingFromOwnTraits = gameState == GameState.DEFINING_TRAITS_VOTING;
        for (String authorId : playerAuthorIds) {
            String submissionId;
            if (choosingFromOwnTraits) {
                List<OutcomeType> outcomeTypes = client.getOutcomeTypes(gameCode, authorId);
                if (outcomeTypes.isEmpty()) {
                    continue;
                }
                submissionId = outcomeTypes.get(0).getId();
            } else {
                List<TextSubmission> candidates = client.getVotingCandidates(gameCode, authorId);
                if (candidates.isEmpty()) {
                    continue;
                }
                // Vote for whichever candidate we deliberately built a scenario around, if any -
                // otherwise fall back to the first candidate, same as before. This is what makes
                // MAKE_CHOICE_VOTING/MAKE_OUTCOME_CHOICE_VOTING resolve deterministically for the
                // stories buildRoundOneRepercussionScenarios set up, instead of depending on
                // candidate order.
                submissionId = candidates.stream()
                        .map(TextSubmission::getSubmissionId)
                        .filter(preferredWinningSubmissionIds::contains)
                        .findFirst()
                        .orElseGet(() -> candidates.get(0).getSubmissionId());
            }
            if (submissionId == null || submissionId.isEmpty()) {
                continue;
            }
            client.submitVote(gameCode, authorId, submissionId);
        }
    }

    /**
     * sequelLocationScopeMode only: splits round 2's LOCATION_VOTING roughly in half instead of
     * every player voting for the same first candidate, so Player.selectedLocationId genuinely
     * diverges afterward - a real vote through the real LOCATION_VOTING mechanism (getVotingCandidates
     * / submitVote), not a direct field override. Without this, every player ends up at the same
     * location every round (today's generic runVotingPhase behavior), and "players at the sequel
     * story's location" would trivially equal "every player", making it impossible to tell the new
     * location-scoped playerIds behavior apart from the old game-wide one.
     */
    private void runSplitLocationVote(String gameCode) {
        for (int i = 0; i < playerAuthorIds.size(); i++) {
            String authorId = playerAuthorIds.get(i);
            List<TextSubmission> candidates = client.getVotingCandidates(gameCode, authorId);
            if (candidates.isEmpty()) {
                continue;
            }
            boolean firstHalf = i < playerAuthorIds.size() / 2;
            int candidateIndex = (firstHalf || candidates.size() < 2) ? 0 : 1;
            client.submitVote(gameCode, authorId, candidates.get(candidateIndex).getSubmissionId());
        }
    }

    /**
     * Submits a text addition for an outcomeType that has subTypes (e.g. WHAT_HAPPENS_HERE's
     * location-wrapped encounter labels under the locationVoting flag, or HOW_DOES_THIS_RESOLVE's
     * story-wrapped options). Mirrors the real player-client's onSelectSubType
     * (collaborative-text.component.ts): keeps the parent's id/label/clarifier but narrows
     * subTypes down to just the one chosen child, instead of flattening to the child alone. The
     * backend's winner-calculation for these phases requires outcomeTypeWithLabel.subTypes to be
     * non-empty to attribute a submission to an outcomeType.
     */
    private static OutcomeType narrowToChosenSubType(OutcomeType parent, OutcomeType chosen) {
        OutcomeType narrowed = new OutcomeType();
        narrowed.setId(parent.getId());
        narrowed.setLabel(parent.getLabel());
        String clarifier = chosen.getClarifier();
        narrowed.setClarifier(clarifier != null && !clarifier.isEmpty() ? clarifier : parent.getClarifier());
        narrowed.setSubTypes(List.of(chosen));
        return narrowed;
    }

    // ===== Round 1 HOW_DOES_THIS_RESOLVE: repercussion scenarios =====

    /**
     * Step 1: every naturally-assigned player submits root resolution text for the first offered
     * option on their assigned story - the same default behavior runGenericSubmissionPhase would
     * have produced. Step 2: layer real, deterministic repercussion scenarios on top, one per
     * available PlayerClass, each targeting a DIFFERENT player's story via GameFlowClient's
     * addToSubmission (real branching - see class javadoc). Runs once; HOW_DOES_THIS_RESOLVE only
     * ever appears for round 1 (round 2 uses the differently-named HOW_DOES_THIS_RESOLVE_AGAIN),
     * so no re-entry guard is needed.
     */
    private void buildRoundOneRepercussionScenarios(String gameCode) {
        if (roundOneScenariosBuilt) {
            return;
        }
        roundOneScenariosBuilt = true;

        submitDefaultRootResolutions(gameCode);

        String scribeAuthorId = classNameByAuthorId.entrySet().stream()
                .filter(entry -> "Scribe".equals(entry.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);
        String bardAuthorId = classNameByAuthorId.entrySet().stream()
                .filter(entry -> "Bard".equals(entry.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);
        String heraldAuthorId = classNameByAuthorId.entrySet().stream()
                .filter(entry -> "Herald".equals(entry.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);
        String fabulistAuthorId = classNameByAuthorId.entrySet().stream()
                .filter(entry -> "Fabulist".equals(entry.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);

        Set<String> usedTargets = new HashSet<>();

        // Complete the WHAT_HAPPENS_HERE-time stack (see .CLASSES.md). Runs before the
        // pickTarget-based scenarios below so this resolver gets reserved first and no other
        // scenario also lands on it.
        if (bardWhatHappensHereStackAuthorId != null && fabulistAuthorId != null) {
            String resolverAuthorId = findResolverForStoryAuthoredBy(gameCode, bardWhatHappensHereStackAuthorId);
            if (resolverAuthorId != null) {
                usedTargets.add(resolverAuthorId);
                bardStackTarget = attachCompanionScenario(gameCode, fabulistAuthorId, resolverAuthorId);
            }
        }

        // Fabulist grants COMPANION, single fork -> auto-select.
        if (fabulistAuthorId != null) {
            String targetAuthorId = pickTarget(fabulistAuthorId, usedTargets);
            if (targetAuthorId != null) {
                usedTargets.add(targetAuthorId);
                companionTarget = attachCompanionScenario(gameCode, fabulistAuthorId, targetAuthorId);
            }
        }

        // Scribe grants TRAIT alone (no ALL_PLAYERS on the same fork), single fork -> auto-select.
        if (scribeAuthorId != null) {
            String targetAuthorId = pickTarget(scribeAuthorId, usedTargets);
            if (targetAuthorId != null) {
                usedTargets.add(targetAuthorId);
                attachTraitAloneScenario(gameCode, scribeAuthorId, targetAuthorId);
            }
        }

        // Scribe grants TRAIT, then Bard chains ALL_PLAYERS onto THAT addition (not onto the
        // root) - one fork whose full addition history carries both repercussions, proving
        // ALL_PLAYERS spreads a trait granted earlier in the same chain to the whole roster.
        if (scribeAuthorId != null && bardAuthorId != null) {
            String targetAuthorId = pickTarget(scribeAuthorId, usedTargets, bardAuthorId);
            if (targetAuthorId != null) {
                usedTargets.add(targetAuthorId);
                attachCombinedTraitAndAllPlayersScenario(gameCode, scribeAuthorId, bardAuthorId, targetAuthorId);
            }
        }

        // Herald grants TITLE, competing against a decoy sibling fork (real MAKE_OUTCOME_CHOICE_VOTING
        // vote) and a decoy second Option (real MAKE_CHOICE_VOTING vote) - both controlled to
        // deliberately resolve in Herald's favor via preferredWinningSubmissionIds.
        if (heraldAuthorId != null) {
            String targetAuthorId = pickTarget(heraldAuthorId, usedTargets);
            if (targetAuthorId != null) {
                usedTargets.add(targetAuthorId);
                String decoyAuthorId = playerAuthorIds.stream()
                        .filter(id -> !id.equals(heraldAuthorId) && !id.equals(targetAuthorId))
                        .findFirst().orElse(null);
                attachTitleScenario(gameCode, heraldAuthorId, targetAuthorId, decoyAuthorId);
            }
        }
    }

    /**
     * sequelLocationScopeMode only: builds a single round-1 story whose winning fork's ONLY
     * repercussion is ALL_PLAYERS (no accompanying TRAIT/TITLE/COMPANION) - the prequel-eligibility
     * combination described in .PLAYER_LIFECYCLE.md, "Prequel stories". Deliberately does not build
     * the other 4 class scenarios - this mode is only exercised together with runSplitLocationVote,
     * which this file's other tests don't need and shouldn't risk being entangled with.
     */
    private void buildSequelLocationScopeRepercussion(String gameCode) {
        if (roundOneScenariosBuilt) {
            return;
        }
        roundOneScenariosBuilt = true;

        submitDefaultRootResolutions(gameCode);

        String bardAuthorId = classNameByAuthorId.entrySet().stream()
                .filter(entry -> "Bard".equals(entry.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);
        if (bardAuthorId == null) {
            throw new IllegalStateException("sequelLocationScopeMode requires a Bard player to grant ALL_PLAYERS");
        }

        String targetAuthorId = pickTarget(bardAuthorId, new HashSet<>());
        if (targetAuthorId == null) {
            throw new IllegalStateException("No available target story for the ALL_PLAYERS-alone scenario");
        }
        ResolvedOption target = rootByResolver.get(targetAuthorId);
        String allPlayersType = onlyRepercussionTypeName(gameCode, bardAuthorId);

        // Single branch, single fork -> auto-select, no forced voting needed: this test isn't
        // about fork voting, just about what happens to this ALL_PLAYERS-alone story's round-2
        // continuation.
        CollaborativeTextPhase response = client.addToSubmission(gameCode, bardAuthorId, target.rootSubmissionId(),
                "Bard senses this will echo for everyone who was here.", allPlayersType, ALL_PLAYERS_REPERCUSSION_NAME);
        String forkId = claimNewSubmissionId(response);
        allPlayersAloneTarget = new ScenarioTarget(target.storyId(), target.optionId(), forkId);
    }

    /**
     * Round-1 WHAT_HAPPENS_HERE: every player submits their own root encounter write-up as usual,
     * then Bard attaches ALL_PLAYERS alone onto another author's encounter (completed into a real
     * stacked effect later by buildRoundOneRepercussionScenarios) and Scribe attaches TRAIT alone
     * onto a different author's - see .CLASSES.md, "WHAT_HAPPENS_HERE-time repercussions stack with
     * the winning fork's own". Runs once; WHAT_HAPPENS_HERE only appears for round 1 with this shape
     * (round 2 offers prequel stories too, which this scenario doesn't need).
     */
    private void buildWhatHappensHereRepercussionScenarios(String gameCode) {
        if (whatHappensHereScenariosBuilt) {
            return;
        }
        whatHappensHereScenariosBuilt = true;

        submitDefaultWhatHappensHereRoots(gameCode);

        String scribeAuthorId = classNameByAuthorId.entrySet().stream()
                .filter(entry -> "Scribe".equals(entry.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);
        String bardAuthorId = classNameByAuthorId.entrySet().stream()
                .filter(entry -> "Bard".equals(entry.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);

        Set<String> usedWhatHappensHereTargets = new HashSet<>();

        if (bardAuthorId != null) {
            String targetAuthorId = pickWhatHappensHereTarget(bardAuthorId, usedWhatHappensHereTargets);
            if (targetAuthorId != null) {
                usedWhatHappensHereTargets.add(targetAuthorId);
                String allPlayersType = onlyRepercussionTypeName(gameCode, bardAuthorId);
                String rootSubmissionId = whatHappensHereRootByAuthor.get(targetAuthorId);
                CollaborativeTextPhase response = client.addToSubmission(gameCode, bardAuthorId, rootSubmissionId,
                        "Bard weaves this telling so it echoes for everyone who hears it.",
                        allPlayersType, ALL_PLAYERS_REPERCUSSION_NAME);
                claimNewSubmissionId(response);
                bardWhatHappensHereStackAuthorId = targetAuthorId;
            }
        }

        if (scribeAuthorId != null) {
            String targetAuthorId = pickWhatHappensHereTarget(scribeAuthorId, usedWhatHappensHereTargets, bardAuthorId);
            if (targetAuthorId != null) {
                usedWhatHappensHereTargets.add(targetAuthorId);
                String traitType = onlyRepercussionTypeName(gameCode, scribeAuthorId);
                String rootSubmissionId = whatHappensHereRootByAuthor.get(targetAuthorId);
                CollaborativeTextPhase response = client.addToSubmission(gameCode, scribeAuthorId, rootSubmissionId,
                        "Scribe notes something here worth remembering.", traitType, WHAT_HAPPENS_HERE_TRAIT_LABEL);
                claimNewSubmissionId(response);
                scribeWhatHappensHereSoloAuthorId = targetAuthorId;
            }
        }
    }

    /**
     * Every player submits their own root WHAT_HAPPENS_HERE encounter write-up - the same default
     * behavior runGenericSubmissionPhase would have produced - recording each author's submissionId
     * so buildWhatHappensHereRepercussionScenarios can branch a repercussion onto a specific target.
     */
    private void submitDefaultWhatHappensHereRoots(String gameCode) {
        for (String authorId : playerAuthorIds) {
            List<OutcomeType> outcomeTypes = client.getOutcomeTypes(gameCode, authorId);
            if (outcomeTypes.isEmpty()) {
                continue;
            }
            OutcomeType parent = outcomeTypes.get(0);
            List<OutcomeType> subTypes = parent.getSubTypes();
            if (subTypes == null || subTypes.isEmpty()) {
                continue;
            }
            OutcomeType chosenEncounter = subTypes.get(0);

            CollaborativeTextPhase response = client.submitTextAddition(gameCode, authorId,
                    narrowToChosenSubType(parent, chosenEncounter), "Encounter write-up by " + userNameByAuthorId.get(authorId), "", "");
            String rootId = claimNewSubmissionId(response);
            whatHappensHereRootByAuthor.put(authorId, rootId);
        }
    }

    /** First author, in join order, with a known WHAT_HAPPENS_HERE root that isn't the granter and isn't already used. */
    private String pickWhatHappensHereTarget(String granterAuthorId, Set<String> used, String... alsoExclude) {
        Set<String> excluded = new HashSet<>(used);
        excluded.add(granterAuthorId);
        for (String exclude : alsoExclude) {
            if (exclude != null) {
                excluded.add(exclude);
            }
        }
        return playerAuthorIds.stream()
                .filter(whatHappensHereRootByAuthor::containsKey)
                .filter(authorId -> !excluded.contains(authorId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Every naturally-assigned player submits root resolution text for the first offered option on
     * their assigned story - the same default behavior runGenericSubmissionPhase would have
     * produced. Shared by both round-1 scenario builders; each layers its own repercussion
     * scenario(s) on top of these roots.
     */
    private void submitDefaultRootResolutions(String gameCode) {
        for (String authorId : playerAuthorIds) {
            List<OutcomeType> outcomeTypes = client.getOutcomeTypes(gameCode, authorId);
            if (outcomeTypes.isEmpty()) {
                continue;
            }
            OutcomeType parent = outcomeTypes.get(0);
            List<OutcomeType> subTypes = parent.getSubTypes();
            if (subTypes == null || subTypes.isEmpty()) {
                continue;
            }
            OutcomeType chosenOption = subTypes.get(0);

            CollaborativeTextPhase response = client.submitTextAddition(gameCode, authorId,
                    narrowToChosenSubType(parent, chosenOption), "Root resolution by " + userNameByAuthorId.get(authorId), "", "");
            String rootId = claimNewSubmissionId(response);
            rootByResolver.put(authorId, new ResolvedOption(parent.getId(), chosenOption.getId(), rootId));
        }
    }

    /** First resolved story's author, in join order, that isn't the granter and isn't already used. */
    private String pickTarget(String granterAuthorId, Set<String> used, String... alsoExclude) {
        Set<String> excluded = new HashSet<>(used);
        excluded.add(granterAuthorId);
        excluded.addAll(List.of(alsoExclude));
        return playerAuthorIds.stream()
                .filter(rootByResolver::containsKey)
                .filter(authorId -> !excluded.contains(authorId))
                .findFirst()
                .orElse(null);
    }

    private ScenarioTarget attachCompanionScenario(String gameCode, String fabulistAuthorId, String targetAuthorId) {
        ResolvedOption target = rootByResolver.get(targetAuthorId);
        String repercussionType = onlyRepercussionTypeName(gameCode, fabulistAuthorId);
        // Real client behavior (collaborative-text.component.ts, onRepercussionChange): for a
        // Companion repercussion, the client doesn't leave repercussionSubmission blank - it
        // fills it in automatically with the target story's own encounter label. Skipping this
        // (submitting "") meant GameFlowClient's now-required "both type and submission must be
        // non-empty to attach a repercussion" guard silently dropped the repercussion entirely,
        // so handleRepercussions's COMPANION branch never ran and no trait was ever granted.
        String encounterLabel = findEncounterLabel(gameCode, target.storyId());
        CollaborativeTextPhase response = client.addToSubmission(gameCode, fabulistAuthorId, target.rootSubmissionId(),
                "Fabulist recognizes a kindred spirit in this encounter.", repercussionType, encounterLabel);
        String forkId = claimNewSubmissionId(response);
        return new ScenarioTarget(target.storyId(), target.optionId(), forkId);
    }

    private String findEncounterLabel(String gameCode, String storyId) {
        Story story = findStoryById(client.getGame(gameCode).getStories(), storyId);
        if (story == null || story.getEncounterLabel() == null || story.getEncounterLabel().getEncounterLabel() == null) {
            throw new IllegalStateException("Story " + storyId + " has no EncounterLabel to use for a Companion repercussion");
        }
        return story.getEncounterLabel().getEncounterLabel();
    }

    /**
     * Finds whichever resolver rootByResolver ended up assigning to resolve the round-1 story a
     * given player authored during WHAT_HAPPENS_HERE - author and resolver aren't necessarily the
     * same player (.PLAYER_LIFECYCLE.md, "WHAT_CAN_WE_TRY"). Must be called after
     * submitDefaultRootResolutions has populated rootByResolver. Returns null if the story can't be
     * found yet or wasn't assigned to any of this game's resolvers (shouldn't happen at these player
     * counts, but this scenario is skipped rather than failing hard if it ever does).
     */
    private String findResolverForStoryAuthoredBy(String gameCode, String storyAuthorId) {
        List<Story> stories = client.getGame(gameCode).getStories();
        Story authoredStory = findRoundOneStoryByAuthorId(stories, storyAuthorId);
        if (authoredStory == null) {
            return null;
        }
        return rootByResolver.entrySet().stream()
                .filter(entry -> entry.getValue().storyId().equals(authoredStory.getStoryId()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private void attachTraitAloneScenario(String gameCode, String scribeAuthorId, String targetAuthorId) {
        ResolvedOption target = rootByResolver.get(targetAuthorId);
        String repercussionType = onlyRepercussionTypeName(gameCode, scribeAuthorId);
        CollaborativeTextPhase response = client.addToSubmission(gameCode, scribeAuthorId, target.rootSubmissionId(),
                "Scribe notes their steadfast resolve.", repercussionType, TRAIT_ALONE_LABEL);
        String forkId = claimNewSubmissionId(response);
        traitAloneTarget = new ScenarioTarget(target.storyId(), target.optionId(), forkId);
    }

    private void attachCombinedTraitAndAllPlayersScenario(String gameCode, String scribeAuthorId, String bardAuthorId, String targetAuthorId) {
        ResolvedOption target = rootByResolver.get(targetAuthorId);
        String traitType = onlyRepercussionTypeName(gameCode, scribeAuthorId);
        String allPlayersType = onlyRepercussionTypeName(gameCode, bardAuthorId);

        CollaborativeTextPhase traitResponse = client.addToSubmission(gameCode, scribeAuthorId, target.rootSubmissionId(),
                "Scribe records their courage for history.", traitType, COMBINED_TRAIT_LABEL);
        String chainMid = claimNewSubmissionId(traitResponse);

        // ALL_PLAYERS has no real text input in the UI (RepercussionType.ALL_PLAYERS.label is ""),
        // so the real client would send repercussionSubmission: "". GameFlowClient now requires a
        // non-empty repercussionSubmission to attach a repercussion at all (added to fix the
        // Companion scenario silently dropping its repercussion the same way), so this placeholder
        // exists purely to satisfy that harness-side guard - the server doesn't read
        // repercissionSubmission for ALL_PLAYERS's effect, only repercussionType.
        CollaborativeTextPhase spreadResponse = client.addToSubmission(gameCode, bardAuthorId, chainMid,
                "Bard makes sure the whole party hears of it.", allPlayersType, ALL_PLAYERS_REPERCUSSION_NAME);
        String chainFinal = claimNewSubmissionId(spreadResponse);

        combinedTarget = new ScenarioTarget(target.storyId(), target.optionId(), chainFinal);
    }

    private void attachTitleScenario(String gameCode, String heraldAuthorId, String targetAuthorId, String decoyAuthorId) {
        ResolvedOption target = rootByResolver.get(targetAuthorId);
        String titleType = onlyRepercussionTypeName(gameCode, heraldAuthorId);

        CollaborativeTextPhase titleResponse = client.addToSubmission(gameCode, heraldAuthorId, target.rootSubmissionId(),
                "Herald names them for this deed.", titleType, TITLE_LABEL);
        String titleForkId = claimNewSubmissionId(titleResponse);
        preferredWinningSubmissionIds.add(titleForkId);

        if (decoyAuthorId != null) {
            // A second, real sibling branch off the SAME root (not chained onto the Title fork)
            // -> a second real OutcomeFork, forcing a real MAKE_OUTCOME_CHOICE_VOTING vote.
            CollaborativeTextPhase decoyForkResponse = client.addToSubmission(gameCode, decoyAuthorId, target.rootSubmissionId(),
                    "A quieter account of the same events.", "", "");
            claimNewSubmissionId(decoyForkResponse);
        }

        // A second, real competing Option on the same story (the resolver's second offered
        // WHAT_CAN_WE_TRY choice, otherwise left untouched by every other scenario) -> forces a
        // real, multi-candidate MAKE_CHOICE_VOTING vote too.
        List<OutcomeType> targetOutcomeTypes = client.getOutcomeTypes(gameCode, targetAuthorId);
        List<OutcomeType> targetSubTypes = targetOutcomeTypes.isEmpty() ? List.of() : targetOutcomeTypes.get(0).getSubTypes();
        if (targetSubTypes != null && targetSubTypes.size() > 1) {
            OutcomeType decoyOption = targetSubTypes.get(1);
            CollaborativeTextPhase decoyOptionResponse = client.submitTextAddition(gameCode, targetAuthorId,
                    narrowToChosenSubType(targetOutcomeTypes.get(0), decoyOption),
                    "A second, less compelling course of action.", "", "");
            claimNewSubmissionId(decoyOptionResponse);
        }
        preferredWinningSubmissionIds.add(target.optionId());

        titleTarget = new ScenarioTarget(target.storyId(), target.optionId(), titleForkId);
    }

    private String onlyRepercussionTypeName(String gameCode, String authorId) {
        List<RepercussionTypeOption> types = client.getPlayerRepercussionTypes(gameCode, authorId);
        if (types.isEmpty()) {
            throw new IllegalStateException("Player " + userNameByAuthorId.get(authorId) + " has no repercission types available - is their PlayerClass set?");
        }
        return types.get(0).getName();
    }

    /** Diffs a submission-phase response against everything seen so far and remembers the new id. */
    private String claimNewSubmissionId(CollaborativeTextPhase phaseResponse) {
        List<String> newIds = phaseResponse.getSubmissions().stream()
                .map(TextSubmission::getSubmissionId)
                .filter(id -> id != null && !knownSubmissionIds.contains(id))
                .distinct()
                .toList();
        if (newIds.size() != 1) {
            throw new IllegalStateException("Expected exactly one new submission, found " + newIds
                    + " (known before: " + knownSubmissionIds + ")");
        }
        String newId = newIds.get(0);
        knownSubmissionIds.add(newId);
        return newId;
    }

    // ===== Round 1 effects, asserted once round 2's WHAT_HAPPENS_HERE is reached =====

    private void assertRoundOneEffects(String gameCode) {
        GameSession game = client.getGame(gameCode);
        List<Story> stories = game.getStories();
        List<Player> players = game.getPlayers();

        for (Map.Entry<String, ResolvedOption> entry : rootByResolver.entrySet()) {
            ResolvedOption resolved = entry.getValue();
            Story story = findStoryById(stories, resolved.storyId());
            assertThat(story).as("round-1 story %s should still exist", resolved.storyId()).isNotNull();
            assertThat(story.getSelectedOptionId())
                    .as("Story.selectedOptionId after MAKE_CHOICE_VOTING for story %s", resolved.storyId())
                    .isNotBlank();

            Option selectedOption = story.getSelectedOption();
            assertThat(selectedOption)
                    .as("selectedOptionId should reference a real Option on story %s", resolved.storyId())
                    .isNotNull();
            assertThat(selectedOption.getSelectedForkId())
                    .as("Option.selectedForkId after MAKE_OUTCOME_CHOICE_VOTING for story %s", resolved.storyId())
                    .isNotBlank();
            assertThat(selectedOption.getSelectedOutcomeFork())
                    .as("selectedForkId should reference a real OutcomeFork on story %s", resolved.storyId())
                    .isNotNull();
        }

        if (companionTarget != null) {
            Story story = assertScenarioResolvedAsExpected(stories, companionTarget, "Companion");
            String companionLabel = story.getEncounterLabel() == null ? null : story.getEncounterLabel().getEncounterLabel();
            assertThat(companionLabel).as("companion story should have an encounter label").isNotBlank();
            assertPlayersHaveTrait(players, allTargetPlayerIds(story), companionLabel, "Companion");
        }

        if (traitAloneTarget != null) {
            Story story = assertScenarioResolvedAsExpected(stories, traitAloneTarget, "Trait-alone");
            assertPlayersHaveTrait(players, allTargetPlayerIds(story), TRAIT_ALONE_LABEL, "Trait");
        }

        if (combinedTarget != null) {
            assertScenarioResolvedAsExpected(stories, combinedTarget, "Trait+All Players combined");
            // ALL_PLAYERS spreads location-scoped, not game-wide (.CLASSES.md, "handleSpread"), but
            // this test's uniform LOCATION_VOTING means everyone shares the location, so this
            // assertion can't by itself distinguish the two - sequelLocationScopeMode's split does.
            assertPlayersHaveTrait(players, playerAuthorIds, COMBINED_TRAIT_LABEL, "Trait");
        }

        if (titleTarget != null) {
            Story story = findStoryById(stories, titleTarget.storyId());
            assertThat(story.getOptions())
                    .as("Title story should have 2 real competing Options (proves real MAKE_CHOICE_VOTING)")
                    .hasSizeGreaterThanOrEqualTo(2);
            assertThat(story.getSelectedOptionId())
                    .as("MAKE_CHOICE_VOTING should have resolved to the intended Option")
                    .isEqualTo(titleTarget.optionId());

            Option selectedOption = story.getSelectedOption();
            assertThat(selectedOption.getOutcomeForks())
                    .as("Title option should have 2 real competing OutcomeForks (proves real MAKE_OUTCOME_CHOICE_VOTING)")
                    .hasSize(2);
            assertThat(selectedOption.getSelectedForkId())
                    .as("MAKE_OUTCOME_CHOICE_VOTING should have resolved to Herald's fork")
                    .isEqualTo(titleTarget.preferredForkSubmissionId());

            for (String authorId : allTargetPlayerIds(story)) {
                Player player = findPlayerById(players, authorId);
                assertThat(player).as("player %s should exist", authorId).isNotNull();
                assertThat(player.getDisplayName())
                        .as("player %s's displayName should include their new Title", authorId)
                        .contains(TITLE_LABEL);
            }
        }

        if (bardStackTarget != null) {
            Story story = assertScenarioResolvedAsExpected(stories, bardStackTarget,
                    "Bard ALL_PLAYERS (WHAT_HAPPENS_HERE) + Fabulist COMPANION (HOW_DOES_THIS_RESOLVE) stacked");

            List<Repercussion> whatHappensHereRepercussions = story.getRepercussions() == null
                    ? List.of() : story.getRepercussions();
            assertThat(whatHappensHereRepercussions)
                    .as("story %s should still carry the ALL_PLAYERS repercussion Bard attached while "
                            + "authoring it during WHAT_HAPPENS_HERE", story.getStoryId())
                    .anyMatch(r -> RepercussionType.ALL_PLAYERS.getName().equals(r.getRepercussionType()));

            String companionLabel = story.getEncounterLabel() == null ? null : story.getEncounterLabel().getEncounterLabel();
            assertThat(companionLabel).as("stacked scenario's story should have an encounter label").isNotBlank();
            // Companion should spread the same way, and with the same uniform-voting caveat, as the
            // Combined scenario above - see .CLASSES.md for the stacking mechanic itself.
            assertPlayersHaveTrait(players, playerAuthorIds, companionLabel, "Companion");
        }

        if (scribeWhatHappensHereSoloAuthorId != null) {
            Story story = findRoundOneStoryByAuthorId(stories, scribeWhatHappensHereSoloAuthorId);
            assertThat(story)
                    .as("story authored by %s should exist (Scribe's WHAT_HAPPENS_HERE-time TRAIT target)",
                            scribeWhatHappensHereSoloAuthorId)
                    .isNotNull();
            assertThat(story.getSelectedOptionId())
                    .as("Scribe's WHAT_HAPPENS_HERE-solo story should still resolve normally through "
                            + "MAKE_CHOICE_VOTING despite carrying a pre-attached repercussion")
                    .isNotBlank();

            List<Repercussion> whatHappensHereRepercussions = story.getRepercussions() == null
                    ? List.of() : story.getRepercussions();
            assertThat(whatHappensHereRepercussions)
                    .as("story %s should carry the TRAIT repercussion Scribe attached while authoring "
                            + "it during WHAT_HAPPENS_HERE", story.getStoryId())
                    .anyMatch(r -> RepercussionType.TRAIT.getName().equals(r.getRepercussionType()));

            // No repercussion was attached at this story's own HOW_DOES_THIS_RESOLVE resolution
            // (entirely generic) - the trait below can only have come from WHAT_HAPPENS_HERE-time.
            assertPlayersHaveTrait(players, allTargetPlayerIds(story), WHAT_HAPPENS_HERE_TRAIT_LABEL, "Trait");
        }

        // Round-2 prequel-story mechanic (.PLAYER_LIFECYCLE.md, "Prequel stories"): computed
        // dynamically per story from its actual winning fork's repercussions, not hardcoded per scenario.
        for (ResolvedOption resolved : rootByResolver.values()) {
            Story story = findStoryById(stories, resolved.storyId());
            String storyAuthorId = story.getAuthorId();
            Option selectedOption = story.getSelectedOption();
            OutcomeFork winningFork = selectedOption == null ? null : selectedOption.getSelectedOutcomeFork();
            List<Repercussion> repercussions = winningFork == null || winningFork.getRepercussions() == null
                    ? List.of() : winningFork.getRepercussions();
            boolean hasNonSpreadRepercussion = repercussions.stream()
                    .anyMatch(r -> !ALL_PLAYERS_REPERCUSSION_NAME.equals(r.getRepercussionType()));

            List<OutcomeType> authorOutcomeTypes = client.getOutcomeTypes(gameCode, storyAuthorId);
            boolean prequelOffered = authorOutcomeTypes.stream()
                    .flatMap(outcomeType -> outcomeType.getSubTypes() == null
                            ? Stream.<OutcomeType>empty() : outcomeType.getSubTypes().stream())
                    .anyMatch(subType -> resolved.storyId().equals(subType.getClarifier()));

            if (hasNonSpreadRepercussion) {
                assertThat(prequelOffered)
                        .as("story %s (author %s) resolved with a real repercussion - should NOT be re-offered "
                                + "as a round-2 prequel", resolved.storyId(), storyAuthorId)
                        .isFalse();
            } else {
                assertThat(prequelOffered)
                        .as("story %s (author %s) resolved with no repercussion - SHOULD be re-offered as a "
                                + "round-2 prequel", resolved.storyId(), storyAuthorId)
                        .isTrue();
            }
        }
    }

    private Story assertScenarioResolvedAsExpected(List<Story> stories, ScenarioTarget target, String scenarioName) {
        Story story = findStoryById(stories, target.storyId());
        assertThat(story).as("%s scenario's story should exist", scenarioName).isNotNull();
        assertThat(story.getSelectedOptionId())
                .as("%s scenario's Option should have won MAKE_CHOICE_VOTING", scenarioName)
                .isEqualTo(target.optionId());
        Option selectedOption = story.getSelectedOption();
        assertThat(selectedOption).as("%s scenario's selected Option should exist", scenarioName).isNotNull();
        assertThat(selectedOption.getSelectedForkId())
                .as("%s scenario's fork should have won MAKE_OUTCOME_CHOICE_VOTING", scenarioName)
                .isEqualTo(target.preferredForkSubmissionId());
        return story;
    }

    /**
     * sequelLocationScopeMode only. Finds the round-2 "sequel" story that continues the
     * ALL_PLAYERS-alone round-1 story and asserts its playerIds are scoped to players at the
     * sequel's own location, not every player in the game - see .PLAYER_LIFECYCLE.md,
     * "WHAT_HAPPENS_HERE", for why this can't assert exact equality (the unconditional
     * distribution-assigned player append). runSplitLocationVote deliberately splits close to
     * evenly (not 3-vs-1) so this assertion stays meaningful even in the worst case where that one
     * extra appended id happens to be an off-location player.
     */
    private void assertSequelLocationScopeEffects(String gameCode) {
        GameSession game = client.getGame(gameCode);
        List<Story> stories = game.getStories();
        List<Player> players = game.getPlayers();

        assertThat(allPlayersAloneTarget).as("ALL_PLAYERS-alone scenario should have been built").isNotNull();

        Story sequelStory = stories.stream()
                .filter(story -> allPlayersAloneTarget.storyId().equals(story.getPrequelStoryId()))
                .findFirst()
                .orElse(null);
        assertThat(sequelStory)
                .as("round-1 story %s (ALL_PLAYERS-alone) should have been continued as a round-2 sequel story",
                        allPlayersAloneTarget.storyId())
                .isNotNull();

        Location sequelLocation = sequelStory.getLocation();
        assertThat(sequelLocation).as("sequel story should have a real Location (locationVoting is on live)").isNotNull();

        List<String> playersAtSequelLocation = players.stream()
                .filter(player -> sequelLocation.getId().equals(player.getSelectedLocationId()))
                .map(Player::getAuthorId)
                .toList();

        assertThat(playersAtSequelLocation)
                .as("the round-2 location split should have left at least one player NOT at the sequel "
                        + "story's location - otherwise this test can't distinguish the new location-scoped "
                        + "behavior from the old game-wide one")
                .hasSizeLessThan(players.size());

        List<String> sequelPlayerIds = sequelStory.getPlayerIds() == null ? List.of() : sequelStory.getPlayerIds();

        assertThat(sequelPlayerIds)
                .as("every player at the sequel story's own location should be in its playerIds")
                .containsAll(playersAtSequelLocation);
        assertThat(sequelPlayerIds.size())
                .as("sequel Story.playerIds should be scoped to (roughly) the sequel's own location, "
                        + "not every player in the game - the whole point of the location-scoping change")
                .isLessThan(players.size());
    }

    private static List<String> allTargetPlayerIds(Story story) {
        List<String> playerIds = story.getPlayerIds() == null ? List.of() : story.getPlayerIds();
        List<String> partnerIds = story.getPartnerIds() == null ? List.of() : story.getPartnerIds();
        return Stream.concat(playerIds.stream(), partnerIds.stream()).distinct().toList();
    }

    private static void assertPlayersHaveTrait(List<Player> players, List<String> targetAuthorIds,
                                                String traitLabel, String traitTypeName) {
        for (String authorId : targetAuthorIds) {
            Player player = findPlayerById(players, authorId);
            assertThat(player).as("player %s should exist", authorId).isNotNull();
            List<Trait> traits = player.getTraits();
            boolean hasTrait = traits != null && traits.stream().anyMatch(trait ->
                    traitLabel.equals(trait.getTraitLabel())
                            && trait.getTraitType() != null
                            && traitTypeName.equals(trait.getTraitType().getName()));
            assertThat(hasTrait)
                    .as("player %s should have gained a \"%s\"-type trait labeled \"%s\"", authorId, traitTypeName, traitLabel)
                    .isTrue();
        }
    }

    private static Story findStoryById(List<Story> stories, String storyId) {
        return stories.stream().filter(story -> storyId.equals(story.getStoryId())).findFirst().orElse(null);
    }

    /**
     * Finds the round-1 story authored by a given player - must be scoped by Story.roundNumber, not
     * authorId alone, since every player also has a round-0 tutorial story with the same authorId.
     * See .CLASSES.md, "Test-authoring pitfall".
     */
    private static Story findRoundOneStoryByAuthorId(List<Story> stories, String authorId) {
        return stories.stream()
                .filter(story -> authorId.equals(story.getAuthorId()))
                .filter(story -> story.getRoundNumber() != null && story.getRoundNumber() == 1)
                .findFirst()
                .orElse(null);
    }

    private static Player findPlayerById(List<Player> players, String authorId) {
        return players.stream().filter(player -> authorId.equals(player.getAuthorId())).findFirst().orElse(null);
    }
}
