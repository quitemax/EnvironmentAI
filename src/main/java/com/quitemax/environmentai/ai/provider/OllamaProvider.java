package com.quitemax.environmentai.ai.provider;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.quitemax.environmentai.config.ModelTierConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/** Client for a local Ollama server's /api/generate endpoint (local tier). */
public class OllamaProvider implements ModelProvider {
	private static final String DEFAULT_ENDPOINT = "http://localhost:11434";
	private static final Gson GSON = new Gson();

	private final HttpClient httpClient;
	private final String endpoint;
	private final String model;
	private final int timeoutMs;

	public OllamaProvider(HttpClient httpClient, ModelTierConfig config) {
		this.httpClient = httpClient;
		this.endpoint = config.endpointOverride != null ? config.endpointOverride : DEFAULT_ENDPOINT;
		this.model = config.model;
		this.timeoutMs = config.timeoutMs;
	}

	@Override
	public CompletableFuture<String> complete(PromptRequest request) {
		JsonObject body = new JsonObject();
		body.addProperty("model", model);
		body.addProperty("system", request.systemPrompt());
		body.addProperty("prompt", request.userPrompt());
		body.addProperty("stream", false);
		// A schema makes Ollama constrain decoding to it; plain "json" only guarantees valid JSON.
		if (request.responseSchema() != null) {
			body.add("format", request.responseSchema());
		} else {
			body.addProperty("format", "json");
		}
		JsonObject options = new JsonObject();
		options.addProperty("temperature", request.temperature());
		options.addProperty("num_predict", request.maxTokens());
		body.add("options", options);

		HttpRequest httpRequest = HttpRequest.newBuilder()
			.uri(URI.create(endpoint + "/api/generate"))
			.timeout(Duration.ofMillis(timeoutMs))
			.header("content-type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body), StandardCharsets.UTF_8))
			.build();

		return httpClient.sendAsync(httpRequest, HttpResponse.BodyHandlers.ofString())
			.thenApply(OllamaProvider::extractText);
	}

	private static String extractText(HttpResponse<String> response) {
		if (response.statusCode() / 100 != 2) {
			throw new RuntimeException("Ollama returned HTTP " + response.statusCode() + ": " + response.body());
		}
		JsonObject json = GSON.fromJson(response.body(), JsonObject.class);
		JsonElement responseField = json.get("response");
		if (responseField == null) {
			throw new RuntimeException("Ollama response had no 'response' field: " + response.body());
		}
		return responseField.getAsString();
	}
}
