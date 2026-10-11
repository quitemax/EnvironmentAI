package com.quitemax.environmentai.network;

import com.quitemax.environmentai.EnvironmentAI;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The narrator's one message: a caption to show in a comic-panel box. Server to client only. */
public final class NarrationPayloads {
	private NarrationPayloads() {
	}

	/**
	 * @param text  the caption, already final
	 * @param style how to draw it: {@code narration}, {@code hint} or {@code elsewhere}
	 */
	public record Caption(String text, String style) implements CustomPacketPayload {
		public static final CustomPacketPayload.Type<Caption> TYPE =
			new CustomPacketPayload.Type<>(EnvironmentAI.id("narration_caption"));
		public static final StreamCodec<ByteBuf, Caption> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Caption::text,
			ByteBufCodecs.STRING_UTF8, Caption::style,
			Caption::new
		);

		@Override
		public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	public static void register() {
		PayloadTypeRegistry.clientboundPlay().register(Caption.TYPE, Caption.CODEC);
	}
}
