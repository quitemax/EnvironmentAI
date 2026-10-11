package com.quitemax.environmentai.ai.prompt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.quitemax.environmentai.ai.perception.TargetAssessment;
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
			+ "Use only the facts you are given: never invent what the target is. Do not put "
				+ "numbers, distances or health values in the memory note; they go stale within "
				+ "seconds. Record events and changes (for example \"was wounded\", \"lost its "
				+ "target\"), not measurements. "
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
			user.append("Current target: ").append(describe(target)).append('\n');
			user.append(TargetAssessment.of(mob, target).describe()).append('\n');
			user.append("Current target distance: ").append(mob.distanceTo(target)).append(" blocks\n");
		} else {
			user.append("No current target.\n");
		}

		user.append(Surroundings.describe(mob, config.awarenessRadiusBlocks));
		user.append("Update the narrative state given this trigger.");

		JsonObject schema = config.structuredOutput ? narrativeSchema() : null;
		return new PromptRequest(SYSTEM_PROMPT, user.toString(), config.temperature, config.maxTokens, schema);
	}

	/** Exactly the four narrative keys, all strings: a small model cannot append junk keys. */
	private static JsonObject narrativeSchema() {
		JsonObject properties = new JsonObject();
		JsonArray required = new JsonArray();
		// maxLength matters as much as the keys: a schema alone still lets a small model write an
		// essay inside one string until the token cap, which cuts the JSON off and loses the reply.
		// The bounds sum to well under the token cap even at ~3 characters per token.
		String[] keys = {"disposition", "intent", "target_description", "memory_notes"};
		int[] maxLengths = {40, 120, 80, 240};
		for (int i = 0; i < keys.length; i++) {
			JsonObject string = new JsonObject();
			string.addProperty("type", "string");
			string.addProperty("maxLength", maxLengths[i]);
			properties.add(keys[i], string);
			required.add(keys[i]);
		}

		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");
		schema.add("properties", properties);
		schema.add("required", required);
		schema.addProperty("additionalProperties", false);
		return schema;
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
