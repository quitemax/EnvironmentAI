package com.quitemax.environmentai.ai.scheduling;

import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.provider.ModelProvider;
import com.quitemax.environmentai.ai.provider.ProviderFactory;
import com.quitemax.environmentai.config.EnvironmentAIConfig;

/** Top-level facade: wires config into providers, executors, and both AI-tier schedulers. */
public final class AiService {
	private final System1Scheduler system1Scheduler;
	private final System2TriggerManager system2TriggerManager;

	public AiService(EnvironmentAIConfig config) {
		AiExecutors executors = new AiExecutors(config.system1ExecutorThreads, config.system2ExecutorThreads);

		ModelProvider system1Provider = ProviderFactory.create(config.system1, executors.system1HttpClient());
		ModelProvider system2Provider = ProviderFactory.create(config.system2, executors.system2HttpClient());

		this.system1Scheduler = new System1Scheduler(system1Provider, config.system1);
		this.system2TriggerManager = new System2TriggerManager(system2Provider, config.system2);
	}

	public void start() {
		MobTracker.register();
		system1Scheduler.register();
		system2TriggerManager.register();

		EnvironmentAI.LOGGER.info("EnvironmentAI dual-process mob AI started");
	}
}
