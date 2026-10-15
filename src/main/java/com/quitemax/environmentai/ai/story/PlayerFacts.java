package com.quitemax.environmentai.ai.story;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the narrator can see of a player right now: how far they have travelled, their level, where
 * they are, what they wear, hold and carry. Read from the game's own statistics and inventory, so it
 * is observation only and costs nothing. Names are never included: this text may go to a cloud model.
 *
 * <p>Built on the server thread once per scan; {@link #describe()} is what a prompt receives.
 */
public record PlayerFacts(
	int level, String biome, int y, boolean night, boolean raining, boolean thundering, boolean overworld,
	long blocksTravelled, long kills, long deaths, long days,
	String held, int bestArmorTier, int piecesWorn, Map<String, Integer> carried, Map<String, String> notable
) {
	/** Items counted by name in "carrying", so the list stays short and means something. */
	private static final Map<String, String> COUNTED = Map.ofEntries(
		Map.entry("diamond", "diamonds"), Map.entry("iron_ingot", "iron ingots"),
		Map.entry("gold_ingot", "gold ingots"), Map.entry("emerald", "emeralds"),
		Map.entry("coal", "coal"), Map.entry("bread", "bread"), Map.entry("cooked_beef", "steaks"),
		Map.entry("torch", "torches"), Map.entry("ender_pearl", "ender pearls"),
		Map.entry("obsidian", "obsidian"), Map.entry("blaze_rod", "blaze rods")
	);

	public static PlayerFacts of(ServerPlayer player, ServerLevel level) {
		BlockPos pos = player.blockPosition();
		String dimension = level.dimension().identifier().getPath();
		boolean overworld = dimension.equals("overworld");
		boolean night = overworld && Math.floorMod(level.getOverworldClockTime(), 24_000L) >= 13_000L;
		boolean skyVisible = level.canSeeSky(pos);

		String biome = level.getBiome(pos).unwrapKey()
			.map(key -> key.identifier().getPath().replace('_', ' ')).orElse("");

		var stats = player.getStats();
		long cm = 0;
		for (var id : new net.minecraft.resources.Identifier[] {
			Stats.WALK_ONE_CM, Stats.SPRINT_ONE_CM, Stats.CROUCH_ONE_CM, Stats.SWIM_ONE_CM,
			Stats.WALK_ON_WATER_ONE_CM, Stats.WALK_UNDER_WATER_ONE_CM, Stats.FLY_ONE_CM,
			Stats.AVIATE_ONE_CM, Stats.BOAT_ONE_CM, Stats.HORSE_ONE_CM, Stats.MINECART_ONE_CM,
			Stats.CLIMB_ONE_CM
		}) {
			cm += stats.getValue(Stats.CUSTOM.get(id));
		}

		ItemStack heldStack = player.getMainHandItem();
		String held = heldStack.isEmpty() ? "" : path(heldStack).replace('_', ' ');

		int best = 0;
		int worn = 0;
		for (EquipmentSlot slot : new EquipmentSlot[] {
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
		}) {
			ItemStack piece = player.getItemBySlot(slot);
			if (!piece.isEmpty()) {
				worn++;
				best = Math.max(best, Milestones.armorTier(path(piece)));
			}
		}

		Map<String, Integer> carried = new LinkedHashMap<>();
		Map<String, String> notable = new LinkedHashMap<>();
		var inventory = player.getInventory();
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.isEmpty()) {
				continue;
			}
			String itemPath = path(stack);
			String counted = COUNTED.get(itemPath);
			if (counted != null) {
				carried.merge(counted, stack.getCount(), Integer::sum);
			}
			String label = Milestones.notableItem(itemPath);
			if (label != null) {
				notable.putIfAbsent(itemPath, label);
			}
		}

		return new PlayerFacts(
			player.experienceLevel, biome, pos.getY(), night,
			overworld && level.isRaining() && skyVisible, overworld && level.isThundering() && skyVisible,
			overworld, cm / 100L,
			stats.getValue(Stats.CUSTOM.get(Stats.MOB_KILLS)), stats.getValue(Stats.CUSTOM.get(Stats.DEATHS)),
			level.getGameTime() / 24_000L, held, best, worn, carried, notable
		);
	}

	private static String path(ItemStack stack) {
		return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
	}

	/** Where, in words: "underground", "high on a mountain", "on the surface". */
	public String altitudeWords() {
		return switch (Milestones.altitudeBand(y)) {
			case 2 -> "far down in the deepest dark";
			case 1 -> "deep underground";
			case 3 -> "high above the lowlands";
			default -> y < 55 ? "underground" : "on the surface";
		};
	}

	/**
	 * A compact paragraph for a prompt. Only things worth saying: nothing is listed that is not
	 * true or not interesting (no "holding nothing", no zero counts).
	 */
	public String describe() {
		List<String> parts = new ArrayList<>();
		parts.add("Experience level " + level);
		if (!biome.isEmpty() && overworld) {
			parts.add("in " + (isVowel(biome) ? "an " : "a ") + biome + ", " + altitudeWords());
		} else if (!overworld) {
			parts.add("far from the overworld");
		}
		parts.add((night ? "night" : "daytime") + (thundering ? ", a thunderstorm" : raining ? ", raining" : ""));
		if (!held.isEmpty()) {
			parts.add("holding " + (isVowel(held) ? "an " : "a ") + held);
		}
		if (piecesWorn > 0) {
			parts.add("wearing " + Milestones.tierName(bestArmorTier) + " armour (" + piecesWorn + " of 4 pieces)");
		}
		if (!carried.isEmpty()) {
			List<String> items = new ArrayList<>();
			carried.forEach((name, count) -> items.add(count + " " + name));
			parts.add("carrying " + String.join(", ", items.subList(0, Math.min(4, items.size()))));
		}
		if (blocksTravelled >= 50) {
			parts.add(String.format(Locale.ROOT, "has travelled about %s", distance(blocksTravelled)));
		}
		if (kills > 0) {
			parts.add("has killed " + kills + (kills == 1 ? " creature" : " creatures"));
		}
		if (deaths > 0) {
			parts.add("has died " + deaths + (deaths == 1 ? " time" : " times"));
		}
		if (days > 0) {
			parts.add("this is day " + (days + 1));
		}
		return String.join("; ", parts) + ".";
	}

	public static String distance(long blocks) {
		return blocks >= 1_000
			? String.format(Locale.ROOT, "%.1f kilometres", blocks / 1_000.0)
			: blocks + " blocks";
	}

	private static boolean isVowel(String word) {
		return !word.isEmpty() && "aeiou".indexOf(word.charAt(0)) >= 0;
	}
}
