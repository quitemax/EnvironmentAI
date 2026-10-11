package com.quitemax.environmentai.ai.scheduling;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Outstanding model calls per tier, shared process-wide. System 1 uses its counter as its global
 * in-flight cap; both tiers also read the other's counter when logging a call, so a slow answer
 * can be correlated with what else was hitting the GPU at the time.
 */
public final class AiLoad {
	public static final AtomicInteger SYSTEM1_IN_FLIGHT = new AtomicInteger();
	public static final AtomicInteger SYSTEM2_IN_FLIGHT = new AtomicInteger();
	public static final AtomicInteger SYSTEM3_IN_FLIGHT = new AtomicInteger();

	/**
	 * Model calls are held back for this many server ticks after a world starts. Logged data showed
	 * every timeout of a session bunched in the first seconds, while the server was reporting itself
	 * dozens of ticks behind: calls made then queue behind world loading and time out.
	 */
	public static final int STARTUP_GRACE_TICKS = 200;

	private AiLoad() {
	}
}
