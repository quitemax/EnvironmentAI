package com.quitemax.environmentai.ai.story;

import java.util.Set;

/**
 * The director's rules: how much pressure a player has been under (a tension budget), and when a
 * nudge toward the next goal is allowed. Same idea as Alien: Isolation's, in miniature: it never
 * scripts anything, it watches and chooses the moment, and it lets the player breathe after a
 * spike. All numbers are plain and in one place, and the class has no game dependencies, so it can
 * be exercised outside the game.
 *
 * <p>In this version the director only decides <em>when</em> the narrator may point somewhere. It
 * touches nothing in the world.
 */
public final class Director {
	// Tension is 0 to 100. The scan runs once a second, so these are per-second figures.
	private static final double MAX = 100.0;
	private static final double DECAY_PER_SECOND = 1.0;
	private static final double PER_HOSTILE_PER_SECOND = 3.0;
	private static final double NIGHT_MULTIPLIER = 1.5;
	private static final int HOSTILES_COUNTED = 4;
	private static final double DAMAGE_WEIGHT = 6.0;
	private static final double NEAR_DEATH_FLOOR = 70.0;

	/** Above this the player is under pressure: no hints, however long they have stalled. */
	public static final double BUSY = 40.0;
	/** Below this (and quiet for {@link #BREATHER_TICKS}) the player is considered at ease. */
	public static final double EASY = 15.0;
	/** A spike at least this high earns a "relief" beat once it has passed. */
	public static final double SPIKE = 60.0;

	/** After tension was last {@link #BUSY}, how long it must stay low before a nudge: one minute. */
	public static final long BREATHER_TICKS = 1_200;

	private Director() {
	}

	/**
	 * One player's tension. Mutable, one per player, owned by the story tracker. Driven by
	 * caller-supplied game time so it is deterministic.
	 */
	public static final class Tension {
		private double value;
		private long lastBusyTick = -1_000_000L;
		/** Highest value since the last relief; what makes a calm-down worth remarking on. */
		private double episodePeak;

		public double value() {
			return value;
		}

		/** A hit of this many health points (half hearts). */
		public void onDamage(double damage, long now) {
			raise(damage * DAMAGE_WEIGHT, now);
		}

		public void onDeath(long now) {
			raise(MAX, now);
		}

		/** Call once per second of game time. */
		public void tick(int hostilesNear, boolean night, double healthFraction, long now) {
			double rise = Math.min(hostilesNear, HOSTILES_COUNTED) * PER_HOSTILE_PER_SECOND
				* (night ? NIGHT_MULTIPLIER : 1.0);
			value = Math.max(0.0, value - DECAY_PER_SECOND);
			if (healthFraction < 0.25 && healthFraction > 0.0) {
				value = Math.max(value, NEAR_DEATH_FLOOR);
			}
			raise(rise, now);
		}

		private void raise(double amount, long now) {
			if (amount <= 0) {
				return;
			}
			value = Math.min(MAX, value + amount);
			episodePeak = Math.max(episodePeak, value);
			if (value >= BUSY) {
				lastBusyTick = now;
			}
		}

		/** Pressure is on right now. */
		public boolean busy() {
			return value >= BUSY;
		}

		/** Low, and has been for a full breather: the moment a quiet beat or a nudge lands well. */
		public boolean atEase(long now) {
			return value < EASY && now - lastBusyTick >= BREATHER_TICKS;
		}

		/**
		 * True once, when a spike has fully passed. Resets the episode, so a long fight is one
		 * relief, not one per wave.
		 */
		public boolean takeRelief(long now) {
			if (episodePeak >= SPIKE && atEase(now)) {
				episodePeak = 0.0;
				return true;
			}
			if (atEase(now)) {
				episodePeak = 0.0;
			}
			return false;
		}
	}

	/**
	 * How long a player may go without completing a goal before the director considers a nudge.
	 * Early on the next step is cheap and a lost player is more likely; later goals are whole
	 * expeditions, and the player is usually busy doing exactly what the hint would say.
	 *
	 * @param done how many ladder goals are complete
	 */
	public static long slackTicks(int done) {
		if (done < 3) {
			return 9_000;
		}
		if (done < 11) {
			return 12_000;
		}
		return 15_000;
	}

	/** The run in four acts, derived from the ladder, for debug output and for pacing. */
	public static String act(Set<String> completed) {
		return switch (actIndex(completed)) {
			case 0 -> "the surface";
			case 1 -> "the deep";
			case 2 -> "the other side";
			case 3 -> "the end";
			default -> "epilogue";
		};
	}

	/** 0 to 3 for the four acts, 4 for the epilogue. */
	public static int actIndex(Set<String> completed) {
		int done = ProgressLadder.done(completed);
		if (done >= ProgressLadder.GOALS.size()) {
			return 4;
		}
		if (done < 4) {
			return 0;
		}
		if (done < 9) {
			return 1;
		}
		if (done < 12) {
			return 2;
		}
		return 3;
	}

	/**
	 * Whether a villager may let a rumour slip. The narrator comes first: only once a hint has been
	 * shown since the last progress and had half a slack to be acted on, and then no more often than
	 * one slack apart.
	 *
	 * @param nudgedSinceProgress whether the narrator has hinted since the last goal was completed
	 */
	public static boolean mayRumour(
		Tension tension, long now, long ticksSinceProgress, boolean nudgedSinceProgress,
		long ticksSinceNudge, long ticksSinceRumour, int done
	) {
		long slack = slackTicks(done);
		return nudgedSinceProgress
			&& tension.atEase(now)
			&& ticksSinceProgress > slack
			&& ticksSinceNudge > slack / 2
			&& ticksSinceRumour > slack;
	}

	/**
	 * Whether the director allows a nudge now.
	 *
	 * @param ticksSinceProgress ticks since a ladder goal was last completed
	 * @param ticksSinceNudge    ticks since the last nudge was actually shown, or a large number
	 * @param done               how many ladder goals are complete
	 */
	public static boolean mayNudge(
		Tension tension, long now, long ticksSinceProgress, long ticksSinceNudge, int done
	) {
		long slack = slackTicks(done);
		return tension.atEase(now)
			&& ticksSinceProgress > slack
			// A repeat waits longer than the first: hints must not become a drip.
			&& ticksSinceNudge > slack + slack / 2;
	}
}
