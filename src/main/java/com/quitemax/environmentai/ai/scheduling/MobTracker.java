package com.quitemax.environmentai.ai.scheduling;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Tracks hostile mobs fitted with System 1 / System 2 AI, keyed by level then UUID. */
public final class MobTracker {
	private static final Map<ServerLevel, Set<UUID>> TRACKED_BY_LEVEL = new ConcurrentHashMap<>();
	private static final Map<UUID, Mob> MOBS_BY_UUID = new ConcurrentHashMap<>();

	private MobTracker() {
	}

	public static void track(Mob mob) {
		if (!(mob.level() instanceof ServerLevel serverLevel)) {
			return;
		}
		TRACKED_BY_LEVEL.computeIfAbsent(serverLevel, level -> ConcurrentHashMap.newKeySet()).add(mob.getUUID());
		MOBS_BY_UUID.put(mob.getUUID(), mob);
	}

	public static void untrack(Mob mob) {
		if (mob.level() instanceof ServerLevel serverLevel) {
			Set<UUID> tracked = TRACKED_BY_LEVEL.get(serverLevel);
			if (tracked != null) {
				tracked.remove(mob.getUUID());
			}
		}
		MOBS_BY_UUID.remove(mob.getUUID());
	}

	public static Set<UUID> trackedIn(ServerLevel level) {
		return TRACKED_BY_LEVEL.getOrDefault(level, Collections.emptySet());
	}

	public static Mob get(UUID uuid) {
		return MOBS_BY_UUID.get(uuid);
	}
}
