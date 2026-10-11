package com.quitemax.environmentai.ai.provider;

import com.google.gson.JsonObject;

/**
 * @param responseSchema a JSON Schema the reply must satisfy, or null for free-form JSON.
 *                       Providers that support constrained decoding (Ollama) enforce it, which
 *                       rules out stray keys and runaway output; others may ignore it.
 */
public record PromptRequest(
	String systemPrompt, String userPrompt, double temperature, int maxTokens, JsonObject responseSchema
) {
	public PromptRequest(String systemPrompt, String userPrompt, double temperature, int maxTokens) {
		this(systemPrompt, userPrompt, temperature, maxTokens, null);
	}
}
