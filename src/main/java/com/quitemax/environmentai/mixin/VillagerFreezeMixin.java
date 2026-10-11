package com.quitemax.environmentai.mixin;

import com.quitemax.environmentai.ai.dialogue.ConversationManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A villager in a conversation must stand still. The Brain is what moves it (every behaviour
 * writes memories and the walk sink acts on them), so skipping its tick for the length of the
 * conversation holds it more cleanly than fighting it from outside every tick, which leaves a
 * visible one-step jitter. {@code customServerAiStep} is declared on {@code Villager} itself.
 */
@Mixin(Villager.class)
public abstract class VillagerFreezeMixin {
	@Inject(method = "customServerAiStep", at = @At("HEAD"), cancellable = true)
	private void environmentai$holdDuringConversation(ServerLevel level, CallbackInfo ci) {
		if (ConversationManager.isTalking(((Villager) (Object) this).getUUID())) {
			ci.cancel();
		}
	}
}
