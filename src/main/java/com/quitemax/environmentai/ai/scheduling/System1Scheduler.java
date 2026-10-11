package com.quitemax.environmentai.ai.scheduling;

import com.google.gson.JsonObject;
import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.action.ActionParser;
import com.quitemax.environmentai.ai.action.MobAction;
import com.quitemax.environmentai.ai.archetype.MobArchetype;
import com.quitemax.environmentai.ai.perception.TargetAssessment;
import com.quitemax.environmentai.ai.prompt.System1PromptBuilder;
import com.quitemax.environmentai.ai.provider.ModelProvider;
import com.quitemax.environmentai.ai.provider.PromptRequest;
import com.quitemax.environmentai.ai.state.MobAiState;
import com.quitemax.environmentai.ai.state.ModAttachments;
import com.quitemax.environmentai.ai.training.TrainingDataLogger;
import com.quitemax.environmentai.config.ModelTierConfig;
import com.quitemax.environmentai.config.ProviderType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Drives System 1: each server tick, considers a staggered slice of tracked mobs (via
 * {@code tickInterval} + a per-mob hash offset, so not every mob is checked on the same tick),
 * and decides each one's action by one of three routes:
 * <ul>
 *   <li><b>skip</b> — the mob is farther than {@code activeRadiusBlocks} from every player;</li>
 *   <li><b>rule</b> — the mob has no target, so there is nothing to react to and the model is
 *       not called (it only produced no-op ATTACK/FLEE answers in that case);</li>
 *   <li><b>model</b> — dispatched asynchronously, throttled per mob by {@code minSpacing*Ms} and
 *       globally by {@code maxInFlight}, least-recently-served mob first.</li>
 * </ul>
 * Results are always applied back on the main thread via {@link MinecraftServer#execute(Runnable)}.
 */
public class System1Scheduler {
	private static final float NEAR_DEATH_HEALTH_FRACTION = 0.2f;
	private static final float NEAR_DEATH_RECOVERY_FRACTION = 0.25f;
	/**
	 * Decisions in a row with the same action before System 1 flags the mob as "stuck". Counted in
	 * decisions (a few per second per mob), so this is tens of seconds: sustained ATTACK during a
	 * fight is normal and must not be reported to System 2 as being stuck.
	 */
	private static final int STUCK_ACTION_TICK_THRESHOLD = 100;

	private static final int DEFAULT_MAX_IN_FLIGHT = 4;

	/**
	 * Once a mob starts fleeing it keeps fleeing for at least this long. The model decides each
	 * tick independently and was observed alternating FLEE/ATTACK every ~300 ms at critical
	 * health, which in-game is a mob stepping away and back again.
	 */
	private static final long FLEE_MIN_HOLD_MS = 2_500;

	/** With no target there is nothing to react to, so let vanilla behaviour run. */
	private static final MobAction NO_TARGET_ACTION = MobAction.IDLE;

	/**
	 * Survival reflex: a wounded mob flees from a target that is still dangerous. This is a rule,
	 * not a model decision, because the 3B model answered ATTACK at critical health about 40% of the
	 * time even when told to flee. Health thresholds are configurable ({@code fleeEnterHealth} /
	 * {@code fleeExitHealth}); it is sustained until health recovers past the exit value or the
	 * target is beyond {@code EXIT_RANGE}, and the gap between each pair is the hysteresis.
	 */
	private static final float CRITICAL_FLEE_ENTER_RANGE = 16.0f;
	private static final float CRITICAL_FLEE_EXIT_RANGE = 24.0f;
	/** Intimidation only applies to a target this close, and is sustained a little further out. */
	private static final float INTIMIDATION_ENTER_RANGE = 24.0f;
	private static final float INTIMIDATION_EXIT_RANGE = 32.0f;
	/** Fellow hostiles within this radius steady a mob's nerve, each by a little, up to a cap. */
	private static final double FELLOW_HOSTILE_RADIUS = 8.0;
	private static final double COMPANY_BONUS_EACH = 0.75;
	private static final double MAX_COMPANY_BONUS = 3.0;
	/** While a rule holds a mob in FLEE, log its situation at most this often, to verify it works. */
	private static final long RULE_HEARTBEAT_MS = 1_000;
	/** A target at or below this health fraction is worth finishing rather than fleeing from. */
	private static final float TARGET_NEARLY_DEAD_FRACTION = 0.25f;

	private final ModelProvider provider;
	private final ModelTierConfig config;
	/** Null when training-data logging is disabled. */
	private final TrainingDataLogger trainingLogger;
	/** Outstanding model calls across all mobs. Decremented off the server thread, hence atomic. */
	private final AtomicInteger inFlight = AiLoad.SYSTEM1_IN_FLIGHT;
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

		List<Mob> candidates = new ArrayList<>();
		for (ServerLevel level : server.getAllLevels()) {
			for (UUID uuid : MobTracker.trackedIn(level)) {
				Mob mob = MobTracker.get(uuid);
				if (mob != null && mob.isAlive() && considerMob(mob)) {
					candidates.add(mob);
				}
			}
		}

		// Rules above still ran; only model calls wait out the startup grace (see AiLoad).
		if (candidates.isEmpty() || server.getTickCount() < AiLoad.STARTUP_GRACE_TICKS) {
			return;
		}

		// Longest-waiting mob first, so a crowd can't starve the mobs that happen to iterate last.
		candidates.sort(Comparator.comparingLong(
			(Mob mob) -> mob.getAttachedOrCreate(ModAttachments.MOB_AI_STATE).lastSystem1DispatchTime()
		));

		int maxInFlight = config.maxInFlight > 0 ? config.maxInFlight : DEFAULT_MAX_IN_FLIGHT;
		long now = System.currentTimeMillis();
		for (Mob mob : candidates) {
			if (inFlight.get() >= maxInFlight) {
				break;
			}
			dispatch(mob, mob.getAttachedOrCreate(ModAttachments.MOB_AI_STATE), now);
		}
	}

	/**
	 * Applies the skip and rule routes. Returns true only if the mob needs a model call and is
	 * eligible for one right now.
	 */
	private boolean considerMob(Mob mob) {
		int interval = Math.max(1, config.tickInterval);
		if (Math.floorMod(mob.getUUID().hashCode() + tickCounter, interval) != 0) {
			return false;
		}

		if (config.activeRadiusBlocks > 0
			&& mob.level().getNearestPlayer(mob, config.activeRadiusBlocks) == null) {
			return false;
		}

		MobAiState state = mob.getAttachedOrCreate(ModAttachments.MOB_AI_STATE);
		if (state.isSystem1InFlight()) {
			return false;
		}

		if (mob.getTarget() == null) {
			applyRule(mob, state, NO_TARGET_ACTION, "no_target");
			return false;
		}

		Rule rule = decideRule(mob, state);
		if (rule != null) {
			applyRule(mob, state, rule.action(), rule.reason());
			logRuleHeartbeat(mob, state, rule.action(), rule.reason() + "_sustained");
			// The model path normally raises these escalations, and this mob no longer goes through it.
			checkForSignificantEvent(mob, state);
			return false;
		}

		long now = System.currentTimeMillis();
		long minSpacing = config.provider == ProviderType.CLOUD ? config.minSpacingCloudMs : config.minSpacingLocalMs;
		return now - state.lastSystem1DispatchTime() >= minSpacing;
	}

	/**
	 * Only writes state (and a log row) on a change: repeatedly "re-applying" the same action would
	 * grow the same-action streak and trip the stuck-action escalation to System 2 for no reason.
	 */
	private void applyRule(Mob mob, MobAiState state, MobAction action, String reason) {
		if (state.currentAction() == action) {
			return;
		}
		JsonObject row = trainingLogger != null
			? trainingLogger.snapshot(mob, state, null, System.currentTimeMillis())
			: null;
		state.updateAction(action);
		if (row != null) {
			state.markRuleLogged(System.currentTimeMillis());
			trainingLogger.recordRule(row, action, reason);
		}
	}

	/**
	 * Rules only log on a change, which leaves a sustained flee invisible: nothing in the log says
	 * whether the mob actually got away. This writes the live situation (including target distance)
	 * about once a second while a rule is holding the mob in an action.
	 */
	private void logRuleHeartbeat(Mob mob, MobAiState state, MobAction action, String reason) {
		if (trainingLogger == null) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now - state.lastRuleLogMs() < RULE_HEARTBEAT_MS) {
			return;
		}
		state.markRuleLogged(now);
		trainingLogger.recordRule(trainingLogger.snapshot(mob, state, null, now), action, reason);
	}

	/** A decision made by a rule instead of the model. */
	private record Rule(MobAction action, String reason) {
	}

	/**
	 * The reflexes, in priority order, or null if none applies and the model should decide:
	 * <ol>
	 *   <li>{@code critical_flee}: badly wounded, so run from a target still dangerous;</li>
	 *   <li>{@code intimidated}: the target's gear is beyond this mob's nerve, so melee mobs flee
	 *       and ranged mobs keep their distance.</li>
	 * </ol>
	 * Both need a target. Sustaining uses wider thresholds than entering, so a mob doesn't flicker
	 * at the boundary.
	 */
	private Rule decideRule(Mob mob, MobAiState state) {
		LivingEntity target = mob.getTarget();
		if (target == null) {
			return null;
		}
		// A target about to die is worth finishing, not running from, however hurt or scared we are.
		if (target.getHealth() / target.getMaxHealth() <= TARGET_NEARLY_DEAD_FRACTION) {
			return null;
		}

		float health = mob.getHealth() / mob.getMaxHealth();
		float distance = mob.distanceTo(target);
		boolean fleeing = state.currentAction() == MobAction.FLEE;
		boolean criticalFlee = fleeing
			? health < config.fleeExitHealth && distance <= CRITICAL_FLEE_EXIT_RANGE
			: health < config.fleeEnterHealth && distance <= CRITICAL_FLEE_ENTER_RANGE;
		if (criticalFlee) {
			return new Rule(MobAction.FLEE, "critical_flee");
		}

		if (config.intimidationEnabled && isIntimidated(mob, state, target, distance)) {
			MobAction response = MobArchetype.of(mob) == MobArchetype.RANGED
				? MobAction.KEEP_DISTANCE
				: MobAction.FLEE;
			return new Rule(response, "intimidated");
		}
		return null;
	}

	/**
	 * Threat (0-10, from the target's armour and held weapon) against the mob's nerve, which its
	 * archetype sets and fellow hostiles nearby raise. Sustaining needs only to stay within a point
	 * of the nerve, so the mob doesn't swap between running and attacking as the player swings.
	 */
	private boolean isIntimidated(Mob mob, MobAiState state, LivingEntity target, float distance) {
		MobAction previous = state.currentAction();
		boolean sustaining = previous == MobAction.KEEP_DISTANCE || previous == MobAction.FLEE;
		if (distance > (sustaining ? INTIMIDATION_EXIT_RANGE : INTIMIDATION_ENTER_RANGE)) {
			return false;
		}

		TargetAssessment assessment = TargetAssessment.of(mob, target);
		// Cheap reject before the area query: no archetype's nerve is below 6.
		if (assessment.threat() < 5.0) {
			return false;
		}

		long fellows = mob.level()
			.getEntitiesOfClass(Mob.class, mob.getBoundingBox().inflate(FELLOW_HOSTILE_RADIUS),
				other -> other != mob && other instanceof Enemy && other.isAlive())
			.size();
		double nerve = MobArchetype.of(mob).nerve() + Math.min(MAX_COMPANY_BONUS, fellows * COMPANY_BONUS_EACH);
		return assessment.threat() >= (sustaining ? nerve - 1.0 : nerve);
	}

	private void dispatch(Mob mob, MobAiState state, long now) {
		state.setSystem1InFlight(true);
		state.markSystem1Dispatched(now);
		int inFlightAtDispatch = inFlight.incrementAndGet();

		PromptRequest request = System1PromptBuilder.build(mob, state, config);
		JsonObject trainingRow = null;
		if (trainingLogger != null) {
			trainingRow = trainingLogger.snapshot(mob, state, request, now);
			// How many calls were outstanding (including this one): the direct measure of queueing.
			trainingRow.addProperty("in_flight_at_dispatch", inFlightAtDispatch);
			// What System 2 had outstanding at the same moment: both tiers share one GPU.
			trainingRow.addProperty("system2_in_flight", AiLoad.SYSTEM2_IN_FLIGHT.get());
		}
		JsonObject row = trainingRow;

		CompletableFuture<String> future;
		try {
			future = provider.complete(request);
		} catch (RuntimeException e) {
			future = CompletableFuture.failedFuture(e);
		}

		future.whenComplete((response, throwable) -> {
			// Release the slot on the HTTP thread, before waiting for the server thread, so the
			// cap measures requests to the model rather than the server's own tick backlog.
			inFlight.decrementAndGet();
			long respondedAt = System.currentTimeMillis();
			runOnServerThread(mob, () -> applyResult(mob, response, throwable, row, respondedAt));
		});
	}

	private void runOnServerThread(Mob mob, Runnable task) {
		if (mob.level() instanceof ServerLevel serverLevel) {
			serverLevel.getServer().execute(task);
		}
	}

	private void applyResult(
		Mob mob, String response, Throwable throwable, JsonObject trainingRow, long respondedAtMs
	) {
		if (!mob.isAlive()) {
			return;
		}

		MobAiState state = mob.getAttachedOrCreate(ModAttachments.MOB_AI_STATE);
		state.setSystem1InFlight(false);
		long appliedAt = System.currentTimeMillis();

		if (throwable != null) {
			EnvironmentAI.LOGGER.debug("System 1 call failed for {}: {}", mob.getUUID(), throwable.toString());
			if (trainingLogger != null && trainingRow != null) {
				trainingLogger.recordError(trainingRow, throwable, state.currentAction(), respondedAtMs, appliedAt);
			}
			return;
		}

		MobAction parsed = ActionParser.tryParse(response, EnvironmentAI.LOGGER).orElse(null);
		if (parsed != null && !System1PromptBuilder.validActions(mob).contains(parsed)) {
			// Parsed, but not an action this mob may take in its current situation (e.g. WANDER
			// with a target, or KEEP_DISTANCE for a zombie). Structured output normally prevents this;
			// providers without it can still produce it, so it is rejected here as well.
			EnvironmentAI.LOGGER.debug("System 1 chose {} which is not valid for {}", parsed, mob.getType());
			if (trainingRow != null) {
				trainingRow.addProperty("invalid_action", parsed.name());
			}
			parsed = null;
		}
		MobAction newAction = parsed != null ? parsed : state.currentAction();
		Rule rule = decideRule(mob, state);
		if (rule != null && newAction != rule.action()) {
			// A rule became applicable while this call was in flight; it wins over the answer.
			newAction = rule.action();
			if (trainingRow != null) {
				trainingRow.addProperty("override", rule.reason());
			}
		} else if (holdFlee(state, newAction, appliedAt)) {
			newAction = MobAction.FLEE;
			if (trainingRow != null) {
				// The raw response still records what the model said; this records why it wasn't applied.
				trainingRow.addProperty("override", "flee_hold");
			}
		}
		state.updateAction(newAction);

		if (trainingLogger != null && trainingRow != null) {
			trainingLogger.recordModel(trainingRow, response, parsed, newAction, respondedAtMs, appliedAt);
		}

		checkForSignificantEvent(mob, state);
	}

	/** True if the mob is mid-flee and the model's new answer should be ignored for now. */
	private static boolean holdFlee(MobAiState state, MobAction proposed, long nowMs) {
		return state.currentAction() == MobAction.FLEE
			&& proposed != MobAction.FLEE
			&& nowMs - state.actionChangedAtMs() < FLEE_MIN_HOLD_MS;
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
