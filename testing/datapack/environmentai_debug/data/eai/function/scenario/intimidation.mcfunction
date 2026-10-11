# Intimidation: one of each archetype 12 blocks away. Run /function eai:gear/netherite first, then
# this, then compare with /function eai:gear/none. Geared: melee mobs flee, skeletons keep range.
function eai:_prep
summon zombie ~12 ~ ~-6 {Tags:["eai_debug"],PersistenceRequired:1b}
summon skeleton ~12 ~ ~-2 {Tags:["eai_debug"],PersistenceRequired:1b}
summon spider ~12 ~ ~2 {Tags:["eai_debug"],PersistenceRequired:1b}
summon creeper ~12 ~ ~6 {Tags:["eai_debug"],PersistenceRequired:1b}
tellraw @s {"text":"[eai] intimidation: inspect with /environmentai inspect @n[tag=eai_debug,type=zombie] etc.","color":"green"}
