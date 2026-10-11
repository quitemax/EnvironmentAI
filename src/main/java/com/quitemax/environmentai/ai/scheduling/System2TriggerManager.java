package com.quitemax.environmentai.ai.scheduling;

import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.narrative.NarrativeState;
import com.quitemax.environmentai.ai.narrative.NarrativeStateParser;
import com.quitemax.environmentai.ai.prompt.System2PromptBuilder;
import com.quitemax.environmentai.ai.provider.ModelProvider;
import com.quitemax.environmentai.ai.provider.PromptRequest;
import com.quitemax.environmentai.ai.state.MobAiState;
import com.quitemax.environmentai.ai.state.ModAttachments;
import com.quitemax.environmentai.ai.training.TrainingDataLogger;
import com.google.gson.JsonObject;
import com.quitemax.environmentai.config.ModelTierConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Drives System 2: coalesces narrative-refresh triggers (damage taken, target acquired/lost, and
 * System 1's own significant-event escalations) per mob with a debounce window (wait for a quiet
 * period after the last trigger) and a cooldown (minimum spacing between actual dispatches), then
 * fires an async narrative refresh. There is no fixed timer — System 2 only ever runs in reaction
 * to a trigger.
 */
public final class System2TriggerManager {
	/**
	 * One call at a time: System 2 shares one GPU with System 1, and a single-slot Ollama instance
	 * serves callers serially anyway. Logged data showed six concurrent calls taking 16 s each.
	 */
	private static final int DEFAULT_MAX_IN_FLIGHT = 1;

	private static System2TriggerManager instance;

	private final ModelProvider provider;
	private final ModelTierConfig config;
	/** Null when call logging is disabled. */
	private final TrainingDataLogger callLogger;

	public System2TriggerManager(ModelProvider provider, ModelTierConfig config, TrainingDataLogger callLogger) {
		this.provider = provider;
		this.config = config;
		this.callLogger = callLogger;
	}

	public void register() {
		instance = this;
		ServerLivingEntityEvents.AFTER_DAMAGE.register(System2TriggerManager::handleDamaged);
		ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
	}

	/** Called by {@link com.quitemax.environmentai.mixin.MobTargetEventMixin}. */
	public static void onTargetChanged(Mob mob, LivingEntity previous, LivingEntity next) {
		if (instance != null && mob instanceof Enemy) {
			instance.trigger(mob, next == null ? "lost target" : "acquired target");
		}
	}

	/** Called by System 1's scheduler when it flags a situation as narratively significant. */
	public static void escalateFromSystem1(Mob mob, String reason) {
		if (instance != null && mob instanceof Enemy) {
			instance.trigger(mob, reason);
		}
	}

	private static void handleDamaged(
		LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked
	) {
		if (instance == null) {
			return;
		}
		asTrackedMob(entity).ifPresent(mob -> instance.trigger(mob, "took damage"));
	}

	private static Optional<Mob> asTrackedMob(LivingEntity entity) {
		if (entity instanceof Mob mob && mob instanceof Enemy) {
			return Optional.of(mob);
		}
		return Optional.empty();
	}

	private void trigger(Mob mob, String reason) {
		MobAiState state = mob.getAttachedOrCreate(ModAttachments.MOB_AI_STATE);
		state.setPendingSystem2Trigger(reason, System.currentTimeMillis());
	}

	/**
	 * Collects every mob whose trigger has cleared debounce and cooldown, then dispatches them
	 * oldest-trigger-first until the global cap is reached. The rest simply stay pending: each
	 * mob holds only its latest trigger, so waiting costs nothing and nothing is duplicated.
	 */
	private void onServerTick(MinecraftServer server) {
		List<Mob> ready = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (UUID uuid : MobTracker.trackedIn(level)) {
				Mob mob = MobTracker.get(uuid);
				if (mob != null && mob.isAlive() && isReady(mob)) {
					ready.add(mob);
				}
			}
		}

		if (ready.isEmpty() || server.getTickCount() < AiLoad.STARTUP_GRACE_TICKS) {
			return;
		}

		ready.sort(Comparator.comparingLong(
			(Mob mob) -> mob.getAttachedOrCreate(ModAttachments.MOB_AI_STATE).pendingSystem2TriggerTime()
		));

		int maxInFlight = config.maxInFlight > 0 ? config.maxInFlight : DEFAULT_MAX_IN_FLIGHT;
		long now = System.currentTimeMillis();
		for (Mob mob : ready) {
			if (AiLoad.SYSTEM2_IN_FLIGHT.get() >= maxInFlight) {
				break;
			}
			dispatch(mob, mob.getAttachedOrCreate(ModAttachments.MOB_AI_STATE), now);
		}
	}

	private boolean isReady(Mob mob) {
		MobAiState state = mob.getAttachedOrCreate(ModAttachments.MOB_AI_STATE);
		if (!state.hasPendingSystem2Trigger() || state.isSystem2InFlight()) {
			return false;
		}

		long now = System.currentTimeMillis();
		if (now - state.pendingSystem2TriggerTime() < config.debounceMs) {
			return false;
		}
		if (now - state.lastSystem2DispatchTime() < config.cooldownMs) {
			return false;
		}

		// A mob nobody is near can't be observed, so a narrative refresh for it is wasted GPU time.
		// Its trigger stays pending and is served when a player comes back.
		return config.activeRadiusBlocks <= 0
			|| mob.level().getNearestPlayer(mob, config.activeRadiusBlocks) != null;
	}

	private void dispatch(Mob mob, MobAiState state, long now) {
		String reason = state.pendingSystem2TriggerReason();
		long triggeredAt = state.pendingSystem2TriggerTime();
		state.clearPendingSystem2Trigger();
		state.setSystem2InFlight(true);
		state.markSystem2Dispatched(now);
		int inFlightAtDispatch = AiLoad.SYSTEM2_IN_FLIGHT.incrementAndGet();

		PromptRequest request = System2PromptBuilder.build(mob, state, reason, config);
		JsonObject row = callLogger != null
			? callRow(mob, state, request, reason, now, now - triggeredAt, inFlightAtDispatch)
			: null;

		CompletableFuture<String> future;
		try {
			future = provider.complete(request);
		} catch (RuntimeException e) {
			future = CompletableFuture.failedFuture(e);
		}

		future.whenComplete((response, throwable) -> {
			AiLoad.SYSTEM2_IN_FLIGHT.decrementAndGet();
			long respondedAt = System.currentTimeMillis();
			runOnServerThread(mob, () -> applyResult(mob, response, throwable, row, respondedAt));
		});
	}

	/** The situation System 2 was asked about, captured on the server thread at dispatch. */
	private static JsonObject callRow(
		Mob mob, MobAiState state, PromptRequest request, String reason,
		long dispatchedAtMs, long triggerWaitMs, int inFlightAtDispatch
	) {
		JsonObject row = new JsonObject();
		row.addProperty("tier", "system2");
		row.addProperty("dispatched_at_ms", dispatchedAtMs);
		row.addProperty("game_time", mob.level().getGameTime());
		row.addProperty("mob_uuid", mob.getUUID().toString());
		row.addProperty("mob_type", EntityType.getKey(mob.getType()).toString());
		row.addProperty("trigger", reason);
		// Time the trigger spent waiting out debounce and cooldown before this call was made.
		row.addProperty("trigger_wait_ms", triggerWaitMs);
		row.addProperty("health", mob.getHealth());
		row.addProperty("max_health", mob.getMaxHealth());
		row.addProperty("previous_disposition", state.narrative().disposition());
		row.addProperty("previous_intent", state.narrative().intent());
		row.addProperty("previous_memory_notes", state.narrative().memoryNotes());
		row.addProperty("in_flight_at_dispatch", inFlightAtDispatch);
		// What System 1 had outstanding at the same moment: both tiers share one GPU.
		row.addProperty("system1_in_flight", AiLoad.SYSTEM1_IN_FLIGHT.get());
		row.addProperty("prompt", request.userPrompt());
		return row;
	}

	private void runOnServerThread(Mob mob, Runnable task) {
		if (mob.level() instanceof ServerLevel serverLevel) {
			serverLevel.getServer().execute(task);
		}
	}

	private void applyResult(Mob mob, String response, Throwable throwable, JsonObject row, long respondedAtMs) {
		if (!mob.isAlive()) {
			return;
		}

		MobAiState state = mob.getAttachedOrCreate(ModAttachments.MOB_AI_STATE);
		state.setSystem2InFlight(false);
		long appliedAt = System.currentTimeMillis();

		if (throwable != null) {
			EnvironmentAI.LOGGER.debug("System 2 call failed for {}: {}", mob.getUUID(), throwable.toString());
			if (row != null) {
				addTiming(row, respondedAtMs, appliedAt);
				row.addProperty("source", "error");
				row.addProperty("error", throwable.toString());
				callLogger.logRow(row);
			}
			return;
		}

		long gameTime = mob.level().getGameTime();
		NarrativeState updated =
			NarrativeStateParser.parse(response, state.narrative(), gameTime, EnvironmentAI.LOGGER);
		state.updateNarrative(updated);

		if (row != null) {
			addTiming(row, respondedAtMs, appliedAt);
			row.addProperty("source", "model");
			row.addProperty("raw_response", response);
			row.addProperty("disposition", updated.disposition());
			row.addProperty("intent", updated.intent());
			row.addProperty("target_description", updated.targetDescription());
			row.addProperty("memory_notes", updated.memoryNotes());
			callLogger.logRow(row);
		}
	}

	private static void addTiming(JsonObject row, long respondedAtMs, long appliedAtMs) {
		long dispatchedAt = row.get("dispatched_at_ms").getAsLong();
		row.addProperty("response_ms", respondedAtMs - dispatchedAt);
		row.addProperty("apply_delay_ms", appliedAtMs - respondedAtMs);
	}
}
