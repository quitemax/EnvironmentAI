package com.quitemax.environmentai.goal;

import com.quitemax.environmentai.ai.action.MobAction;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * Kiting for ranged mobs. This goal is only <em>eligible</em> while the target is closer than
 * {@code TOO_CLOSE}; it backs the mob off until {@code COMFORTABLE}. While the target is in the
 * band between, and beyond it, this goal is inactive and holds no flags, so the mob's own vanilla
 * ranged attack goal keeps running and shoots. That is the point: it must not hold the movement
 * flag while the mob should be firing, or a skeleton would stand mute.
 */
public class KeepDistanceActionGoal extends AbstractSystem1Goal {
	private static final double SPEED_MODIFIER = 1.15D;
	private static final double TOO_CLOSE = 7.0D;
	private static final double COMFORTABLE = 11.0D;
	private static final double RETREAT_STEP = 9.0D;

	public KeepDistanceActionGoal(Mob mob) {
		super(mob, MobAction.KEEP_DISTANCE);
		setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean canUse() {
		LivingEntity target = mob.getTarget();
		return actionActive() && target != null && mob.distanceTo(target) < TOO_CLOSE;
	}

	@Override
	public boolean canContinueToUse() {
		LivingEntity target = mob.getTarget();
		return actionActive() && target != null && mob.distanceTo(target) < COMFORTABLE;
	}

	@Override
	public void start() {
		backOff();
	}

	@Override
	public void tick() {
		if (mob.getNavigation().isDone()) {
			backOff();
		}
		LivingEntity target = mob.getTarget();
		if (target != null) {
			// Keep facing the target while retreating, so the shot is ready when the band is reached.
			mob.getLookControl().setLookAt(target);
		}
	}

	private void backOff() {
		LivingEntity target = mob.getTarget();
		if (target == null) {
			return;
		}

		Vec3 away = mob.position().subtract(target.position());
		if (away.lengthSqr() < 1.0E-4) {
			away = new Vec3(mob.getRandom().nextDouble() - 0.5, 0.0, mob.getRandom().nextDouble() - 0.5);
		}

		Vec3 retreat = mob.position().add(away.normalize().scale(RETREAT_STEP));
		mob.getNavigation().moveTo(retreat.x, retreat.y, retreat.z, SPEED_MODIFIER);
	}
}
