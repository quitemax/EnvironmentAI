package com.quitemax.environmentai.ai.scheduling;

import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.dialogue.ConversationManager;
import com.quitemax.environmentai.ai.dialogue.DialogueService;
import com.quitemax.environmentai.ai.provider.ModelProvider;
import com.quitemax.environmentai.ai.provider.ProviderFactory;
import com.quitemax.environmentai.ai.training.TrainingDataLogger;
import com.quitemax.environmentai.ai.villager.ProactiveVoice;
import com.quitemax.environmentai.ai.villager.VillagerMoodService;
import com.quitemax.environmentai.config.EnvironmentAIConfig;
import com.quitemax.environmentai.config.ModelTierConfig;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/** Top-level facade: wires config into providers, executors, and both AI-tier schedulers. */
public final class AiService {
	private final System1Scheduler system1Scheduler;
	private final System2TriggerManager system2TriggerManager;
	private final DialogueService dialogueService;
	private final ConversationManager conversationManager;
	/** Null when villager moods are disabled. */
	private final VillagerMoodService moodService;

	public AiService(EnvironmentAIConfig config) {
		AiExecutors executors = new AiExecutors(
			config.system1ExecutorThreads, config.system2ExecutorThreads, config.system3ExecutorThreads
		);

		ModelProvider system1Provider = ProviderFactory.create(config.system1, executors.system1HttpClient());
		ModelProvider system2Provider = ProviderFactory.create(config.system2, executors.system2HttpClient());
		ModelProvider system3Provider = ProviderFactory.create(config.system3, executors.system3HttpClient());

		String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
		this.system1Scheduler = new System1Scheduler(
			system1Provider, config.system1, createLogger(config, "system1", config.system1, stamp)
		);
		this.system2TriggerManager = new System2TriggerManager(
			system2Provider, config.system2, createLogger(config, "system2", config.system2, stamp)
		);
		// One writer for the villager log, shared: two writers on the same file would truncate it.
		TrainingDataLogger villagerLogger = createLogger(config, "villager", config.system3, stamp);
		this.dialogueService = new DialogueService(system3Provider, config.system3, villagerLogger);
		this.conversationManager = new ConversationManager(dialogueService);
		ProactiveVoice voice = new ProactiveVoice(dialogueService, config.villagerAmbientChatter);
		this.moodService = config.villagerMoodEnabled
			? new VillagerMoodService(voice, villagerLogger)
			: null;
	}

	/**
	 * One JSONL file per tier per launch (sharing a timestamp so the pair can be correlated).
	 * Returns null when logging is disabled or the file can't be opened.
	 */
	private static TrainingDataLogger createLogger(
		EnvironmentAIConfig config, String tier, ModelTierConfig tierConfig, String stamp
	) {
		if (!config.trainingDataLogging) {
			return null;
		}
		Path file = FabricLoader.getInstance().getGameDir()
			.resolve("environmentai").resolve("training").resolve(tier + "-" + stamp + ".jsonl");
		try {
			TrainingDataLogger logger = new TrainingDataLogger(
				file, tierConfig.provider.name(), tierConfig.model, EnvironmentAI.LOGGER
			);
			EnvironmentAI.LOGGER.info("{} call logging to {}", tier, file);
			return logger;
		} catch (IOException e) {
			EnvironmentAI.LOGGER.error("Could not open {} log {}; logging disabled", tier, file, e);
			return null;
		}
	}

	public DialogueService dialogue() {
		return dialogueService;
	}

	public void start() {
		MobTracker.register();
		system1Scheduler.register();
		system2TriggerManager.register();
		if (moodService != null) {
			moodService.register();
		}
		conversationManager.register();

		EnvironmentAI.LOGGER.info("EnvironmentAI dual-process mob AI started");
	}
}
