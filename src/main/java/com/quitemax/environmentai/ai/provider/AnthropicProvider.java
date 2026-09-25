package com.quitemax.environmentai.ai.provider;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.quitemax.environmentai.config.ModelTierConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Client for Anthropic's Messages API (cloud tier). */
public class AnthropicProvider implements ModelProvider {
	private static final String DEFAULT_ENDPOINT = "https://api.anthropic.com/v1/messages";
	private static final String ANTHROPIC_VERSION = "2023-06-01";
	private static final Gson GSON = new Gson();

	private final HttpClient httpClient;
	private final String endpoint;
	private final String model;
	private final String apiKey;
	private final int timeoutMs;

	public AnthropicProvider(HttpClient httpClient, ModelTierConfig config) {
		this.httpClient = httpClient;
		this.endpoint = config.endpointOverride != null ? config.endpointOverride : DEFAULT_ENDPOINT;
		this.model = config.model;
		this.apiKey = System.getenv(config.apiKeyEnvVar);
		this.timeoutMs = config.timeoutMs;
	}

	@Override
	public CompletableFuture<String> complete(PromptRequest request) {
		if (apiKey == null || apiKey.isBlank()) {
			return CompletableFuture.failedFuture(
				new IllegalStateException("Anthropic API key not set (expected in environment variable)")
			);
		}

		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.addProperty("max_tokens", request.maxTokens());
		body.addProperty("temperature", request.temperature());
		body.addProperty("system", request.systemPrompt());
		JsonArray messages = new JsonArray();
		JsonObject userMessage = new JsonObject();
		userMessage.addProperty("role", "user");
		userMessage.addProperty("content", request.userPrompt());
		messages.add(userMessage);
		body.add("messages", messages);

		HttpRequest httpRequest = HttpRequest.newBuilder()
			.uri(URI.create(endpoint))
			.timeout(Duration.ofMillis(timeoutMs))
			.header("content-type", "application/json")
			.header("x-api-key", apiKey)
			.header("anthropic-version", ANTHROPIC_VERSION)
			.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
			.build();

		return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
			.thenApply(AnthropicProvider::extractText);
	}

	private static String extractText(HttpResponse<String> response) {
		if (response.statusCode() / 100 != 2) {
			throw new RuntimeException(
				"Anthropic API returned HTTP " + response.statusCode() + ": " + response.body()
			);
		}
		JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
		JsonArray content = json.getAsJsonArray("content");
		if (content == null || content.isEmpty()) {
			throw new RuntimeException("Anthropic API response had no content: " + response.body());
		}
		return content.get(0).getAsJsonObject().get("text").getAsString();
	}
}
