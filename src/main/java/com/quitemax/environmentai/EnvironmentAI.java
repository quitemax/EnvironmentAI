package com.quitemax.environmentai;

import com.quitemax.environmentai.ai.scheduling.AiService;
import com.quitemax.environmentai.ai.state.ModAttachments;
import com.quitemax.environmentai.command.EnvironmentAiCommand;
import com.quitemax.environmentai.config.ConfigManager;
import com.quitemax.environmentai.config.EnvironmentAIConfig;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.resources.Identifier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EnvironmentAI implements ModInitializer {
	public static final String MOD_ID = "environmentai";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static EnvironmentAIConfig config;

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		config = ConfigManager.load(LOGGER);
		ModAttachments.register();

		AiService aiService = new AiService(config);
		aiService.start();
		CommandRegistrationCallback.EVENT.register(
			(dispatcher, registryAccess, environment) ->
				EnvironmentAiCommand.register(dispatcher, aiService.dialogue())
		);

		LOGGER.info("EnvironmentAI initialized");
	}

	public static EnvironmentAIConfig config() {
		return config;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
