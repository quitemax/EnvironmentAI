# Villagers: design notes

Status: **L0 + L1 implemented** (observe and talk, no autonomy); L2 (mood) is next. Decisions taken:
a separate **System 3** dialogue tier (local for now, designed to move to the cloud), **English
only**, `/talk` first. Written after reading Minecraft 26.3's villager classes
(bytecode), because the hostile-mob work showed what happens when the engine is assumed rather than
checked: the action goals silently never ran on zombies for hours.

## What the engine actually does (verified in 26.3)

- **Villagers are Brain-driven, not Goal-driven.** `Villager` builds a `Brain` (`makeBrain`,
  `registerBrainGoals`) with activities (core, work, play, rest, meet, idle, panic, pre-raid, raid,
  hide) chosen by a schedule, and it does not override `registerGoals`. The goal-injection approach
  used for hostile mobs does not carry over. Steering a villager means writing Brain *memories*
  (e.g. walk target, look target), switching the *active activity*, or adding *behaviours*.
- **A social model already exists.** Each villager has a `GossipContainer`: per-player entries by
  `GossipType` (`MAJOR_NEGATIVE`, `MINOR_NEGATIVE`, `MINOR_POSITIVE`, `MAJOR_POSITIVE`, `TRADING`),
  with decay, and villagers pass gossip to each other. `ReputationEventType` (trade, hurt, killed,
  golem killed, zombie villager cured) feeds it, and trade prices read it. We should read and write
  this, not invent a parallel reputation system.
- **Trading is the `Merchant` interface** on `AbstractVillager` (offers, restock, demand).
  `mobInteract` is what opens the trade screen.
- **Villagers are not `Enemy`**, so the current tracker ignores them, and they do not despawn.
  They are saved with their chunk and frozen while it is unloaded.

## What "changing villager behaviour" can mean

| Level | What | Risk |
|---|---|---|
| L0 Observe | Track villagers; snapshot profession, level, activity, reputation toward nearby players, time, threats; expose via inspect and logs. No behaviour change. | none |
| L1 Voice | The player talks; the villager answers in character, grounded in L0's snapshot. Optionally nudges gossip a small, capped amount. | low |
| L2 Mood | A per-villager narrative (mood, worries) updated by events: raid, golem killed, trade, night, damage. Affects ambient chatter and a few safe levers (stay indoors when afraid). | medium |
| L3 Autonomy | Discrete villager verbs (go home, gather, avoid player, warn others) carried out by writing Brain memories. | high: fights the Brain |
| L4 Village drift | Batch narrative per village on chunk load: "this village was unobserved for N days, what changed?" | needs L2 |

## "Talk" (L1): how the player addresses a villager

1. **`/talk <text>`**: server only, works immediately, easy to test. Targets the villager you are
   looking at within ~6 blocks.
2. **Chat routing**: while looking at a villager, normal chat goes to it (server chat event). Needs a
   toggle so it does not hijack chat.
3. **Keybind + small input screen**: client code (the `src/client` entrypoint exists but is empty)
   and a Fabric networking payload. Best UX, most code.

Recommended order: 1, then 2, then 3. The conversation logic is the same in all three.

## Open design questions

- **Which model?** Dialogue is not latency-critical (a "typing..." beat is natural), so it could use
  a larger model than System 1/2 on demand. A separate `dialogue` tier config block is cheap, and
  VRAM currently has room for an 8B model.
- **Language:** reply in the language the player wrote in (this matters: the developer types Polish).
- **Persistence:** keep the last few exchanges and a small personality seed per villager in an
  attachment, so a villager is consistent between conversations. Per-player, for multiplayer.
- **Exploits:** talking must not become a free discount machine. Gossip nudges need a per-day cap
  per villager and player.
- **Cost and privacy:** the player's text goes to the model; if the tier is cloud, that is sent
  off-machine. Same rule as for mobs: types and facts, never usernames, unless the player opts in.
- **Prompt injection:** the player controls the text. Replies are schema-bounded and effects are
  capped, so the worst outcome is a silly line, not a game-state change.

## What was built for L1

- `system3` config block (provider, model, endpoint) with its own executor pool; `villager-<ts>.jsonl`
  call log, which records the player's text locally.
- `/talk <message>` talks to the villager in view within 6 blocks; `/environmentai inspect` shows
  profession, level, activity and reputation toward you.
- `VillagerContext` lists the only facts a villager may use (profession, activity, time, danger,
  golem, how it feels about you from vanilla gossip, the player's gear, what it trades);
  `VillagerPersona` gives each villager a stable temperament derived from its UUID.
- Replies are schema-bounded (`reply`, `feeling`); a `warm`/`cold` feeling nudges vanilla gossip by
  one point at most once per villager per game day.
- Conversation memory (last 6 exchanges per player, 8 players) is a persistent attachment.

Measured on the local 3B model before it was wired in: replies of 9-28 words in 0.4-1.0 s, in
character, injection attempts deflected, but weak grounding ("what do you sell?" answered with
generic goods). The 8B model listed the actual trades at about the same warm latency, at 4.7 GB of
VRAM against 2.6 GB. That is the argument for a stronger dialogue model once it can be hosted.

## Proposed first milestone (observe + talk, no autonomy)

1. `VillagerTracker` + a persistent `VillagerState` attachment (personality seed, short conversation
   memory, mood placeholder).
2. `/environmentai inspect` understands villagers: profession, level, active activity, reputation
   toward you, gossip counts.
3. `/talk <text>`: grounded prompt, schema-bounded reply, chat output, logged to
   `villager-<timestamp>.jsonl` like the other tiers.
4. A datapack scenario that summons villagers with professions, to test without a village.

Acceptance: every assumption above that the code relies on is exercised in-game before anything is
built on top of it, and the first conversation works end to end with no behaviour change to the
villager itself.
