# State: what each creature carries, and who changes it

This is the entity view: for each kind of thing in the game that the mod keeps state for, what that
state is, whether it survives a restart, and exactly which part of the mod is allowed to write each
field. The companion file [`SYSTEMS.md`](SYSTEMS.md) is the same information turned around: for each
system, what it reads and what it changes.

"System" here means the mod's own layers:

| Name | What it is | Model? |
|---|---|---|
| **System 1** | fast, per-mob action picker (`System1Scheduler`) | yes, plus rules that override it |
| **System 2** | slow, per-mob narrative refresh (`System2TriggerManager`) | yes |
| **System 3** | villager dialogue (`DialogueService`) | yes |
| **Rules** | deterministic code: reflexes, mood, player state, squad plans | no |
| **Narrator / Director** | captions to players, and when they may be shown (`StoryTracker`) | phrasing only |

A pattern worth knowing before reading on: **a model never writes game state directly.** Each model
output is parsed, validated against what is allowed for that entity, and only then stored; a rule
can overrule it. The one place a model reply reaches the world is a villager's `feeling`, and that
too goes through a cap (see Villager).

---

## Summary

| Entity | Where the state lives | Persisted | Written by |
|---|---|---|---|
| Hostile mob (any `Enemy` `PathfinderMob`) | `MobAiState` attachment | narrative + current action only | System 1, System 2, rules |
| Villager | `VillagerState` attachment | yes, except live flags | System 3, mood rules, event hooks |
| Player | `StoryTracker.Log` (memory) + vanilla advancements | no | Narrator/Director rules |
| Farm animal | none; one extra goal | n/a | nothing writes it; it reads hostile mobs |
| Squad (a group of mobs) | `SquadCoordinator` static maps | no | Squad rules, once a second |
| World / village | none of its own | n/a | nothing yet (see `VILLAGE_GROWTH.md`) |

---

## Hostile mob

Attached by Fabric's attachment API (`ModAttachments.MOB_AI_STATE`) to every `PathfinderMob` that is
an `Enemy`, on entity load. Source: `ai/state/MobAiState.java`.

### Narrative (the "who am I right now" part)

`NarrativeState`, four short strings plus a timestamp. **Persisted.**

| Field | Meaning | Size cap (schema) | Written by |
|---|---|---|---|
| `disposition` | its attitude, a word or two ("wary", "furious") | 40 chars | **System 2** only |
| `intent` | what it is trying to do | 120 | **System 2** only |
| `targetDescription` | what it thinks its target is, from given facts only | 80 | **System 2** only |
| `memoryNotes` | a rolling note of recent events, no numbers | 240 | **System 2** only |
| `lastUpdatedGameTime` | when System 2 last wrote it | n/a | **System 2** only |

Starts as `neutral` / `has not yet formed an intent`. If a System 2 reply cannot be parsed, the
previous narrative is kept unchanged.

**Who reads it:** only the System 1 prompt, and `/environmentai inspect`. No rule consults the
narrative. So at present the narrative influences behaviour solely by being text in the model's
prompt; a weak System 1 model can ignore it entirely.

### Action (the "what am I doing this second" part)

`currentAction`, one of `ATTACK FLEE APPROACH GUARD WANDER IDLE KEEP_DISTANCE`. **Persisted.**
Defaults to `IDLE`.

Written by exactly two writers, both inside `System1Scheduler`:

1. **A rule** (`decideRule`, `applyRule`), which beats the model:
   - no target → `IDLE` (nothing to react to; the model is not called)
   - `critical_flee`: own health below `fleeEnterHealth` (0.40) with a dangerous target within 16
     blocks → `FLEE`, held until health passes `fleeExitHealth` (0.55) or the target is beyond 24
   - `intimidated`: target's threat score ≥ the mob's nerve → `FLEE`, or `KEEP_DISTANCE` for ranged
   - neither applies if the target is at or below 25% health: finishing it beats running
2. **The model's answer**, but only if the action is valid for that mob right now (archetype and
   whether it has a target), and then still subject to: a rule that became applicable while the call
   was in flight, and `flee_hold` (a mob that just started fleeing keeps fleeing for 2.5 s).

The goals in `goal/` do not write the action; they only read it and carry it out through vanilla
pathfinding.

### Bookkeeping (not persisted)

Real-time scheduling data that is meaningless after a restart.

| Field | Purpose | Written by |
|---|---|---|
| `lastSystem1DispatchTime`, `system1InFlight` | per-mob spacing and "one call at a time" | System 1 |
| `lastSystem2DispatchTime`, `system2InFlight` | cooldown and in-flight guard | System 2 |
| `pendingSystem2TriggerReason` / `Time` | the latest unserved reason to refresh the narrative | event hooks, System 1 escalations |
| `consecutiveSameActionTicks`, `stuckEscalated` | detects "stuck repeating one action" | System 1 |
| `nearDeathEscalated` | fires the near-death escalation once per drop | System 1 |
| `actionChangedAtMs` | the 2.5 s flee hold | System 1 |
| `lastRuleLogMs` | throttles rule heartbeats in the training log | System 1 |

### Derived each time, never stored

| Value | From | Used for |
|---|---|---|
| Archetype (`MELEE`, `SKITTISH`, `EXPLOSIVE`, `RELENTLESS`, `RANGED`) | entity type and `RangedAttackMob` | which actions are offered, how much nerve |
| Target threat score 0 to 10 | target's armour/4 + weapon tier | intimidation rule, prompt text |
| Squad assignment (role, point, captain) | `SquadCoordinator`, per second | `FlankGoal`, nerve bonus |

### Events that mark a mob's narrative as stale

These do not write the narrative; they only ask System 2 to refresh it.

- it took damage
- it acquired or lost a target (`MobTargetEventMixin`)
- System 1 reports near death (health ≤ 20%, re-armed above 25%)
- System 1 reports it has repeated one action for 100 decisions in a row

---

## Villager

Attached as `ModAttachments.VILLAGER_STATE`. Source: `ai/state/VillagerState.java`. Villagers are
not given a System 1 or System 2 state: they have no actions chosen by a model and their movement
is vanilla's own, apart from the few nudges listed under "What can change how it moves".

### Persisted

| Field | Meaning | Limits | Written by |
|---|---|---|---|
| `conversations` | last exchanges with each player: what they said, what it answered | 6 per player, 8 players | **System 3** (`DialogueService.deliver`) |
| `mood` | one of `calm content uneasy afraid terrified grieving angry` | n/a | **mood rules** only |
| `mood_reason` | why, in words, always present for a non-calm mood | n/a | mood rules only |
| `mood_since` | game time of the last mood change | n/a | mood rules only |
| `events` | the last few things that happened to or near it (`kind` + `text`) | 5, same kind within 10 s replaces | event hooks, bell, conversation, scan |
| `last_nudge_day` | day of the last reputation nudge | one per villager per day | System 3 |
| `last_seen` | when each player was last seen near it, to notice a return | 8 players | the scan |

### Not persisted (live flags)

`dialogueInFlight`, `hostileNear`, `golemNear`, `lastAmbientMs`, `lastEvaluatedGameTime`: set by the
mood scan and by System 3, rebuilt within a second of loading.

### The mood is a rule, never a model output

`VillagerMood.compute` picks the first that applies:

| Priority | Condition | Mood |
|---|---|---|
| 1 | Brain activity is `raid`, `pre_raid` or `panic` | terrified |
| 2 | saw a villager die (within 5 minutes) | grieving |
| 3 | iron golem was destroyed (within 5 minutes) | afraid |
| 4 | kind words recently **and** no hostile near **and** shaken by an earlier event | calm |
| 5 | hurt by a player | angry |
| 6 | threatened by a traveler / hurt | afraid |
| 7 | saw a villager attacked | angry |
| 8 | hostile within 12 blocks | uneasy if a golem is near, else afraid |
| 9 | bell alarm (30 s), bell prank (90 s) | uneasy |
| 10 | cured a villager, or a recent trade | content |
| 11 | none | calm |

### Event kinds and who records them

| Event kind | Recorded by |
|---|---|
| `hurt`, `hurt_by_player` | damage hook |
| `witness_attack`, `witness_death`, `golem_lost`, `trade`, `cured` | vanilla reputation channel (`VillagerReputationMixin`) and the death hook for witnesses within 24 blocks |
| `threat` | the 1 Hz scan, when a hostile first appears within 12 blocks |
| `bell_alarm`, `bell_prank` | the bell (`BellRingMixin`), by whether danger is near and how often it is rung |
| `threatened` | a conversation in which the villager's `feeling` was `afraid` |
| `reassured` | a conversation in which `feeling` was `warm` and the mood was one kind words can ease |

### What a System 3 reply can change, in total

The model returns `{reply, feeling}` and the reply is bounded by a schema. The effects are:

| Effect | Condition |
|---|---|
| The reply text is shown | always |
| The exchange is remembered | always |
| One point of vanilla gossip toward that player (`MINOR_POSITIVE` / `MINOR_NEGATIVE`) | `feeling` is `warm` or `cold`, and not already nudged today |
| A `reassured` event, which can settle the mood | `feeling` is `warm` and mood is uneasy, afraid or angry |
| The conversation ends and the villager walks 12 blocks away; a `threatened` event is recorded | `feeling` is `afraid` |

Nothing else. In particular the model cannot set a mood, change trades, or give items.

### What can change how it moves

Vanilla's Brain moves a villager. The mod changes that in four places only: the village bell (walk
to it when rung quietly), conversation hold (Brain tick skipped while talking, `VillagerFreezeMixin`),
the one-off scurry away after a frightening conversation, and looking at the player while speaking.

---

## Player

Players have no attachment and nothing is saved by the mod. `StoryTracker` keeps a `Log` per player
in memory, rebuilt from the game each session (it silently takes stock on first sight, so earlier
achievements are not narrated).

| Field | Meaning | Written by |
|---|---|---|
| `completed` | which ladder goals' advancements are done | the advancement poll, every 2 s |
| `lastProgressTick` | when a goal was last completed | the advancement poll |
| `lastDeathTick` | when they last died | death hook |
| `dimension`, `wasNight` | to notice a change | the scan |
| `state` (`PlayerStateRules.State`) | steady, shaken, wounded, hunted, on edge, hungry, triumphant, far from home, restless, always with a reason | rules, once a second |
| `tension` | 0 to 100 pressure value (the director's budget) | damage, death and the scan: rises with nearby hostiles (×1.5 at night), decays 1 per second, floors at 70 when nearly dead |
| `lastNudgeTick`, `lastRumourTick` | spacing of hints and rumours | the scan |
| `nearDeathArmed` | one near-death caption per drop | damage hook and scan |

Player **state** is a rule (`PlayerStateRules`), just as villager mood is. The narrator voices it;
it does not choose it. Priority: died recently → shaken; health under 25% → wounded; two or more
hostiles at night → hunted; one hostile → on edge; low food → hungry; goal just achieved →
triumphant; another dimension → far from home; no progress for 15 minutes → restless; else steady.

The player's own game state (health, items, position, advancements) is never written by the mod.
What the mod writes for a player is *what they are shown*: captions, and a villager's remark.

---

## Farm animals

No state. `AnimalReactions` adds one goal, `ThreatAvoidanceGoal`, to cows, sheep, pigs, chickens,
rabbits, horses and similar on load; they move away from a hostile mob within 8 blocks, checked
every half-second. Predators (wolf, fox) and the self-reliant are left alone, and endermen,
silverfish and endermites are not treated as a threat. Switch off with `animalsAvoidThreats`.

## Squad

Not a stored entity: a plan recomputed once a second. Three to six hostile mobs within 24 blocks
hunting the same player form a squad. Roles: the captain (sturdiest, kept while alive, glows), a
pinner, up to three flankers, a holding ring, and spread-out ranged members. A fallen captain shakes
the rest for 15 seconds. It writes no mob state: members only *read* their assignment, and the
morale effect is a number added into the existing nerve comparison.

---

## Quick reference: who may write what

| State | System 1 | System 2 | System 3 | Rules | Narrator/Director |
|---|---|---|---|---|---|
| Mob `currentAction` | yes (via model, and via its own rules) | no | no | yes (reflexes) | no |
| Mob narrative | no | **yes** | no | no | no |
| Mob bookkeeping | yes | yes | no | event hooks add triggers | no |
| Villager conversations | no | no | **yes** | no | no |
| Villager events and mood | no | no | via `reassured` / `threatened` | **yes** | rumour is spoken, not stored |
| Villager gossip (vanilla) | no | no | **yes**, capped | no | no |
| Player state / tension | no | no | no | **yes** | reads them |
| What a player is shown | no | no | villager lines | no | **yes** (captions) |
