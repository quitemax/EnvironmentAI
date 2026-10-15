package com.quitemax.environmentai.config;

/**
 * The shape of the story the narrator is telling, chosen per world and changeable at any time.
 * {@link NarratorStyle} is the narrator's <em>voice</em>; this is what the story is <em>about</em>.
 * It colours how facts are framed and where the run is said to stand, and never changes which facts
 * are narrated, what the director does, or anything in the world.
 *
 * <p>The run is four acts plus an epilogue (see {@code Director.actIndex}); each arc says what each
 * act means to the person in it.
 */
public enum StoryArc {
	/** No overarching story: captions comment on what happened, as before. */
	STANDARD("", new String[0]),
	ZERO_TO_HERO(
		"The story being told is an underdog's rise, from zero to hero: someone nobody noticed, "
			+ "becoming someone the world cannot ignore. Small wins matter; each setback is part of "
			+ "the climb. Let the telling grow in confidence as the run goes on.",
		new String[] {
			"Act one: a nobody with bare hands and nothing proven, still at the very bottom.",
			"Act two: no longer a nobody, forged by hard work in the dark and learning fast.",
			"Act three: the underdog has become a name others would whisper, and is now tested.",
			"Act four: the whole climb comes down to one last, impossible fight.",
			"Epilogue: the nobody is a legend now, and the telling can say so."
		}),
	CHOSEN_ONE(
		"The story being told is that of the chosen one: someone the world has been waiting for, "
			+ "whether or not they know it. There are omens, a destiny, and an ending that was always "
			+ "going to find them. Let the telling treat events as signs, never as luck.",
		new String[] {
			"Act one: the chosen one does not yet know it; the signs are small and easy to miss.",
			"Act two: destiny pulls them down into the deep, whether they asked or not.",
			"Act three: the omens grow plain, and the other side of the world takes notice.",
			"Act four: the end they were always meant for is waiting, and it knows their name.",
			"Epilogue: the prophecy is fulfilled, and the world is quieter for it."
		});

	private final String premise;
	private final String[] acts;

	StoryArc(String premise, String[] acts) {
		this.premise = premise;
		this.acts = acts;
	}

	public boolean active() {
		return this != STANDARD;
	}

	/** The instruction describing this story, for the prompt; empty for {@link #STANDARD}. */
	public String premise() {
		return premise;
	}

	/** Where the run stands in this story, given {@code Director.actIndex}; empty for {@link #STANDARD}. */
	public String framing(int actIndex) {
		if (acts.length == 0) {
			return "";
		}
		return acts[Math.max(0, Math.min(acts.length - 1, actIndex))];
	}

	/** Parses a command argument, ignoring case and treating '-' as '_'; null if unknown. */
	public static StoryArc parse(String name) {
		String wanted = name.replace('-', '_');
		for (StoryArc arc : values()) {
			if (arc.name().equalsIgnoreCase(wanted)) {
				return arc;
			}
		}
		return null;
	}
}
