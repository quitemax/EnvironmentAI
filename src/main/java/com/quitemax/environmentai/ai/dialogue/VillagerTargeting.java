package com.quitemax.environmentai.ai.dialogue;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.Optional;

/** Which villager is the player actually talking to: the one they are looking at, nearby, in view. */
final class VillagerTargeting {
	/** About 20 degrees either side of the crosshair. */
	private static final double MIN_ALIGNMENT = 0.94;

	private VillagerTargeting() {
	}

	static Optional<Villager> lookedAt(ServerPlayer player, double range) {
		Vec3 eye = player.getEyePosition();
		Vec3 look = player.getLookAngle();

		return player.level()
			.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(range), Villager::isAlive)
			.stream()
			.filter(villager -> player.distanceTo(villager) <= range)
			.filter(player::hasLineOfSight)
			.filter(villager -> alignment(eye, look, villager) >= MIN_ALIGNMENT)
			.max(Comparator.comparingDouble(villager -> alignment(eye, look, villager)));
	}

	/** Cosine of the angle between the look direction and the villager's centre. */
	private static double alignment(Vec3 eye, Vec3 look, Villager villager) {
		Vec3 toVillager = villager.getBoundingBox().getCenter().subtract(eye);
		if (toVillager.lengthSqr() < 1.0E-6) {
			return 1.0;
		}
		return toVillager.normalize().dot(look);
	}
}
