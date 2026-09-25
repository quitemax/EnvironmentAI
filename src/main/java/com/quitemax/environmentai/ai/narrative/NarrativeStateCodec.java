package com.quitemax.environmentai.ai.narrative;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public final class NarrativeStateCodec {
	private NarrativeStateCodec() {
	}

	public static final Codec<NarrativeState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.STRING.fieldOf("disposition").forGetter(NarrativeState::disposition),
		Codec.STRING.fieldOf("intent").forGetter(NarrativeState::intent),
		Codec.STRING.optionalFieldOf("target_description", "").forGetter(NarrativeState::targetDescription),
		Codec.STRING.optionalFieldOf("memory_notes", "").forGetter(NarrativeState::memoryNotes),
		Codec.LONG.fieldOf("last_updated_game_time").forGetter(NarrativeState::lastUpdatedGameTime)
	).apply(instance, NarrativeState::new));
}
