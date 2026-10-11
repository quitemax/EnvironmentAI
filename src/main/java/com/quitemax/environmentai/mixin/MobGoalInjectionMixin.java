package com.quitemax.environmentai.mixin;

import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.state.ModAttachments;
import com.quitemax.environmentai.goal.ApproachActionGoal;
import com.quitemax.environmentai.goal.AttackActionGoal;
import com.quitemax.environmentai.goal.FleeActionGoal;
import com.quitemax.environmentai.goal.GuardActionGoal;
import com.quitemax.environmentai.goal.IdleActionGoal;
import com.quitemax.environmentai.goal.WanderActionGoal;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.monster.Enemy;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fires once per hostile mob, at the tail of vanilla goal registration. {@code Monster} has no
 * shared {@code registerGoals} override to hook, so this targets {@link Mob} directly and filters
 * to {@link Enemy} — which every hostile mob subclass implements — so passive/neutral mobs are
 * untouched. All six System 1 action goals are added at the same (highest) priority: since
 * {@code AbstractSystem1Goal#canUse()} gates on the mob's current action, at most one of them is
 * ever eligible at a time, and that priority preempts whatever lower-precedence vanilla movement
 * goal the subclass also registered. Vanilla's targetSelector goals are left untouched, so target
 * acquisition (e.g. spotting a player) still happens normally and just feeds these goals.
 */
@Mixin(Mob.class)
public abstract class MobGoalInjectionMixin {
	private static final int SYSTEM1_GOAL_PRIORITY = 0;

	@Shadow
	@Final
	public GoalSelector goalSelector;

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void environmentai$onRegisterGoals(CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		if (!(self instanceof Enemy) || !(self instanceof PathfinderMob pathfinderMob)) {
			return;
		}

		this.goalSelector.addGoal(SYSTEM1_GOAL_PRIORITY, new AttackActionGoal(pathfinderMob));
		this.goalSelector.addGoal(SYSTEM1_GOAL_PRIORITY, new FleeActionGoal(pathfinderMob));
		this.goalSelector.addGoal(SYSTEM1_GOAL_PRIORITY, new ApproachActionGoal(pathfinderMob));
		this.goalSelector.addGoal(SYSTEM1_GOAL_PRIORITY, new GuardActionGoal(pathfinderMob));
		this.goalSelector.addGoal(SYSTEM1_GOAL_PRIORITY, new WanderActionGoal(pathfinderMob));
		this.goalSelector.addGoal(SYSTEM1_GOAL_PRIORITY, new IdleActionGoal(pathfinderMob));

		self.getAttachedOrCreate(ModAttachments.MOB_AI_STATE);

		EnvironmentAI.LOGGER.debug(
			"Fitted System 1/2 AI to hostile mob {} ({})", self.getType(), self.getUUID()
		);
	}
}
