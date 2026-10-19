package client.nowhere.support;

import client.nowhere.model.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
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

    // nowhere-player-client caps submission input at 150 characters - see realisticSubmissionText.
    private static final int MAX_SUBMISSION_LENGTH = 150;
    // Candidates to ask GET /collaborativeText/available for per extra addition - a bit above the
    // real client's 2 so there's still something left after filtering out protected submissions.
    private static final int AVAILABLE_SUBMISSIONS_REQUESTED = 4;
    private static final String SUBMISSION_FILLER = "The lanterns gutter as the party presses on, "
            + "boots sinking into wet moss while something ancient stirs beneath the stones and the "
            + "wind carries a name none of them remember speaking aloud.";

    // Known-good, already-authored profile/adventure map with real starting locations/stories.
    private static final String LIVE_USER_PROFILE_ID = "c8d068ae-e180-44c9-940c-011ba632cba4";
    private static final String LIVE_ADVENTURE_ID = "b371256a-015a-4a72-8cc2-8f5ad6b40cd4";

    // One PlayerClass per player, in join order, up to however many of the four exist for the
    // requested player count. Player counts below 4 simply cover fewer classes; counts above 4
    // leave the extra players class-less, which doubles as a "no repercussion" control case -
    // unless cycleClasses() is set, which keeps cycling through this list instead.
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
    // Concurrent: with concurrentPlayers(), player threads claim/record submission ids in parallel.
    private final Set<String> knownSubmissionIds = ConcurrentHashMap.newKeySet();
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

    // Opt-in scale knobs (see the fluent setters below), used by the large-player-count tests to
    // approximate a real, crowded game rather than the minimal one the 3/4/5-player tests play.
    private boolean cycleClasses = false;
    private boolean mixSequelEligibility = false;
    private boolean realisticSubmissionText = false;
    private int extraAdditionsPerPlayer = 0;
    private SessionSizeLog sessionSizeLog;
    private LatencyLog latencyLog;
    private boolean simulateClientTraffic = false;
    private boolean concurrentPlayers = false;
    private ExecutorService playerExecutor;
    private final Set<String> usedResolveTargets = new HashSet<>();

    /** A player's root HOW_DOES_THIS_RESOLVE submission for their naturally assigned story. */
    private record ResolvedOption(String storyId, String optionId, String rootSubmissionId) {}

    /** Where a repercussion scenario landed, and which fork it expects to have won. */
    private record ScenarioTarget(String storyId, String optionId, String preferredForkSubmissionId) {}

    private final RestTemplate restTemplate;

    public FullGameScenario(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.client = new GameFlowClient(restTemplate);
    }

    /**
     * Assigns a PlayerClass to EVERY player by cycling Scribe/Bard/Herald/Fabulist, instead of
     * leaving players past the 4th class-less. Each player beyond the first of their class also
     * attaches their class's repercussion to an otherwise-untouched round-1 story (see
     * attachExtraClassRepercussions), so repercussion/trait data grows with the player count the
     * way it would in a real game.
     */
    public FullGameScenario cycleClasses() {
        this.cycleClasses = true;
        return this;
    }

    /**
     * sequelLocationScopeMode only: also builds the full default-mode repercussion scenarios
     * (Trait/Title/Companion/...), so round 2 has a real mix of round-1 stories that ARE re-offered
     * as prequels (no repercussion, or ALL_PLAYERS alone) and ones that are NOT (anything that
     * lands a trait/title/companion) - instead of sequelLocationScopeMode's default where every
     * story but one is left untouched and therefore eligible.
     */
    public FullGameScenario mixSequelEligibility() {
        this.mixSequelEligibility = true;
        return this;
    }

    /**
     * Pads every submission's text to the player-client's 150-character input limit instead of a
     * short one-liner, so the GameSession document grows the way it does in a real game.
     */
    public FullGameScenario realisticSubmissionText() {
        this.realisticSubmissionText = true;
        return this;
    }

    /**
     * In every SHARE_TEXT submission phase, after the usual root submissions (and any scenario
     * branches), each player additionally adds to other players' submissions this many times -
     * picked from GET /collaborativeText/available the same way the real player-client's
     * onAddToSubmission does, rather than the one-root-per-player minimum the harness otherwise
     * writes. Every extra addition is a plain-text branch (no repercussion) and never lands on a
     * round-1 scenario's target submissions, so the deterministic scenarios and their assertions
     * are unaffected - see addExtraBranches.
     */
    public FullGameScenario extraAdditionsPerPlayer(int additions) {
        this.extraAdditionsPerPlayer = additions;
        return this;
    }

    /**
     * Logs the serialized GameSession size (total and per top-level field) plus wall-clock time
     * spent in each phase, on every state transition, and prints a summary table when the game
     * ends or fails - see SessionSizeLog.
     */
    public FullGameScenario logSessionSize() {
        // Its own client/RestTemplate, so its measurement GETs stay out of LatencyLog's numbers.
        this.sessionSizeLog = new SessionSizeLog(new GameFlowClient(LiveBackend.restTemplate()));
        return this;
    }

    /**
     * Times every HTTP call this scenario makes, tagged by phase, and prints per-endpoint and
     * per-phase latency tables when the game ends or fails - see LatencyLog.
     */
    public FullGameScenario logLatency() {
        this.latencyLog = new LatencyLog();
        restTemplate.getInterceptors().add(latencyLog);
        return this;
    }

    /**
     * Also makes the HTTP calls the real clients make that the scenario itself doesn't need,
     * so the backend sees (and LatencyLog measures) a real game's traffic:
     * - TV (nowhere-game-client), on every phase: GET /player (player-progress), GET /display
     *   (collaborative-text), GET /game-board when phaseInfo.showGameBoard, GET
     *   /collaborativeText/winner in WINNING phases, and the timer writes on PUT
     *   /activePlayerSession - startTimer+contributionPhaseActive when a SHARE_TEXT phase's write
     *   timer ends, then writeTimerDone when its contribution timer (or any other writing phase's
     *   timer) ends (game-state-manager onTimerComplete / onContributionPhaseTimerComplete).
     * - Each player, in submission phases: GET /collaborativeText (loadCollaborativePhase), GET
     *   /story for a chosen outcome type that carries a storyId clarifier, and in WRITE_EPILOGUES
     *   GET /player/authorId for each epilogue player offered.
     * Not simulated: the player-client's GET /display and GET /game-board (only fetched when a
     * player taps "View Our World"), GET /victory (game-client only loads it in WRITE_ENDINGS,
     * which DUNGEON_MODE never reaches), and GET /collaborativeText/outcomeType. Failures on these
     * calls are logged and recorded by LatencyLog but don't fail the game, matching the clients,
     * which just console.error them.
     */
    public FullGameScenario simulateClientTraffic() {
        this.simulateClientTraffic = true;
        return this;
    }

    /**
     * Issues each phase's per-player calls (root submissions, extra additions, votes, client
     * reads, marking INIT done) in parallel, one thread per player, like real phones - instead of
     * one player at a time. Scripted scenario steps that depend on each other (the round-1
     * repercussion scenarios' specific branches) still run in order afterwards. Joining players
     * stays sequential, since join order decides class assignment.
     */
    public FullGameScenario concurrentPlayers() {
        this.concurrentPlayers = true;
        return this;
    }

    public GameSession play(int playerCount) {
        return play(playerCount, false);
    }

    public GameSession play(int playerCount, boolean sequelLocationScopeMode) {
        if (concurrentPlayers) {
            playerExecutor = Executors.newFixedThreadPool(playerCount);
        }
        try {
            return playGame(playerCount, sequelLocationScopeMode);
        } finally {
            if (playerExecutor != null) {
                playerExecutor.shutdownNow();
            }
        }
    }

    private GameSession playGame(int playerCount, boolean sequelLocationScopeMode) {
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

        forEachPlayer(authorId -> {
            client.markPhaseDone(gameCode, "INIT", authorId);
            return null;
        });

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

            if (cycleClasses || i < CLASS_NAMES_IN_JOIN_ORDER.size()) {
                String desiredClassName = CLASS_NAMES_IN_JOIN_ORDER.get(i % CLASS_NAMES_IN_JOIN_ORDER.size());
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
        try {
            return runMainLoopIterations(gameCode);
        } finally {
            if (sessionSizeLog != null) {
                sessionSizeLog.printSummary(gameCode);
            }
            if (latencyLog != null) {
                latencyLog.printSummary(gameCode);
            }
        }
    }

    private GameSession runMainLoopIterations(String gameCode) {
        GameSession finalState = null;
        GameSession lastSeenState = null;
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            GameSession game = client.getGame(gameCode);
            lastSeenState = game;
            GameState gameState = game.getGameState();
            int roundNumber = game.getRoundNumber() == null ? 0 : game.getRoundNumber();
            if (sessionSizeLog != null) {
                sessionSizeLog.record(gameCode, gameState, roundNumber);
            }
            if (latencyLog != null) {
                latencyLog.setPhase(roundNumber, gameState);
            }
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
        boolean buildClassScenarios = !sequelLocationScopeMode || mixSequelEligibility;

        CollaborativeTextPhaseInfo phaseInfo = client.getPhaseInfo(gameCode);
        if (simulateClientTraffic) {
            simulateTvPhaseReads(gameCode, phaseInfo);
        }

        if (buildClassScenarios && gameState == GameState.WHAT_HAPPENS_HERE && roundNumber == 1) {
            if (whatHappensHereScenariosBuilt) {
                return;
            }
            buildWhatHappensHereRepercussionScenarios(gameCode, gameState);
            Set<String> untouchedRoots = new HashSet<>();
            whatHappensHereRootByAuthor.forEach((authorId, rootId) -> {
                if (!authorId.equals(bardWhatHappensHereStackAuthorId) && !authorId.equals(scribeWhatHappensHereSoloAuthorId)) {
                    untouchedRoots.add(rootId);
                }
            });
            finishSubmissionPhase(gameCode, gameState, phaseInfo, protectAllKnownExcept(untouchedRoots));
            return;
        }

        if (gameState == GameState.HOW_DOES_THIS_RESOLVE && roundNumber == 1) {
            if (roundOneScenariosBuilt) {
                return;
            }
            roundOneScenariosBuilt = true;
            submitDefaultRootResolutions(gameCode, gameState);
            if (buildClassScenarios) {
                buildRoundOneRepercussionScenarios(gameCode);
            }
            if (sequelLocationScopeMode) {
                buildSequelLocationScopeRepercussion(gameCode);
            }
            if (cycleClasses) {
                attachExtraClassRepercussions(gameCode);
            }
            Set<String> untouchedRoots = new HashSet<>();
            rootByResolver.forEach((resolverAuthorId, resolved) -> {
                if (!usedResolveTargets.contains(resolverAuthorId)) {
                    untouchedRoots.add(resolved.rootSubmissionId());
                }
            });
            finishSubmissionPhase(gameCode, gameState, phaseInfo, protectAllKnownExcept(untouchedRoots));
            return;
        }

        if (sequelLocationScopeMode && gameState == GameState.LOCATION_VOTING && roundNumber == 2) {
            runSplitLocationVote(gameCode);
            return;
        }

        if (phaseInfo == null) {
            return; // no-op / display-only state (PREAMBLE, ENDING_PREAMBLE, ENDING, NAVIGATE_WINNER, ...)
        }

        PhaseType phaseType = phaseInfo.phaseType();
        if (phaseType == null) {
            return;
        }

        switch (phaseType) {
            case SUBMISSION -> {
                runGenericSubmissionPhase(gameCode, gameState);
                finishSubmissionPhase(gameCode, gameState, phaseInfo, Set.of());
            }
            case VOTING -> runVotingPhase(gameCode, gameState);
            case WINNING -> { /* computed server-side already, nothing to do */ }
        }
    }

    private void runGenericSubmissionPhase(String gameCode, GameState gameState) {
        forEachPlayer(authorId -> {
            List<OutcomeType> outcomeTypes = client.getOutcomeTypes(gameCode, authorId);
            if (outcomeTypes.isEmpty()) {
                return null;
            }
            OutcomeType parent = outcomeTypes.get(0);
            List<OutcomeType> subTypes = parent.getSubTypes();
            // Mirror the real player-client: keep the parent wrapper, narrow subTypes to the
            // one chosen child, rather than flattening to the child alone (see
            // narrowToChosenSubType for why this matters).
            OutcomeType chosen = subTypes != null && !subTypes.isEmpty() ? narrowToChosenSubType(parent, subTypes.get(0)) : parent;
            simulatePlayerSubmissionReads(gameCode, gameState, outcomeTypes, chosen);
            String text = storyText("Integration test submission for " + gameState + " by " + userNameByAuthorId.get(authorId));
            client.submitTextAddition(gameCode, authorId, chosen, text, "", "");
            return null;
        });
    }

    /**
     * Everything after a submission phase's roots are in: for SHARE_TEXT phases, the TV's write
     * timer ending (contribution phase starts) and then players' extra additions; for every
     * writing phase, the TV's final writeTimerDone. The timer writes only happen with
     * simulateClientTraffic - see its javadoc.
     */
    private void finishSubmissionPhase(String gameCode, GameState gameState, CollaborativeTextPhaseInfo phaseInfo,
                                       Set<String> protectedSubmissionIds) {
        boolean shareText = phaseInfo != null && phaseInfo.collaborativeMode() == CollaborativeMode.SHARE_TEXT;
        if (shareText) {
            if (simulateClientTraffic) {
                bestEffort("PUT /activePlayerSession (contributionPhaseActive)", () -> client.setContributionPhaseActive(gameCode));
            }
            addExtraBranches(gameCode, gameState, protectedSubmissionIds);
        }
        if (simulateClientTraffic) {
            bestEffort("PUT /activePlayerSession (writeTimerDone)", () -> client.setWriteTimerDone(gameCode));
        }
    }

    // ===== Client traffic simulation (simulateClientTraffic) =====

    /** The TV's reads on entering a phase - see simulateClientTraffic. */
    private void simulateTvPhaseReads(String gameCode, CollaborativeTextPhaseInfo phaseInfo) {
        bestEffort("GET /player", () -> client.getPlayersRaw(gameCode));
        if (phaseInfo == null) {
            return;
        }
        bestEffort("GET /display", () -> client.getGameSessionDisplayRaw(gameCode));
        if (Boolean.TRUE.equals(phaseInfo.showGameBoard())) {
            bestEffort("GET /game-board", () -> client.getGameBoardRaw(gameCode));
        }
        if (phaseInfo.phaseType() == PhaseType.WINNING) {
            bestEffort("GET /collaborativeText/winner", () -> client.getWinningSubmissionsRaw(gameCode));
        }
    }

    /**
     * One player's reads in a submission phase, alongside their own GET outcomeTypes - see
     * simulateClientTraffic. Called from inside each player's (possibly concurrent) submission step.
     */
    private void simulatePlayerSubmissionReads(String gameCode, GameState gameState, List<OutcomeType> outcomeTypes, OutcomeType chosen) {
        if (!simulateClientTraffic) {
            return;
        }
        bestEffort("GET /collaborativeText", () -> client.getCollaborativeTextPhaseRaw(gameCode));
        String storyId = storyClarifier(chosen);
        if (storyId != null) {
            bestEffort("GET /story", () -> client.getStoryRaw(gameCode, storyId));
        }
        if (gameState == GameState.WRITE_EPILOGUES) {
            for (OutcomeType outcomeType : outcomeTypes) {
                if (outcomeType.getId() != null) {
                    bestEffort("GET /player/authorId", () -> client.getPlayerByAuthorIdRaw(gameCode, outcomeType.getId()));
                }
            }
        }
    }

    /** The storyId the player-client would load a Story for when this outcome type is selected, if any. */
    private static String storyClarifier(OutcomeType chosen) {
        if (chosen == null) {
            return null;
        }
        if (chosen.getSubTypes() != null && !chosen.getSubTypes().isEmpty()) {
            String subTypeClarifier = chosen.getSubTypes().get(0).getClarifier();
            if (subTypeClarifier != null && !subTypeClarifier.isEmpty()) {
                return subTypeClarifier;
            }
        }
        String clarifier = chosen.getClarifier();
        return clarifier == null || clarifier.isEmpty() ? null : clarifier;
    }

    /** Runs a simulated client call, logging instead of failing the game - the clients just console.error these. */
    private static void bestEffort(String description, Runnable call) {
        try {
            call.run();
        } catch (RestClientException e) {
            System.out.println("[FullGameScenario] simulated client call failed (ignored): " + description + ": " + e.getMessage());
        }
    }

    // ===== Concurrency (concurrentPlayers) =====

    /**
     * Runs the action once per player - in parallel with concurrentPlayers(), otherwise one at a
     * time in join order - and returns each player's result in join order either way, so callers
     * record results deterministically. Waits for every player before rethrowing the first
     * failure, so no player's call is still in flight when the phase moves on.
     */
    private <T> Map<String, T> forEachPlayer(Function<String, T> action) {
        Map<String, T> results = new LinkedHashMap<>();
        if (playerExecutor == null) {
            for (String authorId : playerAuthorIds) {
                results.put(authorId, action.apply(authorId));
            }
            return results;
        }

        Map<String, Future<T>> futures = new LinkedHashMap<>();
        for (String authorId : playerAuthorIds) {
            futures.put(authorId, playerExecutor.submit(() -> action.apply(authorId)));
        }
        Throwable failure = null;
        for (Map.Entry<String, Future<T>> entry : futures.entrySet()) {
            try {
                results.put(entry.getKey(), entry.getValue().get());
            } catch (ExecutionException e) {
                if (failure == null) {
                    failure = e.getCause();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted waiting for player " + userNameByAuthorId.get(entry.getKey()), e);
            }
        }
        if (failure instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (failure instanceof Error error) {
            throw error;
        }
        if (failure != null) {
            throw new IllegalStateException(failure);
        }
        return results;
    }

    /** Every submission id claimed so far (scenario roots and branches), minus the given ones. */
    private Set<String> protectAllKnownExcept(Set<String> unprotected) {
        Set<String> protectedIds = new HashSet<>(knownSubmissionIds);
        protectedIds.removeAll(unprotected);
        return protectedIds;
    }

    /**
     * extraAdditionsPerPlayer only: each player, extraAdditionsPerPlayer times, branches a
     * plain-text addition off one of the submissions GET /collaborativeText/available offers them
     * - skipping any in protectedSubmissionIds, which the round-1 scenario builders use to keep
     * their target stories single-fork/controlled (branching onto a scenario's root or fork would
     * add a competing fork and make the scenario's winner a coin flip). Only runs in SHARE_TEXT
     * phases: the real player-client only shows "add to a submission" when
     * phaseInfo.collaborativeMode is SHARE_TEXT (collaborative-text.component.ts,
     * isCollaborativeMode).
     */
    private void addExtraBranches(String gameCode, GameState gameState, Set<String> protectedSubmissionIds) {
        if (extraAdditionsPerPlayer <= 0) {
            return;
        }
        // The real client only offers "add to a submission" once the phase actually has
        // submissions to pick from (hasSubmitted && availableSubmissions.length > 0). Phases with
        // no submissions of their own (e.g. INIT, where GameSession.stories is still null and
        // GET /collaborativeText/available 500s) never get that far.
        if (gameState.getPhaseId() == null) {
            return;
        }
        CollaborativeTextPhase currentPhase = client.getCollaborativeTextPhase(gameCode);
        if (currentPhase == null || currentPhase.getSubmissions() == null || currentPhase.getSubmissions().isEmpty()) {
            return;
        }

        for (int pass = 0; pass < extraAdditionsPerPlayer; pass++) {
            int passIndex = pass;
            forEachPlayer(authorId -> {
                List<TextSubmission> available = client.getAvailableSubmissions(gameCode, authorId,
                                AVAILABLE_SUBMISSIONS_REQUESTED, true).stream()
                        .filter(submission -> submission.getSubmissionId() != null)
                        .filter(submission -> !protectedSubmissionIds.contains(submission.getSubmissionId()))
                        .toList();
                if (available.isEmpty()) {
                    return null;
                }
                TextSubmission parent = available.get(passIndex % available.size());
                CollaborativeTextPhase response = client.addToSubmission(gameCode, authorId, parent.getSubmissionId(),
                        storyText("Extra " + gameState + " addition " + (passIndex + 1) + " by " + userNameByAuthorId.get(authorId)),
                        "", "");
                // Keep claimNewSubmissionId's "exactly one new id" diff accurate for anything that
                // runs later in this same phase.
                response.getSubmissions().stream()
                        .map(TextSubmission::getSubmissionId)
                        .filter(id -> id != null)
                        .forEach(knownSubmissionIds::add);
                return null;
            });
        }
    }

    private void runVotingPhase(String gameCode, GameState gameState) {
        boolean choosingFromOwnTraits = gameState == GameState.DEFINING_TRAITS_VOTING;
        forEachPlayer(authorId -> {
            String submissionId;
            if (choosingFromOwnTraits) {
                List<OutcomeType> outcomeTypes = client.getOutcomeTypes(gameCode, authorId);
                if (outcomeTypes.isEmpty()) {
                    return null;
                }
                submissionId = outcomeTypes.get(0).getId();
            } else {
                List<TextSubmission> candidates = client.getVotingCandidates(gameCode, authorId);
                if (candidates.isEmpty()) {
                    return null;
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
                return null;
            }
            client.submitVote(gameCode, authorId, submissionId);
            return null;
        });
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
        forEachPlayer(authorId -> {
            List<TextSubmission> candidates = client.getVotingCandidates(gameCode, authorId);
            if (candidates.isEmpty()) {
                return null;
            }
            boolean firstHalf = playerAuthorIds.indexOf(authorId) < playerAuthorIds.size() / 2;
            int candidateIndex = (firstHalf || candidates.size() < 2) ? 0 : 1;
            client.submitVote(gameCode, authorId, candidates.get(candidateIndex).getSubmissionId());
            return null;
        });
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
     * Runs after submitDefaultRootResolutions (see runPhaseActions): layers real, deterministic
     * repercussion scenarios on top of those roots, one per available PlayerClass, each targeting
     * a DIFFERENT player's story via GameFlowClient's addToSubmission (real branching - see class
     * javadoc). Every target it takes is recorded in usedResolveTargets so later builders
     * (sequelLocationScopeMode's ALL_PLAYERS-alone scenario, cycleClasses's extras) don't reuse it.
     */
    private void buildRoundOneRepercussionScenarios(String gameCode) {
        String scribeAuthorId = classNameByAuthorId.entrySet().stream()
                .filter(entry -> "Scribe".equals(entry.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);
        String bardAuthorId = classNameByAuthorId.entrySet().stream()
                .filter(entry -> "Bard".equals(entry.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);
        String heraldAuthorId = classNameByAuthorId.entrySet().stream()
                .filter(entry -> "Herald".equals(entry.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);
        String fabulistAuthorId = classNameByAuthorId.entrySet().stream()
                .filter(entry -> "Fabulist".equals(entry.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);

        Set<String> usedTargets = usedResolveTargets;

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
     * combination described in .PLAYER_LIFECYCLE.md, "Prequel stories". By default does not build
     * the other 4 class scenarios - this mode is only exercised together with runSplitLocationVote,
     * which this file's other tests don't need and shouldn't risk being entangled with. With
     * mixSequelEligibility it runs after buildRoundOneRepercussionScenarios and just takes the next
     * unused target story.
     */
    private void buildSequelLocationScopeRepercussion(String gameCode) {
        String bardAuthorId = classNameByAuthorId.entrySet().stream()
                .filter(entry -> "Bard".equals(entry.getValue())).map(Map.Entry::getKey).findFirst().orElse(null);
        if (bardAuthorId == null) {
            throw new IllegalStateException("sequelLocationScopeMode requires a Bard player to grant ALL_PLAYERS");
        }

        String targetAuthorId = pickTarget(bardAuthorId, usedResolveTargets);
        if (targetAuthorId == null) {
            throw new IllegalStateException("No available target story for the ALL_PLAYERS-alone scenario");
        }
        usedResolveTargets.add(targetAuthorId);
        ResolvedOption target = rootByResolver.get(targetAuthorId);
        String allPlayersType = onlyRepercussionTypeName(gameCode, bardAuthorId);

        // Single branch, single fork -> auto-select, no forced voting needed: this test isn't
        // about fork voting, just about what happens to this ALL_PLAYERS-alone story's round-2
        // continuation.
        CollaborativeTextPhase response = client.addToSubmission(gameCode, bardAuthorId, target.rootSubmissionId(),
                storyText("Bard senses this will echo for everyone who was here."), allPlayersType, ALL_PLAYERS_REPERCUSSION_NAME);
        String forkId = claimNewSubmissionId(response);
        allPlayersAloneTarget = new ScenarioTarget(target.storyId(), target.optionId(), forkId);
    }

    /**
     * cycleClasses only: every classed player who isn't the first of their class (and so wasn't
     * already used as a granter by the scenario builders, which always pick the first) attaches
     * their class's repercussion onto a still-untouched round-1 story, one story each, until the
     * untouched stories run out. Single branch per story -> single fork -> auto-select, so each
     * lands deterministically. Not asserted individually: assertRoundOneEffects's prequel check
     * is computed per story from its actual winning fork, so it already covers whichever of these
     * (TRAIT/TITLE/COMPANION: not re-offered; ALL_PLAYERS alone: re-offered) end up applied.
     */
    private void attachExtraClassRepercussions(String gameCode) {
        Set<String> firstOfEachClass = new HashSet<>();
        Set<String> seenClasses = new HashSet<>();
        for (Map.Entry<String, String> entry : classNameByAuthorId.entrySet()) {
            if (seenClasses.add(entry.getValue())) {
                firstOfEachClass.add(entry.getKey());
            }
        }

        for (Map.Entry<String, String> entry : classNameByAuthorId.entrySet()) {
            String granterAuthorId = entry.getKey();
            if (firstOfEachClass.contains(granterAuthorId)) {
                continue;
            }
            String targetAuthorId = pickTarget(granterAuthorId, usedResolveTargets);
            if (targetAuthorId == null) {
                return;
            }
            usedResolveTargets.add(targetAuthorId);
            ResolvedOption target = rootByResolver.get(targetAuthorId);
            String className = entry.getValue();
            String granterName = userNameByAuthorId.get(granterAuthorId);
            String repercussionSubmission = switch (className) {
                case "Fabulist" -> findEncounterLabel(gameCode, target.storyId());
                case "Bard" -> ALL_PLAYERS_REPERCUSSION_NAME;
                case "Herald" -> "Herald of " + granterName;
                default -> "Marked by " + granterName;
            };
            CollaborativeTextPhase response = client.addToSubmission(gameCode, granterAuthorId, target.rootSubmissionId(),
                    storyText(className + " " + granterName + " leaves their mark on this story."),
                    onlyRepercussionTypeName(gameCode, granterAuthorId), repercussionSubmission);
            claimNewSubmissionId(response);
        }
    }

    /**
     * Returns the text as-is, or (with realisticSubmissionText) padded with filler prose up to the
     * player-client's 150-character submission limit.
     */
    private String storyText(String text) {
        if (!realisticSubmissionText || text.length() >= MAX_SUBMISSION_LENGTH) {
            return text;
        }
        String padded = text + " " + SUBMISSION_FILLER;
        return padded.substring(0, MAX_SUBMISSION_LENGTH);
    }

    /**
     * Round-1 WHAT_HAPPENS_HERE: every player submits their own root encounter write-up as usual,
     * then Bard attaches ALL_PLAYERS alone onto another author's encounter (completed into a real
     * stacked effect later by buildRoundOneRepercussionScenarios) and Scribe attaches TRAIT alone
     * onto a different author's - see .CLASSES.md, "WHAT_HAPPENS_HERE-time repercussions stack with
     * the winning fork's own". Runs once; WHAT_HAPPENS_HERE only appears for round 1 with this shape
     * (round 2 offers prequel stories too, which this scenario doesn't need).
     */
    private void buildWhatHappensHereRepercussionScenarios(String gameCode, GameState gameState) {
        if (whatHappensHereScenariosBuilt) {
            return;
        }
        whatHappensHereScenariosBuilt = true;

        submitDefaultWhatHappensHereRoots(gameCode, gameState);

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
                        storyText("Bard weaves this telling so it echoes for everyone who hears it."),
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
                        storyText("Scribe notes something here worth remembering."), traitType, WHAT_HAPPENS_HERE_TRAIT_LABEL);
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
    private void submitDefaultWhatHappensHereRoots(String gameCode, GameState gameState) {
        Map<String, String> rootIdByAuthor = forEachPlayer(authorId -> {
            List<OutcomeType> outcomeTypes = client.getOutcomeTypes(gameCode, authorId);
            if (outcomeTypes.isEmpty()) {
                return null;
            }
            OutcomeType parent = outcomeTypes.get(0);
            List<OutcomeType> subTypes = parent.getSubTypes();
            if (subTypes == null || subTypes.isEmpty()) {
                return null;
            }
            OutcomeType chosenEncounter = narrowToChosenSubType(parent, subTypes.get(0));
            simulatePlayerSubmissionReads(gameCode, gameState, outcomeTypes, chosenEncounter);

            CollaborativeTextPhase response = client.submitTextAddition(gameCode, authorId,
                    chosenEncounter, storyText("Encounter write-up by " + userNameByAuthorId.get(authorId)), "", "");
            return claimOwnNewSubmissionId(response, authorId);
        });
        rootIdByAuthor.forEach((authorId, rootId) -> {
            if (rootId != null) {
                whatHappensHereRootByAuthor.put(authorId, rootId);
            }
        });
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
    private void submitDefaultRootResolutions(String gameCode, GameState gameState) {
        Map<String, ResolvedOption> resolvedByAuthor = forEachPlayer(authorId -> {
            List<OutcomeType> outcomeTypes = client.getOutcomeTypes(gameCode, authorId);
            if (outcomeTypes.isEmpty()) {
                return null;
            }
            OutcomeType parent = outcomeTypes.get(0);
            List<OutcomeType> subTypes = parent.getSubTypes();
            if (subTypes == null || subTypes.isEmpty()) {
                return null;
            }
            OutcomeType chosenOption = subTypes.get(0);
            OutcomeType narrowed = narrowToChosenSubType(parent, chosenOption);
            simulatePlayerSubmissionReads(gameCode, gameState, outcomeTypes, narrowed);

            CollaborativeTextPhase response = client.submitTextAddition(gameCode, authorId,
                    narrowed, storyText("Root resolution by " + userNameByAuthorId.get(authorId)), "", "");
            String rootId = claimOwnNewSubmissionId(response, authorId);
            return new ResolvedOption(parent.getId(), chosenOption.getId(), rootId);
        });
        resolvedByAuthor.forEach((authorId, resolved) -> {
            if (resolved != null) {
                rootByResolver.put(authorId, resolved);
            }
        });
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
                storyText("Fabulist recognizes a kindred spirit in this encounter."), repercussionType, encounterLabel);
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
                storyText("Scribe notes their steadfast resolve."), repercussionType, TRAIT_ALONE_LABEL);
        String forkId = claimNewSubmissionId(response);
        traitAloneTarget = new ScenarioTarget(target.storyId(), target.optionId(), forkId);
    }

    private void attachCombinedTraitAndAllPlayersScenario(String gameCode, String scribeAuthorId, String bardAuthorId, String targetAuthorId) {
        ResolvedOption target = rootByResolver.get(targetAuthorId);
        String traitType = onlyRepercussionTypeName(gameCode, scribeAuthorId);
        String allPlayersType = onlyRepercussionTypeName(gameCode, bardAuthorId);

        CollaborativeTextPhase traitResponse = client.addToSubmission(gameCode, scribeAuthorId, target.rootSubmissionId(),
                storyText("Scribe records their courage for history."), traitType, COMBINED_TRAIT_LABEL);
        String chainMid = claimNewSubmissionId(traitResponse);

        // ALL_PLAYERS has no real text input in the UI (RepercussionType.ALL_PLAYERS.label is ""),
        // so the real client would send repercussionSubmission: "". GameFlowClient now requires a
        // non-empty repercussionSubmission to attach a repercussion at all (added to fix the
        // Companion scenario silently dropping its repercussion the same way), so this placeholder
        // exists purely to satisfy that harness-side guard - the server doesn't read
        // repercissionSubmission for ALL_PLAYERS's effect, only repercussionType.
        CollaborativeTextPhase spreadResponse = client.addToSubmission(gameCode, bardAuthorId, chainMid,
                storyText("Bard makes sure the whole party hears of it."), allPlayersType, ALL_PLAYERS_REPERCUSSION_NAME);
        String chainFinal = claimNewSubmissionId(spreadResponse);

        combinedTarget = new ScenarioTarget(target.storyId(), target.optionId(), chainFinal);
    }

    private void attachTitleScenario(String gameCode, String heraldAuthorId, String targetAuthorId, String decoyAuthorId) {
        ResolvedOption target = rootByResolver.get(targetAuthorId);
        String titleType = onlyRepercussionTypeName(gameCode, heraldAuthorId);

        CollaborativeTextPhase titleResponse = client.addToSubmission(gameCode, heraldAuthorId, target.rootSubmissionId(),
                storyText("Herald names them for this deed."), titleType, TITLE_LABEL);
        String titleForkId = claimNewSubmissionId(titleResponse);
        preferredWinningSubmissionIds.add(titleForkId);

        if (decoyAuthorId != null) {
            // A second, real sibling branch off the SAME root (not chained onto the Title fork)
            // -> a second real OutcomeFork, forcing a real MAKE_OUTCOME_CHOICE_VOTING vote.
            CollaborativeTextPhase decoyForkResponse = client.addToSubmission(gameCode, decoyAuthorId, target.rootSubmissionId(),
                    storyText("A quieter account of the same events."), "", "");
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
                    storyText("A second, less compelling course of action."), "", "");
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

    /**
     * Like claimNewSubmissionId, but only considers submissions authored by this player - safe
     * when other players are submitting at the same moment (concurrentPlayers), where the same
     * response can also contain their brand-new, not-yet-claimed submissions. A root submission's
     * authorId is its submitter (CollaborativeTextHelper.createNewSubmission).
     */
    private String claimOwnNewSubmissionId(CollaborativeTextPhase phaseResponse, String authorId) {
        List<String> newIds = phaseResponse.getSubmissions().stream()
                .filter(submission -> authorId.equals(submission.getAuthorId()))
                .map(TextSubmission::getSubmissionId)
                .filter(id -> id != null && !knownSubmissionIds.contains(id))
                .distinct()
                .toList();
        if (newIds.size() != 1) {
            throw new IllegalStateException("Expected exactly one new submission by " + userNameByAuthorId.get(authorId)
                    + ", found " + newIds);
        }
        String newId = newIds.get(0);
        knownSubmissionIds.add(newId);
        return newId;
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
