# WANDER / no target: sealed 7x7 room, no windows, nobody can be seen.
function eai:_prep
fill ~8 ~-1 ~-3 ~14 ~4 ~3 stone_bricks hollow
summon zombie ~11 ~ ~ {Tags:["eai_debug"],PersistenceRequired:1b}
summon zombie ~12 ~ ~1 {Tags:["eai_debug"],PersistenceRequired:1b}
tellraw @s {"text":"[eai] dark_room: mobs have no target; expect WANDER / IDLE. Dig in to end the test","color":"green"}
