# Full-Game Integration Test Suite — Status / Handoff Notes

Working notes for continuing this session. See the original plan at
`C:\Users\vande\.claude\plans\moonlit-roaming-gem.md` for the original proposal (backend HTTP
integration suite, 3/4/5-player scenarios covering `GameState.getOutcomeTypeOffset`'s
`playerCount > 4` branch). **The approach described there has since pivoted** — see "Architecture
pivot" below. This file is the up-to-date source of truth for where things actually stand.

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
