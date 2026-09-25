package com.quitemax.environmentai.ai.scheduling;

import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.provider.ModelProvider;
import com.quitemax.environmentai.ai.provider.ProviderFactory;
import com.quitemax.environmentai.config.EnvironmentAIConfig;
import com.quitemax.environmentai.config.ModelTierConfig;
import com.quitemax.environmentai.config.ProviderType;

import java.net.http.HttpClient;

/** Top-level facade: wires config into providers, executors, and both AI-tier schedulers. */
public final class AiService {
	private final AiExecutors executors;
	private final System1Scheduler system1Scheduler;
	private final System2TriggerManager system2TriggerManager;

	public AiService(EnvironmentAIConfig config) {
		this.executors = new AiExecutors(config.localExecutorThreads, config.cloudExecutorThreads);

		ModelProvider system1Provider = ProviderFactory.create(config.system1, httpClientFor(config.system1));
		ModelProvider system2Provider = ProviderFactory.create(config.system2, httpClientFor(config.system2));

		this.system1Scheduler = new System1Scheduler(system1Provider, config.system1);
		this.system2TriggerManager = new System2TriggerManager(system2Provider, config.system2);
	}

	private HttpClient httpClientFor(ModelTierConfig tier) {
		return tier.provider == ProviderType.CLOUD ? executors.cloudHttpClient() : executors.localHttpClient();
	}

	public void start() {
		system1Scheduler.register();
		system2TriggerManager.register();

		EnvironmentAI.LOGGER.info("EnvironmentAI dual-process mob AI started");
	}
}
