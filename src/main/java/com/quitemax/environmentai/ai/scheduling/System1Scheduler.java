package com.quitemax.environmentai.ai.scheduling;

import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.action.ActionParser;
import com.quitemax.environmentai.ai.action.MobAction;
import com.quitemax.environmentai.ai.prompt.System1PromptBuilder;
import com.quitemax.environmentai.ai.provider.ModelProvider;
import com.quitemax.environmentai.ai.provider.PromptRequest;
import com.quitemax.environmentai.ai.state.MobAiState;
import com.quitemax.environmentai.ai.state.ModAttachments;
import com.quitemax.environmentai.ai.training.TrainingDataLogger;
import com.google.gson.JsonObject;
import com.quitemax.environmentai.config.ModelTierConfig;
import com.quitemax.environmentai.config.ProviderType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Drives System 1: each server tick, considers a staggered slice of tracked mobs (via
 * {@code tickInterval} + a per-mob hash offset, so not every mob is checked on the same tick),
 * throttles actual model calls per mob, and dispatches asynchronously. Results are always
 * applied back on the main thread via {@link MinecraftServer#execute(Runnable)}.
 */
public class System1Scheduler {
	private static final float NEAR_DEATH_HEALTH_FRACTION = 0.2f;
	private static final float NEAR_DEATH_RECOVERY_FRACTION = 0.25f;
	private static final int STUCK_ACTION_TICK_THRESHOLD = 15;

	private final ModelProvider provider;
	private final ModelTierConfig config;
	/** Null when training-data logging is disabled. */
	private final TrainingDataLogger trainingLogger;
	private long tickCounter = 0;

	public System1Scheduler(ModelProvider provider, ModelTierConfig config, TrainingDataLogger trainingLogger) {
		this.provider = provider;
		this.config = config;
		this.trainingLogger = trainingLogger;
	}

	public void register() {
		ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
	}

	private void onServerTick(MinecraftServer server) {
		tickCounter++;
		for (ServerLevel level : server.getAllLevels()) {
			for (UUID uuid : MobTracker.trackedIn(level)) {
				Mob mob = MobTracker.get(uuid);
				if (mob != null && mob.isAlive()) {
					considerMob(mob);
				}
			}
		}
	}

	private void considerMob(Mob mob) {
		int interval = Math.max(1, config.tickInterval);
		if (Math.floorMod(mob.getUUID().hashCode() + tickCounter, interval) != 0) {
			return;
		}

		MobAiState state = mob.getAttachedOrCreate(ModAttachments.MOB_AI_STATE);
		if (state.isSystem1InFlight()) {
			return;
		}

		long now = System.currentTimeMillis();
		long minSpacing = config.provider == ProviderType.CLOUD ? config.minSpacingCloudMs : config.minSpacingLocalMs;
		if (now - state.lastSystem1DispatchTime() < minSpacing) {
			return;
		}

		dispatch(mob, state, now);
	}

	private void dispatch(Mob mob, MobAiState state, long now) {
		state.setSystem1InFlight(true);
		state.markSystem1Dispatched(now);

		PromptRequest request = System1PromptBuilder.build(mob, state, config);
		JsonObject trainingRow =
			trainingLogger != null ? trainingLogger.snapshot(mob, state, request, now) : null;

		CompletableFuture<String> future;
		try {
			future = provider.complete(request);
		} catch (RuntimeException e) {
			future = CompletableFuture.failedFuture(e);
		}

		future.whenComplete((response, throwable) ->
			runOnServerThread(mob, () -> applyResult(mob, response, throwable, trainingRow))
		);
	}

	private void runOnServerThread(Mob mob, Runnable task) {
		if (mob.level() instanceof ServerLevel serverLevel) {
			serverLevel.getServer().execute(task);
		}
	}

	private void applyResult(Mob mob, String response, Throwable throwable, JsonObject trainingRow) {
		if (!mob.isAlive()) {
			return;
		}

		MobAiState state = mob.getAttachedOrCreate(ModAttachments.MOB_AI_STATE);
		state.setSystem1InFlight(false);

		if (throwable != null) {
			EnvironmentAI.LOGGER.debug("System 1 call failed for {}: {}", mob.getUUID(), throwable.toString());
			return;
		}

		MobAction parsed = ActionParser.tryParse(response, EnvironmentAI.LOGGER).orElse(null);
		MobAction newAction = parsed != null ? parsed : state.currentAction();
		state.updateAction(newAction);

		if (trainingLogger != null && trainingRow != null) {
			trainingLogger.record(trainingRow, response, parsed, newAction, System.currentTimeMillis());
		}

		checkForSignificantEvent(mob, state);
	}

	/**
	 * System 1 can itself flag a situation as narratively significant even though nothing on the
	 * fixed vanilla event list fired — each check is edge-triggered (only fires once per crossing)
	 * so a mob sitting in a bad state doesn't spam System 2 every time it's reconsidered.
	 */
	private void checkForSignificantEvent(Mob mob, MobAiState state) {
		float healthFraction = mob.getHealth() / mob.getMaxHealth();

		if (healthFraction <= NEAR_DEATH_HEALTH_FRACTION) {
			if (!state.isNearDeathEscalated()) {
				state.setNearDeathEscalated(true);
				System2TriggerManager.escalateFromSystem1(mob, "near death");
			}
		} else if (healthFraction > NEAR_DEATH_RECOVERY_FRACTION) {
			state.setNearDeathEscalated(false);
		}

		if (state.consecutiveSameActionTicks() >= STUCK_ACTION_TICK_THRESHOLD && !state.isStuckEscalated()) {
			state.setStuckEscalated(true);
			System2TriggerManager.escalateFromSystem1(mob, "stuck repeating the same action for a while");
		}
	}
}
