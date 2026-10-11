# WANDER / IDLE at noon: zombies burn in the open, a plank roof offers shade at x=20..26.
function eai:_prep
time set noon
fill ~20 ~3 ~-3 ~26 ~3 ~3 oak_planks
summon zombie ~12 ~ ~-4 {Tags:["eai_debug"],PersistenceRequired:1b}
summon zombie ~12 ~ ~4 {Tags:["eai_debug"],PersistenceRequired:1b}
summon zombie ~14 ~ ~ {Tags:["eai_debug"],PersistenceRequired:1b}
tellraw @s {"text":"[eai] daylight: time frozen at noon; do they seek shade or fight vanilla burning behaviour?","color":"green"}
