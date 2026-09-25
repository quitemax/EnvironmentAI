package com.quitemax.environmentai.ai.narrative;

import com.google.gson.JsonObject;
import com.quitemax.environmentai.ai.util.JsonExtraction;
import org.slf4j.Logger;

/**
 * Parses System 2's raw model output into a {@link NarrativeState}, expecting
 * {"disposition": "...", "intent": "...", "target_description": "...", "memory_notes": "..."}.
 */
public final class NarrativeStateParser {
	private NarrativeStateParser() {
	}

	public static NarrativeState parse(String raw, NarrativeState previous, long gameTime, Logger logger) {
		return JsonExtraction.extractJsonObject(raw)
			.map(json -> fromJson(json, gameTime))
			.orElseGet(() -> {
				logger.debug("System 2 response had no parseable JSON, keeping previous narrative: {}", raw);
				return previous;
			});
	}

	private static NarrativeState fromJson(JsonObject json, long gameTime) {
		return new NarrativeState(
			getOrDefault(json, "disposition", "neutral"),
			getOrDefault(json, "intent", "has not yet formed an intent"),
			getOrDefault(json, "target_description", ""),
			getOrDefault(json, "memory_notes", ""),
			gameTime
		);
	}

	private static String getOrDefault(JsonObject json, String field, String fallback) {
		return json.has(field) && !json.get(field).isJsonNull() ? json.get(field).getAsString() : fallback;
	}
}
