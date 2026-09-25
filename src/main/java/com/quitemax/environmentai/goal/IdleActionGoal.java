package com.quitemax.environmentai.goal;

import com.quitemax.environmentai.ai.action.MobAction;
import net.minecraft.world.entity.Mob;

/** Does nothing — a deliberate no-op action, also the fallback when nothing else applies. */
public class IdleActionGoal extends AbstractSystem1Goal {
	public IdleActionGoal(Mob mob) {
		super(mob, MobAction.IDLE);
	}
}
