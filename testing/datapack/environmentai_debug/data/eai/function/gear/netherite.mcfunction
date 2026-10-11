# Equips the executing player as "netherite" so intimidation can be tested without crafting.
item replace entity @s armor.head with minecraft:netherite_helmet
item replace entity @s armor.chest with minecraft:netherite_chestplate
item replace entity @s armor.legs with minecraft:netherite_leggings
item replace entity @s armor.feet with minecraft:netherite_boots
item replace entity @s weapon.mainhand with minecraft:netherite_sword
tellraw @s {"text":"[eai] geared: netherite","color":"green"}
