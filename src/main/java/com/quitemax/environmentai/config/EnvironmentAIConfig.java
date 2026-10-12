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

	/** The narrator's voice. Change at runtime with {@code /environmentai narrator style <name>}. */
	public NarratorStyle narratorStyle = NarratorStyle.NOIR;
	/**
	 * How much the story layer does: OFF (nothing), NARRATION (comments only), HINTS (plus oblique
	 * nudges when you have stalled). Change with {@code /environmentai narrator hints <level>}.
	 */
	public DirectorIntensity directorIntensity = DirectorIntensity.HINTS;
	/**
	 * Whether captions about one player shown to the others may use that player's name. Off by
	 * default ("another traveler"): names would go to a cloud model if System 3 is moved there.
	 */
	public boolean narratorUsesNames = false;

	/**
	 * Framing of the conversation camera, in blocks and multipliers. Read by the client each frame,
	 * so edit the file and restart (or relaunch); no rebuild. Tuned by eye, so expect to adjust.
	 */
	public ConversationCameraConfig conversationCamera = new ConversationCameraConfig();

	public static class ConversationCameraConfig {
		/** How far behind the player's eyes the camera sits. Larger = further from both people. */
		public double behind = 1.8;
		/** How far to the player's right. Larger = a wider angle on the pair; smaller = more head-on. */
		public double aside = 1.2;
		/** How far above the player's eyes. */
		public double above = 0.35;
		/** Shifts the aim toward the player, so the villager sits in the right third of the frame. */
		public double aimTowardPlayer = 0.40;
		/** Field-of-view multiplier at full blend. 1.0 = no zoom, smaller = tighter. */
		public double zoom = 0.80;
	}
	/**
	 * With a strong mood (afraid, terrified, grieving, angry) a villager may exclaim something
	 * unprompted to a player within 10 blocks, at most once a minute. Costs one System 3 call each.
	 */
	public boolean villagerAmbientChatter = true;

	/**
	 * Hostile mobs hunting the same player form a squad (up to six): a captain, a pinner, flankers
	 * that circle to the sides, and a reserve that holds back. A sturdier fight; turn it off for
	 * vanilla approach behaviour.
	 */
	public boolean squadTactics = true;
	/** Whether a squad's captain glows, so the player can read who leads. */
	public boolean squadCaptainGlow = true;
	/** Whether farm animals shy away from hostile mobs that come near. */
	public boolean animalsAvoidThreats = true;

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
