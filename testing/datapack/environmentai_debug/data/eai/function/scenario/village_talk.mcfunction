# Dialogue (System 3): four villagers with different professions, a few blocks apart.
# Look at one and run /talk hello. Inspect with /environmentai inspect @n[tag=eai_debug,type=villager]
function eai:_prep
summon villager ~5 ~ ~-6 {Tags:["eai_debug"],PersistenceRequired:1b,VillagerData:{profession:"minecraft:librarian",level:2,type:"minecraft:plains"}}
summon villager ~5 ~ ~-2 {Tags:["eai_debug"],PersistenceRequired:1b,VillagerData:{profession:"minecraft:farmer",level:1,type:"minecraft:plains"}}
summon villager ~5 ~ ~2 {Tags:["eai_debug"],PersistenceRequired:1b,VillagerData:{profession:"minecraft:weaponsmith",level:3,type:"minecraft:plains"}}
summon villager ~5 ~ ~6 {Tags:["eai_debug"],PersistenceRequired:1b,VillagerData:{profession:"minecraft:none",level:1,type:"minecraft:plains"}}
tellraw @s {"text":"[eai] village_talk: look at a villager and use /talk <message>","color":"green"}
