# Villagers: design notes

Status: **L0, L1 and L2 implemented** (observe, talk, mood; no autonomy). L3 (autonomy) is not
started and is the risky one. Decisions taken:
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

## What was built for L2 (mood)

Mood changes what a villager *says*; it does not move the villager. Steering the Brain is L3.

- **Event sources:** vanilla's own `onReputationEventFrom` channel (a mixin on `Villager`, which
  declares the method itself: trade, villager hurt or killed, golem killed, zombie villager cured),
  damage and death events, and a 1-second scan of each villager's surroundings (a hostile coming
  close, an iron golem near, Brain panic or raid activity).
- **Storage:** the last 5 events per villager, deduplicated (five hits in a row are one event),
  persisted in the villager's attachment with the derived mood and its reason.
- **Mood is rules, not a model call** (`VillagerMood`): free, deterministic and explainable. The
  priority ladder is raid or panic (terrified), grief for a witnessed death, golem lost, attacked by
  a player (angry), recently hurt (afraid), a hostile close (afraid, or uneasy with a golem near),
  a cured villager or good trade (content), otherwise calm. Windows expire in 2 to 5 minutes.
- **Voicing it:** the dialogue prompt lists the mood, its reason and the recent events with
  relative times, so a frightened villager can say why and cannot invent an event.
- **Ambient chatter:** on a change into a strong mood (afraid, terrified, grieving, angry) a villager
  within 10 blocks of a player, with line of sight, may exclaim one short line. At most once a
  minute per villager, one System 3 call, silent on failure. Disable with `villagerAmbientChatter`.
- **Verified outside the game** against the compiled classes: all 15 mood-ladder cases, event
  deduplication and capping, and the save codec (round trip, transient fields reset, and a save with
  none of the new fields still loads).

## The conversation state (built)

Pressing the talk key (V) with a villager under the crosshair asks the server to start a
conversation. The server owns the state; the client only frames the shot and shows what it is told
(packets: `StartRequest`, `Say`, `Leave` upward; `Started`, `Line`, `Ended` downward).

- **The villager holds still** for the length of the conversation: its Brain tick is skipped (a
  mixin on `Villager.customServerAiStep`, which the class declares itself) and it keeps facing you.
  Skipping the tick is cleaner than writing "stop" every tick, which leaves a one-step jitter.
- **It ends when:** you press Escape; you move more than 1 m from where it began; the villager is
  hurt; a hostile creature comes within 8 blocks of it; what you say frightens it (the reply's
  feeling is `afraid`, and it then hurries away and is recorded as threatened, so its mood follows);
  or either party dies, disconnects or changes dimension.
- **The screen** stays open after Enter, shows the villager's last line in a speech box with a short
  transcript, and does not pause the game (a paused integrated server would stop the reply it is
  waiting for).
- **The camera** is an over-the-shoulder shot from behind and to the right of the player, aimed at
  the villager's head, kept out of walls with a raycast, eased in over 450 ms and out over 350 ms,
  and blended from the normal pose each frame so a quick exit does not snap. A client mixin on
  `Camera.alignWithEntity` applies it; `detached` is set so the player's own model is drawn.

## The conversation look (built)

- **Framing:** the camera sits just behind and well to the right of the player, slightly above, so
  the player's head is out of the way; the aim point is nudged so the villager sits in the right
  third of the frame with the player balancing the left.
- **Zoom:** the field of view narrows to 70% at full blend (a mixin on `Camera.calculateFov`),
  eased with the same curve as the move.
- **Film frame:** black letterbox bars slide in and out with the shot (13% of the height each),
  with soft shading toward the edges. The screen stays open through the ease-out so the bars slide
  away with the camera instead of vanishing.
- **Status panel:** name and level, mood with its reason (colour-coded), what it is doing, health,
  how it feels about you (from vanilla gossip) and its temperament. The server sends it on start and
  whenever it changes.
- **Background blur: not done, deliberately.** The vanilla blur is a whole-frame effect, so it would
  blur the villager along with the background. Isolating the villager needs a depth-aware
  post-process; the edge shading is the cheap stand-in.

## Speaking first (built, with a gate)

Villagers can now open a conversation unprompted, for four reasons: **mood** (a strong change),
**warning** (a hostile comes close while a player is near), **thanks** (the player cured a zombie
villager) and **greeting** (a player arrives, or returns after two game days).

The risk was a village talking over itself: one event is witnessed by every villager. So every
attempt goes through one gate (`VoiceLedger`), which allows at most one unprompted line per player
every 20 seconds and, separately, one per reason every 1 to 5 minutes. The first villager to ask
wins; the speaker is chosen as the nearest villager within earshot and in line of sight, and is told
how many neighbours share the feeling so it speaks for all of them ("we"). Nothing is said during a
conversation the player is already having. The gate is pure and was tested outside the game: twelve
villagers wanting to thank one player produce one thanks.

## Reasons a villager might speak first (the original proposals)

Ambient chatter exists already but only reacts to a *mood change*. Other honest reasons, each of
which the villager already has the facts for:

| Reason | Trigger | Why it would feel earned |
|---|---|---|
| Warning | A hostile is close and a player is near | It tells you instead of only reacting |
| Thanks | A player cured a zombie villager or killed what hurt it | Closes the loop on a visible event |
| Plea | Hurt, or the golem died, and the player is near | Gives the mood a direction |
| Greeting | A player returns after a long absence (game time since last seen) | Makes the village feel continuous |
| News | A neighbour died, a raid ended, a trade was good | Gossip the villager actually witnessed |
| Curiosity | A player arrives in conspicuous gear (threat tier changed) | Reacts to what you wear |
| Offer | It restocked, or has a trade it wants to push | Ties talk to the economy |

The constraint is the same as for mood: only things that really happened, rate-limited per villager
and per player, and always one short line. A request that asks the player for something (a quest)
is a different feature and should not be improvised from this.

## The talk key (earlier, superseded by the above)

- **Built:** a rebindable key (default V) that, with a villager under the crosshair, opens chat
  pre-filled with `/talk `. It is only a shortcut for the command, so the conversation still lives
  entirely on the server and there is no custom packet or screen to keep in step. Client code is in
  `src/client`.
- **Planned: a conversation state with its own camera.** Feasible on 26.3: `Camera.update` is the
  per-frame hook and `setPosition`/`setRotation` are protected, so a client mixin can frame an
  over-the-shoulder shot of the villager while the key's state is active, and release it on
  Escape, on moving away, or when the conversation ends. The server-side player position is never
  touched. Things to settle when building it: input handling while in the state (block movement or
  let any movement cancel it), hiding or keeping the HUD, eased transitions in and out, and a
  fallback if the framing is blocked by terrain. A client mixin config does not exist yet; it should
  use `defaultRequire: 1` so a failed injection is loud, not silent.

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
