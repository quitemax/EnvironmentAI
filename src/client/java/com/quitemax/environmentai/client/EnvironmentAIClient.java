package com.quitemax.environmentai.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Client side. Currently one thing: a key that opens chat pre-filled with {@code /talk } when you
 * are looking at a villager. Deliberately just a shortcut for the command: the conversation itself
 * stays entirely on the server, so there is no custom screen or network packet to keep in step.
 * A dedicated conversation screen and camera belong here later, behind the same key.
 */
public class EnvironmentAIClient implements ClientModInitializer {
	private static KeyMapping talkKey;

	@Override
	public void onInitializeClient() {
		talkKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.environmentai.talk", InputConstants.KEY_V, KeyMapping.Category.GAMEPLAY)
		);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// consumeClick, not isDown: one press must open one chat box, however long it is held.
			while (talkKey.consumeClick()) {
				onTalkKey(client);
			}
		});
	}

	private static void onTalkKey(Minecraft client) {
		if (client.player == null || client.gui.screen() != null) {
			return;
		}

		Entity looking = client.crosshairPickEntity;
		if (looking instanceof Villager) {
			client.gui.setScreen(new ChatScreen("/talk ", false));
		} else {
			client.player.sendSystemMessage(
				Component.literal("[EnvironmentAI] Look at a villager to talk to them.")
			);
		}
	}
}
