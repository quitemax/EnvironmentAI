# Project History

A changelog of notable decisions, changes, and milestones for EnvironmentAI. Unlike a git log, this is meant to capture *why*, not just *what*.

## 2026-09-24 — Project scaffolded

- Initialized the repository as a Fabric mod targeting Minecraft 26.3.
- Based the boilerplate on the current official [fabric-example-mod](https://github.com/FabricMC/fabric-example-mod) template (branch `26.3`), rather than an older cached template, since Minecraft 26.1 introduced a breaking toolchain change: the game is now shipped unobfuscated with official Mojang mappings, Yarn mappings are no longer supported, and the Loom plugin/build setup changed accordingly (no more `remapJar`, standard `implementation`/`compileOnly` dependency configs, Java 25 minimum).
- Mod id: `environmentai`, root package: `com.quitemax.environmentai`.
- Versions pinned: Fabric Loader 0.19.5, Fabric API 0.161.0+26.3, Loom 1.17-SNAPSHOT, Gradle 9.5.1, Java 25.
- Added `README.md`, `ROADMAP.md`, and this file to track project docs going forward.

## 2026-09-24 — Dual-process ("System 1 / System 2") hostile mob AI

- Replaced vanilla hostile-mob AI with a two-tier, LLM-driven decision system: System 2 (slow,
  event-triggered) maintains a per-mob narrative state; System 1 (fast, frequent) reads that
  cached state plus immediate situational facts and picks one discrete action from a fixed
  vocabulary (`ATTACK`, `FLEE`, `APPROACH`, `GUARD`, `WANDER`, `IDLE`). Vanilla's own
  goal-selector/pathfinding machinery executes whichever action is active, via six small `Goal`
  implementations gated on the mob's current action — chosen specifically so we don't have to
  hand-roll pathfinding/combat.
- Scope is hostile mobs only (`Monster`/`Enemy`) for this pass, not passive mobs or villagers.
- Both tiers are independently configurable to a local model (Ollama) or cloud model (Anthropic
  API) via `config/environmentai.json`. Default out of the box: System 1 → local Ollama (fast/cheap
  for the frequent tier), System 2 → Anthropic cloud (stronger reasoning for the narrative tier).
- System 2 has no fixed timer — it only refreshes on triggers (damage taken, target acquired/lost,
  or a self-flagged escalation from System 1 for near-death/stuck-repeating-the-same-action), with
  a debounce (wait for a quiet period) and cooldown (minimum spacing between actual dispatches) per
  mob to avoid spamming cloud calls.
- Narrative/action state is stored via Fabric API's `attachment.v1` module (persistent, codec-backed),
  not a hand-rolled map — this gives automatic NBT save/load across chunk unload/restart for free.
- This mod's toolchain had just changed underneath it (Minecraft 26.1 dropped Yarn mappings), so a
  research pass verified real current Mojang-mapped API names against live javadoc before
  implementation, then every stage was validated by an actual `JDK 25` compile (installed locally
  via `winget install Microsoft.OpenJDK.25` specifically to enable this) rather than trusting
  remembered signatures — this caught several wrong assumptions along the way (e.g. `Mob.goalSelector`
  is `protected`, not `public`, requiring a `@Shadow` field in the mixin; `Entity#getServer()`
  doesn't exist — main-thread marshaling goes through `ServerLevel#getServer()` instead). The full
  mod was also smoke-tested with a real headless `./gradlew runServer` boot, confirming all three
  mixins (`registerGoals` injection, `setTarget` injection) apply cleanly against the actual 26.3
  classes with no Mixin errors.
- Removed the leftover `ExampleMixin`/`ExampleClientMixin` template boilerplate and the now-empty
  client mixin config, since this feature is entirely server/common-side.

## 2026-10-11 — Tracker leak, per-tier pools, local tooling, debug datapack

- **`MobTracker` leak fixed.** Mobs were tracked from the `registerGoals` mixin and never untracked,
  so despawned and chunk-unloaded mobs stayed referenced in static maps forever. Tracking now follows
  `ServerEntityEvents.ENTITY_LOAD` / `ENTITY_UNLOAD`, which also stops tracking mobs that were
  constructed but never added to a level. Unload removes by `(uuid, instance)` so a chunk reload that
  re-registers the same UUID can't be evicted by the old instance's unload.
- **Executor pools are per tier, not per provider type.** With both tiers `LOCAL` they previously
  shared one pool, letting a slow System 2 call starve System 1. Config fields
  `localExecutorThreads`/`cloudExecutorThreads` became `system1ExecutorThreads`/`system2ExecutorThreads`
  (old keys are ignored; defaults 4 and 2).
- **Local model setup.** Two native Ollama instances (one per tier) via `tools/ollama/start-instances.ps1`.
  The machine's `:11434` is held by a WSL/Docker Ollama (older version), which is left untouched.
  The target GPU has 8 GB VRAM, so a 3B + 8B pair already runs close to the limit.
- **Debug datapack** (`testing/datapack/environmentai_debug`): twelve scenarios covering each action
  plus species coverage, System 2 divergence and scheduler load. Note that `IDLE` deliberately has
  no goal flags and so lets vanilla behaviour continue; it is also the provider-failure fallback.

## 2026-10-11 — Evidence-driven tuning pass (evening)

Driven by the new JSONL logs rather than guesswork. Measured problems and what was done:

- System 1 was overloaded and making no-op decisions with no target: added a skip/rule/model
  routing, a global in-flight cap and an `activeRadiusBlocks` gate.
- Prompts: the target shared the word `CRITICAL` with the mob's own health and the model fled from
  nearly-dead targets; WANDER was chosen 21% of the time with a target. Separate vocabularies and a
  restricted action list per context fixed both. A scripted edit that only half-applied was caught
  by the log (the new text was absent) and redone with verification.
- System 2 had no cap (6 concurrent calls, 16 s each) and its answers slowed System 1 4x. Capped at
  1 in flight, oldest trigger first. Swapped to `llama3.2:3b` (median 2.1 s -> 1.2 s), which exposed
  runaway JSON output (one 17.9 s call at the 512-token cap), so `maxTokens` is now 160.
- Critical-health flee became a rule with hysteresis, because the model chose ATTACK about 40% of
  the time at critical health even with the hold in place. The 2.5 s FLEE hold remains for the
  non-critical case.
- Surroundings (type and distance of nearby creatures) added to both prompts. A locale bug printed
  `3,2 blocks`; fixed with `Locale.ROOT`.
- Both tiers can share one Ollama instance (`-Shared`): synthetic check showed System 1 at 248 ms
  alone and 308 ms with a System 2 call running, versus 261 ms and 1088 ms across two instances.

## 2026-10-11 — The action goals were never installed on zombies, spiders or skeletons

A playtest showed a zombie whose logged action was FLEE walking straight at the player. The logs
agreed (target distance shrinking during `critical_flee_sustained`). Root cause: the goals were
installed by a mixin on `Mob.registerGoals`, but `Zombie`, `Spider` and `AbstractSkeleton` override
`registerGoals` without calling `super`, so the hook never ran for them. Every earlier "the zombies
behave fine" observation was vanilla behaviour plus logged decisions that had no effect. The earlier
smoke test only proved the mixin *applied* cleanly, not that it *fired* on the mobs that matter.

- Goals are now installed from `ServerEntityEvents.ENTITY_LOAD` via `SystemGoals.install`
  (public `Mob.getGoalSelector()`, idempotent); the injection mixin is removed.
- Because the goals now genuinely run, `AttackActionGoal` is guarded: it only engages for mobs with
  an `ATTACK_DAMAGE` attribute (creepers and witches would otherwise throw in `doHurtTarget`) and
  leaves `RangedAttackMob`s (skeletons, witches, pillagers, drowned) to their vanilla attack goals.
- All behavioural conclusions drawn from sessions before this change describe the model's
  *decisions*, not mob behaviour.

## 2026-10-11 — Archetypes, perception, structured output, startup grace

- **Archetypes** (`MobArchetype`): melee, skittish, explosive and ranged mobs get different action
  sets, with a new `KEEP_DISTANCE` action that retreats ranged mobs from close targets and otherwise
  leaves them to their vanilla bow goal (holding no movement flag while firing).
- **Perception** (`TargetAssessment`): target armour points and held weapon become a 0-10 threat
  score; line of sight and light level join the prompt. **Intimidation** is a rule comparing threat
  to an archetype's nerve, raised by fellow hostiles, so a well-equipped player makes melee mobs
  flee and ranged mobs keep range.
- **Structured output**: Ollama is given a JSON schema, so System 1 can only answer with an action
  valid for that mob and System 2 can only emit its four keys. Actions invalid for the situation
  are also rejected in code for providers without it.
- **Startup grace**: all of a session's timeouts landed in its first seconds, while the server
  reported itself 40 ticks behind; model calls now wait 200 ticks after a world starts.
- **Base-goal fix**: `AbstractSystem1Goal.canContinueToUse` re-ran each subclass's throttled
  `canUse()`, so running goals stopped and restarted constantly. It now checks the action only.
- **Datapack**: `help` had been invalid JSON since it was written (a bad line-continuation
  workaround); regenerated from structured data and every `tellraw` payload is now validated.
- `tools/analyze-log.ps1` replaces the ad hoc queries used during tuning.

## 2026-10-11 — Villagers: observe and talk (System 3)

- Engine check first (26.3 bytecode): villagers are Brain-driven with no goal list, so the hostile
  mob goal approach does not apply, and a gossip/reputation system already exists to build on.
  Design in `docs/VILLAGERS.md`.
- New **System 3** dialogue tier: own config block, executor pool and call log. `/talk <message>`
  addresses the villager you are looking at; replies are schema-bounded and grounded in explicit
  facts; conversation memory persists per villager and per player; a warm/cold exchange nudges
  vanilla gossip by one point at most once per villager per game day.
- English only for now (the mod may be published); local model for now, built to move to the cloud.
- `tools/generate-datapack-help.py` generates and validates the datapack help function.

## 2026-10-11 — Villager mood (L2)

- Villagers record the last few things that happened to or near them (via vanilla's reputation
  events, damage and death events, and a periodic scan) and derive a mood by rules, with a reason.
  The mood and events go into the dialogue prompt, so what a villager says matches what happened.
- Strong mood changes can make a villager exclaim unprompted to a nearby player, rate-limited and
  silent on failure. Speech only: no movement or Brain changes.
- The mood ladder, event bookkeeping and save codec were exercised against the compiled classes
  outside the game (the first time any of this project's logic was tested without launching it).
- Lesson reinforced: the mixin targets a method `Villager` declares itself, after the earlier
  `registerGoals` hook that silently never fired on subclasses that override without `super`.

## 2026-10-11 — Talk key

- Rebindable key (default V) opens chat pre-filled with `/talk ` when a villager is under the
  crosshair; first client code in the project. Deliberately a shortcut, not a new protocol.
- In 26.3 screen handling lives on `Gui` (`gui.screen()`, `gui.setScreen(...)`), not `Minecraft`.
- Conversation camera assessed as feasible via a client mixin on `Camera.update`; not built yet.

