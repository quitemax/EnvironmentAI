# Debug scenarios

A datapack of reproducible test environments for EnvironmentAI. Every scenario builds its own
platform relative to where you stand, tags its mobs `eai_debug`, and makes them persistent so they
don't despawn.

## Install

```powershell
.\tools\install-datapack.ps1 -World test      # copies into run/saves/test/datapacks
```

In-game: `/reload`, then `/function eai:setup` (freezes time and weather, disables natural
monster spawns and mob griefing), then `/function eai:help`.

Scenarios assume a flat world (a superflat world is ideal). Stand on the ground, run one, and use
`/function eai:clear` to remove its mobs. Inspect a mob's state with
`/environmentai inspect @n[tag=eai_debug]`.

## Training-data log

With `"trainingDataLogging": true` in `config/environmentai.json`, every completed System 1
decision is appended to `<game dir>/environmentai/training/system1-<timestamp>.jsonl` (one file per
launch; `run/environmentai/training/` in the dev environment). Each line holds the situation
(mob and target type, health, distance, narrative fields, previous action, the exact prompt), the
raw model response, whether it parsed, and the action applied.

Each row has a `source`: `model` (the model answered; the only rows usable as training labels),
`error` (call failed or timed out, mob kept its action) or `rule` (decided without a model call,
logged on change: `no_target` sets IDLE; `critical_flee` makes a critically wounded mob flee from a
target that is still dangerous, with hysteresis). Timing is split into `response_ms` (dispatch until
the answer arrived) and `apply_delay_ms` (answer arrived until the server thread applied it), and
`in_flight_at_dispatch` shows how many calls were outstanding, the direct measure of queueing.
An `override` field (`flee_hold` or `critical_flee`) means the model's answer was not applied;
`raw_response` still records what it said, so use it, not `action`, as the model's label.

System 2 calls go to a sibling file with the same timestamp, `system2-<timestamp>.jsonl`: one row
per narrative refresh with the `trigger`, `trigger_wait_ms` (debounce + cooldown wait), the
previous and new narrative, `response_ms` / `apply_delay_ms`, and `system1_in_flight`. System 1 rows
carry the mirror field `system2_in_flight`, so a slow answer can be matched against what else was
using the GPU at that moment.

```powershell
# live view, and a per-action tally
Get-Content run\environmentai\training\*.jsonl -Wait -Tail 5
Get-Content run\environmentai\training\*.jsonl | ConvertFrom-Json | Group-Object source, action | Select Name, Count
```

## Scenarios

| Function | Tests | Layout | Watch for |
|---|---|---|---|
| `eai:scenario/arena` | ATTACK | Sealed 21x21 arena, one zombie | Closes and hits, compared with vanilla |
| `eai:scenario/gauntlet` | ATTACK + pathing | Corridor with a 2-high gap and a bridge over a pit | Vanilla pathing intact under the override |
| `eai:scenario/open_field` | FLEE | Open ground, wounded zombie and skeleton | Runs away, repaths, avoids hazards |
| `eai:scenario/corner_trap` | FLEE, near-death | Wounded zombie in a dead-end alcove | No route to flee; near-death escalation |
| `eai:scenario/moat` | APPROACH | 5-wide, 3-deep water channel | Repath loop thrash |
| `eai:scenario/perch` | APPROACH vs ATTACK | Zombie on a plateau with a ramp | Approach, then attack on arrival |
| `eai:scenario/checkpoint` | GUARD | Zombies and skeleton around a chest | Holds position, faces you |
| `eai:scenario/dark_room` | WANDER, no target | Sealed room, no windows | Behaviour with `getTarget() == null` |
| `eai:scenario/daylight` | WANDER / IDLE | Open sky at noon, plank roof for shade | Shade-seeking vs vanilla burning |
| `eai:scenario/zoo` | Species coverage | One glass pen per species, phantom as control | Mobs where melee `ATTACK` is wrong |
| `eai:scenario/pack` | System 2 | Six zombies, one wounded | Diverging narratives per mob |
| `eai:scenario/stress` | Scheduling | 100 zombies in a sealed pit | TPS, call latency, timeouts |
| `eai:scenario/ranged` | KEEP_DISTANCE | Skeletons at 4, 9 and 15 blocks | Retreat to ~11 blocks, then keep firing |
| `eai:scenario/village_talk` | Dialogue | Librarian, farmer, weaponsmith and an unemployed villager | `/talk hello`; inspect each; replies match profession |
| `eai:scenario/village_threat` | Villager mood | Three villagers, a stationary zombie 8 blocks away, no golem | `inspect` shows `mood=afraid`; may exclaim; `/talk` reflects it |
| `eai:scenario/village_golem` | Villager mood | Same, with an iron golem | `mood=uneasy`, not `afraid` |
| `eai:scenario/village_thanks` | Speaking first (thanks) | Four villagers and a weakened zombie villager in a pen | Cure it: exactly **one** villager thanks you, saying "we" |
| `eai:scenario/intimidation` | Intimidation | One zombie, skeleton, spider and creeper at 12 blocks | Compare geared vs ungeared (below) |

Gear for intimidation tests: `/function eai:gear/netherite` (or `diamond`, `iron`, `none`) equips
you; then run `intimidation` and check each mob with `/environmentai inspect`, which shows archetype
and `[threat X vs nerve Y]`. Rows from this rule have `rule: intimidated`.

## Narrator

| Command | Does |
|---|---|
| `/environmentai narrate <kind>` | Shows a caption of that kind now, skipping the rate gate. Kinds: `stage`, `near_death`, `death`, `hunted`, `dimension`, `dawn`, `quiet`, `elsewhere`. |
| `/environmentai inspect <your name>` | Your derived state, progress (of 14 goals) and next goal. |
| `/environmentai narrator style <name>` | Switches the voice (saved). |
| `/environmentai narrator hints <level>` | `off`, `narration` or `hints` (saved). |

`quiet` is the one that carries a hint, and only when the level is `hints`. Captions about others
(`elsewhere`) are best tested with two players.

## Notes

- `IDLE` registers no goal flags, so it lets vanilla goals keep running; it is also the fallback
  when a provider fails. A mob that "does nothing special" is usually IDLE, not frozen.
- The `zoo` scenario is where species that need ranged or special attacks will misbehave under
  the shared melee `ATTACK`; that is expected until per-species config exists.
- These functions have been written against the 26.3 data format (121) but are only validated
  when a world loads them; check `latest.log` for function load errors after `/reload`.
