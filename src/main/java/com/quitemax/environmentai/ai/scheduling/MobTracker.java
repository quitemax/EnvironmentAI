package com.quitemax.environmentai.ai.scheduling;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.monster.Enemy;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks hostile mobs fitted with System 1 / System 2 AI, keyed by level then UUID.
 * Membership follows the entity's presence in a level: added on entity load, removed on entity
 * unload (which Fabric fires for death, despawn, dimension change, and chunk unload alike), so
 * the static maps never hold references to mobs that have left the world.
 */
public final class MobTracker {
	private static final Map<ServerLevel, Set<UUID>> TRACKED_BY_LEVEL = new ConcurrentHashMap<>();
	private static final Map<UUID, Mob> MOBS_BY_UUID = new ConcurrentHashMap<>();

	private MobTracker() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof PathfinderMob mob && mob instanceof Enemy) {
				track(mob, level);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Mob mob) {
				untrack(mob, level);
			}
		});
	}

	private static void track(Mob mob, ServerLevel level) {
		TRACKED_BY_LEVEL.computeIfAbsent(level, l -> ConcurrentHashMap.newKeySet()).add(mob.getUUID());
		MOBS_BY_UUID.put(mob.getUUID(), mob);
	}

	private static void untrack(Mob mob, ServerLevel level) {
		// Remove only if the map still points at this instance: a chunk reload can register a new
		// Mob with the same UUID before the old instance's unload is processed.
		if (!MOBS_BY_UUID.remove(mob.getUUID(), mob)) {
			return;
		}
		Set<UUID> tracked = TRACKED_BY_LEVEL.get(level);
		if (tracked != null) {
			tracked.remove(mob.getUUID());
		}
	}

	public static Set<UUID> trackedIn(ServerLevel level) {
		return TRACKED_BY_LEVEL.getOrDefault(level, Collections.emptySet());
	}

	public static Mob get(UUID uuid) {
		return MOBS_BY_UUID.get(uuid);
	}
}
