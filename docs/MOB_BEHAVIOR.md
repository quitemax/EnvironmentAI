# Mob behaviour, ecology and relations: design notes

Status: written 2026-10-12 from a list of ideas after a playtest. **Built so far:** the enderman
fix, squads with a captain and flanking (M2), and farm animals shying from hostile mobs (the part of
M1 that was asked for). Everything else is design. Decisions taken: loot is set aside for later;
squads are a real squad with a captain; for animals, only their reaction to hostile mobs, not
interactions among animals, which stay vanilla.

## What was built

- **Squads** (`SquadPlanner`, `SquadCoordinator`, `FlankGoal`): hostile mobs hunting the same player
  within 24 blocks, three to six of them, are planned once a second. The sturdiest is the captain and
  stays so while it lives; it glows (`squadCaptainGlow`) and, if melee, holds a ring a little back
  while others fight. The nearest melee member pins, up to three flank to the sides and rear, the rest
  hold a ring and join as the front thins; ranged members spread evenly so they do not share a line
  of fire. A member borrows the move flag only while its action is attack, approach or idle, gives up
  after a few seconds, and then stays off for a moment, so a blocked path costs seconds.
- **Morale:** members of a squad with a captain are 1.5 braver on the intimidation rule; when the
  captain is killed, the rest are 3 less brave for fifteen seconds. The existing nerve mechanism, only
  shifted.
- **Animals** (`Relations`, `ThreatAvoidanceGoal`, `AnimalReactions`): cows, sheep, pigs, chickens,
  rabbits, horses and similar move away from a hostile mob within 8 blocks, checked every half
  second per animal rather than every tick. Endermen, silverfish and endermites do not count.
  Predators (wolf, fox, bear) are not touched.
- **Switches:** `squadTactics`, `squadCaptainGlow`, `animalsAvoidThreats` in the config file, all on.
- **Not verified in game:** the planner's rules are exercised outside the game; how it feels, whether
  the paths hold and the glow reads well are for a playtest.

The common thread: today every hostile mob is an individual that picks one of six verbs. What is
missing is everything *between* mobs (groups, factions, prey and predators), everything *about*
where a mob lives (biome, gear, what it drops), and the non-overworld and non-hostile cast
(pillagers, piglins, animals, villagers reacting to the world).

## What exists today

- **Six verbs plus keep-distance,** executed as goals at top priority: attack, flee, approach,
  guard, wander, idle, keep distance (`MobAction`, `goal/`).
- **Archetypes** (`MobArchetype`): melee, skittish (spiders), explosive (creepers), ranged. An
  archetype decides which verbs the model is offered and how much "nerve" the mob has.
- **A rule layer before the model** (`System1Scheduler.decideRule`): critical flee when badly hurt,
  and "intimidated" when the target's gear threat reaches the mob's nerve (fellow hostiles nearby
  raise the nerve). The model chooses among the verbs when no rule fires.
- **No mob knows about any other mob** beyond a count of fellow hostiles within a radius, and
  nothing about the ground it stands on.

## Found in playtest: the retreating enderman

Looking at an enderman made it back away. Cause, from the call log: 66 of 74 System 1 decisions for
endermen were FLEE. Endermen were classed as plain melee (nerve 9.0), and a player in full
netherite scores a threat of 10, so the intimidation rule fired as soon as they stared. It is the
mod, not vanilla. Fixed with a `RELENTLESS` archetype (nerve 99, never offered FLEE). Left open:
whether a hurt enderman should still run, and teleport behaviour, which is vanilla's and untouched.

## Principles

1. **Rules decide, the model chooses among what the rules offer.** Same split as villagers and the
   director. Anything that changes where a mob stands, who it fights or what it drops is a rule with
   a reason that can be inspected, never a model's free invention.
2. **One coordinator per group, not N mobs negotiating.** Group tactics are computed once per
   second per group, on the server thread, from positions; the individual mob just receives a role.
3. **Data, not code, for relations and loadouts.** A table that can be read, tuned and eventually
   overridden from a datapack, so balance is not a recompile.
4. **Everything has a budget and a switch.** A cap on squad size and on how many squads are
   evaluated per tick, and a config flag per feature, because a hostile-mob layer that costs ticks
   makes the whole game worse.
5. **Vanilla stays the floor.** If a layer is off or fails, mobs behave exactly as in vanilla.

## 1. Group tactics: flanking

Idea: with many zombies or spiders about, they do not all walk in a line at the player.

- **Squad:** hostile mobs of the same faction within ~12 blocks of each other that share a target.
  Formed and re-evaluated by one `SquadCoordinator`; at most 6 members, the rest act as individuals.
- **Roles, per second, from geometry only:**
  - *Pinner:* the member nearest the target; approaches directly and holds its attention.
  - *Flankers:* the next two or three; approach to a point 70 to 110 degrees around the target from
    the pinner, at melee range, so the player has to turn.
  - *Hangers:* the rest; hold at a ring a few blocks out and join as the front dies.
- **New verb:** `FLANK` with a side and an arc point, executed as a walk target, never a teleport.
  The model is offered it only when a squad exists; the rule layer assigns it by default.
- **Per species:** zombies flank on the ground and are loud about it; spiders use walls and ceilings
  (they climb, so flanking can be vertical); skeletons spread out to get separate lines of fire
  rather than stacking; creepers do not flank, they lurk (see 7).
- **Cost and failure:** pathing to an arc point can fail or stall (terrain, caves); if it does not
  progress for ~3 seconds the role falls back to a plain approach.
- **Director link:** at `FULL` the director's tension budget can raise or lower squad size and how
  eager flanking is, the first real use of its pressure lever, and the right place for it.

## 2. Biomes, loadouts and loot

Idea: a hostile in a desert is not the same mob as one in a snowy taiga; what it wears and holds
is its own, and falls when it dies, a bit like Diablo.

- **Biome table:** per biome family, a set of *variants* with weights: spawn mix shifts (more
  husks in desert, strays in snow, more spiders in forest), and each variant has a loadout.
- **Loadout:** helmet, chest, hand items drawn from a tier ladder that rises with the director's
  act and with distance from spawn, never above what the player's gear could plausibly meet.
- **Drops by design:** vanilla equipment drop chance is low by default; mod-assigned gear is set to
  a defined chance (always for a *named* rarity tier, otherwise rolled), so "what it carries is what
  you get" holds and the player can read a mob's threat before a fight.
- **Rarity:** common, uncommon, notable (a visible name tag and a glint), at most one notable per
  squad, and rarely.
- **Guard rails:** loot inflation breaks a survival game quickly. Item tiers are capped by act,
  durability is rolled low, enchantments only on notable, and totals per hour are measurable (the
  call log already records per-mob rows; add drop rows).
- **Interaction with intimidation:** a mob in good gear *reads* the player's gear the same way the
  player reads theirs, so a well-armed variant gets higher nerve. This closes a loop the current
  rule layer has open.

## 3. Villagers and the bell

Idea: villagers should react to a bell.

- **Today:** raids and panic exist as Brain activities, and mood reads them. The bell is not
  modelled by us.
- **Vanilla (verify in 26.3):** ringing a bell records a "heard bell" time on villagers in range
  and, when raiders are near, sends them indoors; it also makes raiders glow. So there is a baseline
  to extend, not a blank page.
- **Design:** treat the bell as a *player event* on the villager side: record it as a mood event
  (`bell rung`), so ringing it with no danger makes villagers uneasy and ringing it in danger is
  read as a warning. Two rungs of response: a spoken line (through the existing voice gate), and a
  mild behavioural lever: gather near the bell for a few seconds if it is safe, or go indoors if
  not.
- **Abuse:** a bell rung ten times in a minute is a prank; the ledger caps it, and villagers get
  annoyed, which mood can already express.

## 4. Pillagers and illagers

- **Outposts and patrols:** patrols already exist in vanilla; the missing piece is intent. A patrol
  that has seen a village becomes a *scouting* state that raises the chance of a raid, and a patrol
  that has lost its captain disperses. Cheap rules, big readability.
- **Captain and morale:** a squad (see 1) with a captain fights differently from one without; when
  the captain dies the rest lose nerve, the same nerve mechanism already in place.
- **Villager targeting:** illagers prefer villagers when the player is far; with the player near they
  split. Needs a faction table (see 7).
- **Loot:** captains and notable pillagers carry the loadout and drop it (2).

## 5. The Nether cast

The Nether has a social structure, which is the best raw material in the game and is barely used.

- **Piglins:** neutral to a player wearing gold, hostile otherwise; barter for gold ingots. The
  design here is a *hierarchy and a mood*: brutes guard bastions and never barter, ordinary piglins
  trade, and a piglin that has been bartered with remembers the player a while (the villager
  reputation shape, much smaller). Groups of piglins flank (1).
- **Hoglins and zoglins:** prey for piglins, hostile to the player; a hoglin herd near a bastion is
  ambient danger the director can lean on.
- **Wither skeletons, blazes, ghasts:** fortress and open-waste ecologies; ghasts stay solitary,
  blazes defend spawners and fall back to reposition, wither skeletons patrol fortress corridors.
- **Magma cubes, striders:** mostly ambient; striders are the "animals" of the Nether and benefit
  from the relations table (7), e.g. piglins ignore them, ghasts do not.
- **Overworld rules do not carry over:** light, sleep and daylight mean nothing here, so tension
  and the nerve rule need Nether-specific inputs (gold worn, bastion proximity, number of piglins).

## 6. Mounts and pairings: skeletons on spiders

Idea: skeletons like climbing onto spiders; not every spider likes it.

- **Today:** vanilla spawns the skeleton jockey only as a rare spawn variant; a spider has no
  opinion about it.
- **Design:** give each spider a persisted, rolled **temperament** (accepts a rider, indifferent,
  refuses), and skeletons an *affinity* for the nearest accepting spider within a short range. A
  skeleton that finds one mounts it; a refusing spider moves away, which reads as personality.
- **Why persist it:** the rider/ridden pair must be stable across chunk reloads or it looks random.
  It uses the same attachment mechanism as mob state.
- **Tactical value:** a mounted skeleton keeps its range and gains the spider's wall-climbing
  (flanks vertically, 1), so this is a *tactic* as much as a gag.
- **Risks:** mounting is a vanilla entity relationship; getting off-by-a-tick ordering wrong
  produces teleporting riders. Needs a careful playtest, and a hard off switch.

## 7. A relations table for everyone

Idea: design interactions between mobs, between ordinary animals, and between the two.

A single data table of **factions** and the relation between each pair, evaluated on a slow scan,
rather than a pile of special cases:

| Relation | Meaning | Examples |
|---|---|---|
| ally | support, share targets, do not harm | zombies and husks; illagers |
| indifferent | ignore each other (vanilla default) | most pairs |
| hostile | attack on sight within range | iron golem and zombies; illagers and villagers |
| predator / prey | pursue and flee, not "fight" | wolf and sheep; fox and chicken; piglin and hoglin |
| wary | keep distance, do not attack | animals around a creeper; animals around a player with a weapon |
| mount | one may ride the other (6) | skeleton and spider |

- **Mostly vanilla already:** wolves hunt sheep and rabbits, foxes hunt chickens, cats scare
  creepers. The table's value is to *make that explicit and extendable*, not to replace it.
- **Animals as a fear signal:** passive animals flee from a creeper that is about to blow, flock
  away from a fight, and bunch up at night. Cheap, and it makes the world read as alive; it is also
  a useful signal for the player (animals scatter before they see the threat).
- **Effects are small and local:** a relation produces either a target set (hostile, predator), an
  avoid vector (prey, wary), or a mount attempt. Nothing is a new AI; it is the existing goals fed
  by a new source.
- **Budget:** evaluated per chunk on a slow cadence, only where a player is within range; a hard cap
  of relation checks per tick.

## Architecture

| Piece | Role | Reuses |
|---|---|---|
| `Faction` + relation table | who is what, and how they relate | data table, per-entity registry path |
| `SquadCoordinator` | groups, roles, arc points, fallback | mob tracker, existing action goals |
| `Loadout` + biome table | what a spawn wears and carries, and what drops | entity-load event, attachments |
| `BellEvents` | bell rung as a villager event | villager mood service, voice ledger |
| `Temperament` | persisted per-mob personality (spiders first) | mob attachments |
| Director hooks | intensity gates, tension scales squads | `Director`, `DirectorIntensity.FULL` |

## Suggested build order

| Step | Delivers | Verified how |
|---|---|---|
| M0 | Enderman fix (done); a table-driven archetype for the rest of the cast so more cases are not code | Call log: no FLEE for endermen |
| M1 | Animals shy from hostile mobs (done, small table in `Relations`); a fuller faction table later | In game, watching animals |
| M2 | Squads with a captain, flanking and morale (done, untried in game) | Rules tested outside the game; in game for feel |
| M3 | Biome variants and loadouts with defined drops | Drop-rate logging; one biome first |
| M4 | Bell as a villager event | In game |
| M5 | Pillager scouting and captains | In game, raids |
| M6 | Nether cast: piglin hierarchy and barter memory | In game, bastion |
| M7 | Spider temperament and skeleton mounting | Careful playtest, hard off switch |

M1 and M2 are the foundation for most of the rest; M3 is the most visible to the player, and the
most able to break the game's balance.

## Open questions

- **Scope of "loot":** every mod-geared mob drops its gear (Diablo-like), or only notable ones?
  The first is generous and needs a hard cap; the second is a rarer, more readable reward.
- **How hostile should flanking feel?** A little (the player is outflanked and must move) or a lot
  (a real squad, with a captain)? It decides how aggressive the director's levers may be.
- **Nether and the overworld rules:** should the director's tension even run in the Nether, or does
  that dimension get its own pacing?
- **Animals:** are ordinary animals meant to be a gameplay signal, or only atmosphere? A signal
  needs them to be reliable; atmosphere can be loose.
- **Opt-in per feature:** every item here changes difficulty. Default off until played, or on at
  `FULL` only?
