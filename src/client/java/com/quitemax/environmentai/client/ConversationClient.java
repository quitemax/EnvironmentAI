package com.quitemax.environmentai.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The client's view of the conversation: what to frame, what to show, and how far through the
 * camera transition we are. The server decides when a conversation starts and ends; this only
 * mirrors it. Touched from the render thread and from packet handlers, which on the client are the
 * same thread, so no locking.
 */
public final class ConversationClient {
	private enum Phase {
		NONE, IN, ACTIVE, OUT
	}

	private static final long EASE_IN_MS = 450;
	private static final long EASE_OUT_MS = 350;
	private static final int TRANSCRIPT_LINES = 6;
	/** A reply that never arrives must not leave the input stuck in "waiting". */
	private static final long PENDING_TIMEOUT_MS = 40_000;

	private static Phase phase = Phase.NONE;
	private static long phaseStartNanos;
	private static int villagerId = -1;
	private static String villagerName = "";
	private static String lastLineSpeaker = "";
	private static String lastLine = "";
	private static boolean pending;
	private static long pendingSinceNanos;
	private static final Deque<String> transcript = new ArrayDeque<>();

	private ConversationClient() {
	}

	// --- lifecycle -----------------------------------------------------------------------------

	public static void begin(int id, String name) {
		villagerId = id;
		villagerName = name;
		lastLineSpeaker = "";
		lastLine = "";
		pending = false;
		transcript.clear();
		phase = Phase.IN;
		phaseStartNanos = System.nanoTime();
	}

	/** Starts the camera easing back to the normal view. Safe to call when nothing is active. */
	public static void end() {
		if (phase == Phase.NONE || phase == Phase.OUT) {
			return;
		}
		// Ease out from wherever the ease-in had got to, so a quick exit does not snap. The blend is
		// smooth(t), so the matching position on the way out is the inverse of that curve.
		double current = blend();
		long elapsed = (long) ((1.0 - inverseSmooth(current)) * EASE_OUT_MS * 1_000_000L);
		phase = Phase.OUT;
		phaseStartNanos = System.nanoTime() - elapsed;
		pending = false;
	}

	/** True from the moment the server accepts until the player has left (not during ease-out). */
	public static boolean active() {
		return phase == Phase.IN || phase == Phase.ACTIVE;
	}

	public static int villagerId() {
		return villagerId;
	}

	public static String villagerName() {
		return villagerName;
	}

	// --- camera blend --------------------------------------------------------------------------

	/**
	 * How much of the conversation shot to apply, 0 (normal view) to 1 (full shot), eased. Also
	 * advances the phase, so it must be called each frame the camera is built.
	 */
	public static double blend() {
		long elapsedMs = (System.nanoTime() - phaseStartNanos) / 1_000_000L;
		switch (phase) {
			case IN: {
				if (elapsedMs >= EASE_IN_MS) {
					phase = Phase.ACTIVE;
					return 1.0;
				}
				return smooth((double) elapsedMs / EASE_IN_MS);
			}
			case ACTIVE:
				return 1.0;
			case OUT: {
				if (elapsedMs >= EASE_OUT_MS) {
					phase = Phase.NONE;
					return 0.0;
				}
				return smooth(1.0 - (double) elapsedMs / EASE_OUT_MS);
			}
			default:
				return 0.0;
		}
	}

	/** The t for which {@code smooth(t) == value}, i.e. the inverse of smoothstep. */
	private static double inverseSmooth(double value) {
		double clamped = Math.max(0.0, Math.min(1.0, value));
		return 0.5 - Math.sin(Math.asin(1.0 - 2.0 * clamped) / 3.0);
	}

	private static double smooth(double t) {
		double clamped = Math.max(0.0, Math.min(1.0, t));
		return clamped * clamped * (3.0 - 2.0 * clamped);
	}

	// --- text ----------------------------------------------------------------------------------

	public static void onPlayerLine(String text) {
		addTranscript("You: " + text);
		pending = true;
		pendingSinceNanos = System.nanoTime();
	}

	public static void onVillagerLine(String speaker, String text) {
		pending = false;
		if (speaker.isEmpty()) {
			// A system line (a refusal, "give them a moment"): shown, but it is not the villager speaking.
			addTranscript(text);
			return;
		}
		lastLineSpeaker = speaker;
		lastLine = text;
		addTranscript(speaker + ": " + text);
	}

	private static void addTranscript(String line) {
		transcript.addLast(line);
		while (transcript.size() > TRANSCRIPT_LINES) {
			transcript.removeFirst();
		}
	}

	public static boolean isWaiting() {
		if (pending && (System.nanoTime() - pendingSinceNanos) / 1_000_000L > PENDING_TIMEOUT_MS) {
			pending = false;
		}
		return pending;
	}

	public static String lastLineSpeaker() {
		return lastLineSpeaker;
	}

	public static String lastLine() {
		return lastLine;
	}

	public static List<String> transcript() {
		return List.copyOf(transcript);
	}
}
