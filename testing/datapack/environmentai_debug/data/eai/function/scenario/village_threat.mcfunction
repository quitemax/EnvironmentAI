# L2 mood: three villagers with a stationary zombie (NoAI) 8 blocks away and no iron golem.
# Expect mood "afraid" within a second or two; /environmentai inspect @n[type=villager] shows it,
# and a villager within 10 blocks of you may exclaim. Then /talk to one: its reply should reflect it.
function eai:_prep
summon villager ~5 ~ ~-3 {Tags:["eai_debug"],PersistenceRequired:1b,VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"}}
summon villager ~5 ~ ~0 {Tags:["eai_debug"],PersistenceRequired:1b,VillagerData:{profession:"minecraft:librarian",level:2,type:"minecraft:plains"}}
summon villager ~5 ~ ~3 {Tags:["eai_debug"],PersistenceRequired:1b,VillagerData:{profession:"minecraft:cleric",level:1,type:"minecraft:plains"}}
summon zombie ~13 ~ ~0 {Tags:["eai_debug"],PersistenceRequired:1b,NoAI:1b}
tellraw @s {"text":"[eai] village_threat: villagers should turn afraid (no golem). Compare with village_golem","color":"green"}
