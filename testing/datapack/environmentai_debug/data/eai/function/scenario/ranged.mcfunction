# KEEP_DISTANCE: skeletons at 4, 9 and 15 blocks. Watch them back off when you close in and shoot
# from range otherwise, instead of standing still or rushing you.
function eai:_prep
summon skeleton ~4 ~ ~ {Tags:["eai_debug"],PersistenceRequired:1b}
summon skeleton ~9 ~ ~4 {Tags:["eai_debug"],PersistenceRequired:1b}
summon skeleton ~15 ~ ~-4 {Tags:["eai_debug"],PersistenceRequired:1b}
tellraw @s {"text":"[eai] ranged: walk toward a skeleton; it should retreat to ~11 blocks then keep firing","color":"green"}
