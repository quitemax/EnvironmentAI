package com.quitemax.environmentai.ai.story;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.dialogue.ConversationManager;
import com.quitemax.environmentai.ai.provider.ModelProvider;
import com.quitemax.environmentai.ai.provider.PromptRequest;
import com.quitemax.environmentai.ai.story.NarrationLedger.Kind;
import com.quitemax.environmentai.ai.story.PlayerStateRules.State;
import com.quitemax.environmentai.ai.training.TrainingDataLogger;
import com.quitemax.environmentai.ai.util.JsonExtraction;
import com.quitemax.environmentai.config.EnvironmentAIConfig;
import com.quitemax.environmentai.config.ModelTierConfig;
import com.quitemax.environmentai.config.NarratorStyle;
import com.quitemax.environmentai.network.NarrationPayloads;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.CompletableFuture;

/**
 * Turns a fact that a rule has already established into a caption in the narrator's voice, and
 * shows it to the right players. Deciding that something is worth narrating is not done here, and
 * neither is deciding what is true: this only phrases, gates and delivers.
 *
 * <p>If the model is slow, absent or fails, a hand-written caption is used, so the narrator works
 * offline and a failed call is never visible. Captions about a player's big moments also go to the
 * other players as a short, model-free note, so the narrator sees everyone.
 */
public final class NarratorService {
	private static final int MAX_CAPTION = NarrationTemplates.MAX_LENGTH;

	private final ModelProvider provider;
	private final ModelTierConfig tier;
	/** Null when call logging is disabled. */
	private final TrainingDataLogger logger;
	private final NarrationLedger ledger = new NarrationLedger();
	private int variant = 0;

	public NarratorService(ModelProvider provider, ModelTierConfig tier, TrainingDataLogger logger) {
		this.provider = provider;
		this.tier = tier;
		this.logger = logger;
	}

	/**
	 * @param detail what specifically happened ("iron", "the Nether"); may be empty
	 * @param hint   an oblique nudge toward the next goal, or empty; only used for QUIET captions
	 * @param major  whether the other players should be told about this
	 * @param background what the narrator can see of the player (level, place, gear, miles); may be empty
	 */
	public boolean narrate(
		ServerPlayer player, Kind kind, String detail, String hint, State state, boolean major, String background
	) {
		EnvironmentAIConfig config = EnvironmentAI.config();
		if (config == null || !config.directorIntensity.narrates()) {
			return false;
		}
		// Nothing over a conversation: it is the whole frame, and it has its own subtitle.
		if (ConversationManager.isInConversation(player.getUUID())) {
			return false;
		}
		long now = System.currentTimeMillis();
		if (!ledger.tryReserve(player.getUUID(), kind, now)) {
			return false;
		}

		boolean withHint = kind == Kind.QUIET && config.directorIntensity.hints() && !hint.isEmpty();
		int pick = variant++;
		String fallback = withHint
			? hint
			: NarrationTemplates.caption(kind, config.narratorStyle, config.storyArc, detail, pick);

		if (major) {
			announceToOthers(player, kind, detail, pick, now);
		}

		// The hints are hand-written and already in the noir voice, and a small model proved unreliable
		// at carrying their image, so in that voice they are shown as written. Other voices rephrase.
		if (withHint && config.narratorStyle == NarratorStyle.NOIR) {
			ServerPlayNetworking.send(player, new NarrationPayloads.Caption(hint, "hint"));
			return true;
		}
		request(player, kind, detail, withHint ? hint : "", state, background, fallback,
			withHint ? "hint" : "narration");
		return true;
	}

	/** Debug: skips the gate so a given kind can be seen on demand. */
	public void narrateNow(
		ServerPlayer player, Kind kind, String detail, String hint, State state, String background
	) {
		ledger.forget(player.getUUID());
		narrate(player, kind, detail, hint, state, false, background);
	}

	// --- others --------------------------------------------------------------------------------

	private void announceToOthers(ServerPlayer subject, Kind kind, String detail, int pick, long now) {
		EnvironmentAIConfig config = EnvironmentAI.config();
		MinecraftServer server = subject.level().getServer();
		if (server == null) {
			return;
		}
		String who = config.narratorUsesNames ? subject.getName().getString() : "another traveler";
		String what = switch (kind) {
			case DEATH -> who + " fell, and the night did not pause";
			case STAGE -> who + " reached " + detail;
			case DIMENSION -> who + " crossed into " + detail;
			default -> "";
		};
		if (what.isEmpty()) {
			return;
		}
		for (ServerPlayer other : server.getPlayerList().getPlayers()) {
			if (other == subject || ConversationManager.isInConversation(other.getUUID())) {
				continue;
			}
			if (ledger.tryReserve(other.getUUID(), Kind.ELSEWHERE, now)) {
				String text = NarrationTemplates.caption(Kind.ELSEWHERE, config.narratorStyle, what, pick);
				ServerPlayNetworking.send(other, new NarrationPayloads.Caption(text, "elsewhere"));
			}
		}
	}

	// --- phrasing ------------------------------------------------------------------------------

	private void request(
		ServerPlayer player, Kind kind, String detail, String hint, State state, String background,
		String fallback, String style
	) {
		EnvironmentAIConfig config = EnvironmentAI.config();
		StringBuilder user = new StringBuilder();
		user.append("What happened: ").append(describe(kind, detail)).append('\n');
		user.append("The player's state: ").append(state.name());
		if (!state.reason().isEmpty()) {
			user.append(" (the player ").append(state.reason()).append(')');
		}
		user.append('\n');
		if (!background.isEmpty()) {
			user.append("What you can see of them").append(config.storyArc.active() ? ", and where the story stands" : "")
				.append(": ").append(background).append('\n');
		}
		if (!hint.isEmpty()) {
			user.append("Work this in indirectly, as part of the story and never as an instruction: ")
				.append(hint).append('\n');
		}
		user.append("Write the caption.");

		String system = "You are the narrator of a Minecraft survival story, writing the caption boxes of a "
			+ "comic book. Your voice: " + config.narratorStyle.persona() + ". Write exactly one caption of "
			+ "at most twenty words, in the third person, about the player. Refer to the player only as "
			+ "\"they\", \"the survivor\" or \"the wanderer\": never assume a gender and never use "
			+ "he, she, him, her, man or woman. Use only the facts you are given; invent no events and "
			+ "no names. If the event names an achievement, name it or give one unmistakable image of it. "
			+ "Never mention game mechanics, menus, advancements, or that this is a game, and never "
			+ "address the player directly or give an order. If you are given something to work in "
			+ "indirectly, include one concrete image from it. "
			+ "You may also be told what you can see of the player (level, place, gear, miles); use at "
			+ "most one such detail, only if it fits the moment, and never list them. "
			+ (config.storyArc.active() ? config.storyArc.premise() + " " : "")
			+ "Respond with strict JSON only: {\"caption\": \"...\"}.";

		JsonObject schema = tier.structuredOutput ? captionSchema() : null;
		PromptRequest request = new PromptRequest(system, user.toString(), tier.temperature, 100, schema);

		long dispatched = System.currentTimeMillis();
		JsonObject row = logger != null ? logRow(player, kind, detail, request, dispatched) : null;

		CompletableFuture<String> future;
		try {
			future = provider.complete(request);
		} catch (RuntimeException e) {
			future = CompletableFuture.failedFuture(e);
		}
		future.whenComplete((response, throwable) -> {
			ServerLevel level = player.level() instanceof ServerLevel serverLevel ? serverLevel : null;
			if (level == null) {
				return;
			}
			level.getServer().execute(() -> {
				String text = fallback;
				String source = "template";
				if (throwable == null) {
					String parsed = JsonExtraction.extractJsonObject(response)
						.map(json -> json.has("caption") && json.get("caption").isJsonPrimitive()
							? json.get("caption").getAsString() : "")
						.map(NarratorService::clean)
						.orElse("");
					if (parsed.isEmpty()) {
						source = "template (unusable reply)";
					} else if (!states(kind, detail, parsed)) {
						// The rule established a fact and the caption does not carry it (a small model
						// described a diamond as "dull grey stone"). Showing it would be a false caption.
						source = "template (caption missed the fact)";
					} else {
						text = parsed;
						source = "model";
					}
				}
				if (!player.isRemoved()) {
					ServerPlayNetworking.send(player, new NarrationPayloads.Caption(text, style));
				}
				if (row != null) {
					row.addProperty("source", source);
					row.addProperty("caption", text);
					row.addProperty("response_ms", System.currentTimeMillis() - dispatched);
					if (throwable != null) {
						row.addProperty("error", throwable.toString());
					}
					logger.logRow(row);
				}
			});
		});
	}

	private JsonObject logRow(ServerPlayer player, Kind kind, String detail, PromptRequest request, long now) {
		JsonObject row = new JsonObject();
		row.addProperty("tier", "narrator");
		row.addProperty("dispatched_at_ms", now);
		row.addProperty("kind", kind.name());
		row.addProperty("detail", detail);
		row.addProperty("player", player.getUUID().toString());
		row.addProperty("prompt", request.userPrompt());
		return row;
	}

	private static String describe(Kind kind, String detail) {
		return switch (kind) {
			case STAGE -> "The player has just achieved: " + detail + ".";
			case NEAR_DEATH -> "The player came within a hair of dying.";
			case DEATH -> "The player died and has returned.";
			case HUNTED -> "Several hostile creatures are closing in on the player in the dark.";
			case DIMENSION -> "The player has just arrived in " + detail + ".";
			case DAWN -> "The player has survived the night; the sun is coming up.";
			case QUIET -> "A long time has passed with nothing of note happening to the player.";
			case RELIEF -> "A dangerous stretch has just ended and the player is safe again.";
			case ELSEWHERE -> detail;
			case JOURNEY -> "The player has now travelled " + detail + " in total.";
			case LEVEL -> "The player has reached experience level " + detail + ".";
			case BIOME -> "The player has wandered into a " + detail + " for the first time.";
			case DEPTH -> "The player is " + detail + ".";
			case LOADOUT -> "The player now has " + detail + ".";
			case WEATHER -> "The weather has turned: " + detail + ".";
			case NIGHTFALL -> "The sun has gone down and night is coming on.";
			case KILLS -> "The player has now killed " + detail + ".";
			case HUNGER -> "The player is running low on food and getting hungry.";
			case OBSERVATION -> "A quiet moment. Remark on one small thing: " + detail;
		};
	}

	/**
	 * Whether a caption actually carries the fact it was asked to. Only achievements and arrivals
	 * name something specific; for the rest, any usable caption is fine. Simple containment is
	 * enough: it only has to catch a caption that does not mention the thing at all.
	 */
	static boolean states(Kind kind, String detail, String caption) {
		if ((kind != Kind.STAGE && kind != Kind.DIMENSION) || detail.isEmpty()) {
			return true;
		}
		String needle = detail.toLowerCase(java.util.Locale.ROOT).replaceFirst("^the ", "");
		return caption.toLowerCase(java.util.Locale.ROOT).contains(needle);
	}

	/**
	 * Collapses whitespace, strips quotes and asterisks. A reply that hit the length cap was cut off
	 * mid-thought. It is only kept if it still contains a complete sentence, which it is then trimmed
	 * back to; trimming at a comma left dangling fragments ("...a lone.") that read worse than the
	 * hand-written fallback, so anything else is rejected (empty) and replaced by a template.
	 */
	static String clean(String text) {
		String cleaned = text.replaceAll("[\\p{Cntrl}]+", " ").replace("*", "").replaceAll("\\s+", " ").trim();
		if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
			cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
		}
		if (cleaned.length() < MAX_CAPTION - 3) {
			return cleaned;
		}
		int cut = -1;
		for (int i = 0; i < cleaned.length(); i++) {
			char c = cleaned.charAt(i);
			if (c == '.' || c == '!' || c == '?') {
				cut = i;
			}
		}
		return cut < 30 ? "" : cleaned.substring(0, cut + 1);
	}

	private static JsonObject captionSchema() {
		JsonObject caption = new JsonObject();
		caption.addProperty("type", "string");
		caption.addProperty("maxLength", MAX_CAPTION);

		JsonObject properties = new JsonObject();
		properties.add("caption", caption);

		JsonArray required = new JsonArray();
		required.add("caption");

		JsonObject schema = new JsonObject();
		schema.addProperty("type", "object");
		schema.add("properties", properties);
		schema.add("required", required);
		schema.addProperty("additionalProperties", false);
		return schema;
	}
}
