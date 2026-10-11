package com.quitemax.environmentai.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.quitemax.environmentai.network.ConversationPayloads;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Client side of conversations. The talk key asks the server to start one; the server answers
 * with {@code Started} (open the screen and frame the shot), {@code Line} (a villager reply) and
 * {@code Ended} (close, with a reason). Everything that decides anything lives on the server, so
 * the client cannot be out of step with the world: it only ever shows what it was told.
 */
public class EnvironmentAIClient implements ClientModInitializer {
	private static KeyMapping talkKey;

	@Override
	public void onInitializeClient() {
		talkKey = KeyMappingHelper.registerKeyMapping(
			new KeyMapping("key.environmentai.talk", InputConstants.KEY_V, KeyMapping.Category.GAMEPLAY)
		);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			// consumeClick, not isDown: one press must start one conversation, however long it is held.
			while (talkKey.consumeClick()) {
				onTalkKey(client);
			}
		});

		ClientPlayNetworking.registerGlobalReceiver(ConversationPayloads.Started.TYPE, (payload, context) -> {
			ConversationClient.begin(payload.villagerId(), payload.name());
			context.client().gui.setScreen(new ConversationScreen());
		});

		ClientPlayNetworking.registerGlobalReceiver(ConversationPayloads.Line.TYPE, (payload, context) ->
			ConversationClient.onVillagerLine(payload.speaker(), payload.text()));

		ClientPlayNetworking.registerGlobalReceiver(ConversationPayloads.Ended.TYPE, (payload, context) -> {
			Minecraft client = context.client();
			ConversationClient.end();
			if (client.gui.screen() instanceof ConversationScreen screen) {
				// Tell the screen the server already knows, so closing it does not send Leave back.
				screen.closeFromServer();
				client.gui.setScreen(null);
			}
			if (!payload.reason().isEmpty() && client.player != null) {
				client.player.sendSystemMessage(Component.literal(payload.reason()));
			}
		});
	}

	private static void onTalkKey(Minecraft client) {
		if (client.player == null || client.gui.screen() != null || ConversationClient.active()) {
			return;
		}

		Entity looking = client.crosshairPickEntity;
		if (!(looking instanceof Villager)) {
			client.player.sendSystemMessage(
				Component.literal("[EnvironmentAI] Look at a villager to talk to them.")
			);
			return;
		}
		if (!ClientPlayNetworking.canSend(ConversationPayloads.StartRequest.TYPE)) {
			client.player.sendSystemMessage(
				Component.literal("[EnvironmentAI] This server does not have EnvironmentAI conversations.")
			);
			return;
		}
		ClientPlayNetworking.send(new ConversationPayloads.StartRequest(looking.getId()));
	}
}
