package com.quitemax.environmentai.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Frames the conversation: an over-the-shoulder shot from behind and to the right of the player,
 * looking at the villager's head. Pure geometry; {@code CameraMixin} applies the result.
 */
public final class ConversationCamera {
	/** How far behind the player's eyes, and to the side, the camera sits. */
	private static final double BEHIND = 1.7;
	private static final double ASIDE = 0.9;
	private static final double ABOVE = 0.25;
	/** Pulled in from a wall by this much so the near plane does not clip into it. */
	private static final double WALL_MARGIN = 0.15;

	public record Shot(Vec3 position, float yRot, float xRot) {
	}

	private ConversationCamera() {
	}

	/**
	 * @param vanillaPos   where the normal camera put itself this frame
	 * @return the shot to use, already blended with the normal view, or null to leave the camera alone
	 */
	public static Shot compute(Vec3 vanillaPos, float vanillaYaw, float vanillaPitch, float partialTick) {
		double blend = ConversationClient.blend();
		if (blend <= 0.0) {
			return null;
		}

		Minecraft minecraft = Minecraft.getInstance();
		ClientLevel level = minecraft.level;
		LocalPlayer player = minecraft.player;
		if (level == null || player == null) {
			return null;
		}
		Entity villager = level.getEntity(ConversationClient.villagerId());
		if (villager == null) {
			return null;
		}

		Vec3 playerEye = player.getEyePosition(partialTick);
		Vec3 villagerHead = villager.getEyePosition(partialTick);

		Vec3 flat = new Vec3(villagerHead.x - playerEye.x, 0.0, villagerHead.z - playerEye.z);
		if (flat.lengthSqr() < 1.0E-6) {
			flat = new Vec3(0.0, 0.0, 1.0);
		}
		Vec3 forward = flat.normalize();
		// Right-hand side of the player's line of sight to the villager.
		Vec3 right = new Vec3(-forward.z, 0.0, forward.x);

		Vec3 desired = playerEye
			.subtract(forward.scale(BEHIND))
			.add(right.scale(ASIDE))
			.add(0.0, ABOVE, 0.0);

		// Keep the camera on the player's side of any wall between it and the player's head.
		BlockHitResult hit = level.clip(
			new ClipContext(playerEye, desired, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player)
		);
		if (hit.getType() != HitResult.Type.MISS) {
			Vec3 inward = playerEye.subtract(hit.getLocation());
			desired = inward.lengthSqr() < 1.0E-6
				? playerEye
				: hit.getLocation().add(inward.normalize().scale(WALL_MARGIN));
		}

		Vec3 target = villagerHead.add(0.0, -0.1, 0.0);
		Vec3 look = target.subtract(desired);
		double horizontal = Math.sqrt(look.x * look.x + look.z * look.z);
		float shotYaw = (float) (Mth.atan2(-look.x, look.z) * (180.0 / Math.PI));
		float shotPitch = (float) (-Mth.atan2(look.y, horizontal) * (180.0 / Math.PI));

		Vec3 position = vanillaPos.lerp(desired, blend);
		float yaw = vanillaYaw + (float) (Mth.wrapDegrees(shotYaw - vanillaYaw) * blend);
		float pitch = vanillaPitch + (float) ((shotPitch - vanillaPitch) * blend);
		return new Shot(position, yaw, pitch);
	}
}
