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
	/** Mobs farther than this from every player are not given decisions or narrative refreshes (0 or less = no limit). */
	public double activeRadiusBlocks = 48.0;
	/**
	 * Ask the provider to constrain replies to a JSON schema (valid actions only for System 1; exactly
	 * the four narrative keys for System 2). Needs a provider that supports it (Ollama 0.5+);
	 * turn off for older servers, which would reject the request.
	 */
	public boolean structuredOutput = true;
	/**
	 * Mobs lose their nerve against a heavily armed and armoured target: melee mobs flee, ranged
	 * mobs keep their distance. Threat comes from the target's armour points and held weapon, and
	 * fellow hostiles nearby steady the mob. Off disables the rule entirely.
	 */
	public boolean intimidationEnabled = true;
	/**
	 * Radius within which other creatures are listed in the prompt (type and distance, nearest
	 * first). 0 or less disables it.
	 */
	public double awarenessRadiusBlocks = 12.0;
	/**
	 * System 1 survival reflex: a mob whose health fraction drops below {@code fleeEnterHealth}
	 * flees from a dangerous target, and keeps fleeing until it recovers past {@code fleeExitHealth}.
	 * The gap between the two is the hysteresis. Unlike the prompt's CRITICAL label (below 25%),
	 * these are tuned for how a player perceives "nearly dead", not for what a model understands.
	 */
	public float fleeEnterHealth = 0.40f;
	public float fleeExitHealth = 0.55f;
	/**
	 * Global cap on simultaneously outstanding calls for this tier. Without it, a crowd of mobs all
	 * dispatching at once queues behind the model and every answer arrives stale. 0 (the default,
	 * also what an existing config file without this field gets) means the tier's own default:
	 * 4 for System 1, and 1 for System 2 (matching a single-slot Ollama instance).
	 */
	public int maxInFlight = 0;

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

	public static ModelTierConfig defaultSystem3() {
		ModelTierConfig config = new ModelTierConfig();
		config.provider = ProviderType.LOCAL;
		config.model = "llama3.2:3b";
		config.temperature = 0.8;
		// The reply schema bounds output to ~280 characters (~80 tokens); this is margin.
		config.maxTokens = 160;
		// A person is waiting on this, but a short pause reads as the villager thinking.
		config.timeoutMs = 30_000;
		// Dialogue is never decided by sight of a target, so these combat-tier knobs do not apply.
		config.awarenessRadiusBlocks = 0;
		config.intimidationEnabled = false;
		return config;
	}

	public static ModelTierConfig defaultSystem2() {
		ModelTierConfig config = new ModelTierConfig();
		config.provider = ProviderType.CLOUD;
		config.model = "claude-sonnet-5";
		config.temperature = 0.8;
		// The schema bounds every string, so a reply is at most ~560 characters (~140-170 tokens).
		// The cap only needs margin above that; without a schema (see structuredOutput) a small model
		// can run on appending junk until the cap, so keep it close rather than generous.
		config.maxTokens = 200;
		return config;
	}
}
