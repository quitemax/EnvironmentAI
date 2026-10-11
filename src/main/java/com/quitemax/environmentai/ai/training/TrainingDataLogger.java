package com.quitemax.environmentai.ai.training;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.quitemax.environmentai.ai.action.MobAction;
import com.quitemax.environmentai.ai.provider.PromptRequest;
import com.quitemax.environmentai.ai.state.MobAiState;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Appends one JSON line per System 1 decision to a file, so real in-game (situation, chosen
 * action) pairs can later be used to train or evaluate a smaller decision model.
 *
 * <p>Every row has a {@code source}: {@code "model"} (the model answered), {@code "error"} (the
 * call failed or timed out; the mob kept its action) or {@code "rule"} (a scheduler rule decided
 * without calling the model). Only {@code "model"} rows are usable as training labels.
 *
 * <p>Capture happens on the server thread at dispatch (the situation as the model saw it), the
 * row is completed when the answer arrives, and file I/O happens on its own daemon thread so
 * logging can never stall the tick loop. If the writer falls behind, rows are dropped rather than
 * queued without bound.
 */
public final class TrainingDataLogger {
	private static final Gson GSON = new Gson();
	private static final int MAX_QUEUED_ROWS = 10_000;

	private final Logger logger;
	private final BlockingQueue<String> queue = new LinkedBlockingQueue<>(MAX_QUEUED_ROWS);
	private final AtomicLong dropped = new AtomicLong();
	private final String provider;
	private final String model;

	public TrainingDataLogger(Path file, String provider, String model, Logger logger) throws IOException {
		this.logger = logger;
		this.provider = provider;
		this.model = model;
		Files.createDirectories(file.getParent());
		BufferedWriter writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8);

		Thread thread = new Thread(() -> writeLoop(writer), "environmentai-training-log");
		thread.setDaemon(true);
		thread.start();
	}

	/**
	 * Captures the situation as System 1 sees it. Call on the server thread.
	 *
	 * @param request the prompt sent to the model, or null for rule-decided rows
	 */
	public JsonObject snapshot(Mob mob, MobAiState state, PromptRequest request, long nowMs) {
		JsonObject row = new JsonObject();
		row.addProperty("dispatched_at_ms", nowMs);
		row.addProperty("game_time", mob.level().getGameTime());
		row.addProperty("mob_uuid", mob.getUUID().toString());
		row.addProperty("mob_type", EntityType.getKey(mob.getType()).toString());
		row.addProperty("health", mob.getHealth());
		row.addProperty("max_health", mob.getMaxHealth());
		row.addProperty("disposition", state.narrative().disposition());
		row.addProperty("intent", state.narrative().intent());
		row.addProperty("target_description", state.narrative().targetDescription());
		row.addProperty("memory_notes", state.narrative().memoryNotes());
		row.addProperty("previous_action", state.currentAction().name());
		row.addProperty("consecutive_same_action", state.consecutiveSameActionTicks());

		LivingEntity target = mob.getTarget();
		row.addProperty("has_target", target != null);
		if (target != null) {
			row.addProperty("target_type", EntityType.getKey(target.getType()).toString());
			row.addProperty("target_distance", mob.distanceTo(target));
			row.addProperty("target_health", target.getHealth());
			row.addProperty("target_max_health", target.getMaxHealth());
		}

		if (request != null) {
			// The exact text the model saw, so a text-input model can be trained on it directly.
			row.addProperty("prompt", request.userPrompt());
		}
		return row;
	}

	/**
	 * Completes and enqueues a row for a model answer.
	 *
	 * @param parsed        the action the model actually returned, or null if the response was unusable
	 * @param applied       the action the mob ended up with (the previous one when parsing failed)
	 * @param respondedAtMs when the response arrived (off the server thread)
	 * @param appliedAtMs   when it was applied on the server thread
	 */
	public void recordModel(
		JsonObject row, String rawResponse, MobAction parsed, MobAction applied, long respondedAtMs, long appliedAtMs
	) {
		row.addProperty("source", "model");
		row.addProperty("raw_response", rawResponse);
		row.addProperty("parsed", parsed != null);
		row.addProperty("action", applied.name());
		addTiming(row, respondedAtMs, appliedAtMs);
		enqueue(row);
	}

	/** Completes and enqueues a row for a failed or timed-out call; the mob kept {@code kept}. */
	public void recordError(JsonObject row, Throwable error, MobAction kept, long respondedAtMs, long appliedAtMs) {
		row.addProperty("source", "error");
		row.addProperty("error", error.toString());
		row.addProperty("parsed", false);
		row.addProperty("action", kept.name());
		addTiming(row, respondedAtMs, appliedAtMs);
		enqueue(row);
	}

	/**
	 * Enqueues a row the caller has fully built, adding only provider and model. Used for System 2
	 * call logs, whose shape differs from System 1 decision rows.
	 */
	public void logRow(JsonObject row) {
		enqueue(row);
	}

	/** Enqueues a row for an action chosen by a scheduler rule, with no model call. */
	public void recordRule(JsonObject row, MobAction applied, String reason) {
		row.addProperty("source", "rule");
		row.addProperty("rule", reason);
		row.addProperty("action", applied.name());
		enqueue(row);
	}

	private static void addTiming(JsonObject row, long respondedAtMs, long appliedAtMs) {
		long dispatchedAt = row.get("dispatched_at_ms").getAsLong();
		// response_ms: dispatch until the answer arrived (executor + network + Ollama queue + inference).
		row.addProperty("response_ms", respondedAtMs - dispatchedAt);
		// apply_delay_ms: answer arrived until the server thread got around to applying it.
		row.addProperty("apply_delay_ms", appliedAtMs - respondedAtMs);
	}

	private void enqueue(JsonObject row) {
		row.addProperty("provider", provider);
		row.addProperty("model", model);
		if (!queue.offer(GSON.toJson(row))) {
			long total = dropped.incrementAndGet();
			if (total == 1 || total % 1000 == 0) {
				logger.warn("Training log writer is behind; dropped {} row(s) so far", total);
			}
		}
	}

	private void writeLoop(BufferedWriter writer) {
		try (writer) {
			while (true) {
				String line = queue.take();
				writer.write(line);
				writer.newLine();
				// Flush whenever we've caught up, so the file is readable while the game runs.
				if (queue.isEmpty()) {
					writer.flush();
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
			logger.error("Training log writer failed; training data logging stopped", e);
		}
	}
}
