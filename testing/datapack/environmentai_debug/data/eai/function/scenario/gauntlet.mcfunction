# ATTACK + pathfinding: 3-wide corridor along +x with a 2-high gap, a 3-long pit with a 1-wide bridge.
function eai:_prep
fill ~-3 ~-1 ~-1 ~32 ~4 ~1 stone_bricks hollow
fill ~16 ~ ~-1 ~16 ~3 ~1 stone_bricks
setblock ~16 ~ ~ air
setblock ~16 ~1 ~ air
fill ~22 ~-1 ~-1 ~24 ~-4 ~1 air
fill ~22 ~-1 ~ ~24 ~-1 ~ stone
summon zombie ~29 ~ ~ {Tags:["eai_debug"],PersistenceRequired:1b}
tellraw @s {"text":"[eai] gauntlet: zombie must pass a gap and a bridge to reach you at x=0","color":"green"}
