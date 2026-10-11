package com.quitemax.environmentai.ai.prompt;

import com.quitemax.environmentai.ai.provider.PromptRequest;
import com.quitemax.environmentai.ai.state.MobAiState;
import com.quitemax.environmentai.config.ModelTierConfig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

public final class System2PromptBuilder {
	private static final String SYSTEM_PROMPT =
		"You are the slow, deliberate narrative layer for a hostile mob in Minecraft. Given what "
			+ "just happened and the mob's previous narrative state, decide its updated disposition, "
			+ "intent, a short description of its target (if any), and a short rolling memory note "
			+ "of recent significant events. This narrative state is what a faster, more reflexive "
			+ "decision layer will act on between now and your next update, so make it useful. "
			+ "Respond with strict JSON only, no other text: {\"disposition\": \"...\", \"intent\": "
			+ "\"...\", \"target_description\": \"...\", \"memory_notes\": \"...\"}.";

	private System2PromptBuilder() {
	}

	public static PromptRequest build(Mob mob, MobAiState state, String triggerReason, ModelTierConfig config) {
		StringBuilder user = new StringBuilder();
		user.append("Trigger: ").append(triggerReason).append('\n');
		user.append("Mob type: ").append(mob.getType()).append('\n');
		user.append("Mob health: ").append(mob.getHealth()).append('/').append(mob.getMaxHealth()).append('\n');
		user.append("Previous disposition: ").append(state.narrative().disposition()).append('\n');
		user.append("Previous intent: ").append(state.narrative().intent()).append('\n');
		user.append("Previous memory notes: ").append(state.narrative().memoryNotes()).append('\n');

		LivingEntity target = mob.getTarget();
		if (target != null) {
			user.append("Current target distance: ").append(mob.distanceTo(target)).append(" blocks\n");
		} else {
			user.append("No current target.\n");
		}

		user.append("Update the narrative state given this trigger.");

		return new PromptRequest(SYSTEM_PROMPT, user.toString(), config.temperature, config.maxTokens);
	}

	/**
	 * Names the target by type only ("a player", "an iron golem"). Player usernames are
	 * deliberately left out: this prompt may be sent to a third-party cloud provider.
	 */
	private static String describe(LivingEntity target) {
		String name = EntityType.getKey(target.getType()).getPath().replace('_', ' ');
		boolean vowel = "aeiou".indexOf(name.charAt(0)) >= 0;
		return (vowel ? "an " : "a ") + name;
	}
}
