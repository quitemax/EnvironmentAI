package com.quitemax.environmentai.ai.squad;

import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.archetype.MobArchetype;
import com.quitemax.environmentai.ai.scheduling.MobTracker;
import com.quitemax.environmentai.ai.squad.SquadPlanner.Assignment;
import com.quitemax.environmentai.ai.squad.SquadPlanner.Member;
import com.quitemax.environmentai.ai.squad.SquadPlanner.Plan;
import com.quitemax.environmentai.config.EnvironmentAIConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Finds the hostile mobs that are hunting the same player, plans them as a squad once a second
 * ({@link SquadPlanner}), and publishes each member's assignment for its {@code FlankGoal} to read.
 * One coordinator decision per group per second, not mobs negotiating with each other.
 *
 * <p>Morale: a member with a living captain is a little braver; when the captain falls, the rest
 * lose their nerve for fifteen seconds. Both are read by the System 1 scheduler's intimidation rule,
 * so there is no separate fear mechanism, only a shift of an existing one.
 */
public final class SquadCoordinator {
	private static final int SCAN_INTERVAL_TICKS = 20;
	/** Members must be within this of the player they are hunting. */
	private static final double JOIN_RADIUS = 24.0;
	private static final double CAPTAIN_BONUS = 1.5;
	private static final double SHAKEN_PENALTY = 3.0;
	private static final long SHAKEN_TICKS = 300;

	private static final Map<UUID, Assignment> ASSIGNMENTS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> SHAKEN_UNTIL = new ConcurrentHashMap<>();
	/** Captain per hunted player, to keep the choice stable between scans. */
	private static final Map<UUID, UUID> CAPTAINS = new ConcurrentHashMap<>();

	private SquadCoordinator() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(SquadCoordinator::onServerTick);
	}

	/** The mob's current assignment, or null if it is not in a squad. */
	public static Assignment assignment(UUID mob) {
		return ASSIGNMENTS.get(mob);
	}

	/** Shift to the mob's nerve from squad morale: positive is braver. Zero outside a squad. */
	public static double moraleBonus(Mob mob) {
		double bonus = 0.0;
		Assignment assignment = ASSIGNMENTS.get(mob.getUUID());
		if (assignment != null && !assignment.captain()) {
			bonus += CAPTAIN_BONUS;
		}
		Long until = SHAKEN_UNTIL.get(mob.getUUID());
		if (until != null && mob.level().getGameTime() < until) {
			bonus -= SHAKEN_PENALTY;
		}
		return bonus;
	}

	private static void onServerTick(MinecraftServer server) {
		if (server.getTickCount() % SCAN_INTERVAL_TICKS != 0) {
			return;
		}
		EnvironmentAIConfig config = EnvironmentAI.config();
		if (config == null || !config.squadTactics) {
			ASSIGNMENTS.clear();
			return;
		}

		Map<UUID, Assignment> next = new HashMap<>();
		Map<UUID, UUID> captains = new HashMap<>();
		for (ServerLevel level : server.getAllLevels()) {
			scanLevel(level, config, next, captains);
		}
		ASSIGNMENTS.clear();
		ASSIGNMENTS.putAll(next);
		CAPTAINS.clear();
		CAPTAINS.putAll(captains);
	}

	private static void scanLevel(
		ServerLevel level, EnvironmentAIConfig config, Map<UUID, Assignment> next, Map<UUID, UUID> captains
	) {
		long now = level.getGameTime();
		Map<UUID, List<Mob>> byTarget = new HashMap<>();
		Map<UUID, ServerPlayer> targets = new HashMap<>();
		for (UUID id : MobTracker.trackedIn(level)) {
			Mob mob = MobTracker.get(id);
			if (mob == null || !mob.isAlive() || mob.isRemoved()) {
				continue;
			}
			LivingEntity target = mob.getTarget();
			if (!(target instanceof ServerPlayer player) || !player.isAlive() || mob.distanceTo(player) > JOIN_RADIUS) {
				continue;
			}
			byTarget.computeIfAbsent(player.getUUID(), key -> new ArrayList<>()).add(mob);
			targets.put(player.getUUID(), player);
		}

		for (Map.Entry<UUID, List<Mob>> entry : byTarget.entrySet()) {
			ServerPlayer player = targets.get(entry.getKey());
			List<Member> members = new ArrayList<>();
			Map<UUID, Mob> mobs = new HashMap<>();
			for (Mob mob : entry.getValue()) {
				members.add(new Member(
					mob.getUUID(), mob.getX(), mob.getZ(), mob.getMaxHealth(),
					MobArchetype.of(mob) == MobArchetype.RANGED
				));
				mobs.put(mob.getUUID(), mob);
			}

			UUID previous = CAPTAINS.get(entry.getKey());
			Plan plan = SquadPlanner.plan(player.getX(), player.getZ(), members, previous);
			if (plan.captain() == null) {
				continue;
			}
			captains.put(entry.getKey(), plan.captain());
			next.putAll(plan.assignments());

			// The old captain is gone (killed, not merely out of range): shake the rest.
			if (previous != null && !previous.equals(plan.captain())) {
				Mob old = MobTracker.get(previous);
				if (old == null || !old.isAlive()) {
					for (UUID member : plan.assignments().keySet()) {
						SHAKEN_UNTIL.put(member, now + SHAKEN_TICKS);
					}
					EnvironmentAI.LOGGER.debug("Squad hunting {} lost its captain", player.getName().getString());
				}
			}

			if (config.squadCaptainGlow) {
				Mob captain = mobs.get(plan.captain());
				if (captain != null) {
					captain.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false));
				}
			}
		}
		SHAKEN_UNTIL.values().removeIf(until -> now >= until);
	}
}
