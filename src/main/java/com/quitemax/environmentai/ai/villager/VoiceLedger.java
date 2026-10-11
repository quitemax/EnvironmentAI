package com.quitemax.environmentai.ai.villager;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Decides whether an unprompted line may be spoken to a player right now. The problem it exists
 * for: one event is witnessed by every villager in the village, and without a gate each of them
 * would speak at once. This is the gate.
 *
 * <p>Two limits, both per player: a short gap between any two unprompted lines, so the chat is
 * never a stream; and a longer cooldown per <em>reason</em>, so five villagers all wanting to thank
 * you produce one thanks, not five. The first villager to ask wins and speaks for the group.
 *
 * <p>Pure and clock-driven by the caller, so the rules are testable without a game.
 */
public final class VoiceLedger {
	/** Why a villager wants to speak. */
	public enum Reason {
		MOOD(60_000),
		THANKS(120_000),
		WARNING(120_000),
		GREETING(300_000);

		private final long cooldownMs;

		Reason(long cooldownMs) {
			this.cooldownMs = cooldownMs;
		}

		public long cooldownMs() {
			return cooldownMs;
		}
	}

	/** Minimum time between any two unprompted lines to the same player. */
	public static final long PLAYER_GAP_MS = 20_000;

	private final Map<UUID, Long> lastAnyLine = new HashMap<>();
	private final Map<UUID, EnumMap<Reason, Long>> lastByReason = new HashMap<>();

	/**
	 * Reserves the right to speak. Returns true (and records it) if allowed; false otherwise, in which
	 * case nothing is recorded and the caller must stay silent.
	 */
	public boolean tryReserve(UUID player, Reason reason, long nowMs) {
		Long any = lastAnyLine.get(player);
		if (any != null && nowMs - any < PLAYER_GAP_MS) {
			return false;
		}

		EnumMap<Reason, Long> reasons = lastByReason.computeIfAbsent(player, id -> new EnumMap<>(Reason.class));
		Long same = reasons.get(reason);
		if (same != null && nowMs - same < reason.cooldownMs()) {
			return false;
		}

		lastAnyLine.put(player, nowMs);
		reasons.put(reason, nowMs);
		return true;
	}

	/** Forgets a player, e.g. on disconnect, so the maps do not grow without bound. */
	public void forget(UUID player) {
		lastAnyLine.remove(player);
		lastByReason.remove(player);
	}
}
