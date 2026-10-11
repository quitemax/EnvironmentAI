# APPROACH across water: 5-wide, 3-deep channel the full width of the footprint.
function eai:_prep
fill ~4 ~-3 ~-20 ~8 ~-1 ~20 water
summon zombie ~16 ~ ~ {Tags:["eai_debug"],PersistenceRequired:1b}
tellraw @s {"text":"[eai] moat: zombie is across the water; watch APPROACH / repath behaviour for thrash","color":"green"}
