package com.quitemax.environmentai.goal;

import com.quitemax.environmentai.ai.action.MobAction;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.RangedAttackMob;
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
		return super.canUse() && canMelee() && mob.getTarget() != null && delegate.canUse();
	}

	@Override
	public boolean canContinueToUse() {
		return super.canContinueToUse() && canMelee() && delegate.canContinueToUse();
	}

	/**
	 * ATTACK is a melee chase-and-hit, so it only engages for mobs that actually melee. Without the
	 * attribute check it would throw on creepers and witches, which have no ATTACK_DAMAGE attribute
	 * ({@code Mob.doHurtTarget} reads it unconditionally). Ranged mobs are left to their own
	 * vanilla attack goals instead of being turned into melee rushers. When this returns false the
	 * vanilla goals simply run, exactly as for IDLE.
	 */
	private boolean canMelee() {
		return mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)
			&& !(mob instanceof RangedAttackMob);
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
