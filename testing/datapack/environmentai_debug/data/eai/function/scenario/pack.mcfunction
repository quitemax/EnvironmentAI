# System 2: six zombies, one wounded. Compare narratives with /environmentai inspect.
function eai:_prep
fill ~-8 ~-1 ~-8 ~8 ~4 ~8 stone_bricks hollow
summon zombie ~5 ~ ~-4 {Tags:["eai_debug"],PersistenceRequired:1b}
summon zombie ~5 ~ ~-2 {Tags:["eai_debug"],PersistenceRequired:1b}
summon zombie ~5 ~ ~0 {Tags:["eai_debug"],PersistenceRequired:1b}
summon zombie ~5 ~ ~2 {Tags:["eai_debug"],PersistenceRequired:1b}
summon zombie ~5 ~ ~4 {Tags:["eai_debug"],PersistenceRequired:1b}
summon zombie ~-5 ~ ~0 {Tags:["eai_debug"],PersistenceRequired:1b,Health:3f}
tellraw @s {"text":"[eai] pack: hurt the one behind you, then inspect each: narratives should diverge","color":"green"}
