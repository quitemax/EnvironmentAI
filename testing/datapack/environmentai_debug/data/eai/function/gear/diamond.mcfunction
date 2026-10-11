# Equips the executing player as "diamond" so intimidation can be tested without crafting.
item replace entity @s armor.head with minecraft:diamond_helmet
item replace entity @s armor.chest with minecraft:diamond_chestplate
item replace entity @s armor.legs with minecraft:diamond_leggings
item replace entity @s armor.feet with minecraft:diamond_boots
item replace entity @s weapon.mainhand with minecraft:diamond_sword
tellraw @s {"text":"[eai] geared: diamond","color":"green"}
