package com.quitemax.environmentai.goal;

import com.quitemax.environmentai.ai.action.MobAction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Moves toward the mob's current target without attacking it. */
public class ApproachActionGoal extends AbstractSystem1Goal {
	private static final double SPEED_MODIFIER = 1.0D;

	public ApproachActionGoal(Mob mob) {
		super(mob, MobAction.APPROACH);
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
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
		approachTarget();
	}

	@Override
	public void tick() {
		mob.getLookControl().setLookAt(mob.getTarget());
		if (mob.getNavigation().isDone()) {
			approachTarget();
		}
	}

	private void approachTarget() {
		LivingEntity target = mob.getTarget();
		if (target != null) {
			mob.getNavigation().moveTo(target, SPEED_MODIFIER);
		}
	}
}
