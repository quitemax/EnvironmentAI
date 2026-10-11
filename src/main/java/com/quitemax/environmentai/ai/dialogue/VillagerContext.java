package com.quitemax.environmentai.ai.dialogue;

import com.quitemax.environmentai.ai.perception.TargetAssessment;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerData;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * What a villager can truthfully say about its own situation, as plain facts for a prompt. The
 * model is told to use only these, so everything it may "know" is listed here and nothing else.
 * No usernames: this prompt can go to a cloud provider once the dialogue tier is moved there.
 */
public final class VillagerContext {
	private static final double DANGER_RADIUS = 16.0;
	private static final double GOLEM_RADIUS = 24.0;
	private static final int MAX_OFFERS_LISTED = 3;

	private VillagerContext() {
	}

	/** "librarian", "farmer"... or "villager" for the unemployed. */
	public static String professionName(Villager villager) {
		String profession = villager.getVillagerData().profession().unwrapKey()
			.map(key -> key.identifier().getPath()).orElse("none");
		return profession.equals("none") ? "villager" : words(profession);
	}

	/** Multi-line facts block for the dialogue prompt. */
	public static String describe(Villager villager, ServerPlayer player) {
		VillagerData data = villager.getVillagerData();
		StringBuilder out = new StringBuilder();

		String profession = professionName(villager);
		out.append("You are a ").append(profession);
		if (!profession.equals("villager") && !profession.equals("nitwit")) {
			out.append(" (level ").append(data.level()).append(" of 5)");
		}
		out.append(".\n");

		out.append("Your village's style: ")
			.append(words(data.type().unwrapKey().map(key -> key.identifier().getPath()).orElse("plains")))
			.append(".\n");

		out.append("What you are doing right now: ").append(activity(villager)).append(".\n");
		out.append("Time and weather: ").append(timeOfDay(villager)).append(", ")
			.append(villager.level().isThundering() ? "thunderstorm"
				: villager.level().isRaining() ? "raining" : "clear sky").append(".\n");
		out.append(String.format(
			Locale.ROOT, "Your health: %.0f of %.0f.\n", villager.getHealth(), villager.getMaxHealth()
		));

		out.append("Danger nearby: ").append(danger(villager)).append(".\n");
		out.append("Iron golem nearby: ").append(golemNearby(villager) ? "yes" : "no").append(".\n");

		out.append("How you feel about this traveler: ").append(feeling(villager, player)).append(".\n");
		out.append("The traveler: ").append(
			TargetAssessment.of(villager, player).describe().replace("Target gear: ", "")
		).append(".\n");

		String offers = offers(villager);
		if (!offers.isEmpty()) {
			out.append("Things you trade: ").append(offers).append(".\n");
		}
		return out.toString();
	}

	/** One line for {@code /environmentai inspect}; {@code viewer} may be null (console). */
	public static String inspect(Villager villager, ServerPlayer viewer) {
		VillagerData data = villager.getVillagerData();
		String reputation = viewer == null
			? "n/a"
			: villager.getGossips().getReputation(viewer.getUUID(), type -> true)
				+ " (" + feeling(villager, viewer) + ")";
		return String.format(
			Locale.ROOT, "%s level %d | activity=%s | reputation toward you=%s | gossip entries=%d",
			professionName(villager), data.level(), activity(villager), reputation,
			villager.getGossips().getGossipEntries().size()
		);
	}

	private static String activity(Villager villager) {
		if (villager.isSleeping()) {
			return "sleeping";
		}
		Optional<Activity> active = villager.getBrain().getActiveNonCoreActivity();
		return active.map(activity -> switch (activity.getName()) {
			case "work" -> "working";
			case "rest" -> "resting at home";
			case "meet" -> "gathering with other villagers";
			case "idle" -> "idling about";
			case "play" -> "playing";
			case "panic" -> "in a panic";
			case "hide" -> "hiding indoors";
			case "pre_raid", "raid" -> "bracing for a raid";
			case "celebrate" -> "celebrating";
			default -> activity.getName();
		}).orElse("going about the day");
	}

	/** Vanilla day time: 0 is sunrise, 6000 noon, 12000 sunset, 18000 midnight. */
	private static String timeOfDay(Villager villager) {
		long time = Math.floorMod(villager.level().getOverworldClockTime(), 24_000L);
		if (time < 1_000) {
			return "early morning";
		}
		if (time < 6_000) {
			return "morning";
		}
		if (time < 9_000) {
			return "midday";
		}
		if (time < 12_000) {
			return "afternoon";
		}
		if (time < 14_000) {
			return "evening";
		}
		return "night";
	}

	private static String danger(Villager villager) {
		List<LivingEntity> hostiles = villager.level()
			.getEntitiesOfClass(LivingEntity.class, villager.getBoundingBox().inflate(DANGER_RADIUS),
				entity -> entity instanceof Enemy && entity.isAlive() && villager.distanceTo(entity) <= DANGER_RADIUS)
			.stream()
			.sorted(Comparator.comparingDouble(villager::distanceTo))
			.toList();
		if (hostiles.isEmpty()) {
			return "none";
		}
		LivingEntity nearest = hostiles.get(0);
		return String.format(
			Locale.ROOT, "%d hostile creature%s within %.0f blocks, the nearest a %s about %.0f blocks away",
			hostiles.size(), hostiles.size() == 1 ? "" : "s", DANGER_RADIUS,
			words(EntityType.getKey(nearest.getType()).getPath()), villager.distanceTo(nearest)
		);
	}

	private static boolean golemNearby(Villager villager) {
		return !villager.level()
			.getEntitiesOfClass(LivingEntity.class, villager.getBoundingBox().inflate(GOLEM_RADIUS),
				entity -> entity.isAlive()
					&& EntityType.getKey(entity.getType()).getPath().equals("iron_golem"))
			.isEmpty();
	}

	/**
	 * From vanilla's own gossip, so what a villager says matches what it charges: the same number
	 * that discounts or raises prices.
	 */
	static String feeling(Villager villager, ServerPlayer player) {
		int reputation = villager.getGossips().getReputation(player.getUUID(), type -> true);
		if (reputation >= 50) {
			return "you adore them";
		}
		if (reputation >= 10) {
			return "you like them";
		}
		if (reputation > -10) {
			return "you have no strong feelings about them yet";
		}
		if (reputation > -50) {
			return "you distrust them";
		}
		return "you despise them";
	}

	private static String offers(Villager villager) {
		MerchantOffers offers = villager.getOffers();
		if (offers.isEmpty()) {
			return "";
		}
		StringBuilder out = new StringBuilder();
		int listed = 0;
		for (MerchantOffer offer : offers) {
			if (listed == MAX_OFFERS_LISTED) {
				break;
			}
			if (listed > 0) {
				out.append(", ");
			}
			out.append(offer.getResult().getCount()).append(' ')
				.append(words(BuiltInRegistries.ITEM.getKey(offer.getResult().getItem()).getPath()));
			listed++;
		}
		return out.toString();
	}

	/** "iron_golem" becomes "iron golem". */
	private static String words(String registryPath) {
		return registryPath.replace('_', ' ');
	}
}
