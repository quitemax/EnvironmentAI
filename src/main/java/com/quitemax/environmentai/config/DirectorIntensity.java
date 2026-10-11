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
	/**
	 * Hints, plus voices in the world: villagers near the player let a rumour slip about the next
	 * goal once the narrator's hint has gone unheeded. Pressure on mobs is not built yet.
	 */
	FULL;

	public boolean narrates() {
		return this != OFF;
	}

	public boolean hints() {
		return this == HINTS || this == FULL;
	}

	public boolean voices() {
		return this == FULL;
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
