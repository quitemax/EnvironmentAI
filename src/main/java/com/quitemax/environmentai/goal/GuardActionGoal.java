package com.quitemax.environmentai.goal;

import com.quitemax.environmentai.ai.action.MobAction;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/** Holds ground: stops moving and, if it has a target, keeps facing it. */
public class GuardActionGoal extends AbstractSystem1Goal {
	public GuardActionGoal(Mob mob) {
		super(mob, MobAction.GUARD);
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public void start() {
		mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (mob.getTarget() != null) {
			mob.getLookControl().setLookAt(mob.getTarget());
		}
	}
}
