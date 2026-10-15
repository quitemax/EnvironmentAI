package com.quitemax.environmentai.ai.story;

import java.util.Locale;

/**
 * Thresholds the narrator remarks on: how far the player has travelled, what experience level they
 * have reached, how many creatures they have killed, how deep or high they are, and what they wear.
 * Pure, so every rule can be checked without the game.
 *
 * <p>A "step" is the index of the highest threshold reached; the tracker remembers the step it last
 * narrated and speaks only when it rises, so a milestone is mentioned once.
 */
public final class Milestones {
	private static final int[] DISTANCE_BLOCKS = {500, 1_000, 2_500, 5_000, 10_000, 25_000, 50_000, 100_000};
	private static final int[] LEVELS = {5, 10, 15, 20, 30, 40, 50};
	private static final int[] KILLS = {1, 10, 25, 50, 100, 250, 500, 1_000};

	/** Deepest and highest the narrator distinguishes. Below zero is deepslate; the rest is a mountain. */
	public static final int DEEP_Y = 0;
	public static final int VERY_DEEP_Y = -40;
	public static final int HIGH_Y = 140;
	/** A player must come back above this before the same depth is remarked on again. */
	public static final int SURFACE_Y = 40;

	private Milestones() {
	}

	public static int distanceStep(long blocks) {
		return step(DISTANCE_BLOCKS, blocks);
	}

	public static int levelStep(int level) {
		return step(LEVELS, level);
	}

	public static int killStep(long kills) {
		return step(KILLS, kills);
	}

	private static int step(int[] thresholds, long value) {
		int step = 0;
		for (int i = 0; i < thresholds.length; i++) {
			if (value >= thresholds[i]) {
				step = i + 1;
			}
		}
		return step;
	}

	/** The distance, in blocks, that a step stands for. */
	public static int distanceAt(int step) {
		return DISTANCE_BLOCKS[Math.max(0, Math.min(DISTANCE_BLOCKS.length, step) - 1)];
	}

	public static int levelAt(int step) {
		return LEVELS[Math.max(0, Math.min(LEVELS.length, step) - 1)];
	}

	public static int killsAt(int step) {
		return KILLS[Math.max(0, Math.min(KILLS.length, step) - 1)];
	}

	/** 0 surface, 1 deep, 2 very deep, 3 high. */
	public static int altitudeBand(int y) {
		if (y <= VERY_DEEP_Y) {
			return 2;
		}
		if (y <= DEEP_Y) {
			return 1;
		}
		if (y >= HIGH_Y) {
			return 3;
		}
		return 0;
	}

	/**
	 * How good a worn piece is, from its registry path: 0 for nothing or an unknown material, up to
	 * 5 for netherite. Unknown materials degrade to 0 rather than failing, like the weapon tiers.
	 */
	public static int armorTier(String itemPath) {
		if (itemPath.isEmpty()) {
			return 0;
		}
		String p = itemPath.toLowerCase(Locale.ROOT);
		if (p.startsWith("netherite_")) {
			return 5;
		}
		if (p.startsWith("diamond_")) {
			return 4;
		}
		if (p.startsWith("iron_") || p.startsWith("chainmail_")) {
			return 3;
		}
		if (p.startsWith("copper_") || p.startsWith("golden_")) {
			return 2;
		}
		if (p.startsWith("leather_") || p.startsWith("turtle_")) {
			return 1;
		}
		return 0;
	}

	public static String tierName(int tier) {
		return switch (tier) {
			case 5 -> "netherite";
			case 4 -> "diamond";
			case 3 -> "iron";
			case 2 -> "copper or gold";
			case 1 -> "leather";
			default -> "nothing much";
		};
	}

	/** A short label for an item worth a remark when first picked up, or null if it is not. */
	public static String notableItem(String itemPath) {
		return switch (itemPath) {
			case "diamond" -> "diamonds";
			case "netherite_ingot", "netherite_scrap", "ancient_debris" -> "netherite";
			case "elytra" -> "wings";
			case "totem_of_undying" -> "a totem";
			case "enchanted_golden_apple" -> "an enchanted apple";
			case "trident" -> "a trident";
			case "blaze_rod" -> "blaze rods";
			case "eye_of_ender" -> "an eye of ender";
			case "ender_pearl" -> "an ender pearl";
			case "emerald" -> "emeralds";
			case "nether_star" -> "a nether star";
			case "shulker_shell" -> "a shulker shell";
			case "dragon_egg" -> "the dragon's egg";
			default -> null;
		};
	}
}
