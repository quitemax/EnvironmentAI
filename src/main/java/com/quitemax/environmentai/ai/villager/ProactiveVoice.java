package com.quitemax.environmentai.ai.villager;

import com.quitemax.environmentai.ai.dialogue.ConversationManager;
import com.quitemax.environmentai.ai.dialogue.DialogueService;
import com.quitemax.environmentai.ai.state.ModAttachments;
import com.quitemax.environmentai.ai.state.VillagerState;
import com.quitemax.environmentai.ai.villager.VoiceLedger.Reason;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.npc.villager.Villager;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Lets a villager speak first, without letting a village talk over itself. An event is usually
 * witnessed by many villagers at once; this turns that into <em>one</em> line, from the villager
 * best placed to say it, phrased on behalf of the group.
 *
 * <p>The decision runs in three steps, in this order so that a failed attempt costs nothing:
 * find who could speak (awake, free, within earshot with a clear line to the player); pick the
 * nearest of them; and only then ask the {@link VoiceLedger}, which enforces the per-player gap
 * and the per-reason cooldown. Everyone else stays silent, and the speaker is told how many
 * villagers share the feeling so it can say "we".
 */
public final class ProactiveVoice {
	/** Villagers this close to the origin are considered to share the feeling. */
	private static final double GROUP_RADIUS = 24.0;
	/** The speaker must be close enough to be heard. */
	private static final double HEARING_RADIUS = 12.0;

	private final DialogueService dialogue;
	private final VoiceLedger ledger = new VoiceLedger();
	private final boolean enabled;

	public ProactiveVoice(DialogueService dialogue, boolean enabled) {
		this.dialogue = dialogue;
		this.enabled = enabled;
	}

	/**
	 * @param situation what happened, as the speaker would put it ("A traveler has come near.")
	 * @param origin    the villager where it happened; its neighbours form the group
	 */
	public void offer(Reason reason, String situation, Villager origin, ServerPlayer player) {
		if (!enabled || player.isRemoved() || !(origin.level() instanceof net.minecraft.server.level.ServerLevel)) {
			return;
		}
		// Never talk over a conversation the player is having.
		if (ConversationManager.isInConversation(player.getUUID())) {
			return;
		}

		List<Villager> group = origin.level()
			.getEntitiesOfClass(Villager.class, origin.getBoundingBox().inflate(GROUP_RADIUS), this::isAvailable);

		Optional<Villager> speaker = group.stream()
			.filter(villager -> player.distanceTo(villager) <= HEARING_RADIUS && villager.hasLineOfSight(player))
			.min(Comparator.comparingDouble(player::distanceTo));
		if (speaker.isEmpty()) {
			return;
		}

		if (!ledger.tryReserve(player.getUUID(), reason, System.currentTimeMillis())) {
			return;
		}
		dialogue.speakProactive(speaker.get(), player, situation, group.size(), reason.name().toLowerCase());
	}

	/**
	 * Like {@link #offer}, for a line that is not about any villager's own experience (a rumour):
	 * the speaker is simply the nearest available villager in earshot of the player.
	 *
	 * @return whether a villager was asked to speak
	 */
	public boolean offerNear(Reason reason, String situation, ServerPlayer player) {
		if (!enabled || player.isRemoved() || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)) {
			return false;
		}
		if (ConversationManager.isInConversation(player.getUUID())) {
			return false;
		}
		Optional<Villager> speaker = level
			.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(HEARING_RADIUS), this::isAvailable)
			.stream()
			.filter(villager -> villager.hasLineOfSight(player))
			.min(Comparator.comparingDouble(player::distanceTo));
		if (speaker.isEmpty() || !ledger.tryReserve(player.getUUID(), reason, System.currentTimeMillis())) {
			return false;
		}
		dialogue.speakProactive(speaker.get(), player, situation, 1, reason.name().toLowerCase());
		return true;
	}

	private boolean isAvailable(Villager villager) {
		if (!villager.isAlive() || villager.isSleeping() || villager.isTrading()) {
			return false;
		}
		VillagerState state = villager.getAttachedOrCreate(ModAttachments.VILLAGER_STATE);
		return !state.isDialogueInFlight() && !ConversationManager.isTalking(villager.getUUID());
	}
}
