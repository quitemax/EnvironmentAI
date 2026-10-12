package com.quitemax.environmentai.goal;

import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.action.MobAction;
import com.quitemax.environmentai.ai.squad.SquadCoordinator;
import com.quitemax.environmentai.ai.squad.SquadPlanner.Assignment;
import com.quitemax.environmentai.ai.squad.SquadPlanner.Role;
import com.quitemax.environmentai.ai.state.ModAttachments;
import com.quitemax.environmentai.config.EnvironmentAIConfig;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * Walks a squad member to the point its squad plan gave it (a flank, a ring to hold, a spot to
 * shoot from), then lets the normal fight take over. It sits one priority above the System 1
 * goals so it can take the move flag from an attack that has not yet closed.
 *
 * <p>It only borrows the mob while its action is one that means "go for the target" (attack,
 * approach, or idle, where vanilla runs the chase), never from a mob that is fleeing or kiting. It
 * gives up on a timer and then stays off for a moment, so a blocked path costs seconds, never a
 * stuck mob.
 */
public class FlankGoal extends Goal {
	private static final double SPEED = 1.1;
	private static final double ARRIVED = 2.5;
	private static final double ENGAGED = 3.5;
	private static final double HOLD_NEAREST = 5.5;
	private static final double HOLD_FARTHEST = 20.0;
	private static final long COOLDOWN_TICKS = 40;
	private static final long HOLD_COOLDOWN_TICKS = 200;
	private static final int REPATH_TICKS = 10;

	private final Mob mob;
	private long startedAt;
	private long coolDownUntil;
	private Role runningRole;

	public FlankGoal(Mob mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	private Assignment assignment() {
		EnvironmentAIConfig config = EnvironmentAI.config();
		if (config == null || !config.squadTactics) {
			return null;
		}
		return SquadCoordinator.assignment(mob.getUUID());
	}

	private boolean actionAllows() {
		MobAction action = mob.getAttachedOrCreate(ModAttachments.MOB_AI_STATE).currentAction();
		return action == MobAction.ATTACK || action == MobAction.APPROACH || action == MobAction.IDLE;
	}

	private boolean wanted(Assignment assignment) {
		LivingEntity target = mob.getTarget();
		if (assignment == null || target == null || !actionAllows()) {
			return false;
		}
		double toTarget = mob.distanceTo(target);
		return switch (assignment.role()) {
			case PIN -> false;
			case FLANK, SPREAD -> toTarget > ENGAGED && distanceToPoint(assignment) > ARRIVED;
			case HOLD -> toTarget > HOLD_NEAREST && toTarget <= HOLD_FARTHEST;
		};
	}

	private double distanceToPoint(Assignment assignment) {
		return Math.hypot(mob.getX() - assignment.x(), mob.getZ() - assignment.z());
	}

	@Override
	public boolean canUse() {
		long now = mob.level().getGameTime();
		return now >= coolDownUntil && wanted(assignment());
	}

	@Override
	public boolean canContinueToUse() {
		Assignment assignment = assignment();
		if (assignment == null || assignment.role() != runningRole) {
			return false;
		}
		return mob.level().getGameTime() - startedAt <= limit(runningRole) && wanted(assignment);
	}

	@Override
	public void start() {
		Assignment assignment = assignment();
		runningRole = assignment == null ? null : assignment.role();
		startedAt = mob.level().getGameTime();
		if (assignment != null && runningRole != Role.HOLD) {
			moveToPoint(assignment);
		} else {
			mob.getNavigation().stop();
		}
	}

	@Override
	public void tick() {
		LivingEntity target = mob.getTarget();
		if (target != null) {
			mob.getLookControl().setLookAt(target);
		}
		Assignment assignment = assignment();
		if (assignment == null || runningRole == Role.HOLD) {
			mob.getNavigation().stop();
			return;
		}
		if ((mob.level().getGameTime() - startedAt) % REPATH_TICKS == 0 || mob.getNavigation().isDone()) {
			moveToPoint(assignment);
		}
	}

	@Override
	public void stop() {
		long now = mob.level().getGameTime();
		coolDownUntil = now + (runningRole == Role.HOLD ? HOLD_COOLDOWN_TICKS : COOLDOWN_TICKS);
		runningRole = null;
		mob.getNavigation().stop();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	private void moveToPoint(Assignment assignment) {
		LivingEntity target = mob.getTarget();
		double y = target != null ? target.getY() : mob.getY();
		mob.getNavigation().moveTo(assignment.x(), y, assignment.z(), SPEED);
	}

	private static int limit(Role role) {
		return switch (role) {
			case HOLD -> 160;
			case SPREAD -> 100;
			default -> 80;
		};
	}
}
