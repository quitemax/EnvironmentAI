package com.quitemax.environmentai.ai.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;

import java.util.Optional;

/**
 * Best-effort extraction of a JSON object out of a raw model response. Local models in
 * particular sometimes wrap the requested JSON in stray prose despite instructions, so this
 * looks for the outermost {...} span rather than requiring the whole response to be valid JSON.
 */
public final class JsonExtraction {
	private JsonExtraction() {
	}

	public static Optional<JsonObject> extractJsonObject(String raw) {
		if (raw == null) {
			return Optional.empty();
		}

		int start = raw.indexOf('{');
		int end = raw.lastIndexOf('}');
		if (start < 0 || end <= start) {
			return Optional.empty();
		}

		String candidate = raw.substring(start, end + 1);
		try {
			return Optional.of(JsonParser.parseString(candidate).getAsJsonObject());
		} catch (JsonSyntaxException | IllegalStateException e) {
			return Optional.empty();
		}
	}
}
