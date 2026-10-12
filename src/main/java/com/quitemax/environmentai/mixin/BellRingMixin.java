package com.quitemax.environmentai.mixin;

import com.quitemax.environmentai.ai.villager.VillagerMoodService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BellBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * There is no Fabric event for a bell being rung. Every ring, by hand, projectile or redstone, goes
 * through the four-argument {@code attemptToRing} (the three-argument one delegates to it with no
 * ringer), and it returns whether the bell really rang. The descriptor is spelled out because the
 * method is overloaded.
 */
@Mixin(BellBlock.class)
public abstract class BellRingMixin {
	@Inject(
		method = "attemptToRing(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/level/Level;"
			+ "Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction;)Z",
		at = @At("RETURN")
	)
	private void environmentai$onRung(
		Entity ringer, Level level, BlockPos pos, Direction direction, CallbackInfoReturnable<Boolean> cir
	) {
		if (cir.getReturnValueZ() && level instanceof ServerLevel serverLevel) {
			VillagerMoodService.onBellRung(serverLevel, pos, ringer);
		}
	}
}
