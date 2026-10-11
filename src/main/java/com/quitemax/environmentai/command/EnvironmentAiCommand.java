package com.quitemax.environmentai.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.quitemax.environmentai.ai.state.MobAiState;
import com.quitemax.environmentai.ai.state.ModAttachments;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

import java.util.Locale;

/** Debug command: {@code /environmentai inspect <target>} prints a mob's live MobAiState. */
public final class EnvironmentAiCommand {
	private EnvironmentAiCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("environmentai")
			.then(Commands.literal("inspect")
				.then(Commands.argument("target", EntityArgument.entity())
					.executes(EnvironmentAiCommand::inspect))));
	}

	private static int inspect(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
		Entity target = EntityArgument.getEntity(context, "target");
		if (!(target instanceof Mob mob)) {
			context.getSource().sendFailure(Component.literal("Target is not a mob."));
			return 0;
		}

		MobAiState state = mob.getAttachedOrCreate(ModAttachments.MOB_AI_STATE);
		// Live facts first: they are what the rules and the model are actually deciding on, so a
		// mismatch between these and what you see in-game is the quickest way to localise a bug.
		LivingEntity liveTarget = mob.getTarget();
		String live = String.format(
			Locale.ROOT, "hp=%.1f/%.1f (%d%%) | live target=%s",
			mob.getHealth(), mob.getMaxHealth(), Math.round(100 * mob.getHealth() / mob.getMaxHealth()),
			liveTarget == null
				? "none"
				: String.format(
					Locale.ROOT, "%s at %.1f blocks, hp=%.1f/%.1f",
					EntityType.getKey(liveTarget.getType()).getPath(), mob.distanceTo(liveTarget),
					liveTarget.getHealth(), liveTarget.getMaxHealth()
				)
		);
		String report = String.format(
			"[%s] action=%s (stuck=%d ticks) | %s | disposition=%s | intent=%s | target=%s | memory=%s",
			mob.getType(), state.currentAction(), state.consecutiveSameActionTicks(), live,
			state.narrative().disposition(), state.narrative().intent(),
			state.narrative().targetDescription(), state.narrative().memoryNotes()
		);

		context.getSource().sendSuccess(() -> Component.literal(report), false);
		return 1;
	}
}
