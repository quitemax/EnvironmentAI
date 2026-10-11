package com.quitemax.environmentai.ai.villager;

import com.google.gson.JsonObject;
import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.dialogue.DialogueService;
import com.quitemax.environmentai.ai.dialogue.VillagerContext;
import com.quitemax.environmentai.ai.state.ModAttachments;
import com.quitemax.environmentai.ai.state.VillagerState;
import com.quitemax.environmentai.ai.training.TrainingDataLogger;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L2: gives villagers a mood. Collects what happens to and around them (vanilla reputation events,
 * damage, deaths, and a periodic scan for hostiles, golems and Brain panic), records it in each
 * villager's {@link VillagerState}, derives a mood with {@link VillagerMood}, and, for strong moods,
 * lets the villager exclaim something unprompted to a player standing near.
 *
 * <p>This changes what villagers <em>say</em> and nothing about how they move: steering the Brain
 * is L3 and is deliberately not attempted here.
 */
public final class VillagerMoodService {
	private static final int SCAN_INTERVAL_TICKS = 20;
	private static final double HOSTILE_RADIUS = 12.0;
	private static final double GOLEM_RADIUS = 24.0;
	private static final double WITNESS_RADIUS = 24.0;
	private static final double AMBIENT_PLAYER_RADIUS = 10.0;
	private static final long AMBIENT_COOLDOWN_MS = 60_000;

	private static VillagerMoodService instance;

	private final Map<UUID, Villager> villagers = new ConcurrentHashMap<>();
	private final DialogueService dialogue;
	/** Null when call logging is disabled. */
	private final TrainingDataLogger logger;
	private final boolean ambientChatter;

	public VillagerMoodService(DialogueService dialogue, TrainingDataLogger logger, boolean ambientChatter) {
		this.dialogue = dialogue;
		this.logger = logger;
		this.ambientChatter = ambientChatter;
	}

	public void register() {
		instance = this;

		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager villager) {
				villagers.put(villager.getUUID(), villager);
			}
		});
		ServerEntityEvents.ENTITY_UNLOAD.register((entity, level) -> {
			if (entity instanceof Villager villager) {
				// Only if the map still points at this instance: a chunk reload can register the
				// replacement before the old instance's unload is processed.
				villagers.remove(villager.getUUID(), villager);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(VillagerMoodService::onDamaged);
		ServerLivingEntityEvents.AFTER_DEATH.register(VillagerMoodService::onDeath);
		ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
	}

	// --- event sources -------------------------------------------------------------------------

	/** For events that originate outside the scan and vanilla's reputation channel (conversations). */
	public static void recordExternal(Villager villager, String kind, String text) {
		if (instance != null) {
			instance.record(villager, kind, text);
		}
	}

	/** Called by the reputation mixin: vanilla's own channel for trade, hurt, kill and cure events. */
	public static void onReputationEvent(Villager villager, ReputationEventType type, Entity source) {
		if (instance == null) {
			return;
		}
		String kind;
		String text;
		if (type == ReputationEventType.TRADE) {
			kind = VillagerMood.TRADE;
			text = "completed a trade";
		} else if (type == ReputationEventType.VILLAGER_HURT) {
			kind = VillagerMood.WITNESS_ATTACK;
			text = "saw a villager attacked by " + describe(source);
		} else if (type == ReputationEventType.VILLAGER_KILLED) {
			kind = VillagerMood.WITNESS_DEATH;
			text = "saw a villager killed by " + describe(source);
		} else if (type == ReputationEventType.GOLEM_KILLED) {
			kind = VillagerMood.GOLEM_LOST;
			text = "saw the iron golem destroyed by " + describe(source);
		} else if (type == ReputationEventType.ZOMBIE_VILLAGER_CURED) {
			kind = VillagerMood.CURED;
			text = "saw a zombie villager cured";
		} else {
			return;
		}
		instance.record(villager, kind, text);
	}

	private static void onDamaged(
		LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked
	) {
		if (instance == null || !(entity instanceof Villager villager) || damageTaken <= 0) {
			return;
		}
		Entity attacker = source.getEntity();
		boolean byPlayer = attacker instanceof Player;
		instance.record(
			villager,
			byPlayer ? VillagerMood.HURT_BY_PLAYER : VillagerMood.HURT,
			attacker == null ? "was hurt" : "was hurt by " + describe(attacker)
		);
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (instance == null || !(entity.level() instanceof ServerLevel level)) {
			return;
		}
		boolean villagerDied = entity instanceof Villager;
		boolean golemDied = EntityType.getKey(entity.getType()).getPath().equals("iron_golem");
		if (!villagerDied && !golemDied) {
			return;
		}

		Entity killer = source.getEntity();
		String kind = villagerDied ? VillagerMood.WITNESS_DEATH : VillagerMood.GOLEM_LOST;
		String text = (villagerDied ? "saw a villager die" : "saw the iron golem fall")
			+ (killer == null ? "" : ", killed by " + describe(killer));

		for (Villager witness : level.getEntitiesOfClass(
			Villager.class, entity.getBoundingBox().inflate(WITNESS_RADIUS),
			other -> other != entity && other.isAlive()
		)) {
			instance.record(witness, kind, text);
		}
	}

	// --- periodic scan -------------------------------------------------------------------------

	private void onServerTick(MinecraftServer server) {
		int tick = server.getTickCount();
		for (Villager villager : villagers.values()) {
			if (!villager.isAlive() || !(villager.level() instanceof ServerLevel level)) {
				continue;
			}
			if (Math.floorMod(tick + villager.getUUID().hashCode(), SCAN_INTERVAL_TICKS) != 0) {
				continue;
			}
			scan(villager, level);
		}
	}

	private void scan(Villager villager, ServerLevel level) {
		VillagerState state = villager.getAttachedOrCreate(ModAttachments.VILLAGER_STATE);
		long now = level.getGameTime();

		List<LivingEntity> hostiles = level
			.getEntitiesOfClass(LivingEntity.class, villager.getBoundingBox().inflate(HOSTILE_RADIUS),
				entity -> entity instanceof Enemy && entity.isAlive() && villager.distanceTo(entity) <= HOSTILE_RADIUS)
			.stream()
			.sorted(Comparator.comparingDouble(villager::distanceTo))
			.toList();
		boolean hostileNear = !hostiles.isEmpty();
		boolean golemNear = !level
			.getEntitiesOfClass(LivingEntity.class, villager.getBoundingBox().inflate(GOLEM_RADIUS),
				entity -> entity.isAlive() && EntityType.getKey(entity.getType()).getPath().equals("iron_golem"))
			.isEmpty();

		if (hostileNear && !state.hostileNear()) {
			state.addEvent(new VillagerState.Event(
				now, VillagerMood.THREAT, "a " + describe(hostiles.get(0)).replace("a ", "") + " came close"
			));
		}
		state.setHostileNear(hostileNear);
		state.setGolemNear(golemNear);
		state.markEvaluated(now);

		refresh(villager, state, level);
	}

	// --- mood ----------------------------------------------------------------------------------

	private void record(Villager villager, String kind, String text) {
		if (!(villager.level() instanceof ServerLevel level)) {
			return;
		}
		VillagerState state = villager.getAttachedOrCreate(ModAttachments.VILLAGER_STATE);
		state.addEvent(new VillagerState.Event(level.getGameTime(), kind, text));
		refresh(villager, state, level);
	}

	/** Recomputes the mood; on a change, logs it and may let the villager say something. */
	private void refresh(Villager villager, VillagerState state, ServerLevel level) {
		long now = level.getGameTime();
		String activity = villager.getBrain().getActiveNonCoreActivity()
			.map(active -> active.getName()).orElse("");
		VillagerMood.Mood mood = VillagerMood.compute(state, state.hostileNear(), state.golemNear(), activity, now);

		if (mood.name().equals(state.mood())) {
			return;
		}
		String previous = state.mood();
		state.setMood(mood.name(), mood.reason(), now);
		EnvironmentAI.LOGGER.debug("Villager {} mood {} -> {} ({})",
			villager.getUUID(), previous, mood.name(), mood.reason());

		if (logger != null) {
			JsonObject row = new JsonObject();
			row.addProperty("tier", "system3");
			row.addProperty("source", "mood");
			row.addProperty("dispatched_at_ms", System.currentTimeMillis());
			row.addProperty("game_time", now);
			row.addProperty("mob_uuid", villager.getUUID().toString());
			row.addProperty("profession", VillagerContext.professionName(villager));
			row.addProperty("previous_mood", previous);
			row.addProperty("mood", mood.name());
			row.addProperty("reason", mood.reason());
			row.addProperty("activity", activity);
			logger.logRow(row);
		}

		maybeSpeak(villager, state, mood);
	}

	private void maybeSpeak(Villager villager, VillagerState state, VillagerMood.Mood mood) {
		if (!ambientChatter || !VillagerMood.isVocal(mood.name())) {
			return;
		}
		long nowMs = System.currentTimeMillis();
		if (nowMs - state.lastAmbientMs() < AMBIENT_COOLDOWN_MS) {
			return;
		}
		Player nearest = villager.level().getNearestPlayer(villager, AMBIENT_PLAYER_RADIUS);
		if (!(nearest instanceof ServerPlayer player) || !villager.hasLineOfSight(player)) {
			return;
		}
		state.markAmbient(nowMs);
		dialogue.speakAmbient(villager, player, mood.name(), mood.reason());
	}

	/** "a zombie", "a player", "an iron golem": a type, never a name. */
	private static String describe(Entity entity) {
		if (entity == null) {
			return "something";
		}
		String name = entity instanceof Player
			? "player"
			: EntityType.getKey(entity.getType()).getPath().replace('_', ' ');
		return ("aeiou".indexOf(name.charAt(0)) >= 0 ? "an " : "a ") + name;
	}
}
