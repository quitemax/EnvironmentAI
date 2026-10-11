package com.quitemax.environmentai.ai.story;

/**
 * Derives the player's current state from a snapshot of facts, the way a villager's mood is
 * derived: a short priority ladder, always with a reason, no model involved. The narrator voices
 * the result; it never invents one.
 */
public final class PlayerStateRules {
	public static final String STEADY = "steady";
	public static final String SHAKEN = "shaken";
	public static final String WOUNDED = "wounded";
	public static final String HUNTED = "hunted";
	public static final String ON_EDGE = "on edge";
	public static final String HUNGRY = "hungry";
	public static final String TRIUMPHANT = "triumphant";
	public static final String FAR_FROM_HOME = "far from home";
	public static final String RESTLESS = "restless";

	/** Ticks: 20 per second, 1200 per minute. */
	private static final long RECENT_DEATH = 3_600;
	private static final long RECENT_PROGRESS = 2_400;
	/** Fifteen minutes with no progress. */
	public static final long STALLED = 18_000;

	/**
	 * @param healthFraction     0 to 1
	 * @param food               food level 0 to 20
	 * @param hostilesNear       hostile creatures within about 16 blocks
	 * @param night              whether it is night in the overworld
	 * @param otherDimension     whether the player is in the Nether or the End
	 * @param ticksSinceDeath    ticks since the player last died, or a large number
	 * @param ticksSinceProgress ticks since the player last completed a ladder goal
	 */
	public record Facts(
		double healthFraction, int food, int hostilesNear, boolean night, boolean otherDimension,
		long ticksSinceDeath, long ticksSinceProgress
	) {
	}

	public record State(String name, String reason) {
	}

	private PlayerStateRules() {
	}

	public static State compute(Facts facts) {
		if (facts.ticksSinceDeath() < RECENT_DEATH) {
			return new State(SHAKEN, "has only just come back from the dead");
		}
		if (facts.healthFraction() < 0.25) {
			return new State(WOUNDED, "is hanging on by a thread");
		}
		if (facts.hostilesNear() >= 2 && facts.night()) {
			return new State(HUNTED, "has several creatures closing in, and it is night");
		}
		if (facts.hostilesNear() >= 1) {
			return new State(ON_EDGE, "has something hostile close by");
		}
		if (facts.food() <= 6) {
			return new State(HUNGRY, "is running low on food");
		}
		if (facts.ticksSinceProgress() < RECENT_PROGRESS) {
			return new State(TRIUMPHANT, "has just achieved something");
		}
		if (facts.otherDimension()) {
			return new State(FAR_FROM_HOME, "is a long way from the overworld");
		}
		if (facts.ticksSinceProgress() > STALLED) {
			return new State(RESTLESS, "has not made any real progress for a long while");
		}
		return new State(STEADY, "");
	}
}
