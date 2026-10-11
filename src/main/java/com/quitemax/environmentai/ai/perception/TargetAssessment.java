package com.quitemax.environmentai.ai.perception;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

import java.util.Locale;

/**
 * What a mob can tell about its target at a glance: how well armoured it is, what it is holding,
 * whether it can be seen. Reduced to a single 0-10 threat score so a rule can compare it to a
 * mob's nerve, and to plain words so a model can read it. Facts only: nothing here is a name.
 *
 * @param armor       the target's armour points (0-20)
 * @param weapon      human-readable held item, e.g. "diamond sword", or "bare hands"
 * @param weaponTier  0 (nothing useful) to 5 (netherite)
 * @param threat      armour/4 + weaponTier, 0-10
 * @param lineOfSight whether the mob can actually see the target
 */
public record TargetAssessment(int armor, String weapon, int weaponTier, double threat, boolean lineOfSight) {

	public static TargetAssessment of(Mob mob, LivingEntity target) {
		int armor = target.getArmorValue();
		ItemStack held = target.getMainHandItem();
		String path = held.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(held.getItem()).getPath();

		int tier = weaponTier(path);
		String weapon = held.isEmpty() ? "bare hands" : path.replace('_', ' ');
		double threat = Math.min(10.0, armor / 4.0 + tier);
		return new TargetAssessment(armor, weapon, tier, threat, mob.hasLineOfSight(target));
	}

	/** "minimal", "low", "moderate", "high" or "extreme". */
	public String threatLabel() {
		if (threat < 2.0) {
			return "minimal";
		}
		if (threat < 4.0) {
			return "low";
		}
		if (threat < 6.0) {
			return "moderate";
		}
		if (threat < 8.0) {
			return "high";
		}
		return "extreme";
	}

	/** One prompt line, e.g. {@code Target gear: armor 18/20, holding diamond sword (threat: high)}. */
	public String describe() {
		return String.format(
			Locale.ROOT, "Target gear: armor %d/20, holding %s (threat: %s)", armor, weapon, threatLabel()
		);
	}

	/**
	 * 0 for anything that is not a weapon (a held pickaxe or torch is not a threat), otherwise the
	 * material tier. Matched on the registry path so modded and future tool materials degrade to
	 * "unknown" (tier 0) rather than failing.
	 */
	static int weaponTier(String path) {
		if (path.isEmpty()) {
			return 0;
		}
		if (path.equals("bow") || path.equals("crossbow")) {
			return 3;
		}
		if (path.equals("trident")) {
			return 3;
		}
		if (path.equals("mace")) {
			return 4;
		}
		boolean weapon = path.endsWith("_sword") || path.endsWith("_axe") || path.endsWith("_spear");
		if (!weapon) {
			return 0;
		}
		if (path.startsWith("netherite")) {
			return 5;
		}
		if (path.startsWith("diamond")) {
			return 4;
		}
		if (path.startsWith("iron")) {
			return 3;
		}
		if (path.startsWith("copper") || path.startsWith("stone")) {
			return 2;
		}
		return 1; // wooden, golden, or an unknown material
	}
}
