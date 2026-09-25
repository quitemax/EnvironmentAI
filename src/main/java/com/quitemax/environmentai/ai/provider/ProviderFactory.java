package com.quitemax.environmentai.ai.provider;

import com.quitemax.environmentai.config.ModelTierConfig;

import java.net.http.HttpClient;

public final class ProviderFactory {
	private ProviderFactory() {
	}

	public static ModelProvider create(ModelTierConfig config, HttpClient httpClient) {
		return switch (config.provider) {
			case LOCAL -> new OllamaProvider(httpClient, config);
			case CLOUD -> new AnthropicProvider(httpClient, config);
		};
	}
}
