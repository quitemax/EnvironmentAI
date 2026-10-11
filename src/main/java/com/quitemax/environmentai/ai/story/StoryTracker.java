package com.quitemax.environmentai.ai.story;

import com.quitemax.environmentai.EnvironmentAI;
import com.quitemax.environmentai.ai.story.NarrationLedger.Kind;
import com.quitemax.environmentai.ai.story.PlayerStateRules.Facts;
import com.quitemax.environmentai.ai.story.PlayerStateRules.State;
import com.quitemax.environmentai.ai.villager.ProactiveVoice;
import com.quitemax.environmentai.ai.villager.VoiceLedger;
import com.quitemax.environmentai.config.EnvironmentAIConfig;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.ServerAdvancementManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Watches each player, derives their {@link State} with {@link PlayerStateRules}, notices the moments
 * worth narrating, and hands them to the {@link NarratorService}. It decides <em>when</em>; the
 * narrator decides how it is said and whether it is allowed right now.
 *
 * <p>Everything is observation: nothing here changes the world. Progress is read by polling the
 * player's advancements every couple of seconds (Fabric has no event for earning one, and polling
 * cannot silently stop firing the way an injection can).
 */
public final class StoryTracker {
	private static final int SCAN_INTERVAL_TICKS = 20;
	private static final int ADVANCEMENT_INTERVAL_TICKS = 40;
	private static final double HOSTILE_RADIUS = 16.0;
	private static final long NEVER = -1_000_000L;

	/** What the tracker remembers about one player. In memory only: it is all derivable or recent. */
	private static final class Log {
		final Set<String> completed = new HashSet<>();
		boolean initialized;
		long lastProgressTick;
		long lastDeathTick = NEVER;
		String dimension = "";
		boolean wasNight;
		String lastState = "";
		boolean nearDeathArmed = true;
		final Director.Tension tension = new Director.Tension();
		long lastNudgeTick = NEVER;
		long lastRumourTick = NEVER;
		State state = new State(PlayerStateRules.STEADY, "");
	}

	private final NarratorService narrator;
	private final ProactiveVoice voice;
	private final Map<UUID, Log> logs = new HashMap<>();

	public StoryTracker(NarratorService narrator, ProactiveVoice voice) {
		this.narrator = narrator;
		this.voice = voice;
	}

	public void register() {
		ServerPlayerEvents.JOIN.register(player -> logs.remove(player.getUUID()));
		ServerPlayerEvents.LEAVE.register(player -> logs.remove(player.getUUID()));
		ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
			Log log = logs.get(newPlayer.getUUID());
			if (log != null && log.initialized) {
				narrator.narrate(newPlayer, Kind.DEATH, "", "", log.state, true);
			}
		});
		ServerLivingEntityEvents.AFTER_DAMAGE.register(this::onDamaged);
		ServerLivingEntityEvents.AFTER_DEATH.register(this::onDeath);
		ServerTickEvents.END_SERVER_TICK.register(this::onServerTick);
	}

	// --- events --------------------------------------------------------------------------------

	private void onDamaged(
		LivingEntity entity, DamageSource source, float baseDamageTaken, float damageTaken, boolean blocked
	) {
		if (!(entity instanceof ServerPlayer player) || damageTaken <= 0) {
			return;
		}
		Log log = logs.get(player.getUUID());
		if (log == null || !log.initialized) {
			return;
		}
		log.tension.onDamage(damageTaken, player.level().getGameTime());
		double fraction = player.getHealth() / player.getMaxHealth();
		if (fraction < 0.25 && fraction > 0.0 && log.nearDeathArmed) {
			log.nearDeathArmed = false;
			narrator.narrate(player, Kind.NEAR_DEATH, "", "", log.state, false);
		}
	}

	private void onDeath(LivingEntity entity, DamageSource source) {
		if (entity instanceof ServerPlayer player) {
			Log log = logs.get(player.getUUID());
			if (log != null) {
				log.lastDeathTick = player.level().getGameTime();
				log.tension.onDeath(log.lastDeathTick);
			}
		}
	}

	// --- scanning ------------------------------------------------------------------------------

	private void onServerTick(MinecraftServer server) {
		int tick = server.getTickCount();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (Math.floorMod(tick + player.getUUID().hashCode(), SCAN_INTERVAL_TICKS) != 0) {
				continue;
			}
			if (player.isRemoved() || !player.isAlive() || !(player.level() instanceof ServerLevel level)) {
				continue;
			}
			scan(server, player, level, tick);
		}
	}

	private void scan(MinecraftServer server, ServerPlayer player, ServerLevel level, int tick) {
		long now = level.getGameTime();
		Log log = logs.computeIfAbsent(player.getUUID(), id -> new Log());

		String dimension = level.dimension().identifier().getPath();
		boolean overworld = dimension.equals("overworld");
		boolean night = overworld && Math.floorMod(level.getOverworldClockTime(), 24_000L) >= 13_000L;

		if (!log.initialized) {
			// First sight of this player this session: take stock silently. Narrating everything
			// they achieved in earlier sessions would be a flood of captions about the past.
			log.completed.addAll(completedGoals(server, player));
			log.lastProgressTick = now;
			log.dimension = dimension;
			log.wasNight = night;
			log.initialized = true;
		}

		// Progress. Polled less often than the rest: it touches the advancement manager.
		if (Math.floorMod(tick + player.getUUID().hashCode(), ADVANCEMENT_INTERVAL_TICKS) == 0) {
			pollProgress(server, player, log, now);
		}

		// Dimension change.
		if (!dimension.equals(log.dimension)) {
			log.dimension = dimension;
			narrator.narrate(player, Kind.DIMENSION, dimensionName(dimension), "", log.state, !overworld);
		}

		// Derived state.
		int hostiles = level.getEntitiesOfClass(
			LivingEntity.class, player.getBoundingBox().inflate(HOSTILE_RADIUS),
			other -> other instanceof Enemy && other.isAlive()
		).size();
		double health = player.getHealth() / player.getMaxHealth();
		if (health > 0.6) {
			log.nearDeathArmed = true;
		}
		log.state = PlayerStateRules.compute(new Facts(
			health, player.getFoodData().getFoodLevel(), hostiles, night, !overworld,
			now - log.lastDeathTick, now - log.lastProgressTick
		));

		// State-driven moments.
		if (log.state.name().equals(PlayerStateRules.HUNTED) && !log.lastState.equals(PlayerStateRules.HUNTED)) {
			narrator.narrate(player, Kind.HUNTED, "", "", log.state, false);
		}
		// The director: pressure builds and eases, and only at ease may it point anywhere.
		log.tension.tick(hostiles, night, health, now);
		if (log.tension.takeRelief(now)) {
			narrator.narrate(player, Kind.RELIEF, "", "", log.state, false);
		}
		if (Director.mayNudge(log.tension, now, now - log.lastProgressTick, now - log.lastNudgeTick,
			ProgressLadder.done(log.completed))) {
			String hint = ProgressLadder.next(log.completed).map(ProgressLadder.Goal::hint).orElse("");
			if (narrator.narrate(player, Kind.QUIET, "", hint, log.state, false)) {
				log.lastNudgeTick = now;
			}
		}
		// Next lever, only at the full setting: a villager in earshot mentions it as gossip.
		EnvironmentAIConfig config = EnvironmentAI.config();
		if (config != null && config.directorIntensity.voices()
			&& Director.mayRumour(log.tension, now, now - log.lastProgressTick,
				log.lastNudgeTick > log.lastProgressTick, now - log.lastNudgeTick,
				now - log.lastRumourTick, ProgressLadder.done(log.completed))) {
			Optional<ProgressLadder.Goal> next = ProgressLadder.next(log.completed);
			if (next.isPresent() && voice.offerNear(VoiceLedger.Reason.RUMOUR,
				"You have heard a rumour, and it comes to mind: \"" + next.get().rumour() + "\" "
					+ "Mention it in passing, as gossip you are passing on, not as an order.",
				player)) {
				log.lastRumourTick = now;
			}
		}
		if (log.wasNight && !night && overworld && !log.state.name().equals(PlayerStateRules.SHAKEN)) {
			narrator.narrate(player, Kind.DAWN, "", "", log.state, false);
		}
		log.wasNight = night;
		log.lastState = log.state.name();
	}

	private void pollProgress(MinecraftServer server, ServerPlayer player, Log log, long now) {
		Set<String> done = completedGoals(server, player);
		for (ProgressLadder.Goal goal : ProgressLadder.GOALS) {
			if (done.contains(goal.advancement()) && log.completed.add(goal.advancement())) {
				log.lastProgressTick = now;
				narrator.narrate(player, Kind.STAGE, goal.label(), "", log.state, goal.major());
			}
		}
	}

	private static Set<String> completedGoals(MinecraftServer server, ServerPlayer player) {
		Set<String> done = new HashSet<>();
		ServerAdvancementManager advancements = server.getAdvancements();
		for (ProgressLadder.Goal goal : ProgressLadder.GOALS) {
			Identifier id = Identifier.tryParse(goal.advancement());
			AdvancementHolder holder = id == null ? null : advancements.get(id);
			if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) {
				done.add(goal.advancement());
			}
		}
		return done;
	}

	private static String dimensionName(String path) {
		return switch (path) {
			case "the_nether" -> "the Nether";
			case "the_end" -> "the End";
			case "overworld" -> "the overworld";
			default -> path.replace('_', ' ');
		};
	}

	// --- debug ---------------------------------------------------------------------------------

	/** One line for {@code /environmentai inspect} on a player. */
	public String describe(ServerPlayer player) {
		Log log = logs.get(player.getUUID());
		if (log == null || !log.initialized) {
			return "not observed yet (wait a second)";
		}
		Optional<ProgressLadder.Goal> next = ProgressLadder.next(log.completed);
		return String.format(
			Locale.ROOT, "state=%s%s | tension=%.0f | act=%s | progress=%d/%d | next=%s",
			log.state.name(), log.state.reason().isEmpty() ? "" : " (" + log.state.reason() + ")",
			log.tension.value(), Director.act(log.completed),
			ProgressLadder.done(log.completed), ProgressLadder.GOALS.size(),
			next.map(ProgressLadder.Goal::label).orElse("the run is complete")
		);
	}

	/** Debug: produces a caption of the given kind now, skipping the gate. Returns false if unknown. */
	public boolean narrateNow(ServerPlayer player, String kindName) {
		Kind kind;
		try {
			kind = Kind.valueOf(kindName.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return false;
		}
		Log log = logs.computeIfAbsent(player.getUUID(), id -> new Log());
		String detail = switch (kind) {
			case STAGE -> ProgressLadder.next(log.completed).map(ProgressLadder.Goal::label).orElse("something");
			case DIMENSION -> "the Nether";
			case ELSEWHERE -> "another traveler fell";
			default -> "";
		};
		String hint = ProgressLadder.next(log.completed).map(ProgressLadder.Goal::hint).orElse("");
		narrator.narrateNow(player, kind, detail, hint, log.state);
		return true;
	}
}
