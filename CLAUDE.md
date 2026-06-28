# Game Flow Reference

## Documentation Files — Read These Before Making Changes

Four reference files live in `src/`. They are the source of truth for game design intent. Always
check the relevant one before touching the areas below.

---

### `src/.README` *(stays in `src/`)*
**Read when:** adding or removing game phases, changing the game mode flow, touching the state
machine, or modifying core domain models.

Contains: full project overview, monorepo structure, all core domain models, collaborative writing
modes (RAPID_FIRE vs SHARE_TEXT), the Dungeon Mode macro flow diagram, player assignment offset
logic, and a table of unimplemented/placeholder states.

---

### `src/docs/.OUTCOMES.md`
**Read when:** working on game state transitions, `getOutcomeTypes`, `initializeLocationVoting`,
or any logic that affects how players are assigned content to write or vote on.

Contains: phase-by-phase breakdown of how `OutcomeType` IDs map to domain objects, how
`TextSubmission.currentText` flows into story fields, and how each phase's handler resolves
winning submissions.

**Update `src/docs/.OUTCOMES.md`** whenever changes affect player content assignment, outcome
type mappings, or phase handler behavior.

---

### `src/docs/.CLASSES.md`
**Read when:** adding new repercussion types, changing how player classes gate abilities,
modifying `handleRepercussions` / `handleSpread` / `handleNewTraitRepercussions`, or building
any phase where player class should affect the outcome.

Contains: all four player classes and which `RepercussionType` each unlocks, all five repercussion
types with colors and effects, the full data flow from class selection → repercussion availability
→ attachment on a `TextAddition` → resolution when a submission wins → traits landing on `Player`.

---

### `src/docs/.PLAYER_LIFECYCLE.md`
**Read when:** adding new game phases that interact with the player character (traits, titles,
companions, stats, location, partner relationships), designing ending/epilogue mechanics, or
implementing features that should have a payoff from earlier in the game (e.g. SEQUEL repercussions,
class-gated epilogue actions, trait-driven prompts).

Contains: the full arc of a player character from join → character creation → PREAMBLE → location
selection and location traits → encounter writing → individual encounter resolution → partner
mechanic → repercussion accumulation → epilogue. Also covers what the final `Player` object holds
and which fields are actively used vs. legacy.

---

## Rules

- When adding a new `GameState`, add it to the state machine in `src/.README` and update
  `src/docs/.OUTCOMES.md` if it involves player content assignment.
- When adding a new `RepercussionType` or changing class abilities, update `src/docs/.CLASSES.md`.
- When adding phases that change the player character arc (new traits, new ending mechanics,
  SEQUEL resolution, class-powered epilogue actions), update `src/docs/.PLAYER_LIFECYCLE.md`.
- Never add a phase that touches `getOutcomeTypes` without reading `src/docs/.OUTCOMES.md` first.
- The `Ending` model has unused fields (`associatedLocationId`, `associatedRitualOption`,
  `didWeSucceed`) left over from a prior ending design. Before using them, confirm intent with
  the user — they may be repurposed for the new epilogue mechanic.
