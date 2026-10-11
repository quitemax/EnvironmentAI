# Equips the executing player as "iron" so intimidation can be tested without crafting.
item replace entity @s armor.head with minecraft:iron_helmet
item replace entity @s armor.chest with minecraft:iron_chestplate
item replace entity @s armor.legs with minecraft:iron_leggings
item replace entity @s armor.feet with minecraft:iron_boots
item replace entity @s weapon.mainhand with minecraft:iron_sword
tellraw @s {"text":"[eai] geared: iron","color":"green"}
