package com.quitemax.environmentai.config;

/**
 * Configuration for one AI tier (System 1 or System 2). Both tiers use the same shape
 * so either can independently point at a local (Ollama) or cloud (Anthropic) model.
 * Cadence fields are only meaningful for the tier that uses them (System 1: tickInterval /
 * minSpacing*; System 2: debounceMs / cooldownMs) — unused fields sit at their defaults.
 */
public class ModelTierConfig {
	public ProviderType provider = ProviderType.LOCAL;
	public String model = "llama3.1";
	/** Overrides the provider's default endpoint when non-null (e.g. a non-default Ollama host). */
	public String endpointOverride = null;
	/** Name of the environment variable to read the API key from. Only used by cloud providers. */
	public String apiKeyEnvVar = "ANTHROPIC_API_KEY";
	public double temperature = 0.7;
	public int maxTokens = 256;
	public int timeoutMs = 10_000;

	// System 1 cadence settings.
	/** How often (in server ticks) the scheduler considers this mob for a new decision. */
	public int tickInterval = 2;
	public long minSpacingLocalMs = 250;
	public long minSpacingCloudMs = 1_000;

	// System 2 cadence settings.
	/** Minimum time between two triggers being coalesced into one refresh. */
	public long debounceMs = 500;
	/** Minimum time between two actual narrative refreshes for the same mob. */
	public long cooldownMs = 5_000;

	public static ModelTierConfig defaultSystem1() {
		ModelTierConfig config = new ModelTierConfig();
		config.provider = ProviderType.LOCAL;
		config.model = "llama3.1";
		config.temperature = 0.4;
		config.maxTokens = 64;
		return config;
	}

	public static ModelTierConfig defaultSystem2() {
		ModelTierConfig config = new ModelTierConfig();
		config.provider = ProviderType.CLOUD;
		config.model = "claude-sonnet-5";
		config.temperature = 0.8;
		config.maxTokens = 512;
		return config;
	}
}
