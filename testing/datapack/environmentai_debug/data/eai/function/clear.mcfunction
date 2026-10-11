# Removes every mob created by a scenario (they all carry the eai_debug tag).
kill @e[tag=eai_debug]
tellraw @s {"text":"[eai] scenario mobs removed","color":"gray"}
