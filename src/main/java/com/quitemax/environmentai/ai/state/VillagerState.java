package com.quitemax.environmentai.ai.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What a villager remembers beyond vanilla's own gossip: the last few things said between it and
 * each player, and a mood. Persisted with the entity. The in-flight flag is real-time bookkeeping
 * and deliberately not saved.
 */
public final class VillagerState {
	/** One round of conversation, as the player said it and as the villager answered. */
	public record Exchange(String player, String villager) {
		public static final Codec<Exchange> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("player").forGetter(Exchange::player),
			Codec.STRING.fieldOf("villager").forGetter(Exchange::villager)
		).apply(instance, Exchange::new));
	}

	public static final int MAX_EXCHANGES_PER_PLAYER = 6;
	/** Bounds the saved size: a busy server cannot grow one villager's NBT without limit. */
	public static final int MAX_PLAYERS_REMEMBERED = 8;

	public static final Codec<VillagerState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Exchange.CODEC.listOf())
			.optionalFieldOf("conversations", Map.of()).forGetter(state -> state.conversations),
		Codec.STRING.optionalFieldOf("mood", "calm").forGetter(state -> state.mood),
		Codec.LONG.optionalFieldOf("last_nudge_day", -1L).forGetter(state -> state.lastNudgeDay)
	).apply(instance, VillagerState::new));

	private final Map<UUID, List<Exchange>> conversations = new HashMap<>();
	private String mood;
	private long lastNudgeDay;

	private boolean dialogueInFlight = false;

	private VillagerState(Map<UUID, List<Exchange>> conversations, String mood, long lastNudgeDay) {
		conversations.forEach((player, exchanges) -> this.conversations.put(player, new ArrayList<>(exchanges)));
		this.mood = mood;
		this.lastNudgeDay = lastNudgeDay;
	}

	public static VillagerState createDefault() {
		return new VillagerState(Map.of(), "calm", -1L);
	}

	/** Oldest first; empty if this villager has never spoken with that player. */
	public List<Exchange> recent(UUID player) {
		return List.copyOf(conversations.getOrDefault(player, List.of()));
	}

	public void remember(UUID player, Exchange exchange) {
		List<Exchange> exchanges = conversations.computeIfAbsent(player, id -> new ArrayList<>());
		exchanges.add(exchange);
		while (exchanges.size() > MAX_EXCHANGES_PER_PLAYER) {
			exchanges.remove(0);
		}
		while (conversations.size() > MAX_PLAYERS_REMEMBERED) {
			UUID oldest = conversations.keySet().iterator().next();
			if (oldest.equals(player)) {
				break;
			}
			conversations.remove(oldest);
		}
	}

	public int conversationCount() {
		return conversations.size();
	}

	public String mood() {
		return mood;
	}

	public void setMood(String mood) {
		this.mood = mood;
	}

	/** Reputation nudges from conversation are limited to one per villager per game day. */
	public boolean canNudge(long gameDay) {
		return gameDay > lastNudgeDay;
	}

	public void markNudged(long gameDay) {
		this.lastNudgeDay = gameDay;
	}

	public boolean isDialogueInFlight() {
		return dialogueInFlight;
	}

	public void setDialogueInFlight(boolean inFlight) {
		this.dialogueInFlight = inFlight;
	}
}
