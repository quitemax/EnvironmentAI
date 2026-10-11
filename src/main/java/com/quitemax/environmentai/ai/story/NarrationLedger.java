package com.quitemax.environmentai.ai.story;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Decides whether a caption may be shown to a player right now. Same shape as the villagers'
 * voice gate and for the same reason: a quiet player-facing channel must never become a stream.
 * A short gap between any two captions, and a longer cooldown per kind, per player. Pure and driven
 * by a caller-supplied clock, so it is testable without the game.
 */
public final class NarrationLedger {
	/** What a caption is about. Cooldowns are per kind. */
	public enum Kind {
		STAGE(10_000),
		NEAR_DEATH(120_000),
		DEATH(60_000),
		HUNTED(180_000),
		DIMENSION(60_000),
		DAWN(600_000),
		QUIET(600_000),
		/** A spike of pressure has passed: the director's "ease off" beat. */
		RELIEF(300_000),
		/** A short note to the other players about someone else's big moment. */
		ELSEWHERE(90_000);

		private final long cooldownMs;

		Kind(long cooldownMs) {
			this.cooldownMs = cooldownMs;
		}

		public long cooldownMs() {
			return cooldownMs;
		}
	}

	/** Minimum time between any two captions for the same player. */
	public static final long PLAYER_GAP_MS = 25_000;

	private final Map<UUID, Long> lastAny = new HashMap<>();
	private final Map<UUID, EnumMap<Kind, Long>> lastByKind = new HashMap<>();

	/** Reserves the right to show a caption. True (and recorded) if allowed; otherwise nothing changes. */
	public boolean tryReserve(UUID player, Kind kind, long nowMs) {
		Long any = lastAny.get(player);
		if (any != null && nowMs - any < PLAYER_GAP_MS) {
			return false;
		}
		EnumMap<Kind, Long> kinds = lastByKind.computeIfAbsent(player, id -> new EnumMap<>(Kind.class));
		Long same = kinds.get(kind);
		if (same != null && nowMs - same < kind.cooldownMs()) {
			return false;
		}
		lastAny.put(player, nowMs);
		kinds.put(kind, nowMs);
		return true;
	}

	public void forget(UUID player) {
		lastAny.remove(player);
		lastByKind.remove(player);
	}
}
