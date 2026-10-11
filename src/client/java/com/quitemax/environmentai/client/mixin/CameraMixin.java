package com.quitemax.environmentai.client.mixin;

import com.quitemax.environmentai.client.ConversationCamera;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * {@code alignWithEntity} is where the camera takes its pose from the entity it follows, once per
 * frame. Running after it lets the conversation shot start from the normal pose and blend toward
 * the framing, which is what makes the transition smooth in both directions.
 *
 * <p>{@code detached} is set so the local player's own model is drawn: in first person it is
 * skipped, and a shot from behind the shoulder would otherwise show nobody.
 */
@Mixin(Camera.class)
public abstract class CameraMixin {
	@Shadow
	private boolean detached;

	@Shadow
	public abstract Vec3 position();

	@Shadow
	public abstract float yRot();

	@Shadow
	public abstract float xRot();

	@Shadow
	protected abstract void setPosition(Vec3 position);

	@Shadow
	protected abstract void setRotation(float yRot, float xRot);

	@Inject(method = "alignWithEntity", at = @At("TAIL"))
	private void environmentai$conversationShot(float partialTick, CallbackInfo ci) {
		ConversationCamera.Shot shot = ConversationCamera.compute(position(), yRot(), xRot(), partialTick);
		if (shot != null) {
			setPosition(shot.position());
			setRotation(shot.yRot(), shot.xRot());
			detached = true;
		}
	}
}
