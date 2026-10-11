package com.quitemax.environmentai.config;

public class EnvironmentAIConfig {
	public ModelTierConfig system1 = ModelTierConfig.defaultSystem1();
	public ModelTierConfig system2 = ModelTierConfig.defaultSystem2();
	/**
	 * System 3: dialogue (villagers, and later any NPC you talk to). Not latency-critical and the
	 * cheapest tier to run in the cloud, so it is configured separately from the combat tiers.
	 */
	public ModelTierConfig system3 = ModelTierConfig.defaultSystem3();

	/**
	 * Thread pool sizes for each tier's model-call executor. System 1 is the high-volume tier, so
	 * its default matches a typical {@code OLLAMA_NUM_PARALLEL}; System 2 fires rarely.
	 */
	public int system1ExecutorThreads = 4;
	public int system2ExecutorThreads = 2;
	public int system3ExecutorThreads = 1;

	public boolean debugLogging = false;

	/**
	 * Villagers derive a mood from what happens to and around them, which colours what they say.
	 * Changes speech only; villager movement is never touched.
	 */
	public boolean villagerMoodEnabled = true;
	/**
	 * With a strong mood (afraid, terrified, grieving, angry) a villager may exclaim something
	 * unprompted to a player within 10 blocks, at most once a minute. Costs one System 3 call each.
	 */
	public boolean villagerAmbientChatter = true;

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
