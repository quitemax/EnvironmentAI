package com.quitemax.environmentai.ai.action;

import com.google.gson.JsonObject;
import com.quitemax.environmentai.ai.util.JsonExtraction;
import org.slf4j.Logger;

import java.util.Optional;

/** Parses System 1's raw model output into a {@link MobAction}, expecting {"action": "..."}. */
public final class ActionParser {
	private ActionParser() {
	}

	public static MobAction parse(String raw, MobAction previous, Logger logger) {
		return tryParse(raw, logger).orElse(previous);
	}

	/** Like {@link #parse}, but reports failure as empty instead of substituting a fallback. */
	public static Optional<MobAction> tryParse(String raw, Logger logger) {
		Optional<JsonObject> json = JsonExtraction.extractJsonObject(raw);
		if (json.isEmpty()) {
			logger.debug("System 1 response had no parseable JSON, keeping previous action: {}", raw);
			return Optional.empty();
		}
		return parseField(json.get(), logger);
	}

	private static Optional<MobAction> parseField(JsonObject json, Logger logger) {
		if (!json.has("action")) {
			logger.debug("System 1 response JSON had no 'action' field, keeping previous action: {}", json);
			return Optional.empty();
		}
		String value = json.get("action").getAsString();
		try {
			return Optional.of(MobAction.valueOf(value.trim().toUpperCase()));
		} catch (IllegalArgumentException e) {
			logger.debug("System 1 response had unknown action '{}', keeping previous action", value);
			return Optional.empty();
		}
	}
}
