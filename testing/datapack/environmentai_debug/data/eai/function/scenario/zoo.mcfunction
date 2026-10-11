# One pen per species (glass, 5x5). phantom is a control: not a PathfinderMob, so the mod ignores it.
fill ~-20 ~ ~-40 ~40 ~12 ~0 air
fill ~-20 ~ ~1 ~40 ~12 ~40 air
fill ~-20 ~-1 ~-40 ~40 ~-1 ~40 stone
kill @e[tag=eai_debug]
fill ~8 ~-1 ~-31 ~14 ~4 ~-25 glass hollow
summon creeper ~11 ~ ~-28 {Tags:["eai_debug"],PersistenceRequired:1b}
fill ~8 ~-1 ~-24 ~14 ~4 ~-18 glass hollow
summon skeleton ~11 ~ ~-21 {Tags:["eai_debug"],PersistenceRequired:1b}
fill ~8 ~-1 ~-17 ~14 ~4 ~-11 glass hollow
summon spider ~11 ~ ~-14 {Tags:["eai_debug"],PersistenceRequired:1b}
fill ~8 ~-1 ~-10 ~14 ~4 ~-4 glass hollow
summon enderman ~11 ~ ~-7 {Tags:["eai_debug"],PersistenceRequired:1b}
fill ~8 ~-1 ~-3 ~14 ~4 ~3 glass hollow
summon witch ~11 ~ ~0 {Tags:["eai_debug"],PersistenceRequired:1b}
fill ~8 ~-1 ~4 ~14 ~4 ~10 glass hollow
summon blaze ~11 ~ ~7 {Tags:["eai_debug"],PersistenceRequired:1b}
fill ~8 ~-1 ~11 ~14 ~4 ~17 glass hollow
summon drowned ~11 ~ ~14 {Tags:["eai_debug"],PersistenceRequired:1b}
fill ~8 ~-1 ~18 ~14 ~4 ~24 glass hollow
summon pillager ~11 ~ ~21 {Tags:["eai_debug"],PersistenceRequired:1b}
fill ~8 ~-1 ~25 ~14 ~4 ~31 glass hollow
summon phantom ~11 ~ ~28 {Tags:["eai_debug"],PersistenceRequired:1b}
tellraw @s {"text":"[eai] zoo: creeper, skeleton, spider, enderman, witch, blaze, drowned, pillager, phantom (control)","color":"green"}
