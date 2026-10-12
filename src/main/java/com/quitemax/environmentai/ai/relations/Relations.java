package com.quitemax.environmentai.ai.relations;

import java.util.Set;

/**
 * Who is afraid of whom, as plain data keyed by registry path, so it can be read and tuned in one
 * place and checked without the game. This version holds one relation: ordinary farm animals shy
 * away from hostile mobs. Predator and prey relations between animals are left to vanilla, which
 * already has wolves, foxes and cats.
 */
public final class Relations {
	/** Animals that run from a threat. Predators and the self-reliant (wolf, fox, bear) are absent. */
	private static final Set<String> SKITTISH_ANIMALS = Set.of(
		"cow", "mooshroom", "sheep", "pig", "chicken", "rabbit", "horse", "donkey", "mule", "llama",
		"goat", "camel", "turtle", "frog"
	);

	/** Hostile-class mobs that animals do not mind: they are not hunting anything nearby. */
	private static final Set<String> HARMLESS_TO_ANIMALS = Set.of("enderman", "silverfish", "endermite");

	private Relations() {
	}

	public static boolean isSkittishAnimal(String path) {
		return SKITTISH_ANIMALS.contains(path);
	}

	/** For a mob that is already known to be hostile. */
	public static boolean threatensAnimals(String path) {
		return !HARMLESS_TO_ANIMALS.contains(path);
	}
}
