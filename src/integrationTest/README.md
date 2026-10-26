# Full-Game Integration Test Suite — Status / Handoff Notes

Working notes for continuing this session. See the original plan at
`C:\Users\vande\.claude\plans\moonlit-roaming-gem.md` for the original proposal (backend HTTP
integration suite, 3/4/5-player scenarios covering `GameState.getOutcomeTypeOffset`'s
`playerCount > 4` branch). **The approach described there has since pivoted** — see "Architecture
pivot" below. This file is the up-to-date source of truth for where things actually stand.

> **Latest work (2026-10-25): backend latency.** See "Latency work (in progress)" below for where
> things stand, the measurements so far, and next steps. The rest of this section is from 2026-08-10.

## Current state (as of pausing this session, 2026-08-10)

`FullGameScenario` is now a single, self-contained engine (no more separate scenario-hook class) —
see "Architecture: FullGameScenario is now one file, fully typed, with deterministic scenarios"
below for the full design. `FullGameThreePlayersTest` / `FullGameFourPlayersTest` /
`FullGameFivePlayersTest` are all thin (create scenario, `play(n)`, assert `ENDING`, `deleteGame`)
and all pass reliably against the live deployment:

- **3 players**: covers 3 of the 4 `PlayerClass` repercussion scenarios (no Fabulist/Companion —
  not enough players to hold that class).
- **4 players**: full coverage — Title, Trait, Companion, combined Trait+All-Players, a forced real
  multi-candidate `MAKE_CHOICE_VOTING` vote, and a forced real multi-candidate
  `MAKE_OUTCOME_CHOICE_VOTING` vote, all resolved deterministically via controlled voting (not left
  to candidate order).
- **5 players**: same as 4, plus a 5th class-less player whose story is left untouched, serving as
  the "no repercussion → round-2 prequel-eligible" control case, and exercising
  `GameState.getOutcomeTypeOffset`'s `playerCount > 4` branch.

All three tests delete their game via the new `DELETE /admin/game` endpoint on success (not on
failure — a failed run's game is left behind for debugging, matching the existing convention of not
touching repro games without asking first).

## Architecture pivot: live backend, not Firestore emulator

The original plan (see plan file) called for a **local Firestore emulator** with a **hand-authored
AdventureMap fixture**. That approach hit a wall: correctly authoring `Location.startingStories`
content (options, outcomeForks, textSubmissions, gameSessionDisplay, etc.) by hand is itself a real
content-authoring task, and several early failures I attributed to production bugs turned out to be
gaps/timing issues in that fixture instead (see "False leads" below).

**Current approach (per explicit user direction):** tests create games against the **real deployed
backend** (`https://nowhere-java-556057816518.us-east4.run.app`, project `nowhere-af065`) using a
**known-good, already-authored** user profile + adventure map:

```
LIVE_USER_PROFILE_ID = c8d068ae-e180-44c9-940c-011ba632cba4
LIVE_ADVENTURE_ID    = b371256a-015a-4a72-8cc2-8f5ad6b40cd4
```

`FullGameScenario.play()` calls `POST /game` with these fixed IDs (see
`support/FullGameScenario.java`) instead of authoring an `AdventureMap` from scratch. This is a
**real, meaningful architectural decision** worth flagging to the user again before this suite is
considered "done" — it means the test suite depends on a specific live account/adventure map
existing in a real (shared?) deployment rather than being fully self-contained/isolated. Open
questions for next session: is this deployment a dedicated test/dev environment or shared with real
users? Should CI point here, or should the emulator approach be revisited once a better way to seed
realistic content exists? The user directed this pivot explicitly mid-session; it hasn't been
re-examined against the original "isolated, deterministic" design goal from the plan file.

**The Firestore-emulator scaffolding still exists and still works** (confirmed via
`SmokeIntegrationTest`), just isn't used by `FullGameScenario` anymore:
- `src/main/java/client/nowhere/config/FirestoreConfig.java` — changed `@Profile("!test")` to
  `@Profile("!test & !integration")` so the prod bean doesn't clash with the emulator bean.
- `src/integrationTest/java/client/nowhere/config/FirestoreEmulatorConfig.java` — `@Profile("integration")`
  bean pointing `Firestore` at `localhost:8080` (or `$FIRESTORE_EMULATOR_HOST`) via
  `FirestoreOptions.newBuilder().setEmulatorHost(...)` — **do not use `getDefaultInstance().toBuilder()`**,
  that caused a TLS-vs-plaintext channel bug (see "Gotchas" below).
- `src/integrationTest/resources/application-integration.properties`
- `build.gradle` — new `integrationTest` source set + `integrationTest` Gradle task (not wired into
  `check`/`build`).
- To use it: `gcloud emulators firestore start --host-port=localhost:8080 --project=nowhere-integration-test`,
  then `FIRESTORE_EMULATOR_HOST=localhost:8080 ./gradlew integrationTest --tests "..."`.
- `support/WorldSetup.java` — the abandoned hand-authored AdventureMap fixture. Still compiles, no
  longer referenced by `FullGameScenario`. Candidate for deletion once the emulator-vs-live-backend
  question above is resolved (it's only useful if the emulator approach comes back).

## RESOLVED: round-1 `LOCATION_VOTING`/`SET_ENCOUNTERS` crash was a test-harness bug, not a backend bug

The theory below (the state silently chains from `LOCATION_VOTING` straight to
`SET_ENCOUNTERS_WINNERS` in one call, skipping `SET_ENCOUNTERS`) was **wrong**. Root-caused
2026-08-05 via `gcloud logging read` (full HTTP request sequence per gameCode, not just the error
severity logs) + manual curl probing against `S9FU4D`/`64S7KT`/a fresh throwaway game:

- The games flagged as stuck (`S9FU4D`, `64S7KT`) were sitting at **`SET_ENCOUNTERS`**, not
  `LOCATION_VOTING` or `SET_ENCOUNTERS_WINNERS` — that was the tell the original theory didn't
  account for.
- **`ActiveSessionHelper.update(gameCode, gamePhase, authorId, isDone)`** (see
  `src/main/java/client/nowhere/helper/ActiveSessionHelper.java:31-38`) auto-advances game state
  as a side effect: when the last player is marked done (e.g. the final vote in `LOCATION_VOTING`),
  it calls `GameSessionHelper.updateToNextGameState()` itself, **inside the same
  `POST /collaborativeText/votes` request**. That single request chained
  `LOCATION_VOTING → LOCATION_WINNING → SET_ENCOUNTERS` and persisted it successfully — no crash.
- `FullGameScenario`'s loop didn't know that happened. It **unconditionally** called
  `PUT /game/next` after every `runPhaseActions()`, regardless of whether the state had already
  moved. That redundant call tried to advance the *already-current* `SET_ENCOUNTERS` state to
  `SET_ENCOUNTERS_WINNERS` — but round 1's `SET_ENCOUNTERS` genuinely needs real player submissions
  first (round 0 skips this via `handleRoundZeroBoard`, so round 1 is the first time this phase is
  exercised for real), and none existed yet. Hence: `Collaborative text phase not found for game
  state: SET_ENCOUNTERS_WINNERS`.
- Verified directly: manually submitting `SET_ENCOUNTERS` content for all 3 players in `S9FU4D` via
  curl, then calling `PUT /game/next`, advanced cleanly to `WHAT_HAPPENS_HERE` with no error. This
  confirms the backend transition logic itself is correct.

**Fix applied** in `support/FullGameScenario.java`: after `runPhaseActions()`, re-fetch the game
state before deciding whether to call `next()`. If it already changed (auto-advanced
server-side), skip the redundant call:

```java
runPhaseActions(gameCode, gameState);
String stateAfterActions = GameFlowClient.stringField(client.getGame(gameCode), "gameState");
if (gameState.equals(stateAfterActions)) {
    client.nextGameState(gameCode);
}
```

This matches how a real client would behave (it reacts to the Firestore-delivered state rather
than blindly calling next after every action) — see the root `CLAUDE.md` rule: "Clients should
never infer phase logic from their own state — always trust what Firestore delivers from the
backend." The test harness was violating that.

With the fix, `FullGameThreePlayersTest` now reliably gets past `LOCATION_VOTING`, `SET_ENCOUNTERS`,
and `WHAT_HAPPENS_HERE` in round 1, and fails further along at `WHAT_CAN_WE_TRY` — see "Known bug"
below for that new, real blocker.

Live repro games from the *old*, now-corrected investigation: `S9FU4D` (manually advanced past
`SET_ENCOUNTERS` to `WHAT_HAPPENS_HERE` during this session's probing), `64S7KT` (left stuck at
`SET_ENCOUNTERS`, untouched), plus earlier ones `3YTPT2`, `KQUJYD`. **None deleted** — revisit
cleanup with the user at the end per standing instruction.

## RESOLVED: round 1 `WHAT_CAN_WE_TRY` had zero outcome types — another test-harness bug, not backend

Root-caused 2026-08-09. The theory that this was a backend content-creation or game-state-skip bug
was **wrong** — it was the test harness sending malformed submission data, this time on the
*submit* side rather than the *advance* side (see the RESOLVED section above for the sibling bug
on the advance side).

- `WHAT_HAPPENS_HERE`'s `getOutcomeTypes`, under the live `locationVoting` feature flag (confirmed
  `true` via `GET /featureFlags/value?flagName=locationVoting`), returns a single "location" parent
  `OutcomeType` wrapping the real encounter choices as `subTypes`
  (`CollaborativeTextHelper.buildLocationWrappedOutcomeTypes`, line 2473).
- The real Angular player-client (`nowhere-player-client/src/collaborative-text/collaborative-text.component.ts`,
  `onSelectSubType`, lines 345-354) keeps that wrapper intact when a player picks an encounter: it
  sends `outcomeTypeWithLabel` as `{ id: parent.id, label: parent.label, clarifier, subTypes:
  [chosenEncounter] }` — parent fields, with `subTypes` narrowed to the one chosen child — and
  separately flattens the flat `outcomeType` field to `subTypes[0].id` (lines 812-820, 861-868).
- `FullGameScenario.runSubmissionPhase` instead drilled into `subTypes.get(0)` and **discarded the
  parent entirely**, and `GameFlowClient.submitTextAddition` built a bare `new OutcomeType(id,
  label)` (empty `subTypes`) as `outcomeTypeWithLabel`.
- Server-side, `calculateWinnersFromAdditions`'s `WHAT_HAPPENS_HERE` branch under `locationVoting`
  (`CollaborativeTextHelper.java:448-453`) only counts submissions whose `outcomeTypeWithLabel`
  has non-empty `subTypes`. Since the harness's submissions never carried `subTypes`, **every
  submission was filtered out**, so `winners` came back `[]`.
- `GameSessionHelper.updateGameSession` (line 136) only calls `updateGameSessionWithWinningSubmissions`
  — the thing that invokes `handleWhatHappensHereStreamlined` and actually creates the round-1
  stories — `if (!winningSubmissions.isEmpty())`. Empty list → silently skipped. No exception, 200
  OK, exactly matching what was observed. Everything downstream (empty `WHAT_CAN_WE_TRY`
  outcome types, the `WHAT_CAN_WE_TRY_WINNERS` `ValidationException`) was a correct consequence of
  this, not an independent backend bug.

**Fix applied**: `GameFlowClient.submitTextAdditionWithSubType(gameCode, authorId, parent, chosen,
text)` builds the same parent-plus-narrowed-`subTypes` shape the real client sends.
`FullGameScenario.runSubmissionPhase` now calls it whenever the offered `OutcomeType` has
`subTypes`, instead of flattening to the child alone.

**Verified**: `FullGameThreePlayersTest` now advances from iteration 14 (previously died at round 1
`WHAT_CAN_WE_TRY`) all the way to iteration 46, passing through all of round 1, all of round 2, and
`DEFINING_TRAITS_VOTING` / `WRITE_EPILOGUES` before hitting the next (new, real) blocker below.

**Lesson reinforced:** the harness's "grab the first thing offered" simplification has now caused
two separate false leads (this one, and the `LOCATION_VOTING`/`SET_ENCOUNTERS` one above) by
silently not matching real client wire behavior. Any future phase with `subTypes` in the
`.OUTCOMES.md` table (`HOW_DOES_THIS_RESOLVE`, `DEFINING_TRAITS_VOTING`) should be assumed to need
the same treatment if/when the harness starts exercising it for real.

## Known bug (live deployment, real repro, still unfixed) — `ENDING` misclassified as a voting phase

**Worked around in the harness, not fixed in production** — per user direction, since `ENDING`/
`WRITE_EPILOGUES` are about to be redesigned anyway: `FullGameScenario.play()` now treats
`gameState == "ENDING"` as a terminal/successful stop, the same as `FINALE`, and never calls
`GET /collaborativeText/phaseInfo`/attempts phase actions on it. All three player-count tests assert
on `GameState.ENDING` rather than `FINALE` as a result. If/when `FINALE` becomes reachable again
post-redesign, revisit whether the terminal check should require `FINALE` again.

Original bug, for whenever the `ENDING`/epilogue rework picks this up — before the workaround above,
`FullGameThreePlayersTest` reached **iteration 46, `gameState=ENDING`** and failed there:

```
org.springframework.web.client.HttpServerErrorException$InternalServerError: 500 Internal Server Error
  path: /collaborativeText/voting
  at FullGameScenario.runVotingPhase(FullGameScenario.java:166)
```

Root cause (read-only, confirmed via code inspection, not yet fixed): `GameState.getPhaseBaseInfo()`
for `ENDING` (`GameState.java:418-428`) is:

```java
if (phaseId == ENDING) {
    return new PhaseBaseInfo(
            gameSessionDisplay.getEndingDescription(),
            "We see who we will become",
            CollaborativeMode.INFORMATION,
            null,      // collaboratingState
            ENDING,    // votingState  <-- looks like a copy/paste slip
            null,       // winningState
            false
    );
}
```

Every other `CollaborativeMode.INFORMATION` (pure display) state — `PREAMBLE`, `PREAMBLE_AGAIN`,
`ENDING_PREAMBLE` — puts *itself* in the **collaboratingState** slot and leaves voting/winning
`null`. `ENDING` is the only one that puts itself in the **votingState** slot instead. Since
`ENDING.getPhaseId()` falls through to `default -> this` (it's not grouped with any other state),
`CollaborativeTextHelper.determinePhaseType` sees `gameState == baseInfo.votingState()` and reports
`phaseType: VOTING` for `GET /collaborativeText/phaseInfo` during `ENDING`. Any client that trusts
that (this harness does, matching real client behavior of reacting to server-reported phase type)
then calls `GET /collaborativeText/voting`, which 500s: `VotingHelper.getVotingSubmissionsForPlayer`'s
switch has no `ENDING` case and there is no real `ENDING` collaborative-text phase in Firestore to
look up.

**Likely one-line fix**: swap `ENDING`'s `PhaseBaseInfo` to match the `PREAMBLE`/`ENDING_PREAMBLE`
pattern — `collaboratingState = ENDING`, `votingState = null` — but **not applied yet**. Per the
root `nowhere_java/CLAUDE.md` note that the `Ending` model/epilogue mechanic is mid-redesign
("confirm intent with the user" before touching related fields), this should be confirmed with the
user before changing production code, even though the fix itself looks unambiguous.

Live repro case: `gameCode=UMD7MX`, failed at iteration 46. Not yet deleted (see cleanup list
below).

## Architecture: `FullGameScenario` is now one file, fully typed, with deterministic scenarios

Added 2026-08-09/10, second pass. Originally this repercussion/fork-voting coverage lived in a
separate `RepercussionForkScenario` class, hooked into a generic `FullGameScenario` via a pluggable
`PhaseObserver` interface, and every response was handled as a generic `Map<String, Object>`
(matching what an HTTP client literally receives, "black box" style). Per explicit user direction
this was reworked twice more:

1. **Merged into one file.** `RepercussionForkScenario` no longer exists — its logic is now
   directly inside `FullGameScenario`, and the `PhaseObserver` pluggable-hook indirection is gone.
   `FullGameScenario.play(playerCount)` is the only entry point; it always builds and asserts the
   repercussion scenarios below as part of the standard run, for every player count (see "Current
   state" above for what each player count covers) — there's no more "generic" run that skips them.
2. **Fully typed.** `GameFlowClient` now deserializes every response directly into the real model
   classes (`GameSession`, `Story`, `Option`, `OutcomeFork`, `Player`, `Trait`, `OutcomeType`,
   `CollaborativeTextPhase`, ...) instead of `Map<String, Object>`. This needed two `ObjectMapper`
   fixes in `LiveBackend.restTemplate()` (see `TimestampModule.java`): `com.google.cloud.Timestamp`
   fields (e.g. `PlayerVote.votedAt`) have no default Jackson constructor, so a custom
   serializer/deserializer module reads/writes the same `{"seconds", "nanos"}` shape the server's
   own default bean serialization produces; and `FAIL_ON_UNKNOWN_PROPERTIES` had to be disabled
   since the client only needs a subset of each model's fields.
3. **Deterministic, not probabilistic, repercussion coverage.** Earlier runs only ever produced a
   real 2-fork vote between Bard's `ALL_PLAYERS`-alone branch and Herald's `TITLE` branch, and
   `getVotingCandidates` happened to return the same candidate first on every run observed, so the
   `TITLE` path was never actually exercised in practice (only `Battle-Scarred`, from the separate
   Trait+All-Players chain, ever showed up as a real granted trait — this was caught by the user
   reviewing two live game codes directly, not by the test itself, since the test's assertions were
   written to accept either outcome). Fixed by adding **controlled voting**
   (`preferredWinningSubmissionIds` in `FullGameScenario`): every vote checks whether any candidate
   matches a submission/option id the scenario deliberately built, and if so every voter votes for
   it, guaranteeing which fork/option wins instead of leaving it to candidate order. Each
   `PlayerClass`'s effect now gets its **own dedicated, non-competing target story** (see below) so
   all four resolve on every 4+ player run, not just whichever happens to win a shared vote.

### The four scenarios, one per `PlayerClass`, each targeting a different player's story

Real `Option`/`OutcomeFork` composition only matters for the story whose `Option` actually **wins**
`MAKE_CHOICE_VOTING` and whose fork actually wins `MAKE_OUTCOME_CHOICE_VOTING` — a losing
`Option`/`OutcomeFork` is still created and persisted, but `handleMakeOutcomeChoices` never applies
its repercussions, since `initializeMakeOutcomeChoiceVotingPhase` only ever pushes the **selected**
option's forks into voting. So each of the 4 deterministic scenarios needs its own story (one
player's natural `HOW_DOES_THIS_RESOLVE` assignment can't host two independently-verifiable
outcomes) — `buildRoundOneRepercussionScenarios` assigns each present class's target via `pickTarget`
(first available story that isn't the granter's own and isn't already used by another scenario):

| Granter | Target story | Mechanism | Asserts |
|---|---|---|---|
| Fabulist | first available (not Fabulist's own) | single branch, single fork → auto-select | a `Companion`-type trait, labeled with the story's own `EncounterLabel`, lands on the story's players |
| Scribe | next available | single branch, single fork → auto-select | a `Trait`-type trait "Steadfast" lands on the story's players |
| Scribe **then** Bard | next available | `TRAIT` branch, then `ALL_PLAYERS` **chained onto that branch** (not onto the root) → single fork → auto-select | the resulting `Trait` "Battle-Scarred" lands on **every** player in the game, not just that story's own `playerIds`/`partnerIds` — proves `ALL_PLAYERS` spreads a trait granted earlier in the same addition chain |
| Herald (+ a decoy author) | last available | Herald's `TITLE` branch **and** a decoy plain branch, sibling forks off the same root (real `MAKE_OUTCOME_CHOICE_VOTING`); **and** a second, real competing `Option` from the target's second `WHAT_CAN_WE_TRY` choice (real `MAKE_CHOICE_VOTING`); both controlled to resolve to Herald's content | 2 real `Option`s, 2 real `OutcomeFork`s on the winning one, `Player.getDisplayName()` includes "Voice of the Vale" for the story's players |

No player ever branches onto their own root (`pickTarget` excludes the granter) — mirrors a real
player picking up *someone else's* submission via `GET /collaborativeText/available`, not their own
(the endpoint excludes the requester's own submissions anyway — see `CollaborativeTextPhase.
getAvailableSubmissionsForPlayer`).

**Known gap, not forced in this pass:** `ALL_PLAYERS` with **no** other repercussion on the same
winning fork (as opposed to combined with `TRAIT`, which the Combined scenario above covers) isn't
given its own dedicated deterministic slot — there are exactly 4 target-story slots at 4 players and
4 new scenarios that needed one each. The dynamic, per-story prequel-eligibility check at the bottom
of `assertRoundOneEffects` (see below) still correctly handles an `ALL_PLAYERS`-alone winning fork
*whenever one occurs* (e.g. if a future run's decoy fork won instead of Herald's), it's just not
deliberately engineered to occur every run. Flag to the user if dedicated coverage for this specific
combination is still wanted.

### How a second `OutcomeFork` actually gets created — corrected mid-session

My first theory (submitted to the user, then corrected by them) was that a second fork comes from
**two independent root submissions** targeting the same option's `optionId`. **That's wrong.** The
real mechanism, confirmed by reading `nowhere-player-client/src/collaborative-text/
collaborative-text.component.ts` (`onSubmitNewText`, `onAddToSubmission`, `updateAvailableSubmissions`):

1. The naturally-assigned player submits a **root** `TextSubmission` for an option (`submissionId:
   null` → `CollaborativeTextHelper.createNewSubmission`).
2. `GET /collaborativeText/available` (`CollaborativeTextPhase.getAvailableSubmissionsForPlayer`,
   `CollaborativeTextPhase.java:117-152`) surfaces that root submission to **other** players (not
   its author, not whoever was last to add to it).
3. A **different** player calls `onAddToSubmission` — `POST /collaborativeText` with `submissionId`
   set to the root's id → `CollaborativeTextHelper.createBranchedSubmission`
   (`CollaborativeTextHelper.java:239-282`) creates a **new child** submission, copying the root's
   `outcomeTypeWithLabel` so it still targets the same option.
4. A **second, different** player branching directly off the **same root** (not off the first
   child) produces a sibling — two leaves under one root.
5. `CollaborativeTextPhase.getSubmissionsWithoutParentSubmissions()` excludes the root (now
   referenced as a parent) and returns both siblings as `HOW_DOES_THIS_RESOLVE`'s "winners".
   `handleHowDoesThisResolve` (`CollaborativeTextHelper.java:1319-1428`) turns the first into the
   `Option` + one `OutcomeFork`, and the second (same `optionId`) appends a **second**
   `OutcomeFork` (lines 1384-1400) — only then does `GameSessionHelper`'s `outcomeForks.size() < 2`
   auto-select skip (`GameSessionHelper.java:246-250`) fail to trigger, forcing real voting.

Chaining a second addition **onto a child** (not onto the root) instead of branching a sibling
accumulates multiple repercussions on one fork's addition history — used in the Combined scenario
above to prove `ALL_PLAYERS` spreads a trait a different repercussion just granted earlier in the
same chain.

`GameFlowClient.addToSubmission` + `getAvailableSubmissions` implement this. `FullGameScenario`'s
main loop special-cases exactly `(gameState == HOW_DOES_THIS_RESOLVE, roundNumber == 1)` to call
`buildRoundOneRepercussionScenarios` instead of the generic submission handling; every other phase
in the run (`LOCATION_VOTING`, `SET_ENCOUNTERS`, `WHAT_HAPPENS_HERE`, `WHAT_CAN_WE_TRY`, voting
phases, round 2, ...) still goes through the same generic `runGenericSubmissionPhase`/
`runVotingPhase` code path, now just inlined in the same class rather than split across two.

### `RepercussionType.SEQUEL` is dead code — the real "unfinished business" mechanic is prequel stories

Initially assumed "sequel" testing meant `RepercussionType.SEQUEL`. **Wrong, corrected by the
user.** `SEQUEL` is confirmed fully inert: no `PlayerClass` unlocks it (grep of the whole
`src/main/java` tree turns up nothing that constructs a `Repercussion` with that type), and the
`handleRepercussions` block that looked like a "SEQUEL default" (`repercussions.isEmpty() &&
roundNumber < 2` → append an outcome-display string) is unreachable — its only call site is already
gated by `if (!repercussions.isEmpty())` — and even if reached, never constructs an actual
`RepercussionType.SEQUEL` object, just a cosmetically-broken display string. Out of scope, not
tested.

The game's **real** "a round-1 story with unfinished business carries into round 2" mechanic is the
**prequel story** path in `CollaborativeTextHelper.getOutcomeTypes`'s `WHAT_HAPPENS_HERE` branch,
`roundNumber > 1` (`CollaborativeTextHelper.java:2194-2230`): a round-1 story the requesting player
authored, that's `visited`, and whose winning fork carried **no repercussion other than possibly a
lone `ALL_PLAYERS`** (`hasNonSpreadRepercussions`, `CollaborativeTextHelper.java:2456-2465`) gets
re-offered to that same author in round 2 as a `clarifier`-tagged subType (`clarifier` = the
original story's `storyId`). `FullGameScenario.assertRoundOneEffects` asserts this directly: for
every round-1 story, it computes the same "non-spread repercussion present?" check the server does
from the winning fork's actual repercussions, then asserts the round-2 prequel offer for that
story's author matches — present when there's no qualifying repercussion, absent when there is.

### `createBranchedSubmission`'s additions-list aliasing — checked once, not currently asserted

`createBranchedSubmission` does `newSubmission.setAdditions(parentSubmission.getAdditions())` (a
reference assignment, not a copy) then mutates it in place via `addTextAddition`
(`TextSubmission.java:89-93`) — in theory, two submissions branching off the same parent could end
up sharing (and cross-polluting) one underlying list. This was explicitly asserted on in the
now-removed `RepercussionForkScenario` (Bard's/Herald's sibling forks checked for cross-contaminated
`additions`) and **passed on every run** — most likely because `CollaborativeTextDAO.
addSubmissionAtomically` only persists the one new submission to Firestore per request; the
in-memory aliasing is real within a single request's object graph but never gets written anywhere
the aliasing would be observable afterward. The redesigned `FullGameScenario` still creates the
same sibling-fork shape (Herald's `TITLE` fork + the decoy fork, both branched off the same target
root — see the scenario table above) but no longer asserts on `additions` content specifically;
easy to re-add there if the risk is still worth standing guard on.

## 10-player sequel load test (`FullGameTenPlayersSequelTest`, 2026-09-29)

Added to reproduce the `gameSessions/{gameCode}` document outgrowing Firestore's 1 MiB limit in
large games. It turns on four opt-in `FullGameScenario` knobs:

- `cycleClasses()`: every player gets a class. Every class holder after the first of each class
  also attaches a repercussion to another untouched round-1 story.
- `mixSequelEligibility()`: with `sequelLocationScopeMode`, it also builds the full class
  scenarios. Round 2 then gets both kinds of round-1 story: ones re-offered as sequels and ones
  that aren't. `assertRoundOneEffects` checks each one.
- `realisticSubmissionText()`: pads every submission to the player-client's 150-character limit.
- `logSessionSize()`: `SessionSizeLog` samples the size of the GET /game JSON (total and per
  top-level field) and the time spent in each phase. It prints a table at the end, including
  when the run fails. The JSON size is a proxy for the Firestore document size, not the exact
  byte count Firestore enforces.

**First run (game SCKE82): passed and reached ENDING in about 13 minutes. It did NOT hit the
limit.** The peak was about 726 KB (about 71% of 1 MiB), at a round-2 `MAKE_OUTCOME_CHOICE_WINNER`.
`collaborativeTextPhases` is the largest field (about 380 KB at peak), then `stories` (about
233 KB). `adventureMap` is about 60 KB. The biggest jumps come at `HOW_DOES_THIS_RESOLVE_AGAIN`
and at the first `NAVIGATE_WINNER` of each round. Size drops after the first `MAKE_CHOICE_VOTING`
of each round, which looks like phase cleanup. The harness writes one addition per player per
phase plus the scenario branches. Real players write many more additions and branches, which
is probably why the real 10-player game crashed and this one didn't.

**`extraAdditionsPerPlayer(n)` added; with n=3 the crash reproduces.** In every `SHARE_TEXT`
phase that already has submissions, each player makes `n` plain-text additions to submissions from
`GET /collaborativeText/available`, the same way the real client's `onAddToSubmission` works. In
round 1 it skips the scripted scenarios' roots and forks, so their outcomes and assertions don't
change. It checks that the phase has submissions first because `GET /collaborativeText/available`
throws a 500 in `INIT`: `getAvailableSubmissionsForPlayer` calls `gameSession.getStories().stream()`
while `stories` is still null. That's a real but harmless backend fragility, since the client never
calls it there, and it's left unfixed.

Game EZFWSS failed at round-1 `HOW_DOES_THIS_RESOLVE_AGAIN` (`POST /collaborativeText` returned 500),
after about 4.5 minutes. Cloud Run log: `INVALID_ARGUMENT: Document '.../gameSessions/EZFWSS' cannot
be written because its size (1,050,513 bytes) exceeds the maximum allowed size of 1,048,576 bytes.`
`SessionSizeLog` read about 949 KB at that point, so its JSON measurement undercounts Firestore's by
about 10%. `collaborativeTextPhases` made up about 696 KB of that, with `stories` at about 140 KB. The
jump came at round 1 `WHAT_HAPPENS_HERE` → `WHAT_CAN_WE_TRY`: `collaborativeTextPhases` grew from
82 KB to 448 KB in that one phase.

**Latency logging, client-traffic simulation and concurrent players added (2026-09-29).**
`FullGameTenPlayersSequelTest` also turns on three more switches:

- `logLatency()`: `LatencyLog` is a RestTemplate interceptor that times every call, tags it with
  the current round and gameState, and prints two tables: one per endpoint (calls, errors,
  avg/p50/p95/max) and one per phase and endpoint. The endpoints the user asked about are marked
  with `*` and always listed, even when never called.
- `simulateClientTraffic()`: makes the calls the real clients make that the scenario doesn't need
  itself. From the TV: `GET /player`, `/display`, `/game-board`, `/collaborativeText/winner`, and
  the `PUT /activePlayerSession` timer writes. From each player: `GET /collaborativeText`, `/story`,
  and `/player/authorId`. Calls that only happen when a user taps something are not simulated. See
  that method's javadoc for the full list and its reasons.
- `concurrentPlayers()`: each phase's per-player calls run in parallel, one thread per player.
  Round-1 root submissions now identify the player's own new submission by `authorId`
  (`claimOwnNewSubmissionId`) instead of diffing the whole response.

`GET /collaborativeText/outcomeType` (singular) is still never called. It was left out on purpose.

**First concurrent run (game Y833DT) failed at round-1 `MAKE_CHOICE_VOTING` after about 6.5
minutes.** `PUT /game/next` returned 500 with the Firestore size error at 1,048,977 bytes. What the
latency tables show:

- `PUT /game/next` takes 1–2 s early in the game, then 10.9 s at round-1 `HOW_DOES_THIS_RESOLVE`
  and 16.4 s at `HOW_DOES_THIS_RESOLVE_AGAIN`.
- `POST /collaborativeText/votes` averages about 4 s, with a p95 around 11 s.
- `GET /collaborativeText/available` averages about 3.4 s. `POST /collaborativeText` averages about
  2.3 s, with a p95 around 10 s by `HOW_DOES_THIS_RESOLVE_AGAIN`.

The simulated client calls also found two real backend behaviours, both left unfixed:

- `GET /collaborativeText` returns 500 ("Collaborative text phase not found. Please submit text
  first.") whenever it's called before anyone has submitted in the phase. The player client calls
  it from `ngOnInit`, so real phones hit this every phase.
- `GET /collaborativeText/winner` returns 500 in `NAVIGATE_WINNER` ("Collaborative text phase not
  found for game state"). The TV calls it in every WINNING-type phase.

**Phases moved out of the GameSession document (2026-10-13, branch
`split-collaborative-text-phases`).** Submissions, votes and views are now one Firestore document
each under `gameSessions/{code}/rounds/{round}/[encounters/{xy}/]phases/{phaseId}/` (see `src/docs/.OUTCOMES.md`, "Where phase data
lives"). Harness changes that came with it:
- `addExtraBranches` reads the current phase from `GET /collaborativeText` instead of
  `GET /game`'s `collaborativeTextPhases`, which no longer exists.
- `SessionSizeLog` no longer tracks `collaborativeTextPhases`.
- `POST /collaborativeText` now returns only the submitting player's submissions.
  `claimOwnNewSubmissionId` / `claimNewSubmissionId` still work, because the author's new submission
  is still the only unknown ID in the response.
- `deleteGame` cleanup now deletes the game recursively (`GameSessionDAO.deleteGame`), so phase
  subcollections don't pile up in the live deployment.
- Both 500s above are gone, confirmed live: `FullGameFivePlayersTest` run WXB7TT had 0 errors on
  every endpoint (W9Z4RS, before the split, had 35 `GET /collaborativeText` + 3
  `GET /collaborativeText/winner` 500s). Same pair of runs: peak GameSession size
  337.6KB -> 181.5KB, summed phase time 326s -> 174s.
- `FullGameTenPlayersSequelTest` now **passes** (run MZPSAZ, 2026-10-25): peak 342.6KB (33.5% of
  1 MiB), down from failing at the 1 MiB limit. `stories` (~233KB) and `adventureMap` (~60KB) are
  now the biggest fields.

## Latency work (in progress, last updated 2026-10-25)

**Where things stand:** the phase split (PR #10) is merged and deployed as Cloud Run revision
`nowhere-java-00422-nnr` (2026-10-25 22:33Z). The next fix, for `POST /collaborativeText/votes` and
`GET /collaborativeText/voting`, is written and unit-tested but **uncommitted on local `master`**
and **not deployed or measured yet**. Next action: move it to a branch, deploy to sandbox, re-run
`FullGameTenPlayersSequelTest`, and compare against the numbers below.

### How the numbers were gathered (repeatable from this machine)

`gcloud` is signed in to project `nowhere-af065`. The backend service is `nowhere-java`
(us-east4; 1 vCPU, 512Mi, containerConcurrency 80, maxScale 3). Pick the test run's time window, then:
- Per-request latency: `gcloud logging read 'resource.type="cloud_run_revision" AND
  resource.labels.service_name="nowhere-java" AND httpRequest.requestUrl:* AND timestamp>=... AND
  timestamp<=...' --format=json`, grouped by method + path and `resource.labels.revision_name`.
- Where the time goes inside a request: Cloud Trace (`cloudtrace.googleapis.com/v1/projects/
  nowhere-af065/traces?view=COMPLETE`). Each `Recv.http ...` root span has the
  `FirestoreTimingAspect` DAO spans (`<Dao>.<method>`) nested under it.
- CPU / memory / concurrency: Cloud Monitoring `run.googleapis.com/container/{cpu,memory}/utilizations`
  and `max_request_concurrencies`.
- Deploy times: `gcloud run revisions list --service nowhere-java --region us-east4`.

### Measurements, 2026-10-25 22:08-22:55Z (full integration suite, before and after the deploy)

Server-side latency from Cloud Run request logs, all tests in the window combined (ms):

| Endpoint | 00421 (before) avg / p95 | 00422 (after) avg / p95 |
|---|---|---|
| `POST /collaborativeText/votes` | 3162 / 7523 | 1836 / 4949 |
| `GET /collaborativeText/voting` | 1274 / 4513 | 1264 / 3904 (unchanged) |
| `PUT /game/next` | 1736 / 3339 | 901 / 2039 |
| `PUT /activeGameStateSession` | 720 / 1573 | 659 / 1286 |
| `POST /collaborativeText` | 1371 / 3844 | 240 / 656 |
| `GET /collaborativeText` | 385 / 1356 | 265 / 748 |
| `GET /collaborativeText/outcomeTypes` | 215 / 789 | 323 / 788 (max 16.9s, see stalls) |
| `GET /collaborativeText/available` | 3534 / 7142 | not called after the deploy, so the new version is untested live |

Errors: 176 500s before (the empty-phase errors), 0 after.

Client-side, 10-player run MZPSAZ (`LatencyLog`): `/votes` avg 3000 / p95 6079 / max 12630,
`/voting` avg 1952 / p95 4362, `PUT /game/next` avg 1335 / p95 4042.

### Diagnosis (Cloud Trace, revision 00422)

| Endpoint | avg | time in `ActiveSessionDAO.updateActiveGameStateSession` | calls per request |
|---|---|---|---|
| `POST /votes` | 1826ms | 1455ms | 3.0 |
| `GET /voting` | 1262ms | 952ms | 0.7 |

`updateActiveGameStateSession` sets one player's "done" flag inside a transaction that read and
deserialized the **whole** GameSession document (~340KB at 10 players). Every player runs it at
about the same time, so the transactions queue on that one document. `/votes` ran it once per
player being marked done. `GET /voting` ran it on every poll, even for players already done.

Other findings from the same data:
- **`updatePlayer` / `updateStory` loops.** `GameSessionDAO.updatePlayer` and
  `StoryDAO.updateStory` each read the full document, change one array element, and write the
  whole `players` / `stories` array back, with no transaction (so concurrent requests can overwrite
  each other's changes). Phase transitions call them sequentially: up to 20 `updatePlayer` in one
  `/votes` request (the last voter's request runs the phase transition), and up to 10
  `updateStory` in `PUT /game/next` (one slow trace spent 5.1s in them).
- **Container memory sits near the 512Mi limit under load, but the Java heap never fills.**
  Cloud Run's memory metric counts the whole process. Under load it ran at 86-92% median and up to
  94-100%; idle is about 60-66%. The same pattern shows on 2026-09-25 (93-97% during that load
  test), so it predates both the phase split and the observability commits (those raised idle by
  roughly 5-10 points). **One OOM kill:** `Memory limit of 512 MiB exceeded with 512 MiB used` on
  revision 00422 at 22:58:14Z, about 8 minutes after the test's last request (22:50:31Z), so no
  request failed. It's the only one in 60 days of logs. Memory kept climbing after traffic stopped,
  which fits native memory the JVM never returns to the OS. (An earlier version of this note said
  there were none; that check missed it.) JVM metrics
  (`custom.googleapis.com/jvm/*`, exported by Micrometer) for the same window:
  - The container runs `java -jar` with no flags on 1 vCPU, so the JVM picks the **Serial GC** and
    a max heap of 25% of the container: about **124MB**. Heap used peaked at 107MB.
  - Non-heap (metaspace ~70-80MB, JIT code cache growing 10 -> 26MB under load, class space ~9MB)
    peaked at ~121MB, direct buffers (gRPC/Netty) at ~20MB, and threads grew from 41 to 61 under load.
  - That's ~250MB the JVM accounts for. The rest, up to ~230MB at peak, is native memory the JVM
    doesn't report here (thread stacks, GC/JIT internals, malloc arenas). Measuring it would need
    `-XX:NativeMemoryTracking=summary` on sandbox.
  - **GC churn is the real cost.** One instance did 2710 young and 116 full GCs in about 25
    minutes (a full GC every ~13s), all stop-the-world on the single vCPU. GC overhead peaked at
    7%. The worst young-gen pause was 2.9s, which is abnormal for a 34MB eden and suggests CPU or
    memory starvation. The cause is the allocation rate: every request deserializes the ~340KB
    GameSession document, often more than once, for 10 concurrent players.
  - Around 22:44-22:46Z the metrics exporter logged `DEADLINE_EXCEEDED` to
    `monitoring.googleapis.com` (one call waited 16.7s for a connection, one DNS lookup took 9.3s),
    in the same minutes as MZPSAZ's 16-17s outliers in round 2 `HOW_DOES_THIS_RESOLVE_AGAIN`.
    That fits the process stalling, though which stall (GC vs CPU throttling) isn't proven.
  - **Applied 2026-10-26:** 1Gi on both services (prod revision `nowhere-java-00423-dzn`, sandbox
    `nowhere-java-sandbox-00002-qzf`), plus `--memory 1Gi` in both deploy steps of
    `.github/workflows/deploy.yml`, since the sandbox is recreated with defaults for every PR.
    Monitoring added the same day: memory/GC/OOM tiles on both "Nowhere Health" dashboards, the
    log-based metric `nowhere_out_of_memory` (container kills + `java.lang.OutOfMemoryError`), and
    the alert policy "Nowhere: out of memory", which emails the account owner.
  - Other options, not applied:
    `JAVA_TOOL_OPTIONS=-XX:MaxRAMPercentage=...` (raising it at 512Mi risks OOM, since non-heap
    already takes ~250-300MB). The lasting fix is allocating less per request, i.e. not reading the
    whole GameSession on every call (the done-flag change below is one step).
- **Empty-ID bug from the split (fixed in the uncommitted change).** 26 `IllegalArgumentException:
  'path' must be a non-empty String` errors in `initializeMakeOutcomeChoiceVotingPhase`. A lone
  `OutcomeFork` (every round-0 option) has a `TextSubmission` with no `submissionId`. Harmless,
  because that phase is skipped with fewer than 2 forks, but the error was logged on every such
  choice.

### The uncommitted change (local `master`, on top of 381197b)

- `ActiveSessionDAO.updateActiveGameStateSession`: new overload taking a `Collection` of author
  IDs, so one transaction marks several players done. The transaction reads only `gameState`,
  `players` and `activeGameStateSession` (`Transaction.getAll(..., FieldMask)`), and skips the
  write when nothing changed. The single-player overload delegates to it.
- `ActiveSessionHelper.update`: matching `Collection` overload; no-op for an empty list.
- `VotingHelper.markPlayersDone`: every "mark done" in `/voting` and `/votes` goes through it.
  At most one transaction per request, and none for players the request's already-loaded
  GameSession shows as done.
  **Behavior change:** a `GET /voting` poll from a player who's already done no longer runs the
  transaction, so it can't start a second `updateToNextGameState` while the first is still
  running. Progression now comes only from the request that marks the last player done.
- `CollaborativeTextHelper.initializeMakeOutcomeChoiceVotingPhase`: returns early when there are
  fewer than 2 forks (documented in `src/docs/.OUTCOMES.md`, `MAKE_OUTCOME_CHOICE_VOTING`).
- `VotingHelperTest`: verifications switched to the batched overload; players a fixture already
  marks done are now expected to be skipped.

Verified: all unit tests pass. **Not verified:** the `FieldMask` transaction read. The DAO is
mocked in unit tests, so it only runs against real Firestore.

### Next steps, in order

1. Commit the change on a branch, deploy to sandbox, run `FullGameTenPlayersSequelTest`, and compare
   `/votes`, `/voting` and `PUT /activeGameStateSession` against the tables above. Also check the
   logs for any new errors from `updateActiveGameStateSession`.
2. Memory is now 1Gi (see above). Check the dashboard's GC tiles during the next 10-player run: GC
   count per minute should roughly halve, and the 16-17s outliers should go away. Note that the
   pre-1Gi tables above were measured at 512Mi, so a sandbox run now changes two things at once.
3. Batch `updatePlayer` / `updateStory`: one read and one write per request instead of one per
   player or story (and close the lost-update race while doing it). Longer term, `players` and
   `stories` could move out of the GameSession document the same way the phases did.
4. `GET /voting` still does a full `getGame` (~240ms avg) on every poll; worth trimming after 1-3.
5. Exercise `GET /collaborativeText/available` live; it hasn't been called since the deploy.

## False leads from this session (corrected, for context)

Chased two "production bug" theories against the local emulator that turned out to be wrong —
recorded so they aren't re-investigated:

1. **`GameSession.stories` null → NPE in `getStoryAtCurrentPlayerCoordinates()`.** Real crash
   locally, but root cause was NOT "stories is never populated" (it round-trips fine via
   `StoryDAO.createStory`'s `arrayUnion` + Firestore's native `document.toObject()`). A null-guard
   (`if (stories == null) return null;`) was added to `GameSession.java:267` anyway at the user's
   request ("leave it in place, it's fine") even though it's not proven to fix a reachable bug.
2. **`Option.outcomeForks` always null after Firestore round-trip → suspected deep-nesting bug in
   Firestore's Java POJO mapper.** Disproven by both an isolated diagnostic test (create+GET
   round-trip preserved `outcomeForks` fine) and by inspecting the real production adventure map
   directly (`Location.startingStories[].options[].outcomeForks` populated correctly in production
   data at the exact same nesting depth). The actual local "stuck at MAKE_CHOICE_VOTING" symptom was
   a **test-driver timing bug**: checking voting candidates after state had already advanced past
   `MAKE_CHOICE_VOTING`, not a serialization bug. Confirmed by manually replaying the same mistake
   against the live backend and getting the same "0 candidates" result, then fixing the timing and
   getting real candidates back.

**Lesson for next time:** when something looks broken locally, check whether it reproduces against
the live backend with known-good content before assuming a Firestore/serialization root cause.

3. **(2026-08-05) `LOCATION_VOTING`/`SET_ENCOUNTERS` "chains straight to `SET_ENCOUNTERS_WINNERS`
   in one call" theory.** See the RESOLVED section above — real root cause was a redundant
   `PUT /game/next` call in the test harness racing against the backend's own auto-advance
   (`ActiveSessionHelper.update`), not a backend state-machine bug. Fixed in `FullGameScenario`.
4. **(2026-08-08/09) Round-1 `WHAT_CAN_WE_TRY` empty-outcome-types → suspected backend
   game-state-skip or story-creation bug.** See the RESOLVED section further above — real root
   cause was the test harness flattening `outcomeTypeWithLabel` and losing `subTypes` when
   submitting `WHAT_HAPPENS_HERE` content under the `locationVoting` flag, not a backend defect.
5. **(2026-08-09) "A second `OutcomeFork` comes from two independent root submissions targeting the
   same optionId" theory.** My own first-pass theory, proposed to the user before writing any code
   for the original `RepercussionForkScenario`. **Corrected by the user**: real forks come from two
   different players each branching a `TextAddition` (`submissionId` set) directly onto the *same*
   root submission (`onAddToSubmission` in the real player-client), not from two separate root
   submissions. See "How a second `OutcomeFork` actually gets created" above for the corrected
   mechanism and why it matters (`getSubmissionsWithoutParentSubmissions` excludes referenced-as-
   parent submissions, so root submissions with no branches don't behave the same as branched ones).
6. **(2026-08-10) The original 2-fork "forced voting" test silently only ever exercised one branch.**
   Not a backend theory this time, but a real gap in the test's own design, caught by the *user*
   reviewing two live game codes directly (not by the test's own assertions, which were written to
   accept either outcome). Bard's `ALL_PLAYERS`-alone fork and Herald's `TITLE` fork competed for the
   same `MAKE_OUTCOME_CHOICE_VOTING` vote; `getVotingCandidates` happened to return Bard's fork first
   on every run observed, so every voter (voting for `candidates.get(0)`) always picked it, and the
   `TITLE` path was never actually proven to work. Fixed by adding controlled voting and giving each
   `PlayerClass` its own dedicated, non-competing target story — see "Architecture: `FullGameScenario`
   is now one file..." above.

**Running lesson across this whole session:** every "backend bug" theory chased so far (the
`LOCATION_VOTING`/`SET_ENCOUNTERS` redundant-`next()` bug, the `WHAT_HAPPENS_HERE` `subTypes`
flattening bug, and the fork-creation mechanism) turned out to be the test harness not matching real
client wire behavior closely enough. Before writing a new harness capability that submits content,
check the real Angular client's actual request shape first rather than inferring it from the
server-side model/response shape alone. Separately: a test whose assertions are written to tolerate
either branch of a non-deterministic outcome can pass every run while only ever actually exercising
one of them — worth designing for determinism (or explicitly asserting on *which* branch occurred)
rather than "accept whatever happened."

## Files touched this session

Production code:
- `src/main/java/client/nowhere/config/FirestoreConfig.java` — profile expression change (needed
  regardless of the emulator-vs-live-backend question, harmless).
- `src/main/java/client/nowhere/model/GameSession.java` — null-guard in
  `getStoryAtCurrentPlayerCoordinates()`, kept per user request despite unproven necessity.
- `src/main/java/client/nowhere/dao/GameSessionDAO.java` — new `deleteGame(gameCode)`, a plain
  `db.collection("gameSessions").document(gameCode).delete()`. Everything gameCode-scoped (players,
  stories, endings, rituals, collaborativeTextPhases, activeGameStateSession/activePlayerSession,
  gameBoard, adventureMap) lives as fields on that one Firestore document — no subcollections, so
  one document delete is a complete cleanup. Doesn't touch the global `stories` collection or
  `userProfiles.*.saveGames` (unrelated to normal gameplay, only written by `POST/PUT /admin/story*`).
- `src/main/java/client/nowhere/helper/GameSessionHelper.java` — `deleteGame(gameCode)` wrapping
  the DAO call.
- `src/main/java/client/nowhere/controller/AdminController.java` — `DELETE /admin/game?gameCode=`.
  Deployed to the live backend (pushed to `master`, GitHub Actions `.github/workflows/deploy.yml`)
  partway through this session — the test games created before that deploy finished predate the
  cleanup step existing at all.

The `ENDING` misclassification bug (see above) remains deliberately unfixed pending the user's
planned EPILOGUES/ENDING rework.

Test infra (`src/integrationTest/...`): `config/FirestoreEmulatorConfig.java`,
`resources/application-integration.properties`, `SmokeIntegrationTest.java`,
`support/{GameFlowClient,FullGameScenario,LiveBackend,TimestampModule,WorldSetup}.java`,
`{FullGameThreePlayersTest,FullGameFourPlayersTest,FullGameFivePlayersTest}.java`. `build.gradle`
has the `integrationTest` source set/task. `RepercussionForkScenario.java` was created, then merged
into `FullGameScenario` and deleted in the same session (see "Architecture" above). `GameFlowClient`
now returns typed model objects everywhere (`GameSession`, `Player`, `Story`, `TextSubmission`,
`OutcomeType`, `CollaborativeTextPhase`, ...) and gained `getPlayerClasses`, `selectPlayerClass`,
`getPlayerRepercussionTypes`, `getAvailableSubmissions`, `addToSubmission`, and `deleteGame`.

## Next steps

1. Resolve the architecture question: is the live-backend approach meant to be permanent for this
   suite, or a debugging convenience to be replaced once a better way to seed realistic content
   exists? (Affects whether `WorldSetup`/emulator scaffolding should be deleted or finished.)
2. Decide on and apply the `ENDING` `PhaseBaseInfo` fix (see above) once the user's EPILOGUES/ENDING
   rework lands, and switch the terminal-state check back to (or in addition to) `FINALE`.
3. Consider a dedicated deterministic scenario for `ALL_PLAYERS` with no other repercussion on the
   winning fork, if the user still wants it forced every run rather than opportunistically covered
   by the dynamic prequel-eligibility check (see "Known gap" above).
4. Revisit cleanup of the live test games created **before** the `DELETE /admin/game` endpoint was
   deployed this session — `3YTPT2`, `KQUJYD`, `S9FU4D`, `64S7KT`, `PXH3DY`, `PK247B`, `Y7JFKU`,
   `UMD7MX`, `TJKK94`, `CEUYW3`, plus any others from this session's probing. Every run **after**
   the endpoint went live cleans itself up automatically on success (not on failure, so a failed
   run's game is still left behind for debugging).
