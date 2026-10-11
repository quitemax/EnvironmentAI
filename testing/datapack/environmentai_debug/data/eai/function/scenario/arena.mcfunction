# ATTACK baseline: sealed 21x21 brick arena, one zombie, you in the middle.
function eai:_prep
fill ~-10 ~-1 ~-10 ~10 ~4 ~10 stone_bricks hollow
summon zombie ~7 ~ ~ {Tags:["eai_debug"],PersistenceRequired:1b}
tellraw @s {"text":"[eai] arena: expect ATTACK once the zombie targets you","color":"green"}
