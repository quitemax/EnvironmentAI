package com.quitemax.environmentai.ai.prompt;

import com.quitemax.environmentai.ai.provider.PromptRequest;
import com.quitemax.environmentai.ai.state.MobAiState;
import com.quitemax.environmentai.config.ModelTierConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public final class System1PromptBuilder {
	private static final String SYSTEM_PROMPT_INTRO =
		"You are the fast, reflexive decision layer for a hostile mob in Minecraft. Given its "
			+ "current narrative state (set by a slower, more deliberate reasoning layer) and its "
			+ "immediate situation, pick exactly one action for it to take right now. "
			+ "Respond with strict JSON only, no other text: {\"action\": \"<ACTION>\"}. ";

	/**
	 * Only offered when the mob has a target. WANDER and IDLE are deliberately absent: with a
	 * target they are never sensible, and a small model otherwise anchors on its own previous
	 * WANDER and keeps strolling while a player stands right next to it.
	 */
	private static final String WITH_TARGET_ACTIONS =
		"Valid actions: ATTACK, FLEE, APPROACH, GUARD. "
			+ "Your own health is shown as healthy, wounded or CRITICAL. If YOUR health is CRITICAL, "
			+ "prefer FLEE over ATTACK unless the target is nearly dead. The target's health only "
			+ "says how hurt the target is: a target that is nearly dead is a reason to ATTACK, "
			+ "never a reason to flee. When you are healthy, ATTACK if the target is close and "
			+ "APPROACH if it is far.";

	private static final String NO_TARGET_ACTIONS =
		"Valid actions: WANDER, IDLE. You have no target, so there is nothing to attack or flee from.";

	private System1PromptBuilder() {
	}

	public static PromptRequest build(Mob mob, MobAiState state, ModelTierConfig config) {
		LivingEntity target = mob.getTarget();
		String systemPrompt = SYSTEM_PROMPT_INTRO + (target != null ? WITH_TARGET_ACTIONS : NO_TARGET_ACTIONS);

		StringBuilder user = new StringBuilder();
		user.append("Mob type: ").append(mob.getType()).append('\n');
		user.append("Your health: ").append(mob.getHealth()).append('/').append(mob.getMaxHealth())
			.append(" (").append(ownHealthLabel(mob.getHealth() / mob.getMaxHealth())).append(")\n");
		user.append("Disposition: ").append(state.narrative().disposition()).append('\n');
		user.append("Intent: ").append(state.narrative().intent()).append('\n');
		user.append("Target description: ").append(state.narrative().targetDescription()).append('\n');
		user.append("Memory notes: ").append(state.narrative().memoryNotes()).append('\n');

		if (target != null) {
			user.append("Current target distance: ").append(mob.distanceTo(target)).append(" blocks\n");
			user.append("Current target health: ")
				.append(target.getHealth()).append('/').append(target.getMaxHealth())
				.append(" (").append(targetHealthLabel(target.getHealth() / target.getMaxHealth())).append(")\n");
		} else {
			user.append("No current target.\n");
		}

		user.append("Previous action: ").append(state.currentAction()).append('\n');
		user.append("Pick the single best action now.");

		return new PromptRequest(systemPrompt, user.toString(), config.temperature, config.maxTokens);
	}

	/**
	 * A word for the mob's own health fraction, so a small model doesn't have to do the division.
	 * Deliberately a different vocabulary from {@link #targetHealthLabel}: sharing "CRITICAL"
	 * made the model flee from a healthy position because its target was nearly dead.
	 */
	private static String ownHealthLabel(float fraction) {
		if (fraction < 0.25f) {
			return "CRITICAL";
		}
		if (fraction < 0.6f) {
			return "wounded";
		}
		return "healthy";
	}

	private static String targetHealthLabel(float fraction) {
		if (fraction < 0.25f) {
			return "nearly dead";
		}
		if (fraction < 0.6f) {
			return "hurt";
		}
		return "fine";
	}
}
