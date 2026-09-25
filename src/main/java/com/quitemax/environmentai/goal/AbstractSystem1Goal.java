package com.quitemax.environmentai.goal;

import com.quitemax.environmentai.ai.action.MobAction;
import com.quitemax.environmentai.ai.state.ModAttachments;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Base for the six discrete-action goals. {@code canUse()}/{@code canContinueToUse()} simply
 * check whether this goal's action is the mob's currently active {@link MobAction} — the
 * scheduler is the only thing that changes that value, so at most one of the six ever reports
 * {@code true} at a time.
 */
public abstract class AbstractSystem1Goal extends Goal {
	protected final Mob mob;
	private final MobAction represents;

	protected AbstractSystem1Goal(Mob mob, MobAction represents) {
		this.mob = mob;
		this.represents = represents;
	}

	@Override
	public boolean canUse() {
		return mob.getAttachedOrCreate(ModAttachments.MOB_AI_STATE).currentAction() == represents;
	}

	@Override
	public boolean canContinueToUse() {
		return canUse();
	}
}
