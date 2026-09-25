package com.quitemax.environmentai.ai.narrative;

/**
 * A mob's narrative state as maintained by System 2: its disposition, current intent, a
 * description of whatever it's focused on, and a short rolling memory of recent events.
 * This is the ground that System 1's fast per-tick decisions are made against.
 */
public record NarrativeState(
	String disposition,
	String intent,
	String targetDescription,
	String memoryNotes,
	long lastUpdatedGameTime
) {
	public static NarrativeState initial(long gameTime) {
		return new NarrativeState("neutral", "has not yet formed an intent", "", "", gameTime);
	}
}
