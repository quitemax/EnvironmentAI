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
 * each player, the few most recent things that happened to it or near it, and a mood derived from
 * those. Persisted with the entity. Environment flags and in-flight markers are real-time
 * bookkeeping and deliberately not saved.
 */
public final class VillagerState {
	/** One round of conversation, as the player said it and as the villager answered. */
	public record Exchange(String player, String villager) {
		public static final Codec<Exchange> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.STRING.fieldOf("player").forGetter(Exchange::player),
			Codec.STRING.fieldOf("villager").forGetter(Exchange::villager)
		).apply(instance, Exchange::new));
	}

	/**
	 * Something that happened to or near the villager. {@code kind} is what the mood rules key on;
	 * {@code text} is what the villager can say about it.
	 */
	public record Event(long gameTime, String kind, String text) {
		public static final Codec<Event> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.LONG.fieldOf("time").forGetter(Event::gameTime),
			Codec.STRING.fieldOf("kind").forGetter(Event::kind),
			Codec.STRING.fieldOf("text").forGetter(Event::text)
		).apply(instance, Event::new));
	}

	public static final int MAX_EXCHANGES_PER_PLAYER = 6;
	/** Bounds the saved size: a busy server cannot grow one villager's NBT without limit. */
	public static final int MAX_PLAYERS_REMEMBERED = 8;
	public static final int MAX_EVENTS = 5;
	/** The same kind of event inside this window replaces the previous one rather than piling up. */
	private static final long EVENT_DEDUPE_TICKS = 200;

	public static final Codec<VillagerState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Exchange.CODEC.listOf())
			.optionalFieldOf("conversations", Map.of()).forGetter(state -> state.conversations),
		Codec.STRING.optionalFieldOf("mood", "calm").forGetter(state -> state.mood),
		Codec.STRING.optionalFieldOf("mood_reason", "").forGetter(state -> state.moodReason),
		Codec.LONG.optionalFieldOf("mood_since", 0L).forGetter(state -> state.moodSince),
		Event.CODEC.listOf().optionalFieldOf("events", List.of()).forGetter(state -> state.events),
		Codec.LONG.optionalFieldOf("last_nudge_day", -1L).forGetter(state -> state.lastNudgeDay),
		Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG)
			.optionalFieldOf("last_seen", Map.of()).forGetter(state -> state.lastSeen)
	).apply(instance, VillagerState::new));

	private final Map<UUID, List<Exchange>> conversations = new HashMap<>();
	private final List<Event> events = new ArrayList<>();
	/** Game time each player was last seen near this villager, for noticing a return. */
	private final Map<UUID, Long> lastSeen = new HashMap<>();
	private String mood;
	private String moodReason;
	private long moodSince;
	private long lastNudgeDay;

	private boolean dialogueInFlight = false;
	private boolean hostileNear = false;
	private boolean golemNear = false;
	private long lastAmbientMs = 0L;
	private long lastEvaluatedGameTime = 0L;

	private VillagerState(
		Map<UUID, List<Exchange>> conversations, String mood, String moodReason, long moodSince,
		List<Event> events, long lastNudgeDay, Map<UUID, Long> lastSeen
	) {
		conversations.forEach((player, exchanges) -> this.conversations.put(player, new ArrayList<>(exchanges)));
		this.mood = mood;
		this.moodReason = moodReason;
		this.moodSince = moodSince;
		this.events.addAll(events);
		this.lastNudgeDay = lastNudgeDay;
		this.lastSeen.putAll(lastSeen);
	}

	public static VillagerState createDefault() {
		return new VillagerState(Map.of(), "calm", "", 0L, List.of(), -1L, Map.of());
	}

	/** Game time this player was last seen near this villager, or -1 if never. */
	public long lastSeen(UUID player) {
		return lastSeen.getOrDefault(player, -1L);
	}

	public void markSeen(UUID player, long gameTime) {
		lastSeen.put(player, gameTime);
		while (lastSeen.size() > MAX_PLAYERS_REMEMBERED) {
			UUID oldest = lastSeen.keySet().iterator().next();
			if (oldest.equals(player)) {
				break;
			}
			lastSeen.remove(oldest);
		}
	}

	// --- conversation memory -------------------------------------------------------------------

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

	// --- events and mood -----------------------------------------------------------------------

	/** Oldest first. */
	public List<Event> events() {
		return List.copyOf(events);
	}

	/** True if an event of this kind happened within {@code windowTicks} before {@code now}. */
	public boolean hasRecent(String kind, long now, long windowTicks) {
		for (Event event : events) {
			if (event.kind().equals(kind) && now - event.gameTime() <= windowTicks) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Records an event. A repeat of the same kind within a short window replaces the earlier one
	 * (a villager being hit five times is one event, not five), and the list stays small.
	 */
	public void addEvent(Event event) {
		events.removeIf(existing ->
			existing.kind().equals(event.kind()) && event.gameTime() - existing.gameTime() <= EVENT_DEDUPE_TICKS);
		events.add(event);
		while (events.size() > MAX_EVENTS) {
			events.remove(0);
		}
	}

	public String mood() {
		return mood;
	}

	public String moodReason() {
		return moodReason;
	}

	public long moodSince() {
		return moodSince;
	}

	public void setMood(String mood, String reason, long gameTime) {
		this.mood = mood;
		this.moodReason = reason;
		this.moodSince = gameTime;
	}

	// --- reputation nudges ---------------------------------------------------------------------

	/** Reputation nudges from conversation are limited to one per villager per game day. */
	public boolean canNudge(long gameDay) {
		return gameDay > lastNudgeDay;
	}

	public void markNudged(long gameDay) {
		this.lastNudgeDay = gameDay;
	}

	// --- transient bookkeeping -----------------------------------------------------------------

	public boolean isDialogueInFlight() {
		return dialogueInFlight;
	}

	public void setDialogueInFlight(boolean inFlight) {
		this.dialogueInFlight = inFlight;
	}

	public boolean hostileNear() {
		return hostileNear;
	}

	public void setHostileNear(boolean hostileNear) {
		this.hostileNear = hostileNear;
	}

	public boolean golemNear() {
		return golemNear;
	}

	public void setGolemNear(boolean golemNear) {
		this.golemNear = golemNear;
	}

	public long lastAmbientMs() {
		return lastAmbientMs;
	}

	public void markAmbient(long now) {
		this.lastAmbientMs = now;
	}

	public long lastEvaluatedGameTime() {
		return lastEvaluatedGameTime;
	}

	public void markEvaluated(long gameTime) {
		this.lastEvaluatedGameTime = gameTime;
	}
}
