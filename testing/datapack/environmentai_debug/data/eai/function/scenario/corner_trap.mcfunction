# FLEE with no route: hurt zombie in a dead-end alcove, the only exit faces you.
function eai:_prep
fill ~8 ~-1 ~-2 ~13 ~3 ~2 stone_bricks hollow
fill ~8 ~ ~-1 ~8 ~2 ~1 air
summon zombie ~12 ~ ~ {Tags:["eai_debug"],PersistenceRequired:1b,Health:2f}
tellraw @s {"text":"[eai] corner_trap: FLEE should fail to find a route; check near-death escalation","color":"green"}
