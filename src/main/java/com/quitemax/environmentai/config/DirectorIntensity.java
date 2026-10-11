package com.quitemax.environmentai.config;

/**
 * How much the story layer is allowed to do. A player who wants to find things out for themselves
 * must be able to switch the hints off without losing the narration, and the narration off without
 * touching anything else.
 */
public enum DirectorIntensity {
	/** No captions at all. */
	OFF,
	/** The narrator comments on what happened; it never points anywhere. */
	NARRATION,
	/** Narration, plus indirect hints toward the next goal when the player has stalled. */
	HINTS,
	/** Reserved: hints plus pressure applied to the world (not built yet). */
	FULL;

	public boolean narrates() {
		return this != OFF;
	}

	public boolean hints() {
		return this == HINTS || this == FULL;
	}

	public static DirectorIntensity parse(String name) {
		for (DirectorIntensity value : values()) {
			if (value.name().equalsIgnoreCase(name)) {
				return value;
			}
		}
		return null;
	}
}
