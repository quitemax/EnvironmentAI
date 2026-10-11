package com.quitemax.environmentai.ai.villager;

import com.quitemax.environmentai.ai.state.VillagerState;

/**
 * Derives a villager's mood from what happened to it recently and what is around it right now.
 * Deliberately rules rather than a model call: it is free, deterministic and explainable (every
 * mood carries its reason), the model's job is only to voice it. Pure, so the whole priority
 * ladder can be read in one place.
 */
public final class VillagerMood {
	/** Event kinds recorded in {@link VillagerState}. */
	public static final String HURT = "hurt";
	public static final String HURT_BY_PLAYER = "hurt_by_player";
	public static final String WITNESS_DEATH = "witness_death";
	public static final String WITNESS_ATTACK = "witness_attack";
	public static final String GOLEM_LOST = "golem_lost";
	public static final String TRADE = "trade";
	public static final String CURED = "cured";
	public static final String THREAT = "threat";

	public static final String CALM = "calm";
	public static final String CONTENT = "content";
	public static final String UNEASY = "uneasy";
	public static final String AFRAID = "afraid";
	public static final String TERRIFIED = "terrified";
	public static final String GRIEVING = "grieving";
	public static final String ANGRY = "angry";

	// Windows in game ticks (20 per second, 1200 per minute).
	private static final long HURT_WINDOW = 3_600;
	private static final long GRIEF_WINDOW = 6_000;
	private static final long ANGER_WINDOW = 6_000;
	private static final long CONTENT_WINDOW = 2_400;

	public record Mood(String name, String reason) {
	}

	private VillagerMood() {
	}

	/** Whether a mood is strong enough that the villager might exclaim about it unprompted. */
	public static boolean isVocal(String mood) {
		return mood.equals(AFRAID) || mood.equals(TERRIFIED) || mood.equals(GRIEVING) || mood.equals(ANGRY);
	}

	/**
	 * @param activity the Brain's active non-core activity name ("work", "panic", "raid", ...), or ""
	 */
	public static Mood compute(
		VillagerState state, boolean hostileNear, boolean golemNear, String activity, long now
	) {
		if (activity.equals("raid") || activity.equals("pre_raid")) {
			return new Mood(TERRIFIED, "a raid is coming to the village");
		}
		if (activity.equals("panic")) {
			return new Mood(TERRIFIED, "something is terrifying the village");
		}
		if (state.hasRecent(WITNESS_DEATH, now, GRIEF_WINDOW)) {
			return new Mood(GRIEVING, "saw a neighbour die");
		}
		if (state.hasRecent(GOLEM_LOST, now, GRIEF_WINDOW)) {
			return new Mood(AFRAID, "the village's iron golem was destroyed");
		}
		if (state.hasRecent(HURT_BY_PLAYER, now, ANGER_WINDOW)) {
			return new Mood(ANGRY, "was attacked by a player");
		}
		if (state.hasRecent(HURT, now, HURT_WINDOW)) {
			return new Mood(AFRAID, "was attacked not long ago");
		}
		if (state.hasRecent(WITNESS_ATTACK, now, ANGER_WINDOW)) {
			return new Mood(ANGRY, "saw a villager attacked");
		}
		if (hostileNear) {
			return golemNear
				? new Mood(UNEASY, "a hostile creature is close, though the iron golem is near")
				: new Mood(AFRAID, "a hostile creature is close and there is no golem to protect you");
		}
		if (state.hasRecent(CURED, now, CONTENT_WINDOW * 2)) {
			return new Mood(CONTENT, "a villager was cured of the zombie curse");
		}
		if (state.hasRecent(TRADE, now, CONTENT_WINDOW)) {
			return new Mood(CONTENT, "trade has been good lately");
		}
		return new Mood(CALM, "");
	}
}
