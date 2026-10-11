package com.quitemax.environmentai.ai.prompt;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.quitemax.environmentai.ai.action.MobAction;
import com.quitemax.environmentai.ai.archetype.MobArchetype;
import com.quitemax.environmentai.ai.perception.TargetAssessment;
import com.quitemax.environmentai.ai.provider.PromptRequest;
import com.quitemax.environmentai.ai.state.MobAiState;
import com.quitemax.environmentai.config.ModelTierConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.List;
import java.util.Locale;

public final class System1PromptBuilder {
	private static final String SYSTEM_PROMPT_INTRO =
		"You are the fast, reflexive decision layer for a hostile mob in Minecraft. Given its "
			+ "current narrative state (set by a slower, more deliberate reasoning layer) and its "
			+ "immediate situation, pick exactly one action for it to take right now. "
			+ "Respond with strict JSON only, no other text: {\"action\": \"<ACTION>\"}. ";

	/**
	 * Offered when the mob has a target. WANDER and IDLE are deliberately absent: with a target they
	 * are never sensible, and a small model otherwise anchors on its own previous WANDER and keeps
	 * strolling while a player stands right next to it.
	 */
	private static final String WITH_TARGET_GUIDELINES =
		"Your own health is shown as healthy, wounded or CRITICAL. If YOUR health is CRITICAL, "
			+ "prefer FLEE over ATTACK unless the target is nearly dead. The target's health only "
			+ "says how hurt the target is: a target that is nearly dead is a reason to ATTACK, "
			+ "never a reason to flee. When you are healthy, ATTACK if the target is close and "
			+ "APPROACH if it is far.";

	private static final List<MobAction> NO_TARGET_ACTIONS = List.of(MobAction.WANDER, MobAction.IDLE);

	private System1PromptBuilder() {
	}

	/** The actions this mob may choose from right now; the prompt, schema and parser all agree. */
	public static List<MobAction> validActions(Mob mob) {
		return mob.getTarget() != null ? MobArchetype.of(mob).actionsWithTarget() : NO_TARGET_ACTIONS;
	}

	public static PromptRequest build(Mob mob, MobAiState state, ModelTierConfig config) {
		LivingEntity target = mob.getTarget();
		List<MobAction> actions = validActions(mob);

		StringBuilder system = new StringBuilder(SYSTEM_PROMPT_INTRO);
		system.append("Valid actions: ").append(actions.stream().map(MobAction::name).reduce((a, b) -> a + ", " + b).orElse(""))
			.append(". ");
		if (target != null) {
			system.append(describeActions(actions)).append(WITH_TARGET_GUIDELINES);
		} else {
			system.append("You have no target, so there is nothing to attack or flee from.");
		}

		StringBuilder user = new StringBuilder();
		user.append("Mob type: ").append(mob.getType()).append('\n');
		user.append("Your health: ").append(mob.getHealth()).append('/').append(mob.getMaxHealth())
			.append(" (").append(ownHealthLabel(mob.getHealth() / mob.getMaxHealth())).append(")\n");
		user.append("Light level where you stand: ")
			.append(mob.level().getMaxLocalRawBrightness(mob.blockPosition())).append("/15\n");
		user.append("Disposition: ").append(state.narrative().disposition()).append('\n');
		user.append("Intent: ").append(state.narrative().intent()).append('\n');
		user.append("Target description: ").append(state.narrative().targetDescription()).append('\n');
		user.append("Memory notes: ").append(state.narrative().memoryNotes()).append('\n');

		if (target != null) {
			TargetAssessment assessment = TargetAssessment.of(mob, target);
			user.append("Current target distance: ").append(mob.distanceTo(target)).append(" blocks\n");
			user.append("Current target health: ")
				.append(target.getHealth()).append('/').append(target.getMaxHealth())
				.append(" (").append(targetHealthLabel(target.getHealth() / target.getMaxHealth())).append(")\n");
			user.append(assessment.describe()).append('\n');
			user.append("Line of sight to target: ").append(assessment.lineOfSight() ? "yes" : "no").append('\n');
		} else {
			user.append("No current target.\n");
		}

		user.append(Surroundings.describe(mob, config.awarenessRadiusBlocks));
		user.append("Previous action: ").append(state.currentAction()).append('\n');
		user.append("Pick the single best action now.");

		JsonObject schema = config.structuredOutput ? actionSchema(actions) : null;
		return new PromptRequest(system.toString(), user.toString(), config.temperature, config.maxTokens, schema);
	}

	/** One sentence per offered action, so "KEEP_DISTANCE" means something to a small model. */
	private static String describeActions(List<MobAction> actions) {
		StringBuilder out = new StringBuilder();
		for (MobAction action : actions) {
			out.append(switch (action) {
				case ATTACK -> "ATTACK engages with your normal attack (melee, bow or explosion). ";
				case FLEE -> "FLEE runs away from the target. ";
				case APPROACH -> "APPROACH moves closer without attacking. ";
				case GUARD -> "GUARD holds your position and faces the target. ";
				case KEEP_DISTANCE -> "KEEP_DISTANCE backs away if the target gets close, then shoots from range. ";
				default -> "";
			});
		}
		return out.toString();
	}

	/** {"action": one of the valid actions}, nothing else. */
	private static JsonObject actionSchema(List<MobAction> actions) {
		JsonArray names = new JsonArray();
		actions.forEach(action -> names.add(action.name()));

		JsonObject action = new JsonObject();
		action.addProperty("type", "string");
		action.add("enum", names);

		JsonObject properties = new JsonObject();
		properties.add("action", action);

		JsonArray required = new JsonArray();
		required.add("action");

		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");
		schema.add("properties", properties);
		schema.add("required", required);
		schema.addProperty("additionalProperties", false);
		return schema;
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
