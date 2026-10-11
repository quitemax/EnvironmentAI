# Strips all armour and the held item from the executing player.
item replace entity @s armor.head with minecraft:air
item replace entity @s armor.chest with minecraft:air
item replace entity @s armor.legs with minecraft:air
item replace entity @s armor.feet with minecraft:air
item replace entity @s weapon.mainhand with minecraft:air
tellraw @s {"text":"[eai] geared: none","color":"green"}
