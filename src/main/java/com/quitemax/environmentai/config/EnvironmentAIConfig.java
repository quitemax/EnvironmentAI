package com.quitemax.environmentai.config;

public class EnvironmentAIConfig {
	public ModelTierConfig system1 = ModelTierConfig.defaultSystem1();
	public ModelTierConfig system2 = ModelTierConfig.defaultSystem2();

	/**
	 * Thread pool sizes for each tier's model-call executor. System 1 is the high-volume tier, so
	 * its default matches a typical {@code OLLAMA_NUM_PARALLEL}; System 2 fires rarely.
	 */
	public int system1ExecutorThreads = 4;
	public int system2ExecutorThreads = 2;

	public boolean debugLogging = false;

	public static EnvironmentAIConfig defaults() {
		return new EnvironmentAIConfig();
	}
}
