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
raw model response, whether it parsed, the action applied, and latency.

```powershell
# live view, and a per-action tally
Get-Content run\environmentai\training\*.jsonl -Wait -Tail 5
Get-Content run\environmentai\training\*.jsonl | ConvertFrom-Json | Group-Object action | Select Name, Count
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

## Notes

- `IDLE` registers no goal flags, so it lets vanilla goals keep running; it is also the fallback
  when a provider fails. A mob that "does nothing special" is usually IDLE, not frozen.
- The `zoo` scenario is where species that need ranged or special attacks will misbehave under
  the shared melee `ATTACK`; that is expected until per-species config exists.
- These functions have been written against the 26.3 data format (121) but are only validated
  when a world loads them; check `latest.log` for function load errors after `/reload`.
