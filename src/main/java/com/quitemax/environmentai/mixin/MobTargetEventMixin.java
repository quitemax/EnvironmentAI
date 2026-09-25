package com.quitemax.environmentai.mixin;

import com.quitemax.environmentai.ai.scheduling.System2TriggerManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fabric API has no target-acquired/target-lost event, so this reads the mob's outgoing target
 * at the head of {@code setTarget} (before the real method body overwrites it) and diffs it
 * against the incoming value.
 */
@Mixin(Mob.class)
public abstract class MobTargetEventMixin {
	@Inject(method = "setTarget", at = @At("HEAD"))
	private void environmentai$onSetTarget(LivingEntity target, CallbackInfo ci) {
		Mob self = (Mob) (Object) this;
		LivingEntity previous = self.getTarget();
		if (previous == target) {
			return;
		}
		System2TriggerManager.onTargetChanged(self, previous, target);
	}
}
