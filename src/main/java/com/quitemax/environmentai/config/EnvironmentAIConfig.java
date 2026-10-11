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

	/**
	 * When true, every completed System 1 decision is appended as a JSON line to
	 * {@code <game dir>/environmentai/training/system1-<timestamp>.jsonl}, for training a smaller
	 * decision model later.
	 */
	public boolean trainingDataLogging = false;

	public static EnvironmentAIConfig defaults() {
		return new EnvironmentAIConfig();
	}
}
