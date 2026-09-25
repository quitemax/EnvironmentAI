package com.quitemax.environmentai.goal;

import com.quitemax.environmentai.ai.action.MobAction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/** Moves directly away from the mob's current target. */
public class FleeActionGoal extends AbstractSystem1Goal {
	private static final double SPEED_MODIFIER = 1.2D;
	private static final double FLEE_DISTANCE = 12.0D;

	public FleeActionGoal(Mob mob) {
		super(mob, MobAction.FLEE);
		setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		return super.canUse() && mob.getTarget() != null;
	}

	@Override
	public boolean canContinueToUse() {
		return super.canContinueToUse() && mob.getTarget() != null;
	}

	@Override
	public void start() {
		fleeFromTarget();
	}

	@Override
	public void tick() {
		if (mob.getNavigation().isDone()) {
			fleeFromTarget();
		}
	}

	private void fleeFromTarget() {
		LivingEntity target = mob.getTarget();
		if (target == null) {
			return;
		}

		Vec3 away = mob.position().subtract(target.position());
		if (away.lengthSqr() < 1.0E-4) {
			away = new Vec3(mob.getRandom().nextDouble() - 0.5, 0.0, mob.getRandom().nextDouble() - 0.5);
		}

		Vec3 fleeTarget = mob.position().add(away.normalize().scale(FLEE_DISTANCE));
		mob.getNavigation().moveTo(fleeTarget.x, fleeTarget.y, fleeTarget.z, SPEED_MODIFIER);
	}
}
