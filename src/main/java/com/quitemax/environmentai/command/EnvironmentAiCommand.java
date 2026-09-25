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
import net.minecraft.world.entity.Mob;

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
		String report = String.format(
			"[%s] action=%s (stuck=%d ticks) | disposition=%s | intent=%s | target=%s | memory=%s",
			mob.getType(), state.currentAction(), state.consecutiveSameActionTicks(),
			state.narrative().disposition(), state.narrative().intent(),
			state.narrative().targetDescription(), state.narrative().memoryNotes()
		);

		context.getSource().sendSuccess(() -> Component.literal(report), false);
		return 1;
	}
}
