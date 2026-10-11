package com.quitemax.environmentai.mixin;

import com.quitemax.environmentai.ai.villager.VillagerMoodService;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.village.ReputationEventType;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla funnels every villager-relevant happening (trade, a villager hurt or killed, the golem
 * killed, a zombie villager cured) through {@code onReputationEventFrom}, and there is no Fabric
 * event for it. {@code Villager} declares the method itself, so unlike the earlier goal hook this
 * target exists on exactly the class being injected into.
 */
@Mixin(Villager.class)
public abstract class VillagerReputationMixin {
	@Inject(method = "onReputationEventFrom", at = @At("HEAD"))
	private void environmentai$onReputationEvent(ReputationEventType type, Entity source, CallbackInfo ci) {
		VillagerMoodService.onReputationEvent((Villager) (Object) this, type, source);
	}
}
