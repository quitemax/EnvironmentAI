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
	 * @param hint        an oblique nudge toward the goal, for the narrator
	 * @param rumour      the same pointer as village gossip: plainer and more concrete than the hint,
	 *                    because a villager would say it, and it is only used after the hint was missed
	 * @param major       whether reaching it is worth telling the other players about
	 */
	public record Goal(String advancement, String label, String hint, String rumour, boolean major) {
	}

	public static final List<Goal> GOALS = List.of(
		new Goal("minecraft:story/mine_stone", "stone",
			"The ground itself is the first thing worth taking from this place.",
			"You can dig into the ground and get stone, and stone makes better tools than wood.", false),
		new Goal("minecraft:story/upgrade_tools", "better tools",
			"A wooden pick will only get a person so far; stone bites harder.",
			"A pickaxe made of cobblestone digs faster and reaches iron.", false),
		new Goal("minecraft:story/smelt_iron", "iron",
			"Something dull and grey in the rock, waiting for a fire hot enough to wake it.",
			"There is iron in the hills; you smelt the raw ore in a furnace to get ingots.", false),
		new Goal("minecraft:story/obtain_armor", "armour",
			"Nobody survives the nights in a shirt. Iron can be worn.",
			"Armour from iron ingots saves lives; nobody should walk out at night without it.", false),
		new Goal("minecraft:story/lava_bucket", "lava",
			"Down where the light goes orange, there is a river that never learned to be still.",
			"Deep underground there is lava; with a bucket you could carry some of it.", false),
		new Goal("minecraft:story/iron_tools", "iron tools",
			"A full set of iron would make the miles shorter.",
			"Iron tools last longer and cut quicker; a whole set would be worth the ore.", false),
		new Goal("minecraft:story/mine_diamond", "diamond",
			"The deepest dark keeps something bright. It does not give it up for free.",
			"They say diamonds sleep in the deepest stone, and only an iron pick can wake them.", true),
		new Goal("minecraft:story/form_obsidian", "obsidian",
			"Water on lava makes a black glass nothing can break but the best of picks.",
			"Pour water over still lava and it turns to obsidian, hard as anything.", false),
		new Goal("minecraft:story/enter_the_nether", "the Nether",
			"A frame of that black glass, and a spark. There is another place on the other side.",
			"Obsidian stacked into a doorway and lit with a flint spark opens a way to somewhere else.", true),
		new Goal("minecraft:nether/find_fortress", "a fortress",
			"Out across the red waste, something built stands where nothing should be able to build.",
			"Travellers say there is a fortress of dark bricks out in that red country.", true),
		new Goal("minecraft:nether/obtain_blaze_rod", "blaze rods",
			"The fortress keeps its fire in cages that walk. Take a little of it.",
			"The blazes in that fortress carry rods worth having; you need them for eyes.", false),
		new Goal("minecraft:story/follow_ender_eye", "the stronghold",
			"An eye thrown to the sky does not fall straight. It knows where it wants to go.",
			"An eye of ender, thrown, floats toward the stronghold; follow where it leads.", true),
		new Goal("minecraft:story/enter_the_end", "the End",
			"Under the stronghold, a ring of frames with a hollow at the centre of each.",
			"In the stronghold there is a ring of frames; an eye in each opens the way to the End.", true),
		new Goal("minecraft:end/kill_dragon", "the dragon",
			"Whatever lives out there has been circling a long time. It is not going to leave.",
			"The dragon in the End will not go away by itself; somebody has to face it.", true)
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
