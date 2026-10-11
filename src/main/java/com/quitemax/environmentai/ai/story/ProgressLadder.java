package com.quitemax.environmentai.ai.story;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The run from a bare start to the Ender Dragon, as an ordered list of goals keyed to the game's
 * own advancements. The advancement tree is the skill tree, but it has gaps: blaze rods and the
 * fortress that holds them live in the {@code nether} tab, not on the story path, so they are added
 * here by hand. The order is one sensible linearisation; the game allows others, and "next goal"
 * only means "the first one not done yet".
 *
 * <p>Pure data and logic, so it can be tested without the game. Hints are written to be said
 * obliquely (a place to look, not an instruction), because the narrator is a storyteller, not a
 * checklist.
 */
public final class ProgressLadder {
	/**
	 * @param advancement the vanilla advancement id that marks this goal done
	 * @param label       a short plain name, for debug output and as a fact for the narrator
	 * @param hint        an oblique nudge toward the goal
	 * @param major       whether reaching it is worth telling the other players about
	 */
	public record Goal(String advancement, String label, String hint, boolean major) {
	}

	public static final List<Goal> GOALS = List.of(
		new Goal("minecraft:story/mine_stone", "stone",
			"The ground itself is the first thing worth taking from this place.", false),
		new Goal("minecraft:story/upgrade_tools", "better tools",
			"A wooden pick will only get a person so far; stone bites harder.", false),
		new Goal("minecraft:story/smelt_iron", "iron",
			"Something dull and grey in the rock, waiting for a fire hot enough to wake it.", false),
		new Goal("minecraft:story/obtain_armor", "armour",
			"Nobody survives the nights in a shirt. Iron can be worn.", false),
		new Goal("minecraft:story/lava_bucket", "lava",
			"Down where the light goes orange, there is a river that never learned to be still.", false),
		new Goal("minecraft:story/iron_tools", "iron tools",
			"A full set of iron would make the miles shorter.", false),
		new Goal("minecraft:story/mine_diamond", "diamond",
			"The deepest dark keeps something bright. It does not give it up for free.", true),
		new Goal("minecraft:story/form_obsidian", "obsidian",
			"Water on lava makes a black glass nothing can break but the best of picks.", false),
		new Goal("minecraft:story/enter_the_nether", "the Nether",
			"A frame of that black glass, and a spark. There is another place on the other side.", true),
		new Goal("minecraft:nether/find_fortress", "a fortress",
			"Out across the red waste, something built stands where nothing should be able to build.", true),
		new Goal("minecraft:nether/obtain_blaze_rod", "blaze rods",
			"The fortress keeps its fire in cages that walk. Take a little of it.", false),
		new Goal("minecraft:story/follow_ender_eye", "the stronghold",
			"An eye thrown to the sky does not fall straight. It knows where it wants to go.", true),
		new Goal("minecraft:story/enter_the_end", "the End",
			"Under the stronghold, a ring of frames with a hollow at the centre of each.", true),
		new Goal("minecraft:end/kill_dragon", "the dragon",
			"Whatever lives out there has been circling a long time. It is not going to leave.", true)
	);

	private ProgressLadder() {
	}

	/** The first goal whose advancement is not in {@code completed}, or empty if the run is finished. */
	public static Optional<Goal> next(Set<String> completed) {
		return GOALS.stream().filter(goal -> !completed.contains(goal.advancement())).findFirst();
	}

	/** How many goals are done. */
	public static int done(Set<String> completed) {
		return (int) GOALS.stream().filter(goal -> completed.contains(goal.advancement())).count();
	}

	public static Optional<Goal> byAdvancement(String advancement) {
		return GOALS.stream().filter(goal -> goal.advancement().equals(advancement)).findFirst();
	}
}
