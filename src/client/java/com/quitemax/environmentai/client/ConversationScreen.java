package com.quitemax.environmentai.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.quitemax.environmentai.network.ConversationPayloads;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * The conversation overlay, drawn as a film frame: black bars at the top and bottom that slide in
 * with the camera move, soft edge shading, the villager's status in the top corner, and its last
 * line as a subtitle above a text field that stays open after Enter.
 *
 * <p>The screen is not paused (a paused integrated server would stop the reply being waited for),
 * and it outlives the conversation by the length of the camera's ease-out, so the bars slide away
 * with the shot instead of vanishing the instant it ends. In that closing phase it takes no input.
 */
public final class ConversationScreen extends Screen {
	private static final int MAX_INPUT = 200;
	private static final int MAX_SPEECH_LINES = 4;
	private static final int LINE_HEIGHT = 11;
	private static final double BAR_FRACTION = 0.13;
	private static final int MIN_BAR_HEIGHT = 30;

	private EditBox input;
	/** Set once the server has been told (or told us), so it is never told twice. */
	private boolean leaveSent;

	public ConversationScreen() {
		super(Component.literal("Conversation"));
	}

	private int barHeight() {
		return Math.max(MIN_BAR_HEIGHT, (int) (height * BAR_FRACTION));
	}

	private int boxWidth() {
		return Math.min(width - 40, 440);
	}

	@Override
	protected void init() {
		int boxWidth = boxWidth();
		// Inside the bottom bar at full height, so the field never sits on the picture.
		int y = height - barHeight() + (barHeight() - 20) / 2;
		input = new EditBox(font, (width - boxWidth) / 2, y, boxWidth, 20, Component.literal("Say something"));
		input.setMaxLength(MAX_INPUT);
		// Clicking elsewhere must not take focus away: the field is the whole interaction.
		input.setCanLoseFocus(false);
		addRenderableWidget(input);
		setInitialFocus(input);
	}

	// --- input ---------------------------------------------------------------------------------

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (closing()) {
			return true;
		}
		int key = event.key();
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			submit();
			return true;
		}
		return super.keyPressed(event);
	}

	private void submit() {
		String text = input.getValue().trim();
		if (text.isEmpty() || ConversationClient.isWaiting()) {
			return;
		}
		ConversationClient.onPlayerLine(text);
		ClientPlayNetworking.send(new ConversationPayloads.Say(text));
		// Stay open: this is the point. Clear the field and keep the focus.
		input.setValue("");
	}

	/** True once the conversation has ended and only the camera ease-out remains. */
	private boolean closing() {
		return !ConversationClient.active();
	}

	/**
	 * Escape. Tells the server and starts the ease-out; the screen itself closes in {@link #tick()}
	 * once the shot has finished returning, so the bars slide away rather than pop.
	 */
	@Override
	public void onClose() {
		sendLeave();
		ConversationClient.end();
	}

	@Override
	public void tick() {
		super.tick();
		if (closing()) {
			input.visible = false;
			input.setFocused(false);
			if (ConversationClient.blend() <= 0.0 && minecraft != null) {
				minecraft.gui.setScreen(null);
			}
		}
	}

	/** Called whenever the screen goes away for any reason (death screen, disconnect, ours). */
	@Override
	public void removed() {
		if (ConversationClient.active()) {
			sendLeave();
			ConversationClient.end();
		}
		super.removed();
	}

	private void sendLeave() {
		if (!leaveSent) {
			leaveSent = true;
			ClientPlayNetworking.send(new ConversationPayloads.Leave());
		}
	}

	/** The server ended it; do not echo a Leave back. */
	void serverEnded() {
		leaveSent = true;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// --- drawing -------------------------------------------------------------------------------

	/** Deliberately empty: no dimming or blur, the shot is the point. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		double blend = ConversationClient.blend();
		int bar = (int) Math.round(barHeight() * blend);

		drawVignette(graphics, blend);

		// Letterbox bars.
		if (bar > 0) {
			graphics.fill(0, 0, width, bar, 0xFF000000);
			graphics.fill(0, height - bar, width, height, 0xFF000000);
		}

		if (!closing()) {
			drawStatus(graphics);
			drawSubtitle(graphics);
			String hint = "Esc to leave";
			graphics.text(font, hint, width - font.width(hint) - 10, Math.max(4, (bar - 8) / 2), 0xFF808080);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}

	/** Dark falloff at the edges of the frame, so attention sits in the middle. */
	private void drawVignette(GuiGraphicsExtractor graphics, double blend) {
		int strength = (int) (0x70 * blend);
		if (strength <= 0) {
			return;
		}
		int band = Math.max(24, height / 5);
		graphics.fillGradient(0, 0, width, band, (strength << 24), 0x00000000);
		graphics.fillGradient(0, height - band, width, height, 0x00000000, (strength << 24));

		// Sides: vertical gradients only exist, so step the alpha down across narrow strips.
		int steps = 10;
		int sideWidth = Math.max(30, width / 8);
		for (int i = 0; i < steps; i++) {
			int alpha = (int) (strength * 0.8 * (1.0 - (double) i / steps));
			int x = i * sideWidth / steps;
			int w = Math.max(1, sideWidth / steps);
			graphics.fill(x, 0, x + w, height, alpha << 24);
			graphics.fill(width - x - w, 0, width - x, height, alpha << 24);
		}
	}

	private void drawStatus(GuiGraphicsExtractor graphics) {
		ConversationPayloads.Status status = ConversationClient.status();
		if (status == null) {
			return;
		}
		int x = 12;
		int y = barHeight() + 10;

		String moodLine = "Mood: " + status.mood()
			+ (status.moodReason().isEmpty() ? "" : " - " + status.moodReason());
		String[] lines = {
			status.title(),
			moodLine,
			"Doing: " + status.activity(),
			"Health: " + status.health(),
			"Towards you: " + status.standing(),
			"Temperament: " + status.temperament(),
		};

		int panelWidth = 0;
		for (String line : lines) {
			panelWidth = Math.max(panelWidth, font.width(line));
		}
		panelWidth = Math.min(panelWidth, width / 2 - 20);
		graphics.fill(x - 6, y - 6, x + panelWidth + 8, y + lines.length * LINE_HEIGHT + 4, 0x99000000);

		for (int i = 0; i < lines.length; i++) {
			int color = i == 0 ? 0xFFFFFFFF : i == 1 ? moodColor(status.mood()) : 0xFFD0D0D0;
			graphics.text(font, font.plainSubstrByWidth(lines[i], panelWidth), x, y + i * LINE_HEIGHT, color);
		}
	}

	private static int moodColor(String mood) {
		return switch (mood) {
			case "content" -> 0xFFB6E88A;
			case "uneasy" -> 0xFFE8D66A;
			case "afraid" -> 0xFFFFA64D;
			case "terrified" -> 0xFFFF5555;
			case "grieving" -> 0xFF9AA6FF;
			case "angry" -> 0xFFFF6B6B;
			default -> 0xFF9ADFA0;
		};
	}

	/** The villager's last line as a subtitle above the bottom bar; "..." while it is thinking. */
	private void drawSubtitle(GuiGraphicsExtractor graphics) {
		int boxWidth = boxWidth();
		int left = (width - boxWidth) / 2;

		boolean waiting = ConversationClient.isWaiting();
		String speaker = ConversationClient.lastLineSpeaker();
		String shown = waiting ? "..." : ConversationClient.lastLine();

		List<FormattedCharSequence> wrapped = shown.isEmpty()
			? List.of()
			: font.split(Component.literal(shown), boxWidth - 16);
		int lines = Math.min(MAX_SPEECH_LINES, wrapped.size());
		int speechHeight = (lines == 0 ? 0 : lines * LINE_HEIGHT + 12) + (speaker.isEmpty() ? 0 : LINE_HEIGHT + 4);
		int bottom = height - barHeight() - 8;

		if (speechHeight > 0) {
			int top = bottom - speechHeight;
			graphics.fill(left, top, left + boxWidth, bottom, 0xBB000000);
			int y = top + 6;
			if (!speaker.isEmpty()) {
				graphics.text(font, speaker, left + 8, y, 0xFFFFD866);
				y += LINE_HEIGHT + 2;
			}
			for (int i = 0; i < lines; i++) {
				graphics.text(font, wrapped.get(i), left + 8, y, 0xFFFFFFFF);
				y += LINE_HEIGHT;
			}
		}

		// Transcript, dimmer, above the subtitle. Bounded so it never reaches the status panel.
		List<String> transcript = ConversationClient.transcript();
		int y = bottom - speechHeight - 8;
		for (int i = transcript.size() - 1; i >= 0 && y > height / 2; i--) {
			String entry = font.plainSubstrByWidth(transcript.get(i), boxWidth);
			y -= LINE_HEIGHT;
			graphics.text(font, entry, left, y, 0xFFB0B0B0);
		}
	}
}
