package com.quitemax.environmentai.ai.action;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

public enum MobAction {
	ATTACK,
	FLEE,
	APPROACH,
	GUARD,
	WANDER,
	IDLE,
	/**
	 * Ranged mobs only: back away when the target gets close, otherwise leave the mob to its
	 * vanilla ranged attack. Appended last so persisted names and ordinals stay stable.
	 */
	KEEP_DISTANCE;

	public static final Codec<MobAction> CODEC = Codec.STRING.flatXmap(
		MobAction::parse,
		action -> DataResult.success(action.name())
	);

	public static MobAction fallback() {
		return IDLE;
	}

	private static DataResult<MobAction> parse(String value) {
		try {
			return DataResult.success(MobAction.valueOf(value));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Unknown mob action: " + value);
		}
	}
}
