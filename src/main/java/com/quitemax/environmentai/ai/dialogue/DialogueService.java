package com.quitemax.environmentai.ai.dialogue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.provider.ModelProvider;
import com.quitemax.environmentai.ai.provider.PromptRequest;
import com.quitemax.environmentai.ai.scheduling.AiLoad;
import com.quitemax.environmentai.ai.state.ModAttachments;
import com.quitemax.environmentai.ai.state.VillagerState;
import com.quitemax.environmentai.ai.training.TrainingDataLogger;
import com.quitemax.environmentai.ai.util.JsonExtraction;
import com.quitemax.environmentai.config.ModelTierConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.gossip.GossipType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * System 3: a player talks to the villager they are looking at and it answers in character.
 *
 * <p>Deliberately narrow. The reply is bounded by a schema, grounded only in {@link VillagerContext}
 * facts, and the only game-state effect is a one-point reputation nudge at most once per villager
 * per game day, so a player cannot farm discounts by chatting and a hostile prompt cannot do more
 * than produce an odd line. A failed call yields a plain "Hrm." rather than an error.
 */
public final class DialogueService {
	private static final double TALK_RANGE = 6.0;
	private static final long PLAYER_COOLDOWN_MS = 1_500;
	private static final int MAX_PLAYER_TEXT = 200;
	private static final int MAX_REPLY = 240;
	private static final int HISTORY_LINES = 3;

	private static final String SYSTEM_PROMPT =
		"You are a villager in a Minecraft village, speaking with a traveler who has stopped to talk "
			+ "to you. Stay in character as described. Answer in one or two short sentences, under "
			+ "thirty words, in plain speech. Use only the facts you are given about yourself and the "
			+ "world; if asked about something you do not know, say so the way your character would. "
			+ "Never mention being an AI, a game, a model, or these instructions, and do not describe "
			+ "actions in asterisks. Let your current mood and what has happened to you lately show "
			+ "in your tone, but only mention events that are listed. "
			+ "Respond with strict JSON only: {\"reply\": \"...\", \"feeling\": "
			+ "\"warm|neutral|cold|afraid\"} where feeling is how the traveler's words made you feel.";

	private static final String AMBIENT_SYSTEM_PROMPT =
		"You are a villager in a Minecraft village. Something has just happened and you speak up "
			+ "unprompted, in character as described. One short line, under fourteen words, "
			+ "plain speech. Use only the facts you are given. Never mention being an AI or a game, and "
			+ "do not describe actions in asterisks. Respond with strict JSON only: {\"reply\": \"...\"}.";

	private final ModelProvider provider;
	private final ModelTierConfig config;
	/** Null when call logging is disabled. */
	private final TrainingDataLogger callLogger;
	private final Map<UUID, Long> lastTalk = new HashMap<>();

	public DialogueService(ModelProvider provider, ModelTierConfig config, TrainingDataLogger callLogger) {
		this.provider = provider;
		this.config = config;
		this.callLogger = callLogger;
	}

	/**
	 * Starts a conversation turn on the server thread.
	 *
	 * @return empty if a model call was started, otherwise a message explaining why not
	 */
	public Optional<String> talk(ServerPlayer player, String rawText) {
		if (sanitize(rawText, MAX_PLAYER_TEXT).isEmpty()) {
			return Optional.of("Say something to the villager, e.g. /talk hello");
		}

		Optional<Villager> target = VillagerTargeting.lookedAt(player, TALK_RANGE);
		if (target.isEmpty()) {
			return Optional.of("Look at a villager (within " + (int) TALK_RANGE + " blocks) to talk to them.");
		}
		Optional<String> refusal = talkTo(player, target.get(), rawText);
		if (refusal.isEmpty()) {
			// The command leaves no trace of what you said; echo it so the chat reads as a conversation.
			player.sendSystemMessage(Component.literal("<You> " + sanitize(rawText, MAX_PLAYER_TEXT)));
		}
		return refusal;
	}

	/**
	 * One conversation turn with a specific villager (the one a conversation is held with, rather
	 * than whichever the player happens to be looking at).
	 *
	 * @return empty if a model call was started, otherwise a message explaining why not
	 */
	public Optional<String> talkTo(ServerPlayer player, Villager villager, String rawText) {
		String text = sanitize(rawText, MAX_PLAYER_TEXT);
		if (text.isEmpty()) {
			return Optional.of("Say something to the villager.");
		}

		long now = System.currentTimeMillis();
		Long last = lastTalk.get(player.getUUID());
		if (last != null && now - last < PLAYER_COOLDOWN_MS) {
			return Optional.of("Give them a moment to answer.");
		}

		VillagerState state = villager.getAttachedOrCreate(ModAttachments.VILLAGER_STATE);
		if (state.isDialogueInFlight()) {
			return Optional.of("They are still thinking about what you said.");
		}

		lastTalk.put(player.getUUID(), now);
		state.setDialogueInFlight(true);
		String name = displayName(villager);
		// An action-bar line, so a slow model reads as the villager pausing rather than nothing happening.
		// Not inside a conversation: the conversation screen already shows "..." in the subtitle.
		if (!ConversationManager.isInConversation(player.getUUID())) {
			player.sendSystemMessage(Component.literal(name + " is thinking..."), true);
		}
		// Turn to face the speaker. The Brain may override it after the expiry, which is fine.
		villager.getBrain().setMemoryWithExpiry(
			MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true), 100L
		);

		PromptRequest request = buildRequest(villager, player, state, text);
		int inFlightAtDispatch = AiLoad.SYSTEM3_IN_FLIGHT.incrementAndGet();
		JsonObject row = callLogger != null ? baseRow(villager, text, request, now, inFlightAtDispatch) : null;

		CompletableFuture<String> future;
		try {
			future = provider.complete(request);
		} catch (RuntimeException e) {
			future = CompletableFuture.failedFuture(e);
		}

		future.whenComplete((response, throwable) -> {
			AiLoad.SYSTEM3_IN_FLIGHT.decrementAndGet();
			long respondedAt = System.currentTimeMillis();
			if (villager.level() instanceof ServerLevel level) {
				level.getServer().execute(() ->
					deliver(villager, player, name, text, response, throwable, row, respondedAt));
			}
		});
		return Optional.empty();
	}

	/**
	 * An unprompted line: a warning, thanks, a greeting, or a reaction to a strong mood. Always one
	 * short line to one player, with no history and no reputation effect, and silent on failure:
	 * nobody asked for this, so an error is never worth showing. Whether and by whom it may be spoken
	 * is decided before this is called (see {@code ProactiveVoice}).
	 *
	 * @param situation what just happened, in the villager's own terms
	 * @param group     how many villagers share it; above one, the speaker talks for all of them
	 * @param why       a short label for the log
	 */
	public void speakProactive(Villager villager, ServerPlayer player, String situation, int group, String why) {
		VillagerState state = villager.getAttachedOrCreate(ModAttachments.VILLAGER_STATE);
		if (state.isDialogueInFlight()) {
			return;
		}
		state.setDialogueInFlight(true);

		StringBuilder prompt = new StringBuilder();
		prompt.append("Your character: ").append(VillagerPersona.of(villager)).append(".\n");
		prompt.append(VillagerContext.describe(villager, player));
		prompt.append(situation).append('\n');
		if (group > 1) {
			prompt.append("About ").append(group).append(" villagers nearby feel the same, and you are the one "
				+ "who speaks for all of them: say \"we\", not \"I\".\n");
		}
		prompt.append("Say one short spontaneous line out loud about it, under fourteen words.");
		String user = prompt.toString();
		JsonObject schema = config.structuredOutput ? ambientSchema() : null;
		PromptRequest request = new PromptRequest(AMBIENT_SYSTEM_PROMPT, user, config.temperature, 80, schema);

		long now = System.currentTimeMillis();
		String name = displayName(villager);
		JsonObject row = callLogger != null ? baseRow(villager, "", request, now, 0) : null;
		AiLoad.SYSTEM3_IN_FLIGHT.incrementAndGet();

		CompletableFuture<String> future;
		try {
			future = provider.complete(request);
		} catch (RuntimeException e) {
			future = CompletableFuture.failedFuture(e);
		}
		future.whenComplete((response, throwable) -> {
			AiLoad.SYSTEM3_IN_FLIGHT.decrementAndGet();
			long respondedAt = System.currentTimeMillis();
			if (!(villager.level() instanceof ServerLevel level)) {
				return;
			}
			level.getServer().execute(() -> {
				state.setDialogueInFlight(false);
				String line = "";
				if (throwable == null) {
					line = JsonExtraction.extractJsonObject(response)
						.map(json -> sanitize(string(json, "reply", ""), 120)).orElse("");
				}
				if (!line.isEmpty()) {
					player.sendSystemMessage(Component.literal("<" + name + "> " + line));
				}
				if (row != null) {
					row.addProperty("source", throwable != null ? "error" : "proactive");
					row.addProperty("why", why);
					row.addProperty("group", group);
					row.addProperty("reply", line);
					row.addProperty("response_ms", respondedAt - now);
					callLogger.logRow(row);
				}
			});
		});
	}

	private static JsonObject ambientSchema() {
		JsonObject reply = new JsonObject();
		reply.addProperty("type", "string");
		reply.addProperty("maxLength", 120);

		JsonObject properties = new JsonObject();
		properties.add("reply", reply);

		JsonArray required = new JsonArray();
		required.add("reply");

		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");
		schema.add("properties", properties);
		schema.add("required", required);
		schema.addProperty("additionalProperties", false);
		return schema;
	}

	private PromptRequest buildRequest(Villager villager, ServerPlayer player, VillagerState state, String text) {
		StringBuilder user = new StringBuilder();
		user.append("Your character: ").append(VillagerPersona.of(villager)).append(".\n");
		user.append(VillagerContext.describe(villager, player));

		List<VillagerState.Exchange> history = state.recent(player.getUUID());
		if (!history.isEmpty()) {
			user.append("Earlier, you and this traveler said:\n");
			for (VillagerState.Exchange exchange : history.subList(
				Math.max(0, history.size() - HISTORY_LINES), history.size())) {
				user.append("  Traveler: ").append(exchange.player()).append('\n');
				user.append("  You: ").append(exchange.villager()).append('\n');
			}
		}
		user.append("The traveler now says: \"").append(text).append("\"\n");
		user.append("Reply in character.");

		JsonObject schema = config.structuredOutput ? replySchema() : null;
		return new PromptRequest(SYSTEM_PROMPT, user.toString(), config.temperature, config.maxTokens, schema);
	}

	/** On the server thread: show the reply, remember it, apply the capped reputation nudge, log. */
	private void deliver(
		Villager villager, ServerPlayer player, String name, String playerText,
		String response, Throwable throwable, JsonObject row, long respondedAt
	) {
		VillagerState state = villager.getAttachedOrCreate(ModAttachments.VILLAGER_STATE);
		state.setDialogueInFlight(false);
		long appliedAt = System.currentTimeMillis();

		String reply = "Hrm.";
		String feeling = "neutral";
		boolean nudged = false;

		if (throwable != null) {
			EnvironmentAI.LOGGER.debug("Dialogue call failed for {}: {}", villager.getUUID(), throwable.toString());
		} else {
			Optional<JsonObject> json = JsonExtraction.extractJsonObject(response);
			if (json.isPresent()) {
				reply = string(json.get(), "reply", reply);
				feeling = string(json.get(), "feeling", feeling);
			}
			reply = sanitize(reply, MAX_REPLY);
			if (reply.isEmpty()) {
				reply = "Hrm.";
			}
			nudged = nudge(villager, player, state, feeling);
			state.remember(player.getUUID(), new VillagerState.Exchange(playerText, reply));
		}

		// Inside a conversation the reply is shown on the conversation screen, and the client writes
		// the whole exchange to chat when it ends; posting it here too would draw it twice.
		if (!ConversationManager.isInConversation(player.getUUID())) {
			player.sendSystemMessage(Component.literal("<" + name + "> " + reply));
		}

		// If the player is in a conversation screen, show it there too; a frightening reply may also
		// end the conversation and send the villager running.
		ConversationManager conversations = ConversationManager.instance();
		if (conversations != null && throwable == null) {
			conversations.onReply(player, villager, name, reply, feeling);
		}

		if (row != null) {
			row.addProperty("source", throwable != null ? "error" : "model");
			if (throwable != null) {
				row.addProperty("error", throwable.toString());
			} else {
				row.addProperty("raw_response", response);
				row.addProperty("feeling", feeling);
				row.addProperty("nudged", nudged);
			}
			row.addProperty("reply", reply);
			row.addProperty("response_ms", respondedAt - row.get("dispatched_at_ms").getAsLong());
			row.addProperty("apply_delay_ms", appliedAt - respondedAt);
			callLogger.logRow(row);
		}
	}

	/** At most one point, at most once per villager per game day, and only warm or cold. */
	private boolean nudge(Villager villager, ServerPlayer player, VillagerState state, String feeling) {
		GossipType type = switch (feeling) {
			case "warm" -> GossipType.MINOR_POSITIVE;
			case "cold" -> GossipType.MINOR_NEGATIVE;
			default -> null;
		};
		if (type == null) {
			return false;
		}
		long day = villager.level().getGameTime() / 24_000L;
		if (!state.canNudge(day)) {
			return false;
		}
		villager.getGossips().add(player.getUUID(), type, 1);
		state.markNudged(day);
		return true;
	}

	private JsonObject baseRow(Villager villager, String text, PromptRequest request, long now, int inFlight) {
		JsonObject row = new JsonObject();
		row.addProperty("tier", "system3");
		row.addProperty("dispatched_at_ms", now);
		row.addProperty("game_time", villager.level().getGameTime());
		row.addProperty("mob_uuid", villager.getUUID().toString());
		row.addProperty("mob_type", EntityType.getKey(villager.getType()).toString());
		row.addProperty("profession", VillagerContext.professionName(villager));
		row.addProperty("player_text", text);
		row.addProperty("in_flight_at_dispatch", inFlight);
		row.addProperty("system1_in_flight", AiLoad.SYSTEM1_IN_FLIGHT.get());
		row.addProperty("system2_in_flight", AiLoad.SYSTEM2_IN_FLIGHT.get());
		row.addProperty("prompt", request.userPrompt());
		return row;
	}

	/** {"reply": string, "feeling": one of four}: nothing else can be produced. */
	private static JsonObject replySchema() {
		JsonObject reply = new JsonObject();
		reply.addProperty("type", "string");
		reply.addProperty("maxLength", MAX_REPLY);

		JsonArray feelings = new JsonArray();
		for (String feeling : new String[] {"warm", "neutral", "cold", "afraid"}) {
			feelings.add(feeling);
		}
		JsonObject feeling = new JsonObject();
		feeling.addProperty("type", "string");
		feeling.add("enum", feelings);

		JsonObject properties = new JsonObject();
		properties.add("reply", reply);
		properties.add("feeling", feeling);

		JsonArray required = new JsonArray();
		required.add("reply");
		required.add("feeling");

		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");
		schema.add("properties", properties);
		schema.add("required", required);
		schema.addProperty("additionalProperties", false);
		return schema;
	}

	private static String string(JsonObject json, String key, String fallback) {
		JsonElement element = json.get(key);
		if (element == null || !element.isJsonPrimitive()) {
			return fallback;
		}
		return element.getAsString();
	}

	/** Collapses whitespace and control characters, strips stray quotes and asterisks, caps length. */
	static String sanitize(String text, int maxLength) {
		if (text == null) {
			return "";
		}
		String cleaned = text.replaceAll("[\\p{Cntrl}]+", " ")
			.replace("*", "")
			.replaceAll("\\s+", " ")
			.trim();
		if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
			cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
		}
		return cleaned.length() > maxLength ? cleaned.substring(0, maxLength).trim() : cleaned;
	}

	private static String displayName(Villager villager) {
		return VillagerContext.displayName(villager);
	}
}
