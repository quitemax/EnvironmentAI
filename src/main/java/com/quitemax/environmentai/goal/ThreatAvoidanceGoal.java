package com.quitemax.environmentai.goal;

import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.relations.Relations;
import com.quitemax.environmentai.config.EnvironmentAIConfig;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Makes a farm animal move away from a hostile mob that comes within a few blocks. It is vanilla's
 * own avoid goal with a predicate and a throttle: the stock goal runs an area query every tick, which
 * is fine for a handful of cats and rabbits and not for a field of cows.
 */
public final class ThreatAvoidanceGoal extends AvoidEntityGoal<Mob> {
	private static final float DISTANCE = 8.0F;
	private static final double WALK_SPEED = 1.15;
	private static final double SPRINT_SPEED = 1.45;
	private static final int CHECK_INTERVAL = 10;

	private final PathfinderMob animal;

	public ThreatAvoidanceGoal(PathfinderMob animal) {
		super(animal, Mob.class, DISTANCE, WALK_SPEED, SPRINT_SPEED, ThreatAvoidanceGoal::isThreat);
		this.animal = animal;
	}

	private static boolean isThreat(LivingEntity other) {
		return other instanceof Enemy && other.isAlive()
			&& Relations.threatensAnimals(EntityType.getKey(other.getType()).getPath());
	}

	@Override
	public boolean canUse() {
		if ((animal.tickCount + animal.getId()) % CHECK_INTERVAL != 0) {
			return false;
		}
		EnvironmentAIConfig config = EnvironmentAI.config();
		return (config == null || config.animalsAvoidThreats) && super.canUse();
	}
}
