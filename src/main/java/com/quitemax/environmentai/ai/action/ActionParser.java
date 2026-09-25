package com.quitemax.environmentai.ai.action;

import com.google.gson.JsonObject;
import com.quitemax.environmentai.ai.util.JsonExtraction;
import org.slf4j.Logger;

/** Parses System 1's raw model output into a {@link MobAction}, expecting {"action": "..."}. */
public final class ActionParser {
	private ActionParser() {
	}

	public static MobAction parse(String raw, MobAction previous, Logger logger) {
		return JsonExtraction.extractJsonObject(raw)
			.map(json -> parseField(json, previous, logger))
			.orElseGet(() -> {
				logger.debug("System 1 response had no parseable JSON, keeping previous action: {}", raw);
				return previous;
			});
	}

	private static MobAction parseField(JsonObject json, MobAction previous, Logger logger) {
		if (!json.has("action")) {
			logger.debug("System 1 response JSON had no 'action' field, keeping previous action: {}", json);
			return previous;
		}
		String value = json.get("action").getAsString();
		try {
			return MobAction.valueOf(value.trim().toUpperCase());
		} catch (IllegalArgumentException e) {
			logger.debug("System 1 response had unknown action '{}', keeping previous action", value);
			return previous;
		}
	}
}
