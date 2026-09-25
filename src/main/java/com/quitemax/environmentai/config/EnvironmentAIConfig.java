package com.quitemax.environmentai.config;

public class EnvironmentAIConfig {
	public ModelTierConfig system1 = ModelTierConfig.defaultSystem1();
	public ModelTierConfig system2 = ModelTierConfig.defaultSystem2();

	/** Thread pool sizes for the two model-call executors. */
	public int localExecutorThreads = 4;
	public int cloudExecutorThreads = 2;

	public boolean debugLogging = false;

	public static EnvironmentAIConfig defaults() {
		return new EnvironmentAIConfig();
	}
}
