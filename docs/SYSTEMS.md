# Systems: what each one does and what it changes

The system view. [`STATE.md`](STATE.md) walks through each kind of entity and its state; this file
takes each system in turn and lists what wakes it, what it reads, what it changes, and what it is
not allowed to touch. If the two ever disagree, the code is right and one of them needs fixing.

## The shape

```
 world events ──► System 2 triggers ──► (model) ──► mob narrative ─┐
                                                                   ▼
 every ~2 ticks ─► System 1 scheduler ──► rule? ──yes──► action ──► goals ──► vanilla pathfinding
                                          └─no─► (model) ─┘          ▲
 once a second ─► squad coordinator ──► assignment ───────────────────┘ (FlankGoal, nerve bonus)

 player events ─► story tracker ──► rules (state, tension) ──► narrator ──► caption (client)
                                                         └──► rumour ──► villager voice

 world events ──► villager mood rules ──► mood ──► (vocal?) ──► villager voice ──► System 3 line
 player /talk ──► conversation manager ──► System 3 ──► reply, memory, capped gossip nudge
```

Three tiers use a model (System 1, 2, 3) and one more (the narrator) uses one only to phrase a fact a
rule already established. Everything that *decides* a fact (a mood, a player's state, a flee, a
squad role) is ordinary code, which is why those are free, repeatable and explainable.

Each tier has its own config block (`system1`, `system2`, `system3`; the narrator reuses
`system3`'s), its own model, endpoint, thread pool and in-flight cap. All model failures are silent
and fall back (see the table at the end).

---

## System 1: the fast action picker

`ai/scheduling/System1Scheduler`, prompt in `ai/prompt/System1PromptBuilder`, parse in
`ai/action/ActionParser`.

**Wakes:** every server tick. Each mob is considered once every `tickInterval` ticks (default 2),
staggered by its UUID so they do not all land together.

**Decides per mob, in order:**

| Route | When | Result |
|---|---|---|
| skip | no player within `activeRadiusBlocks` (48) | nothing |
| skip | its previous call is still in flight | nothing |
| rule: `no_target` | it has no target | `IDLE`, no model call |
| rule: `critical_flee` | health below 40%, target dangerous and within 16 | `FLEE`, held until 55% |
| rule: `intimidated` | target's threat ≥ mob nerve (+ fellow hostiles, + squad morale) | `FLEE` (`KEEP_DISTANCE` if ranged) |
| model | otherwise, if spacing and the global cap allow | one action from the valid set |

**Reads:** the mob's health, light, narrative (all four strings), target distance, health and gear,
line of sight, up to six nearby creatures with distances, its previous action. Never player names.

**Changes:** the mob's `currentAction` and its bookkeeping. And it raises two escalations to System
2: near death, and stuck repeating an action.

**Limits it enforces:**

- Replies are constrained by schema to the actions valid for this mob and situation (archetype, with
  or without a target). An invalid one is discarded and the old action kept.
- A rule that becomes true while a call is in flight wins over the answer.
- A mob that started fleeing keeps fleeing for 2.5 s regardless of what the model says.
- At most `maxInFlight` (default 4) calls outstanding; least-recently-served mob goes first.
- Nothing is dispatched during the startup grace.

**Does not:** touch the narrative, move anything itself, or call System 2 directly.

### How an action becomes movement

`goal/` holds one goal per action, installed on entity load at priority 0. Each goal's `canUse()` is
"is this the mob's current action?", so at most one is eligible. Attack delegates to vanilla's
`MeleeAttackGoal`, wander to `WaterAvoidingRandomStrollGoal`; flee, approach, guard and keep-distance
are small custom goals. `FlankGoal` sits one priority above them and may borrow the move flag while
the action is attack, approach or idle, to walk a squad member to its point.

---

## System 2: the slow narrative refresh

`ai/scheduling/System2TriggerManager`, prompt in `ai/prompt/System2PromptBuilder`, parse in
`ai/narrative/NarrativeStateParser`.

**Wakes:** only in response to a trigger. There is no timer.

| Trigger | Source |
|---|---|
| `took damage` | damage hook, any hostile mob |
| `acquired target` / `lost target` | `MobTargetEventMixin` |
| `near death` | System 1 escalation |
| `stuck repeating the same action for a while` | System 1 escalation |

Each mob keeps only its **latest** pending trigger, so waiting costs nothing. A trigger is served
after a 500 ms debounce (`debounceMs`), no sooner than 5 s after that mob's last refresh
(`cooldownMs`), only if a player is within `activeRadiusBlocks`, and only when the single global slot
(`maxInFlight`, default 1) is free. Oldest trigger first.

**Reads:** the trigger reason, the mob's health, its previous disposition, intent and memory notes,
its target described *by type only* ("a player"), the target's gear and distance, and nearby
creatures.

**Changes:** the mob's narrative, wholesale (`disposition`, `intent`, `targetDescription`,
`memoryNotes`, timestamp). If the reply does not parse, nothing changes.

**Limits:** the schema caps each string (40/120/80/240 characters) so a small model cannot ramble
until the token cap truncates the JSON; the prompt forbids numbers and invented facts in the notes.

**Does not:** choose an action, move a mob, or affect any other mob. Its output matters only
through the System 1 prompt.

---

## System 3: villager dialogue

`ai/dialogue/DialogueService` (the model call), `ConversationManager` (the session),
`VillagerContext` (the facts it is given), `VillagerPersona`.

**Wakes:** on a player's line (`/talk`, or a line typed in a conversation), or when
`ProactiveVoice` hands it a situation for an unprompted line.

**Reads:** only facts assembled by `VillagerContext`: profession and level, village type, what it is
doing, time and weather, danger near, its mood and reason, its five recent events, whether a golem is
near, how it feels about this traveler (vanilla gossip), what the traveler wears and holds, what it
trades, and its last six exchanges with that player. The prompt says to mention only listed events.

**Changes** (and this is the complete list):

| What | How |
|---|---|
| Shows a line to the player | chat, or the conversation screen |
| Remembers the exchange | `VillagerState.conversations` |
| Nudges vanilla gossip by one point toward that player | `feeling` warm or cold, once per villager per day |
| Records `reassured` | `feeling` warm and a mood that can ease |
| Ends the conversation, sends the villager walking away, records `threatened` | `feeling` afraid |

**Two kinds of call:**

- *Conversation turn:* full facts and history, a 1.5-second cooldown per player, one in flight per
  villager, writes memory and may nudge.
- *Proactive line:* one short line, no history, no reputation effect, silent on failure.

**Does not:** set a mood, change trades, give items, or run commands.

---

## Villager mood and voice (rules)

`ai/villager/VillagerMoodService`, `VillagerMood`, `ProactiveVoice`, `VoiceLedger`.

**Wakes:** vanilla reputation events (trade, villager hurt or killed, golem killed, zombie villager
cured), damage and death hooks, the bell, and a once-per-second scan per villager.

**Changes:** the villager's recorded events, then its mood (recomputed by priority ladder, see
`STATE.md`). On entering a **vocal** mood (afraid, terrified, grieving, angry) with a player within
10 blocks and in sight, it offers a line to `ProactiveVoice`.

**Unprompted voice reasons and their gates** (`VoiceLedger`): a 20 s gap between any two lines to
the same player, plus a per-reason cooldown:

| Reason | Cooldown | Trigger |
|---|---|---|
| `MOOD` | 60 s | became vocal |
| `THANKS` | 120 s | player cured a zombie villager |
| `WARNING` | 120 s | hostile came close, player within 16 |
| `GREETING` | 300 s | first sight, or back after two days |
| `BELL` | 30 s | bell rung by a player |
| `RUMOUR` | 900 s | the director's gossip (below) |

One event is seen by a whole village, so `ProactiveVoice` finds who could speak (awake, free, in
earshot, line of sight), picks the nearest, *then* asks the ledger. One villager speaks for the
group. It never speaks over a conversation.

**The bell** (`BellRingMixin`): danger within 40 blocks makes a ring an alarm; four rings in a minute
with no danger makes it a prank; otherwise villagers within 20 blocks walk over to it. Villagers
within 32 blocks record the event; one may remark.

**Does not:** change how a villager moves, other than the bell walk. That is a deliberate boundary.

---

## Narrator and Director (rules plus phrasing)

`ai/story/`: `StoryTracker` (observes), `PlayerStateRules`, `Director`, `ProgressLadder`,
`NarrationLedger`, `NarratorService`, `NarrationTemplates`.

**Wakes:** a scan per player once a second; an advancement poll every two seconds; damage, death,
respawn hooks.

**Changes:** the in-memory per-player `Log` only, and **what the player is shown**:

| Moment | Source | Gate |
|---|---|---|
| goal reached (`STAGE`) | advancement poll | 10 s kind cooldown |
| near death | damage, below 25% | 120 s |
| death (on respawn) | respawn hook | 60 s |
| hunted (state entered) | state rules | 180 s |
| dimension change | scan | 60 s |
| dawn after a night | scan | 600 s |
| relief after a pressure spike | tension reaches *at ease* after peaking ≥ 60 | 300 s |
| quiet hint (`QUIET`) | director allows a nudge | 600 s |
| "elsewhere" note to others | someone else's major moment | 90 s |

Plus a 25 s gap between any two captions to one player. Never during a conversation.

**The director** is a rule on top: pressure (damage ×6, hostiles 3 per second each, ×1.5 at night,
decay 1 per second) and a slack that grows with the run (9 000, 12 000, then 15 000 ticks). A hint
is allowed only when the player is *at ease* (low for a minute) and has made no progress for a full
slack; a villager's rumour only after a hint went unheeded for half a slack more, and only at the
`FULL` setting.

**Intensity** (`directorIntensity`): `OFF` silent; `NARRATION` comments only; `HINTS` adds hints;
`FULL` adds villager rumours. Pressure on mobs is not built.

**The model's part:** it only rephrases a caption in the chosen voice, from a fact already given.
The result is checked against that fact; a slow, failed or off-topic answer is replaced by a
hand-written template, so the narrator works with no model at all.

**Does not:** change the world, spawn anything or alter a mob. It is observation and speech.

---

## Squad coordinator (rules)

`ai/squad/SquadCoordinator`, `SquadPlanner` (pure geometry), `goal/FlankGoal`.

**Wakes:** once a second.

**Reads:** every tracked mob with a player as its target within 24 blocks.

**Changes:** a static map of assignments (role, point, captain); the captain gets a 2-second glow
effect each scan (`squadCaptainGlow`); a shaken-until time for members when the captain dies.

**Effect on mobs:** members read their assignment in `FlankGoal`; and the intimidation rule gets
+1.5 nerve for a member with a living captain, -3 for 15 s after the captain's death. There is no
second fear system.

**Off switch:** `squadTactics`. Needs three or more mobs hunting one player.

---

## Cross-cutting

### Fallbacks: what happens when a model call goes wrong

Every provider error, sync or async, is caught and the game keeps running. Never a crash.

| Tier | On failure |
|---|---|
| System 1 | keeps the previous action; rules continue to apply |
| System 2 | keeps the previous narrative |
| System 3 conversation | the villager says "Hrm." and the state is otherwise unchanged except `dialogueInFlight` clears |
| System 3 proactive | silent |
| Narrator | hand-written caption |

### Load control

`AiLoad` holds shared in-flight counters. All tiers can run against one GPU, so the logs record how
many of each were outstanding at dispatch; a startup grace (200 ticks, 10 s) keeps the first seconds model-free so
loading a world does not stampede the model. Per-tier thread pools mean a slow System 2 call cannot
starve System 1.

### Where a change should go

| You want to change… | Edit |
|---|---|
| when a mob runs away | `System1Scheduler.decideRule`, `ModelTierConfig` flee/intimidation knobs |
| what a mob is told | `System1PromptBuilder`, `System2PromptBuilder`, `Surroundings`, `TargetAssessment` |
| which actions a mob type may pick | `MobArchetype` |
| what makes a villager frightened | `VillagerMood.compute` and the event sources |
| how chatty villagers are | `VoiceLedger` cooldowns, `ProactiveVoice` radii |
| when the narrator speaks or hints | `NarrationLedger`, `Director`, `PlayerStateRules` |
| the story path | `ProgressLadder` |
| squad behaviour | `SquadPlanner`, `SquadCoordinator`, `FlankGoal` |
| who animals fear | `Relations` |

### Not built yet

Shown here so the boundary is clear: nothing writes world state beyond the entities above. The
director cannot spawn mobs or add pressure; villagers have no model-chosen behaviour; animals have
no narrative; nothing remembers a *player* across sessions except vanilla gossip and what villagers
recorded themselves. Design notes for those are in `NARRATOR_DIRECTOR.md`, `MOB_BEHAVIOR.md`,
`VILLAGERS.md` and `VILLAGE_GROWTH.md`.
