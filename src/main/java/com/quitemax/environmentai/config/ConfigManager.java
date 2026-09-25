package com.quitemax.environmentai.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final String FILE_NAME = "environmentai.json";

	private ConfigManager() {
	}

	public static EnvironmentAIConfig load(Logger logger) {
		Path path = FabricLoader.getInstance().getConfigDir().resolve(FILE_NAME);
		EnvironmentAIConfig config;

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				config = GSON.fromJson(reader, EnvironmentAIConfig.class);
				if (config == null) {
					logger.warn("{} was empty or invalid, falling back to defaults", FILE_NAME);
					config = EnvironmentAIConfig.defaults();
				}
			} catch (IOException e) {
				logger.error("Failed to read {}, falling back to defaults", FILE_NAME, e);
				config = EnvironmentAIConfig.defaults();
			}
		} else {
			config = EnvironmentAIConfig.defaults();
			save(config, path, logger);
		}

		validate(config, logger);
		return config;
	}

	private static void save(EnvironmentAIConfig config, Path path, Logger logger) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				GSON.toJson(config, writer);
			}
		} catch (IOException e) {
			logger.error("Failed to write default {}", FILE_NAME, e);
		}
	}

	private static void validate(EnvironmentAIConfig config, Logger logger) {
		validateTier("system1", config.system1, logger);
		validateTier("system2", config.system2, logger);
	}

	private static void validateTier(String name, ModelTierConfig tier, Logger logger) {
		if (tier.provider == ProviderType.CLOUD) {
			String key = System.getenv(tier.apiKeyEnvVar);
			if (key == null || key.isBlank()) {
				logger.warn(
					"{} is configured for a cloud provider but environment variable {} is not set; "
						+ "calls will fail until it is",
					name, tier.apiKeyEnvVar
				);
			}
		}
	}
}
