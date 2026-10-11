# One-time world setup for debugging: no natural mobs, frozen time/weather, nothing grief-y.
gamerule spawn_monsters false
gamerule advance_time false
gamerule advance_weather false
gamerule mob_griefing false
difficulty normal
weather clear
time set night
tellraw @s {"text":"[eai] debug world ready. Run /function eai:help","color":"green"}
