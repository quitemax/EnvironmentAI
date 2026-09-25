package com.quitemax.environmentai.ai.scheduling;

import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.narrative.NarrativeState;
import com.quitemax.environmentai.ai.narrative.NarrativeStateParser;
import com.quitemax.environmentai.ai.prompt.System2PromptBuilder;
import com.quitemax.environmentai.ai.provider.ModelProvider;
import com.quitemax.environmentai.ai.provider.PromptRequest;
import com.quitemax.environmentai.ai.state.MobAiState;
import com.quitemax.environmentai.ai.state.ModAttachments;
import com.quitemax.environmentai.config.ModelTierConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

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
	private static System2TriggerManager instance;

	private final ModelProvider provider;
	private final ModelTierConfig config;

	public System2TriggerManager(ModelProvider provider, ModelTierConfig config) {
		this.provider = provider;
		this.config = config;
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

	private void onServerTick(MinecraftServer server) {
		for (ServerLevel level : server.getAllLevels()) {
			for (UUID uuid : MobTracker.trackedIn(level)) {
				Mob mob = MobTracker.get(uuid);
				if (mob != null && mob.isAlive()) {
					considerDispatch(mob);
				}
			}
		}
	}

	private void considerDispatch(Mob mob) {
		MobAiState state = mob.getAttachedOrCreate(ModAttachments.MOB_AI_STATE);
		if (!state.hasPendingSystem2Trigger() || state.isSystem2InFlight()) {
			return;
		}

		long now = System.currentTimeMillis();
		if (now - state.pendingSystem2TriggerTime() < config.debounceMs) {
			return;
		}
		if (now - state.lastSystem2DispatchTime() < config.cooldownMs) {
			return;
		}

		dispatch(mob, state, now);
	}

	private void dispatch(Mob mob, MobAiState state, long now) {
		String reason = state.pendingSystem2TriggerReason();
		state.clearPendingSystem2Trigger();
		state.setSystem2InFlight(true);
		state.markSystem2Dispatched(now);

		PromptRequest request = System2PromptBuilder.build(mob, state, reason, config);

		CompletableFuture<String> future;
		try {
			future = provider.complete(request);
		} catch (RuntimeException e) {
			future = CompletableFuture.failedFuture(e);
		}

		future.whenComplete((response, throwable) ->
			runOnServerThread(mob, () -> applyResult(mob, response, throwable))
		);
	}

	private void runOnServerThread(Mob mob, Runnable task) {
		if (mob.level() instanceof ServerLevel serverLevel) {
			serverLevel.getServer().execute(task);
		}
	}

	private void applyResult(Mob mob, String response, Throwable throwable) {
		if (!mob.isAlive()) {
			return;
		}

		MobAiState state = mob.getAttachedOrCreate(ModAttachments.MOB_AI_STATE);
		state.setSystem2InFlight(false);

		if (throwable != null) {
			EnvironmentAI.LOGGER.debug("System 2 call failed for {}: {}", mob.getUUID(), throwable.toString());
			return;
		}

		long gameTime = mob.level().getGameTime();
		NarrativeState updated =
			NarrativeStateParser.parse(response, state.narrative(), gameTime, EnvironmentAI.LOGGER);
		state.updateNarrative(updated);
	}
}
