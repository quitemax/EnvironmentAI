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

	private AiLoad() {
	}
}
