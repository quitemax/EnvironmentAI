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
 * The conversation overlay: the villager's last line in a speech box, a short transcript, and a
 * text field that stays open after Enter, so a conversation is a sequence of lines rather than
 * open-chat-type-send-close for each. The world stays visible behind it and the game is not paused
 * (a paused integrated server would stop the very reply we are waiting for).
 */
public final class ConversationScreen extends Screen {
	private static final int MAX_INPUT = 200;
	private static final int MAX_SPEECH_LINES = 4;
	private static final int LINE_HEIGHT = 11;

	private EditBox input;
	/** Set once the server has been told, so removing the screen does not tell it twice. */
	private boolean leaveSent;

	public ConversationScreen() {
		super(Component.literal("Conversation"));
	}

	@Override
	protected void init() {
		int boxWidth = boxWidth();
		input = new EditBox(font, (width - boxWidth) / 2, height - 30, boxWidth, 20, Component.literal("Say something"));
		input.setMaxLength(MAX_INPUT);
		// Clicking elsewhere must not take focus away: the field is the whole interaction.
		input.setCanLoseFocus(false);
		addRenderableWidget(input);
		setInitialFocus(input);
	}

	private int boxWidth() {
		return Math.min(width - 40, 440);
	}

	// --- input ---------------------------------------------------------------------------------

	@Override
	public boolean keyPressed(KeyEvent event) {
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

	/** Escape: tell the server, then close. {@code super.onClose} clears the screen. */
	@Override
	public void onClose() {
		sendLeave();
		ConversationClient.end();
		super.onClose();
	}

	/** Called whenever the screen goes away for any reason (death screen, disconnect, server end). */
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

	/** The server ended it; close without echoing a Leave back. */
	void closeFromServer() {
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
		int boxWidth = boxWidth();
		int left = (width - boxWidth) / 2;

		// Title.
		String title = "Talking with " + ConversationClient.villagerName() + "   (Esc to leave)";
		int titleWidth = font.width(title) + 16;
		int titleLeft = (width - titleWidth) / 2;
		graphics.fill(titleLeft, 8, titleLeft + titleWidth, 8 + LINE_HEIGHT + 6, 0x99000000);
		graphics.centeredText(font, title, width / 2, 11, 0xFFFFFFFF);

		// Speech box: the villager's last line, or an ellipsis while it is thinking.
		boolean waiting = ConversationClient.isWaiting();
		String speaker = ConversationClient.lastLineSpeaker();
		String line = ConversationClient.lastLine();
		String shown = waiting ? "..." : (line.isEmpty() ? "" : line);

		List<FormattedCharSequence> wrapped = shown.isEmpty()
			? List.of()
			: font.split(Component.literal(shown), boxWidth - 16);
		int lines = Math.min(MAX_SPEECH_LINES, wrapped.size());
		int speechHeight = (lines == 0 ? 0 : lines * LINE_HEIGHT + 12) + (speaker.isEmpty() ? 0 : LINE_HEIGHT + 4);
		int speechBottom = height - 36;

		if (speechHeight > 0) {
			int top = speechBottom - speechHeight;
			graphics.fill(left, top, left + boxWidth, speechBottom, 0xBB000000);
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

		// Transcript, dimmer, above the speech box. Bounded so it never reaches the title.
		List<String> transcript = ConversationClient.transcript();
		int y = speechBottom - speechHeight - 8;
		for (int i = transcript.size() - 1; i >= 0 && y > 40; i--) {
			String entry = font.plainSubstrByWidth(transcript.get(i), boxWidth);
			y -= LINE_HEIGHT;
			graphics.text(font, entry, left, y, 0xFFB0B0B0);
		}

		super.extractRenderState(graphics, mouseX, mouseY, partialTick);
	}
}
