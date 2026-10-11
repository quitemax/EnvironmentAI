package com.quitemax.environmentai.ai.scheduling;

import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.provider.ModelProvider;
import com.quitemax.environmentai.ai.provider.ProviderFactory;
import com.quitemax.environmentai.ai.training.TrainingDataLogger;
import com.quitemax.environmentai.config.EnvironmentAIConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Top-level facade: wires config into providers, executors, and both AI-tier schedulers. */
public final class AiService {
	private final System1Scheduler system1Scheduler;
	private final System2TriggerManager system2TriggerManager;

	public AiService(EnvironmentAIConfig config) {
		AiExecutors executors = new AiExecutors(config.system1ExecutorThreads, config.system2ExecutorThreads);

		ModelProvider system1Provider = ProviderFactory.create(config.system1, executors.system1HttpClient());
		ModelProvider system2Provider = ProviderFactory.create(config.system2, executors.system2HttpClient());

		this.system1Scheduler = new System1Scheduler(system1Provider, config.system1, createTrainingLogger(config));
		this.system2TriggerManager = new System2TriggerManager(system2Provider, config.system2);
	}

	/** Returns null when training-data logging is disabled or its file can't be opened. */
	private static TrainingDataLogger createTrainingLogger(EnvironmentAIConfig config) {
		if (!config.trainingDataLogging) {
			return null;
		}
		String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		Path file = FabricLoader.getInstance().getGameDir()
			.resolve("environmentai").resolve("training").resolve("system1-" + stamp + ".jsonl");
		try {
			TrainingDataLogger logger = new TrainingDataLogger(
				file, config.system1.provider.name(), config.system1.model, EnvironmentAI.LOGGER
			);
			EnvironmentAI.LOGGER.info("System 1 training data logging to {}", file);
			return logger;
		} catch (IOException e) {
			EnvironmentAI.LOGGER.error("Could not open training data log {}; logging disabled", file, e);
			return null;
		}
	}

	public void start() {
		MobTracker.register();
		system1Scheduler.register();
		system2TriggerManager.register();

		EnvironmentAI.LOGGER.info("EnvironmentAI dual-process mob AI started");
	}
}
