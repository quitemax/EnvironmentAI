package com.quitemax.environmentai.goal;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;

/**
 * Adds the System 1 action goals to a mob's goal selector.
 *
 * <p>This runs from the entity-load event rather than from a mixin on {@code Mob.registerGoals}:
 * {@code Zombie}, {@code Spider}, {@code AbstractSkeleton} and others override
 * {@code registerGoals} without calling {@code super}, so a hook on the base method never fires for
 * them and the goals were silently never installed. Idempotent, since an entity can be loaded into
 * a level more than once over its lifetime.
 *
 * <p>All six share the highest priority. {@link AbstractSystem1Goal#canUse()} gates each on the
 * mob's current action, so at most one is eligible at a time, and that priority preempts the
 * lower-precedence vanilla movement goals the subclass registered. Vanilla's target selector is
 * untouched, so target acquisition still happens normally and just feeds these goals.
 */
public final class SystemGoals {
	private static final int PRIORITY = 0;

	private SystemGoals() {
	}

	public static void install(PathfinderMob mob) {
		GoalSelector selector = mob.getGoalSelector();
		boolean installed = selector.getAvailableGoals().stream()
			.anyMatch(wrapped -> wrapped.getGoal() instanceof AbstractSystem1Goal);
		if (installed) {
			return;
		}

		selector.addGoal(PRIORITY, new AttackActionGoal(mob));
		selector.addGoal(PRIORITY, new FleeActionGoal(mob));
		selector.addGoal(PRIORITY, new KeepDistanceActionGoal(mob));
		selector.addGoal(PRIORITY, new ApproachActionGoal(mob));
		selector.addGoal(PRIORITY, new GuardActionGoal(mob));
		selector.addGoal(PRIORITY, new WanderActionGoal(mob));
		selector.addGoal(PRIORITY, new IdleActionGoal(mob));
	}
}
