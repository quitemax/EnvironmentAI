package com.quitemax.environmentai.config;

/**
 * The narrator's voice. Chosen per world and changeable at any time; it only changes how a fact is
 * phrased, never which facts are narrated or what the director does.
 */
public enum NarratorStyle {
	NOIR("a hard-boiled noir detective narrating in a comic panel caption: terse, world-weary, "
		+ "rain-slicked metaphors, dry understatement"),
	PULP("a breathless pulp-adventure narrator in a comic panel caption: dramatic, exclamatory, "
		+ "cliffhanger energy"),
	DEADPAN("a deadpan, matter-of-fact narrator in a comic panel caption: flat, understated, "
		+ "mildly sarcastic"),
	DOCUMENTARY("a calm nature-documentary narrator in a comic panel caption: hushed, observational, "
		+ "treating the player as a creature in its habitat");

	private final String persona;

	NarratorStyle(String persona) {
		this.persona = persona;
	}

	/** The instruction describing this voice, for the prompt. */
	public String persona() {
		return persona;
	}

	/** Parses a command argument, ignoring case; null if unknown. */
	public static NarratorStyle parse(String name) {
		for (NarratorStyle style : values()) {
			if (style.name().equalsIgnoreCase(name)) {
				return style;
			}
		}
		return null;
	}
}
