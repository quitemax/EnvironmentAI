package com.quitemax.environmentai.network;

import com.quitemax.environmentai.EnvironmentAI;
import io.netty.buffer.ByteBuf;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The conversation protocol between client and server. The server owns the state (who is talking to
 * whom, when it ends); the client only renders it and sends what the player does.
 *
 * <p>Client to server: {@link StartRequest}, {@link Say}, {@link Leave}.
 * Server to client: {@link Started}, {@link Line}, {@link Ended}.
 */
public final class ConversationPayloads {
	private ConversationPayloads() {
	}

	private static <T extends CustomPacketPayload> CustomPacketPayload.Type<T> typeOf(String path) {
		return new CustomPacketPayload.Type<>(EnvironmentAI.id(path));
	}

	/** The player pressed the talk key while looking at a villager. */
	public record StartRequest(int villagerId) implements CustomPacketPayload {
		public static final Type<StartRequest> TYPE = typeOf("conversation_start");
		public static final StreamCodec<ByteBuf, StartRequest> CODEC =
			StreamCodec.composite(ByteBufCodecs.VAR_INT, StartRequest::villagerId, StartRequest::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The player sent a line of text. */
	public record Say(String text) implements CustomPacketPayload {
		public static final Type<Say> TYPE = typeOf("conversation_say");
		public static final StreamCodec<ByteBuf, Say> CODEC =
			StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Say::text, Say::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The player left the conversation (Escape). */
	public record Leave() implements CustomPacketPayload {
		public static final Type<Leave> TYPE = typeOf("conversation_leave");
		public static final StreamCodec<ByteBuf, Leave> CODEC = StreamCodec.unit(new Leave());

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The server accepted the conversation: frame this villager and open the screen. */
	public record Started(int villagerId, String name) implements CustomPacketPayload {
		public static final Type<Started> TYPE = typeOf("conversation_started");
		public static final StreamCodec<ByteBuf, Started> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, Started::villagerId,
			ByteBufCodecs.STRING_UTF8, Started::name,
			Started::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The villager said something; {@code speaker} is empty for a system line. */
	public record Line(String speaker, String text) implements CustomPacketPayload {
		public static final Type<Line> TYPE = typeOf("conversation_line");
		public static final StreamCodec<ByteBuf, Line> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, Line::speaker,
			ByteBufCodecs.STRING_UTF8, Line::text,
			Line::new
		);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** The conversation is over (or was refused); {@code reason} is shown to the player. */
	public record Ended(String reason) implements CustomPacketPayload {
		public static final Type<Ended> TYPE = typeOf("conversation_ended");
		public static final StreamCodec<ByteBuf, Ended> CODEC =
			StreamCodec.composite(ByteBufCodecs.STRING_UTF8, Ended::reason, Ended::new);

		@Override
		public Type<? extends CustomPacketPayload> type() {
			return TYPE;
		}
	}

	/** Registers every payload type. Must run on both client and server, before any are sent. */
	public static void register() {
		PayloadTypeRegistry.serverboundPlay().register(StartRequest.TYPE, StartRequest.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Say.TYPE, Say.CODEC);
		PayloadTypeRegistry.serverboundPlay().register(Leave.TYPE, Leave.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Started.TYPE, Started.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Line.TYPE, Line.CODEC);
		PayloadTypeRegistry.clientboundPlay().register(Ended.TYPE, Ended.CODEC);
	}
}
