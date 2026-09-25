package com.quitemax.environmentai.goal;

import com.quitemax.environmentai.ai.action.MobAction;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;

/** Delegates to vanilla's random-stroll goal. */
public class WanderActionGoal extends AbstractSystem1Goal {
	private final WaterAvoidingRandomStrollGoal delegate;

	public WanderActionGoal(PathfinderMob mob) {
		super(mob, MobAction.WANDER);
		this.delegate = new WaterAvoidingRandomStrollGoal(mob, 1.0D);
		setFlags(delegate.getFlags());
	}

	@Override
	public boolean canUse() {
		return super.canUse() && delegate.canUse();
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
