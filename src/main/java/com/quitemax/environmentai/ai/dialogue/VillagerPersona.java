package com.quitemax.environmentai.ai.dialogue;

import net.minecraft.world.entity.npc.villager.Villager;

import java.util.UUID;

/**
 * A stable personality for each villager, derived from its UUID so it needs no storage and is the
 * same every conversation and every restart. Variety without invention: two villagers of the same
 * profession still sound different, and neither changes its character between visits.
 */
final class VillagerPersona {
	private static final String[] TEMPERAMENTS = {
		"cheerful and chatty",
		"gruff and short-tempered",
		"nervous and easily startled",
		"dry-witted and a little sarcastic",
		"gentle and patient",
		"proud of their work",
		"fond of gossip",
		"weary but kind-hearted",
	};

	private static final String[] CONCERNS = {
		"worries about zombies at night",
		"dreams of the village growing bigger",
		"is rather fond of bread",
		"distrusts strangers until they have traded a few times",
		"loves clear weather and complains about rain",
		"likes to talk about the harvest",
		"is proud of the village iron golem",
		"misses the quiet days before the village got busy",
	};

	private VillagerPersona() {
	}

	/** E.g. {@code cheerful and chatty; worries about zombies at night}. */
	static String of(Villager villager) {
		UUID id = villager.getUUID();
		long hash = id.getMostSignificantBits() * 31 + id.getLeastSignificantBits();
		// floorMod: the hash is frequently negative.
		int temperament = (int) Math.floorMod(hash, (long) TEMPERAMENTS.length);
		int concern = (int) Math.floorMod(hash / TEMPERAMENTS.length, (long) CONCERNS.length);
		return TEMPERAMENTS[temperament] + "; " + CONCERNS[concern];
	}
}
