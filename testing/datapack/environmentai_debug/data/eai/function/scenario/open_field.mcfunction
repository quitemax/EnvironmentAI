# FLEE in the open: hurt zombie (1 heart) with room to run.
function eai:_prep
summon zombie ~8 ~ ~ {Tags:["eai_debug"],PersistenceRequired:1b,Health:2f}
summon skeleton ~-8 ~ ~ {Tags:["eai_debug"],PersistenceRequired:1b,Health:3f}
tellraw @s {"text":"[eai] open_field: low-health mobs; watch for FLEE and whether it repaths","color":"green"}
