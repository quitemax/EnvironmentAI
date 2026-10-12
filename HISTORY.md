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

## 2026-10-11 — Conversation state and camera

- The talk key now starts a server-owned conversation: a villager held still (Brain tick skipped),
  a persistent text box, the villager's last line on screen, and an over-the-shoulder camera eased
  in and out. First custom network protocol (six payloads) and first client mixin.
- Ends on Escape, moving more than 1 m, the villager being hurt, a hostile within 8 blocks, a
  frightening reply (it runs away and is recorded as threatened), death, disconnect or dimension
  change.
- Verified outside the game: the mood ladder (now including `threatened`) and the camera ease state
  machine, which exposed a small snap when leaving during ease-in (fixed with the inverse of
  smoothstep). Fabric's documented contract was read to confirm receivers run on the server thread.
- In 26.3: screens are on `Gui`, rendering is `extractRenderState(GuiGraphicsExtractor, ...)`, payload
  registries are `serverboundPlay()`/`clientboundPlay()`, and the camera pose is `alignWithEntity`.

## 2026-10-11 — Conversation look, status panel, speaking first

- Feedback from the first in-game use: the villager was hard to see. Camera moved further to the
  side and slightly above; the field of view narrows to 70% with the same easing; black letterbox
  bars and edge shading added; the screen now outlives the conversation by the ease-out so the bars
  slide away with the shot.
- Status panel (name, mood and reason, activity, health, standing toward you, temperament) driven by
  a server `Status` payload sent on start and on change.
- Villagers can speak first (mood, warning, thanks, greeting). The design problem was many
  villagers wanting to speak at once; `VoiceLedger` gates every attempt per player and per reason,
  a speaker is chosen by distance and line of sight, and it is told its group size to say "we".
  Tested outside the game: twelve villagers wanting to thank one player yield one thanks.
- Background blur was not added: vanilla's blur is whole-frame and would blur the villager too.

## 2026-10-11 — Camera tuning and a quiet chat during conversations

- Second round of in-game feedback: too close to both people, and the side angle a little wide.
  Camera moved back (0.35 to 1.4 blocks), side offset reduced (1.35 to 1.1), zoom eased (0.70 to
  0.80); after a second look settled at 1.8 / 1.2 / 0.80. The numbers are now a `conversationCamera` config block so further tuning needs no rebuild.
- Chat no longer receives the villager's replies or the "thinking" action-bar line while a
  conversation is on screen (they duplicated the subtitle); the client keeps the session and writes
  it to chat once at the end, including the player's own lines. The `/talk` command echoes the
  player's line too, so its chat also reads as a conversation.

## 2026-10-11 — Camera settled; narrator and director design

- Camera settled at behind 1.8 / aside 1.2 / zoom 0.80 after three rounds of in-game feedback.
- Design written for a player-state tracker, a comic-caption narrator and an Alien: Isolation-style
  director that paces a run through the advancement ladder to the dragon, with per-world seeded arcs
  (`docs/NARRATOR_DIRECTOR.md`). Checked against the game's data first: the story chain runs
  `mine_stone` to `enter_the_end`, the dragon is in the `end` tab, and the blaze rods and pearls
  needed on the way are not on the story path. Advancement completion has no Fabric event.

## 2026-10-11 — Player state and the narrator (N0, N1)

- Built the first two steps of the narrator/director plan: a 14-goal progress ladder keyed to real
  advancements (with the nether-tab gaps filled by hand), a rule-derived player state, and a comic
  caption narrator with four voices, a rate gate, and hand-written fallbacks.
- Defaults as decided: noir voice, hints on; both changeable at runtime. The narrator sees every
  player and tells the others about big moments, without names unless configured.
- The model is only a phraser and is checked. Testing it on the real prompt found it assigned
  genders to the player, failed to name an achievement (a diamond described as "dull grey stone"),
  and got cut off mid-sentence by the length cap in 3 of 16 samples. The first was fixed in the
  prompt (0 of 16 afterwards); the other two are fixed in code (a caption is checked against the fact
  it was given, and a cut-off reply is rejected rather than trimmed, after trimming at a comma was
  found to leave "a lone." as a caption).
- Wrote down the village growth, trade route and disaster ideas for later
  (`docs/VILLAGE_GROWTH.md`), anchored to what the engine already has (POIs, templates, sieges,
  raids) and explicit about what must exist first.

## 2026-10-12 — Director v1 (N2) and a mood that follows the conversation

- Added the director's tension budget and paced hints (`Director`): pressure from damage and nearby
  hostiles, decay over time, and a hint allowed only once the player is at ease and has stalled for a
  slack that grows with the run. After a spike passes it adds a one-line "relief" caption. Still only
  narration; the numbers are untried in play.
- During a conversation the villager's shown mood now follows what is said: a warm reply from a
  shaken villager records a reassurance, which settles fear and anger left over from earlier events
  (never a raid, a death it witnessed, or a hostile actually nearby), and the status panel is pushed
  right after the reply instead of at the next periodic refresh.
- Looked through the logs for the machine freeze after the last playtest: the game itself shows no
  errors, a handful of small model calls, millisecond latencies and a clean exit at 00:01:37; the
  hard reset (Kernel-Power 41) came at 00:06, after the game had closed, so the cause is outside the
  mod.

## 2026-10-12 — Villager rumours (first N3 lever)

- At the `full` setting the director can now use villagers as a voice: after a missed narrator hint,
  a villager in earshot passes on a plain rumour about the next goal. One per slack, gated by the
  existing voice ledger, never over a conversation. Every goal now carries a hand-written `rumour`
  beside its oblique hint. Mob pressure is deliberately left for a separate decision.

## 2026-10-12 — The retreating enderman, and a design for mob behaviour

- A geared player looking at an enderman made it back away. The call log showed 66 FLEE decisions in
  one playtest: endermen were plain melee (nerve 9) and a netherite player's threat of 10 tripped the
  intimidation rule. Fixed with a `RELENTLESS` archetype that is never intimidated. The bug was ours,
  not vanilla's.
- Wrote `docs/MOB_BEHAVIOR.md`: group tactics and flanking, biome variants with gear that drops,
  villagers and the bell, pillagers, the Nether cast, skeleton-spider pairing, and a single relations
  table for mobs and animals. Design only; the open questions (loot scope, difficulty defaults) are
  listed there.

## 2026-10-12 — Squads with a captain, and animals that shy from threats

- Hostile mobs hunting the same player now plan as a squad: a captain (glows, kept while alive),
  a pinner, up to three flankers sent to the sides, a reserve that holds a ring, and ranged members
  spread round the target. A killed captain shakes the rest for fifteen seconds, as a shift of the
  existing nerve rule, not a new fear system. The planner is pure geometry and was checked outside
  the game; the feel is untried.
- Farm animals move away from hostile mobs within 8 blocks, through vanilla's own avoid goal with a
  predicate and a half-second throttle (the stock goal queries every tick).
- Loot and gear for mobs was set aside by decision; interactions among animals stay vanilla.
