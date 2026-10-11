package com.quitemax.environmentai.ai.prompt;

import com.quitemax.environmentai.ai.provider.PromptRequest;
import com.quitemax.environmentai.ai.state.MobAiState;
import com.quitemax.environmentai.config.ModelTierConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public final class System1PromptBuilder {
	private static final String SYSTEM_PROMPT =
		"You are the fast, reflexive decision layer for a hostile mob in Minecraft. Given its "
			+ "current narrative state (set by a slower, more deliberate reasoning layer) and its "
			+ "immediate situation, pick exactly one action for it to take right now. "
			+ "Respond with strict JSON only, no other text: {\"action\": \"<ACTION>\"}. "
			+ "Valid actions: ATTACK, FLEE, APPROACH, GUARD, WANDER, IDLE. "
			+ "Guidelines: if your health is CRITICAL and the target is not nearly dead, prefer "
			+ "FLEE over ATTACK, whatever your disposition says. If you have a target and are "
			+ "healthy, ATTACK when it is close and APPROACH when it is far. Without a target, "
			+ "WANDER or IDLE.";

	private System1PromptBuilder() {
	}

	public static PromptRequest build(Mob mob, MobAiState state, ModelTierConfig config) {
		StringBuilder user = new StringBuilder();
		user.append("Mob type: ").append(mob.getType()).append('\n');
		user.append("Mob health: ").append(mob.getHealth()).append('/').append(mob.getMaxHealth())
			.append(" (").append(healthLabel(mob.getHealth() / mob.getMaxHealth())).append(")\n");
		user.append("Disposition: ").append(state.narrative().disposition()).append('\n');
		user.append("Intent: ").append(state.narrative().intent()).append('\n');
		user.append("Target description: ").append(state.narrative().targetDescription()).append('\n');
		user.append("Memory notes: ").append(state.narrative().memoryNotes()).append('\n');

		LivingEntity target = mob.getTarget();
		if (target != null) {
			user.append("Current target distance: ").append(mob.distanceTo(target)).append(" blocks\n");
			user.append("Current target health: ")
				.append(target.getHealth()).append('/').append(target.getMaxHealth())
				.append(" (").append(healthLabel(target.getHealth() / target.getMaxHealth())).append(")\n");
		} else {
			user.append("No current target.\n");
		}

		user.append("Previous action: ").append(state.currentAction()).append('\n');
		user.append("Pick the single best action now.");

		return new PromptRequest(SYSTEM_PROMPT, user.toString(), config.temperature, config.maxTokens);
	}

	/** A word for the health fraction, so a small model doesn't have to do the division itself. */
	private static String healthLabel(float fraction) {
		if (fraction < 0.25f) {
			return "CRITICAL";
		}
		if (fraction < 0.6f) {
			return "wounded";
		}
		return "healthy";
	}
}
