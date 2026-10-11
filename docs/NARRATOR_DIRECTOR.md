# Player state, narrator and director: design notes

Status: **N0, N1 and N2 implemented** (player state, progress tracking, narrator captions, and the
director's tension budget with paced hints); the director's levers on the world (N3) are not started. Decisions taken: noir is the default voice and hints are
on by default, both changeable at runtime; the narrator sees every player. The idea: the game tracks the *player's* state from recent
events the way villagers track their mood; a comic-book **narrator** comments on it in caption boxes;
and a **director**, in the spirit of Alien: Isolation's, paces and steers the whole run through
Minecraft's progression to the Ender Dragon, differently each game.

## What the engine gives us (checked in 26.3)

- **The progression ladder already exists as data.** The advancement tree is the skill tree. The
  main line from the game's own files:

  `story/root` > `mine_stone` > `upgrade_tools` > `smelt_iron` > (`iron_tools` > `mine_diamond` >
  `enchant_item` / `shiny_gear`) and (`obtain_armor` > `deflect_arrow`) and (`lava_bucket` >
  `form_obsidian` > `enter_the_nether` > `follow_ender_eye` > `enter_the_end`) > `end/kill_dragon`

  Branches: `cure_zombie_villager` hangs off `enter_the_nether`; `end/` continues past the dragon
  (`dragon_egg`, `find_end_city`, `elytra`). 16 story, 23 nether, 31 husbandry, 47 adventure, 9 end
  advancements to build from.
- **The ladder has gaps the director must fill itself.** Blaze rods and ender pearls (needed for the
  eyes) live in the `nether/` tab, not on the story path, so "what is the next step" is not just
  "the next story advancement".
- **Completion is observable, but not by a ready-made event.** Fabric exposes events for advancements
  *loading*, not for a player *earning* one. Options: poll `PlayerAdvancements.getOrStartProgress(...)`
  every few seconds (simple, robust), or a mixin on `PlayerAdvancements.award` (exact, one more
  injection that could silently stop firing). Polling first.
- **Everything else we need already exists in this mod:** a rule-derived mood (villagers), a gate that
  stops a crowd talking at once (`VoiceLedger`), a persistent per-entity attachment, a server-to-client
  payload channel, a client HUD layer, and a way to phrase facts without letting a model invent them.

## Three layers, kept separate

| Layer | Question it answers | Decided by | Voiced by |
|---|---|---|---|
| **Player state** | What is the player's situation right now? | Rules over recent events | (shown, or fed to the narrator) |
| **Narrator** | What is worth saying about it, and when? | Rules (a trigger and a gate) | A model, constrained |
| **Director** | Where should the story go next, and how hard should it push? | Rules plus a per-world seed | The narrator and other voices |

The rule that keeps this safe: **the model never decides game state.** It only phrases facts that a
rule already established, exactly as villager mood works. Whatever the director "decides" is the
output of explicit, inspectable rules and a seed, so a run can be reproduced and debugged.

## Player state

Derived like a villager's mood, from a few recent events with decay, and always with a reason:

- **Inputs:** damage taken and near-death, kills by type, deaths, dimension changes, nights survived,
  hunger, time since last progress, what was just crafted or mined, being hunted (hostiles close),
  and the advancement stage.
- **Output:** a small vocabulary, e.g. *wounded, hunted, triumphant, prepared, reckless, lost,
  settling in, restless*. A state has a reason ("three zombies and no armour") so the narrator can
  say why and cannot claim what did not happen.
- **Why rules:** free, deterministic and testable outside the game. The villager mood ladder was
  verified that way before it ever ran.

## The narrator

- **What it is on screen:** a caption box in the corner, comic-book style, that slides in when
  something worth narrating happens and fades after a few seconds. One short line.
- **What triggers it:** a state change, a stage reached, a near miss, a long quiet stretch ("Meanwhile,
  the night grew long."), a death. Each trigger is a rule.
- **Gated like everything else:** a minimum gap between captions and a cooldown per kind, so it never
  becomes a stream. It also stays silent during a villager conversation, then may comment after.
- **Persona:** a tone chosen per world (see seeds): noir, pulp adventure, deadpan, nature-documentary.
  English only for now.
- **Delivery:** a `Narration(text, style)` payload to the client and a HUD element; the model call
  runs on the System 3 tier, not time-critical.

## The director

Modelled on the idea behind Alien: Isolation's: not a script, but a controller that watches the
player and manages **tension and relief**.

- **Tension budget.** The director tracks how much pressure the player has had recently and how long
  since real progress. After a spike it eases off; after a long safe stretch with no progress it
  nudges. Pure numbers, tunable, testable.
- **Stage model.** The advancement tree plus the missing links (blaze rods, pearls, a stronghold)
  as an explicit ladder of goals. The director knows which are done and which are reachable.
- **Levers, in order of how little they intrude:**
  1. **Narration:** a hint framed as story ("...somewhere below, lava glowed.").
  2. **Voices in the world:** villagers mentioning rumours or news, through the existing gate.
  3. **Pressure:** the existing hostile-mob systems, e.g. a more aggressive disposition for nearby
     mobs, when the player is comfortable and stalling.
  4. **Never:** spawning things on top of the player, handing out items, or changing damage. A
     "rescue" mode could exist but must be opt-in.
- **Intensity setting.** Off, narration only, hints, or full direction. A player who wants to find
  things out must be able to.

## A different game each time

Each new world rolls an **arc**, stored with the world and derived from its seed so it is
reproducible:

- tone and narrator persona;
- pace (leisurely or urgent) and how much slack before the director nudges;
- emphasis (exploration, combat, building) that weights which goals it points toward;
- an antagonistic flavour for the flavour text of pressure;
- a preferred order where the game allows more than one (e.g. Nether first or stronghold first).

## One attention budget

The villager voice gate, the narrator and director hints compete for the same player attention.
They should share a single budget, so a thanks, a warning and a caption cannot arrive together. The
current `VoiceLedger` is the seed of that: per-player gaps and per-reason cooldowns.

## What was built (N0 and N1)

- **Progress ladder** (`ProgressLadder`): 14 goals from `mine_stone` to `kill_dragon`, keyed to real
  advancements, with the nether-tab gaps (fortress, blaze rods) added by hand and an oblique hint per
  goal. Progress is read by polling the player's advancements, not by an event.
- **Player state** (`PlayerStateRules`): *shaken, wounded, hunted, on edge, hungry, triumphant, far
  from home, restless, steady*, always with a reason. A pure priority ladder, tested outside the game.
- **Moments** (`StoryTracker`): a goal reached, a near miss, a death, being hunted at night, a
  dimension change, dawn after a night survived, and a long quiet stretch.
- **Narrator** (`NarratorService`): rule-triggered, gated per player (a 25 s gap and a cooldown per
  kind), delivered as a caption box. Four voices: noir (default), pulp, deadpan, documentary.
- **Hints:** only on the quiet-stretch caption, only when the level is `hints`. In the noir voice the
  hand-written hint is shown as written (a small model was unreliable at carrying its image); in the
  other voices the model rephrases it.
- **Sees every player:** a big moment (a death, a major goal, entering the Nether or the End) also
  sends the other players a short model-free "elsewhere" note. Names are off by default
  (`narratorUsesNames`), so it says "another traveler".
- **Never over a conversation,** and it works offline: every caption has a hand-written fallback.
- **The model is only a phraser, and its output is checked.** A caption that does not carry the fact
  it was given (a diamond described as "dull grey stone") is replaced by a template, and a reply cut
  off by the length cap is rejected rather than shown mid-sentence.

Controls: `/environmentai narrator style <noir|pulp|deadpan|documentary>`,
`/environmentai narrator hints <off|narration|hints>`, `/environmentai narrate <kind>` (debug, skips
the gate), and `/environmentai inspect <player>` for state and progress.

## What was built (N2)

- **Tension budget** (`Director.Tension`): per player, 0 to 100. Damage raises it by six per health
  point, hostiles within 16 blocks add three per second each (half as much again at night, counted up
  to four), near death floors it at 70, a death sets it to 100, and it decays by one per second.
- **Busy and at ease:** 40 and above is *busy*. *At ease* means under 15 and at least a minute since
  it was last busy, so a hint never lands in the gap between two waves.
- **Paced nudges:** a hint is allowed only at ease, after the player has gone a slack period without
  completing a goal (7.5 minutes for the first three, 10 minutes up to the Nether, 12.5 after), and a
  repeat waits one and a half slacks. The nudge counter only advances when a caption was actually
  shown, so a conversation or the gate swallowing it does not use up the budget.
- **Relief beat:** when a spike of 60 or more has passed and the player is at ease, one short caption
  says so ("The quiet came back..."), once per episode, 5 minute cooldown.
- **Acts:** the run is read as four acts from the ladder (surface, deep, other side, end), shown in
  `/environmentai inspect` together with the tension, to tune the numbers by playing.
- **Still only narration:** nothing here touches the world; N3 is where pressure would. The old rule
  (a nudge whenever the state was *restless*, after 15 minutes) is replaced by this.
- **Untested in game:** the rules were exercised outside it; the numbers are first guesses and need
  a playtest for pacing.

## Suggested build order

| Step | Delivers | Verifiable how |
|---|---|---|
| N0 | Player state rules and a progress tracker (advancements polled), `/environmentai inspect player` | Pure rules tested outside the game, like the mood ladder |
| N1 | Narrator captions: HUD box, payload, six to eight triggers, gate | In game; gate tested outside |
| N2 | Director v1: stage model and tension budget, hints through the narrator only (built, pacing untried) | Rules tested; in-game for pacing |
| N3 | Director levers into villagers and mobs | In game, behind the intensity setting |
| N4 | Seeded arcs and personas | Same seed gives the same arc |

N0 and N1 are useful alone and reuse the most existing machinery.

## Open questions

- **Agency versus guidance.** Where is the line between a storyteller and a hand that steers? The
  intensity setting is the answer in principle; the defaults are a design decision.
- **Spoilers.** Hints about the End to a player who has not found a stronghold yet.
- **Multiplayer.** Player state and narration are per player; the director is per world. Whose
  progress does it follow?
- **Voice consistency.** A model will drift in tone over a long run; the persona and a short history
  of recent captions in the prompt are the mitigation, and need to be tried.
- **Cost.** Every caption is a model call. Triggers are rule-gated and rare, but a cloud tier needs
  a budget cap.
