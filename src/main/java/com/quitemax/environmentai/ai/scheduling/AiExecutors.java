package com.quitemax.environmentai.ai.scheduling;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * One bounded thread pool (and one {@link HttpClient} bound to it) per AI tier, so a slow
 * System 2 round-trip can never starve System 1 throughput regardless of whether either tier is
 * local or cloud.
 * Scoped to the whole game process (constructed once in {@code onInitialize}), not to any single
 * world — threads are daemon threads, so they never need an explicit shutdown; tearing them down
 * on a world stop would leave the process-lifetime schedulers holding dead executors for any
 * later world in the same session.
 */
public final class AiExecutors {
	private final HttpClient system1HttpClient;
	private final HttpClient system2HttpClient;

	public AiExecutors(int system1Threads, int system2Threads) {
		this.system1HttpClient = httpClient(
			Executors.newFixedThreadPool(system1Threads, threadFactory("environmentai-system1"))
		);
		this.system2HttpClient = httpClient(
			Executors.newFixedThreadPool(system2Threads, threadFactory("environmentai-system2"))
		);
	}

	public HttpClient system1HttpClient() {
		return system1HttpClient;
	}

	public HttpClient system2HttpClient() {
		return system2HttpClient;
	}

	private static HttpClient httpClient(ExecutorService pool) {
		return HttpClient.newBuilder()
			.executor(pool)
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	}

	private static ThreadFactory threadFactory(String namePrefix) {
		AtomicInteger counter = new AtomicInteger();
		return runnable -> {
			Thread thread = new Thread(runnable, namePrefix + "-" + counter.incrementAndGet());
			thread.setDaemon(true);
			return thread;
		};
	}
}
