# L2 mood: the same threat, but with an iron golem standing by. Expect "uneasy", not "afraid".
function eai:_prep
summon villager ~5 ~ ~-3 {Tags:["eai_debug"],PersistenceRequired:1b,VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"}}
summon villager ~5 ~ ~0 {Tags:["eai_debug"],PersistenceRequired:1b,VillagerData:{profession:"minecraft:librarian",level:2,type:"minecraft:plains"}}
summon iron_golem ~8 ~ ~0 {Tags:["eai_debug"],PersistenceRequired:1b,PlayerCreated:1b}
summon zombie ~13 ~ ~0 {Tags:["eai_debug"],PersistenceRequired:1b,NoAI:1b}
tellraw @s {"text":"[eai] village_golem: same threat with an iron golem near; mood should be uneasy","color":"green"}
