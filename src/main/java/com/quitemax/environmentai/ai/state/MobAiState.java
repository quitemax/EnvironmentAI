package com.quitemax.environmentai.ai.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.quitemax.environmentai.ai.action.MobAction;
import com.quitemax.environmentai.ai.narrative.NarrativeState;
import com.quitemax.environmentai.ai.narrative.NarrativeStateCodec;

/**
 * Per-mob AI state: the System 2 narrative, the currently active System 1 action, and the
 * scheduling bookkeeping both tiers need (call timestamps, in-flight guards, debounce/cooldown
 * tracking). Only {@code narrative} and {@code currentAction} are persisted — the rest is
 * real-time scheduling metadata that's meaningless across a restart and simply resets.
 */
public final class MobAiState {
	public static final Codec<MobAiState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
		NarrativeStateCodec.CODEC.fieldOf("narrative").forGetter(state -> state.narrative),
		MobAction.CODEC.optionalFieldOf("current_action", MobAction.fallback()).forGetter(state -> state.currentAction)
	).apply(instance, MobAiState::new));

	private NarrativeState narrative;
	private MobAction currentAction;

	private int consecutiveSameActionTicks = 0;
	private boolean stuckEscalated = false;
	private boolean nearDeathEscalated = false;

	private long lastSystem1DispatchTime = 0L;
	private boolean system1InFlight = false;

	private long lastSystem2DispatchTime = 0L;
	private boolean system2InFlight = false;
	private String pendingSystem2TriggerReason = null;
	private long pendingSystem2TriggerTime = 0L;

	private MobAiState(NarrativeState narrative, MobAction currentAction) {
		this.narrative = narrative;
		this.currentAction = currentAction;
	}

	public static MobAiState createDefault() {
		return new MobAiState(NarrativeState.initial(0L), MobAction.fallback());
	}

	public NarrativeState narrative() {
		return narrative;
	}

	public void updateNarrative(NarrativeState narrative) {
		this.narrative = narrative;
	}

	public MobAction currentAction() {
		return currentAction;
	}

	public void updateAction(MobAction action) {
		if (action == this.currentAction) {
			consecutiveSameActionTicks++;
		} else {
			consecutiveSameActionTicks = 0;
			stuckEscalated = false;
		}
		this.currentAction = action;
	}

	public int consecutiveSameActionTicks() {
		return consecutiveSameActionTicks;
	}

	/** Guards the "stuck repeating the same action" escalation so it fires once per streak, not every tick. */
	public boolean isStuckEscalated() {
		return stuckEscalated;
	}

	public void setStuckEscalated(boolean escalated) {
		this.stuckEscalated = escalated;
	}

	/** Guards the "near death" escalation so it fires once per drop below the threshold, not every tick. */
	public boolean isNearDeathEscalated() {
		return nearDeathEscalated;
	}

	public void setNearDeathEscalated(boolean escalated) {
		this.nearDeathEscalated = escalated;
	}

	public long lastSystem1DispatchTime() {
		return lastSystem1DispatchTime;
	}

	public void markSystem1Dispatched(long now) {
		this.lastSystem1DispatchTime = now;
	}

	public boolean isSystem1InFlight() {
		return system1InFlight;
	}

	public void setSystem1InFlight(boolean inFlight) {
		this.system1InFlight = inFlight;
	}

	public long lastSystem2DispatchTime() {
		return lastSystem2DispatchTime;
	}

	public void markSystem2Dispatched(long now) {
		this.lastSystem2DispatchTime = now;
	}

	public boolean isSystem2InFlight() {
		return system2InFlight;
	}

	public void setSystem2InFlight(boolean inFlight) {
		this.system2InFlight = inFlight;
	}

	/** Records (or refreshes) a pending narrative-refresh trigger for the debounce window. */
	public void setPendingSystem2Trigger(String reason, long now) {
		this.pendingSystem2TriggerReason = reason;
		this.pendingSystem2TriggerTime = now;
	}

	public String pendingSystem2TriggerReason() {
		return pendingSystem2TriggerReason;
	}

	public long pendingSystem2TriggerTime() {
		return pendingSystem2TriggerTime;
	}

	public boolean hasPendingSystem2Trigger() {
		return pendingSystem2TriggerReason != null;
	}

	public void clearPendingSystem2Trigger() {
		this.pendingSystem2TriggerReason = null;
	}
}
