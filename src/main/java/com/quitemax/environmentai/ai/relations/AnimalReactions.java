package com.quitemax.environmentai.ai.relations;

import com.quitemax.environmentai.goal.ThreatAvoidanceGoal;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;

/**
 * Fits skittish farm animals with {@link ThreatAvoidanceGoal} as they load. Installed on entity
 * load for the same reason as the System 1 goals: an animal's own {@code registerGoals} does not
 * call a hook we could rely on. Idempotent, since an entity can load into a level more than once.
 */
public final class AnimalReactions {
	/** Below the animals' own panic (1) and above breeding and wandering. */
	private static final int PRIORITY = 2;

	private AnimalReactions() {
	}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (!(entity instanceof PathfinderMob animal)) {
				return;
			}
			if (!Relations.isSkittishAnimal(EntityType.getKey(animal.getType()).getPath())) {
				return;
			}
			boolean installed = animal.getGoalSelector().getAvailableGoals().stream()
				.anyMatch(wrapped -> wrapped.getGoal() instanceof ThreatAvoidanceGoal);
			if (!installed) {
				animal.getGoalSelector().addGoal(PRIORITY, new ThreatAvoidanceGoal(animal));
			}
		});
	}
}
