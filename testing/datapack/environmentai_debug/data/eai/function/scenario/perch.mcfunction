# APPROACH vs ATTACK: zombie on a 6-high plateau with a 1-block-step ramp toward you.
function eai:_prep
fill ~20 ~-1 ~-6 ~32 ~5 ~6 stone
fill ~19 ~ ~-1 ~19 ~4 ~1 stone
fill ~18 ~ ~-1 ~18 ~3 ~1 stone
fill ~17 ~ ~-1 ~17 ~2 ~1 stone
fill ~16 ~ ~-1 ~16 ~1 ~1 stone
fill ~15 ~ ~-1 ~15 ~0 ~1 stone
summon zombie ~26 ~6 ~ {Tags:["eai_debug"],PersistenceRequired:1b}
tellraw @s {"text":"[eai] perch: zombie starts high and far; expect APPROACH then ATTACK on arrival","color":"green"}
