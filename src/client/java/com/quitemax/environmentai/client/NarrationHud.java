package com.quitemax.environmentai.client;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * The narrator's caption box: a comic-panel rectangle that slides in from the left, holds for a
 * time proportional to its length, and slides out. Captions that arrive while one is showing wait
 * in a short queue rather than interrupting it.
 *
 * <p>Drawn as a HUD element, so it sits over the world but under any open screen: the conversation
 * overlay covers it, and the server does not send captions during a conversation anyway.
 */
public final class NarrationHud implements HudElement {
	private record Caption(String text, String style) {
	}

	private static final int MAX_QUEUED = 3;
	private static final long SLIDE_MS = 300;
	private static final long MIN_HOLD_MS = 3_500;
	private static final long MAX_HOLD_MS = 9_000;
	private static final long PER_WORD_MS = 350;
	private static final int BOX_WIDTH = 220;
	private static final int PADDING = 6;

	private static final Deque<Caption> queue = new ArrayDeque<>();
	private static Caption current;
	private static long shownAtMs;
	private static long holdMs;

	/** Called from the packet handler on the client thread. */
	public static void show(String text, String style) {
		if (text.isBlank()) {
			return;
		}
		while (queue.size() >= MAX_QUEUED) {
			queue.removeFirst();
		}
		queue.addLast(new Caption(text, style));
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		long now = System.currentTimeMillis();

		if (current != null && now - shownAtMs > SLIDE_MS + holdMs + SLIDE_MS) {
			current = null;
		}
		if (current == null && !queue.isEmpty()) {
			current = queue.removeFirst();
			shownAtMs = now;
			int words = current.text().split("\\s+").length;
			holdMs = Math.max(MIN_HOLD_MS, Math.min(MAX_HOLD_MS, words * PER_WORD_MS + 2_000));
		}
		if (current == null) {
			return;
		}

		Font font = Minecraft.getInstance().font;
		List<FormattedCharSequence> lines = font.split(Component.literal(current.text()), BOX_WIDTH - PADDING * 2);
		int lineHeight = 10;
		int height = lines.size() * lineHeight + PADDING * 2;

		// 0 while sliding in, 1 once settled, and back to 0 while leaving.
		long elapsed = now - shownAtMs;
		double slide;
		if (elapsed < SLIDE_MS) {
			slide = ease((double) elapsed / SLIDE_MS);
		} else if (elapsed > SLIDE_MS + holdMs) {
			slide = ease(1.0 - (double) (elapsed - SLIDE_MS - holdMs) / SLIDE_MS);
		} else {
			slide = 1.0;
		}
		int x = (int) (-BOX_WIDTH + (BOX_WIDTH + 12) * slide);
		int y = 24;

		// A slightly rotated-looking panel is not possible here; a double border and a hard shadow
		// give the printed-comic feel.
		boolean hint = current.style().equals("hint");
		boolean elsewhere = current.style().equals("elsewhere");
		int paper = hint ? 0xFFE6F0FF : elsewhere ? 0xFFEDEDED : 0xFFF5E7A6;
		int ink = 0xFF1A1208;

		graphics.fill(x + 3, y + 3, x + BOX_WIDTH + 3, y + height + 3, 0xAA000000);
		graphics.fill(x - 2, y - 2, x + BOX_WIDTH + 2, y + height + 2, ink);
		graphics.fill(x, y, x + BOX_WIDTH, y + height, paper);

		int textY = y + PADDING;
		for (FormattedCharSequence line : lines) {
			graphics.text(font, line, x + PADDING, textY, ink, false);
			textY += lineHeight;
		}
	}

	private static double ease(double t) {
		double clamped = Math.max(0.0, Math.min(1.0, t));
		return clamped * clamped * (3.0 - 2.0 * clamped);
	}
}
