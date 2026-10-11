package com.quitemax.environmentai.ai.prompt;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Describes the creatures around a mob for a prompt: what they are and how far away, nearest
 * first. Types only: player usernames are deliberately never included, since prompts may be sent
 * to a third-party cloud provider.
 */
final class Surroundings {
	/** Keeps the prompt short for small models; the nearest creatures are what matter. */
	private static final int MAX_LISTED = 6;

	private Surroundings() {
	}

	/**
	 * One line, e.g. {@code Nearby creatures (within 12 blocks): player 3.2 blocks (your target);
	 * zombie 5.0 blocks (fellow hostile); villager 8.1 blocks.}
	 * Call on the server thread.
	 */
	static String describe(Mob mob, double radius) {
		if (radius <= 0) {
			return "";
		}

		LivingEntity target = mob.getTarget();
		List<LivingEntity> nearby = mob.level()
			.getEntitiesOfClass(LivingEntity.class, mob.getBoundingBox().inflate(radius), entity ->
				entity != mob
					&& entity.isAlive()
					&& !(entity instanceof ArmorStand)
					&& !(entity instanceof Player player && player.isSpectator())
					&& mob.distanceTo(entity) <= radius)
			.stream()
			.sorted(Comparator.comparingDouble(mob::distanceTo))
			.toList();

		String header = "Nearby creatures (within " + Math.round(radius) + " blocks): ";
		if (nearby.isEmpty()) {
			return header + "none.\n";
		}

		StringBuilder line = new StringBuilder(header);
		int listed = Math.min(MAX_LISTED, nearby.size());
		for (int i = 0; i < listed; i++) {
			LivingEntity entity = nearby.get(i);
			if (i > 0) {
				line.append("; ");
			}
			line.append(name(entity)).append(' ')
				// Locale.ROOT: the default locale prints "3,2", which a model reads as a list separator.
				.append(String.format(Locale.ROOT, "%.1f", mob.distanceTo(entity))).append(" blocks");
			if (entity == target) {
				line.append(" (your target)");
			} else if (entity instanceof Enemy) {
				line.append(" (fellow hostile)");
			}
		}
		if (nearby.size() > listed) {
			line.append("; and ").append(nearby.size() - listed).append(" more");
		}
		return line.append(".\n").toString();
	}

	private static String name(LivingEntity entity) {
		return EntityType.getKey(entity.getType()).getPath().replace('_', ' ');
	}
}
