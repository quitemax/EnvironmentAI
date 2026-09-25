package com.quitemax.environmentai.ai.scheduling;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Two separate bounded thread pools (and one {@link HttpClient} bound to each) for local vs
 * cloud model calls, so a slow cloud round-trip can never starve local System 1 throughput.
 * Scoped to the whole game process (constructed once in {@code onInitialize}), not to any single
 * world — threads are daemon threads, so they never need an explicit shutdown; tearing them down
 * on a world stop would leave the process-lifetime schedulers holding dead executors for any
 * later world in the same session.
 */
public final class AiExecutors {
	private final ExecutorService localPool;
	private final ExecutorService cloudPool;
	private final HttpClient localHttpClient;
	private final HttpClient cloudHttpClient;

	public AiExecutors(int localThreads, int cloudThreads) {
		this.localPool = Executors.newFixedThreadPool(localThreads, threadFactory("environmentai-local"));
		this.cloudPool = Executors.newFixedThreadPool(cloudThreads, threadFactory("environmentai-cloud"));
		this.localHttpClient = HttpClient.newBuilder()
			.executor(localPool)
			.connectTimeout(Duration.ofSeconds(10))
			.build();
		this.cloudHttpClient = HttpClient.newBuilder()
			.executor(cloudPool)
			.connectTimeout(Duration.ofSeconds(10))
			.build();
	}

	public HttpClient localHttpClient() {
		return localHttpClient;
	}

	public HttpClient cloudHttpClient() {
		return cloudHttpClient;
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
