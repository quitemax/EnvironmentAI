# Village growth and disasters: ideas for later

Status: **idea, deliberately not designed in detail and not started.** Recorded so it is not lost.
It depends on several things that do not exist yet (see "What has to come first").

## The idea, as it was put

Villages should grow on their own:

- build new homes as the population grows;
- build walls to protect themselves from zombies;
- have their own mines and gather resources for those builds;
- send people to seek other villages, establish **trade routes**, and build **roads** between them;
- and suffer **disaster events**.

## What the engine already gives us (checked in 26.3)

- **A village is already a concept: points of interest.** Beds and job-site blocks register as POIs
  (`PoiManager`, `PoiTypes`, tag `point_of_interest_type/village`). "How big is this village" and "how
  many are homeless" are answerable from data the game already keeps.
- **Real house templates exist and can be placed.** `StructureTemplateManager` and `StructureTemplate`
  load and place structures programmatically, and the game ships on the order of a hundred village
  templates per biome style (plains 115, desert 88, savanna 110, snowy 99, taiga 102), plus common
  pieces and `decays` variants (the ruined, zombie-infested versions).
- **Two disaster systems already exist.** `VillageSiege` (zombies attacking at night) and
  `Raid`/`Raids` (illager raids with waves). Disasters are therefore not new machinery, they are
  *pacing* of machinery that is already there.
- **Villager breeding already responds to beds.** `VillagerMakeLove` needs free beds, so "build
  homes" feeds a loop the game already runs.
- **Our own systems supply the rest:** a per-villager mood and memory (events, state), a per-player
  voice gate, a conversation layer, and the narrator/director for telling the story of it.

## What has to come first

1. **A village-level model.** Everything so far is per villager. Growth needs a village as an entity:
   its members, its POIs, its resources, its needs ("short of beds", "no wall on the north side"),
   and a memory of what happened to it. The natural source of truth is the POI data.
2. **Villager autonomy (the roadmap's L3).** Walking to a site and doing a job means steering the
   Brain by writing memories and behaviours. Hostile-mob steering worked through goals; villagers do
   not use goals. This is the riskiest prerequisite and was explicitly deferred.
3. **A policy for changing the world.** See below.

## The hard problems

- **Modifying the world is a different class of action.** Everything the mod does so far changes
  *behaviour*. Placing blocks changes the *terrain*, and can destroy something a player built, block
  a path, or flood a farm. It needs rules: where building is permitted, never inside a player's
  claim or near their chests and beds, never replacing anything that is not natural or the village's
  own, and a gamerule or config switch that turns it off.
- **Reversibility and auditing.** Every placement should be logged with enough information to undo
  it, and a command to show what the village built and when.
- **Cost.** Placing a template, finding a valid site and pathing builders to it all run on the server
  thread. It has to be incremental (a few blocks per tick, like construction), not a burst.
- **Chunk loading.** Villages far from any player are frozen. Growth must either happen only near
  players, or be *settled in aggregate on load* (the "what changed while you were away" idea), not
  simulated block by block off-screen.
- **Resources.** "Gather materials for the builds" implies an economy: where wood and stone come
  from, how a mine is represented, and what stops a village strip-mining the landscape. It is easy
  to make this feel like a bug rather than life.
- **Roads between villages.** Needs a path search across unloaded terrain and a rule for what a road
  may cross (water, ravines, other builds). A road is also a very visible, very permanent change.
- **Balance.** A village that grows without limit becomes a lag source and trivialises the game. It
  needs a cap, and growth needs a rate the story layer can set.

## Disasters

Mostly a director concern (see `NARRATOR_DIRECTOR.md`): the existing systems are the mechanism, the
director is the pacing.

- **Existing, paced:** sieges and raids at moments that fit the arc rather than at random.
- **Candidates that need new machinery:** plague (a wave of zombie-villager conversions), fire,
  famine (farms failing), a harsh winter, a hostile faction passing through.
- **Rules for any disaster:** telegraphed (a rumour, a narrator caption, a villager warning) before it
  lands; survivable; never destroys a player's own builds; and the village's response (walls, golem,
  evacuation) should be what decides the outcome, so growth and disaster are one story.

## A suggested order, when the time comes

| Step | What | Why this order |
|---|---|---|
| G0 | A read-only village model from POIs, plus `/environmentai inspect village` | Exercises the data with zero risk |
| G1 | Needs reported, not acted on ("short of beds"), surfaced through villager dialogue and the narrator | Gives the idea a face before it touches the world |
| G2 | One safe build type: place a bed-and-house template on a validated site near existing homes, incrementally, with a log and a switch | The smallest real world change, fully reversible |
| G3 | Walls and a golem response to sieges | Ties growth to the disaster loop |
| G4 | Mines and resource flow | Needs a settled economy model first |
| G5 | Trade routes and roads between villages | Largest blast radius, so last |
| G6 | Authored disasters under the director | Needs the director first |

## Open questions

- Should growth happen only while a player is nearby, or be settled on load?
- Who owns a builder: a villager profession (a "mason"), a village-level abstraction, or both?
- How is a player's own build protected without a claim system? A radius around player-placed
  containers and beds is the cheapest guess and will have gaps.
- Is the target a faithful simulation or a story told through a few visible changes? The second is
  far cheaper and may be the better game.
