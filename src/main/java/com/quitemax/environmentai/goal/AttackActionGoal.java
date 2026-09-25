package com.quitemax.environmentai.goal;

import com.quitemax.environmentai.ai.action.MobAction;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/** Delegates to vanilla's melee attack goal (cooldowns, swing, damage) against the mob's current target. */
public class AttackActionGoal extends AbstractSystem1Goal {
	private final MeleeAttackGoal delegate;

	public AttackActionGoal(PathfinderMob mob) {
		super(mob, MobAction.ATTACK);
		this.delegate = new MeleeAttackGoal(mob, 1.0D, true);
		setFlags(delegate.getFlags());
	}

	@Override
	public boolean canUse() {
		return super.canUse() && mob.getTarget() != null && delegate.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		return super.canContinueToUse() && delegate.canContinueToUse();
	}

	@Override
	public void start() {
		delegate.start();
	}

	@Override
	public void stop() {
		delegate.stop();
	}

	@Override
	public void tick() {
		delegate.tick();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return delegate.requiresUpdateEveryTick();
	}
}
