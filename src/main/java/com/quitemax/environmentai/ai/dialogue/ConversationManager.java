package com.quitemax.environmentai.ai.dialogue;

import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.state.ModAttachments;
import com.quitemax.environmentai.ai.state.VillagerState;
import com.quitemax.environmentai.ai.villager.VillagerMood;
import com.quitemax.environmentai.ai.villager.VillagerMoodService;
import com.quitemax.environmentai.network.ConversationPayloads;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The server's side of the conversation state: who is talking to which villager, and when it
 * ends. The client frames the shot and shows the screen; it never decides anything.
 *
 * <p>While a conversation is active the villager is held still (its Brain tick is skipped, see
 * {@code VillagerFreezeMixin}) and keeps facing the player. It ends when:
 * <ul>
 *   <li>the player leaves (Escape);</li>
 *   <li>the player moves more than {@code MOVE_LIMIT} from where it began;</li>
 *   <li>the villager is hurt, or a hostile creature comes within {@code THREAT_RADIUS};</li>
 *   <li>the player says something that frightens it, in which case it runs away;</li>
 *   <li>either party dies, disconnects or changes dimension.</li>
 * </ul>
 */
public final class ConversationManager {
	/** Metres the player may drift from where the conversation began. */
	private static final double MOVE_LIMIT = 1.0;
	private static final double START_RANGE = 6.0;
	private static final double THREAT_RADIUS = 8.0;
	private static final int THREAT_CHECK_INTERVAL = 5;
	private static final double FLEE_DISTANCE = 12.0;

	private static ConversationManager instance;

	private record Conversation(ServerPlayer player, Villager villager, Vec3 startPos) {
	}

	private final DialogueService dialogue;
	private final Map<UUID, Conversation> byPlayer = new HashMap<>();
	private final Map<UUID, UUID> playerByVillager = new HashMap<>();

	public ConversationManager(DialogueService dialogue) {
		this.dialogue = dialogue;
	}

	public static ConversationManager instance() {
		return instance;
	}

	/** Whether this villager is mid-conversation. Read from the Brain-freeze mixin every tick. */
	public static boolean isTalking(UUID villager) {
		return instance != null && instance.playerByVillager.containsKey(villager);
	}

	public void register() {
		instance = this;

		ServerPlayNetworking.registerGlobalReceiver(ConversationPayloads.StartRequest.TYPE,
			(payload, context) -> start(context.player(), payload.villagerId()));
		ServerPlayNetworking.registerGlobalReceiver(ConversationPayloads.Say.TYPE,
			(payload, context) -> say(context.player(), payload.text()));
		ServerPlayNetworking.registerGlobalReceiver(ConversationPayloads.Leave.TYPE,
			(payload, context) -> end(context.player(), "", false));

		ServerLivingEntityEvents.AFTER_DAMAGE.register(this::onDamaged);
		ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
	}

	// --- starting ------------------------------------------------------------------------------

	private void start(ServerPlayer player, int villagerId) {
		if (byPlayer.containsKey(player.getUUID())) {
			return;
		}
		Entity entity = player.level().getEntity(villagerId);
		if (!(entity instanceof Villager villager) || !villager.isAlive()) {
			refuse(player, "There is nobody there to talk to.");
			return;
		}
		if (player.distanceTo(villager) > START_RANGE || !player.hasLineOfSight(villager)) {
			refuse(player, "You are too far away to talk.");
			return;
		}
		if (villager.isSleeping()) {
			refuse(player, "They are asleep.");
			return;
		}
		if (villager.isTrading() || playerByVillager.containsKey(villager.getUUID())) {
			refuse(player, "They are busy.");
			return;
		}

		byPlayer.put(player.getUUID(), new Conversation(player, villager, player.position()));
		playerByVillager.put(villager.getUUID(), player.getUUID());
		hold(villager, player);

		ServerPlayNetworking.send(player, new ConversationPayloads.Started(
			villager.getId(), VillagerContext.displayName(villager)
		));
	}

	private static void refuse(ServerPlayer player, String reason) {
		ServerPlayNetworking.send(player, new ConversationPayloads.Ended(reason));
	}

	// --- talking -------------------------------------------------------------------------------

	private void say(ServerPlayer player, String rawText) {
		Conversation conversation = byPlayer.get(player.getUUID());
		if (conversation == null) {
			return;
		}
		Optional<String> refusal = dialogue.talkTo(player, conversation.villager(), rawText);
		refusal.ifPresent(message -> ServerPlayNetworking.send(player, new ConversationPayloads.Line("", message)));
	}

	/**
	 * Called by {@link DialogueService} when a reply is ready: shows it on the conversation screen,
	 * and if it frightened the villager, ends the conversation and sends it running.
	 */
	void onReply(ServerPlayer player, Villager villager, String name, String reply, String feeling) {
		Conversation conversation = byPlayer.get(player.getUUID());
		if (conversation == null || conversation.villager() != villager) {
			return;
		}
		ServerPlayNetworking.send(player, new ConversationPayloads.Line(name, reply));

		if (feeling.equals("afraid")) {
			end(player, name + " backs away from you and hurries off.", true);
			flee(villager, player);
		}
	}

	// --- ending --------------------------------------------------------------------------------

	/**
	 * @param reason shown to the player; empty for none
	 * @param notify whether to tell the client (false when the client itself asked to leave)
	 */
	private void end(ServerPlayer player, String reason, boolean notify) {
		Conversation conversation = byPlayer.remove(player.getUUID());
		if (conversation == null) {
			return;
		}
		playerByVillager.remove(conversation.villager().getUUID());
		if (notify) {
			ServerPlayNetworking.send(player, new ConversationPayloads.Ended(reason));
		}
	}

	private void onDamaged(
		LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked
	) {
		if (!(entity instanceof Villager villager) || damageTaken <= 0) {
			return;
		}
		UUID playerId = playerByVillager.get(villager.getUUID());
		Conversation conversation = playerId == null ? null : byPlayer.get(playerId);
		if (conversation != null) {
			end(conversation.player(), VillagerContext.displayName(villager) + " cries out in pain.", true);
		}
	}

	private void onServerTick(MinecraftServer server) {
		if (byPlayer.isEmpty()) {
			return;
		}
		boolean checkThreats = server.getTickCount() % THREAT_CHECK_INTERVAL == 0;

		Iterator<Conversation> iterator = byPlayer.values().iterator();
		while (iterator.hasNext()) {
			Conversation conversation = iterator.next();
			ServerPlayer player = conversation.player();
			Villager villager = conversation.villager();

			String reason = endReason(conversation, checkThreats);
			if (reason != null) {
				iterator.remove();
				playerByVillager.remove(villager.getUUID());
				if (!player.isRemoved()) {
					ServerPlayNetworking.send(player, new ConversationPayloads.Ended(reason));
				}
				continue;
			}
			hold(villager, player);
		}
	}

	/** Null if the conversation should continue. */
	private static String endReason(Conversation conversation, boolean checkThreats) {
		ServerPlayer player = conversation.player();
		Villager villager = conversation.villager();

		if (player.isRemoved() || !player.isAlive()) {
			return "";
		}
		if (villager.isRemoved() || !villager.isAlive()) {
			return "The villager is gone.";
		}
		if (villager.level() != player.level()) {
			return "";
		}
		if (player.position().distanceTo(conversation.startPos()) > MOVE_LIMIT) {
			return "You step away from " + VillagerContext.displayName(villager) + ".";
		}
		if (checkThreats && hostileNear(villager)) {
			return VillagerContext.displayName(villager) + " is startled by something nearby.";
		}
		return null;
	}

	private static boolean hostileNear(Villager villager) {
		return !villager.level()
			.getEntitiesOfClass(LivingEntity.class, villager.getBoundingBox().inflate(THREAT_RADIUS),
				other -> other instanceof Enemy && other.isAlive() && villager.distanceTo(other) <= THREAT_RADIUS)
			.isEmpty();
	}

	// --- villager control ----------------------------------------------------------------------

	/** Keeps the villager still and facing the player. Cheap enough to run every tick. */
	private static void hold(Villager villager, ServerPlayer player) {
		villager.getNavigation().stop();
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.getBrain().setMemoryWithExpiry(
			MemoryModuleType.LOOK_TARGET, new EntityTracker(player, true), 40L
		);
		villager.getLookControl().setLookAt(player);
	}

	/**
	 * A one-shot scurry away. The Brain takes over again afterwards, which is fine: a villager that
	 * has been frightened by a player gets its mood from the recorded event, not from this walk.
	 */
	private static void flee(Villager villager, ServerPlayer player) {
		Vec3 away = villager.position().subtract(player.position());
		if (away.lengthSqr() < 1.0E-4) {
			away = new Vec3(1.0, 0.0, 0.0);
		}
		Vec3 target = villager.position().add(away.normalize().scale(FLEE_DISTANCE));
		villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(target, 0.9F, 2));
		VillagerMoodService.recordExternal(villager, VillagerMood.THREATENED, "was threatened by a traveler");
		EnvironmentAI.LOGGER.debug("Villager {} fled from a frightening conversation", villager.getUUID());
	}

	/** For the debug command and logs. */
	public String describe(UUID player) {
		Conversation conversation = byPlayer.get(player);
		return conversation == null
			? "no conversation"
			: String.format(Locale.ROOT, "talking with %s", VillagerContext.displayName(conversation.villager()));
	}
}
