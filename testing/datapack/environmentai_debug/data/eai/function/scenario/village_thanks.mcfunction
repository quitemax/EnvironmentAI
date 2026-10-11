# Proactive voice, THANKS: four villagers and a weakened zombie villager in a pen. Feed it the golden
# apple you are given (right-click) and wait about 15 seconds. Every villager nearby sees the cure,
# but exactly one should thank you, speaking for the group ("we"), and the rest stay silent.
function eai:_prep
fill ~8 ~-1 ~-3 ~12 ~3 ~3 glass hollow
summon zombie_villager ~10 ~ ~0 {Tags:["eai_debug"],PersistenceRequired:1b}
effect give @e[type=zombie_villager,tag=eai_debug,limit=1,sort=nearest] minecraft:weakness 600 0
summon villager ~4 ~ ~-4 {Tags:["eai_debug"],PersistenceRequired:1b,VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"}}
summon villager ~5 ~ ~-1 {Tags:["eai_debug"],PersistenceRequired:1b,VillagerData:{profession:"minecraft:librarian",level:2,type:"minecraft:plains"}}
summon villager ~5 ~ ~2 {Tags:["eai_debug"],PersistenceRequired:1b,VillagerData:{profession:"minecraft:cleric",level:1,type:"minecraft:plains"}}
summon villager ~4 ~ ~5 {Tags:["eai_debug"],PersistenceRequired:1b,VillagerData:{profession:"minecraft:mason",level:1,type:"minecraft:plains"}}
give @s minecraft:golden_apple 1
tellraw @s {"text":"[eai] village_thanks: feed the zombie villager the golden apple; one villager should thank you for all","color":"green"}
