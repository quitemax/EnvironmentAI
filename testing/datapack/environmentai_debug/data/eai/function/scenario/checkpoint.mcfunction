# GUARD: two zombies and a skeleton posted around a chest.
function eai:_prep
setblock ~15 ~ ~ chest
summon zombie ~14 ~ ~2 {Tags:["eai_debug"],PersistenceRequired:1b}
summon zombie ~14 ~ ~-2 {Tags:["eai_debug"],PersistenceRequired:1b}
summon skeleton ~16 ~ ~ {Tags:["eai_debug"],PersistenceRequired:1b}
tellraw @s {"text":"[eai] checkpoint: approach slowly from outside ~16 blocks; look for GUARD and facing","color":"green"}
