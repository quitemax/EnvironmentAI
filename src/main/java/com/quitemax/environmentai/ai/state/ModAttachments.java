package com.quitemax.environmentai.ai.state;

import com.quitemax.environmentai.EnvironmentAI;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;

public final class ModAttachments {
	public static final AttachmentType<MobAiState> MOB_AI_STATE = AttachmentRegistry.create(
		EnvironmentAI.id("mob_ai_state"),
		builder -> builder
			.persistent(MobAiState.CODEC)
			.initializer(MobAiState::createDefault)
	);

	public static final AttachmentType<VillagerState> VILLAGER_STATE = AttachmentRegistry.create(
		EnvironmentAI.id("villager_state"),
		builder -> builder
			.persistent(VillagerState.CODEC)
			.initializer(VillagerState::createDefault)
	);

	private ModAttachments() {
	}

	/** No-op body; calling this forces the class (and the static attachment registration) to load. */
	public static void register() {
	}
}
