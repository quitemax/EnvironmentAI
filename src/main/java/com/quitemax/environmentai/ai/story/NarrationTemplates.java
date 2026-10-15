package com.quitemax.environmentai.ai.story;

import com.quitemax.environmentai.ai.story.NarrationLedger.Kind;
import com.quitemax.environmentai.config.NarratorStyle;
import com.quitemax.environmentai.config.StoryArc;

/**
 * Captions that need no model. Used when the model is slow, absent or fails, and always for the
 * short notes sent to the other players. Hand-written, in the noir default voice; other styles get
 * the same facts in a plainer register rather than a wrong-sounding imitation.
 *
 * <p>Pure, so every kind can be checked for length and emptiness without the game.
 */
public final class NarrationTemplates {
	public static final int MAX_LENGTH = 140;

	private NarrationTemplates() {
	}

	/**
	 * @param detail  the specific thing (a goal label, a dimension name, an event); may be empty
	 * @param variant any number; picks among alternatives so repeats do not read identically
	 */
	public static String caption(Kind kind, NarratorStyle style, String detail, int variant) {
		return caption(kind, style, StoryArc.STANDARD, detail, variant);
	}

	/**
	 * As above, but in a story arc: the moments an arc is most about (goals, deaths, arrivals,
	 * levels, dawn) are said in its terms. Other kinds, and other voices, are unchanged.
	 */
	public static String caption(Kind kind, NarratorStyle style, StoryArc arc, String detail, int variant) {
		boolean noir = style == NarratorStyle.NOIR;
		String[] arcOptions = noir && arc.active() ? arcOptions(kind, arc, detail) : null;
		if (arcOptions != null) {
			String text = arcOptions[Math.floorMod(variant, arcOptions.length)];
			return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH - 1).trim() + "..." : text;
		}
		String[] options = switch (kind) {
			case STAGE -> noir
				? new String[] {
					"Another rung. " + cap(detail) + " was behind them now, and the city never noticed.",
					cap(detail) + ". It wasn't much, but nobody else was keeping score.",
					"They got " + detail + ". In this town, that counts as a good day."}
				: new String[] {"Progress: " + detail + ".", cap(detail) + " achieved."};
			case NEAR_DEATH -> noir
				? new String[] {
					"One more hit and the story would've ended there. It didn't.",
					"Hanging on by a thread, and the thread was fraying.",
					"The kind of close call that leaves a taste."}
				: new String[] {"Barely alive.", "Close to the end."};
			case DEATH -> noir
				? new String[] {
					"They went down. The night kept going, like it always does.",
					"Dead is just a word until it's your name. Then it's a Tuesday.",
					"The world took its cut. They came back anyway."}
				: new String[] {"The player died, and returned.", "A death, and a return."};
			case HUNTED -> noir
				? new String[] {
					"Eyes in the dark. More than a couple. They weren't here for conversation.",
					"The shadows had company, and the company had teeth.",
					"Something was herding them. Nobody was asking if they minded."}
				: new String[] {"Several creatures closing in.", "Surrounded, and it is dark."};
			case DIMENSION -> noir
				? new String[] {
					"Another world, and it didn't care who they used to be.",
					"New air. Wrong air. " + cap(detail) + " had its own rules.",
					"They crossed over. " + cap(detail) + " noticed."}
				: new String[] {"Arrived in " + detail + ".", "A new place: " + detail + "."};
			case DAWN -> noir
				? new String[] {
					"Dawn came up grey and indifferent. They'd made it through.",
					"The sun showed up late and acted like nothing happened.",
					"Another night survived. The city kept no record."}
				: new String[] {"Morning. The night is over.", "Dawn."};
			case QUIET -> noir
				? new String[] {
					"Time passed. Nothing came of it. The kind of quiet that wants something.",
					"A long stretch of nothing in particular. It couldn't last.",
					"The clock kept ticking. Somebody ought to do something."}
				: new String[] {"A long, uneventful stretch.", "Nothing much has happened lately."};
			case RELIEF -> noir
				? new String[] {
					"The quiet came back, slowly, like it had never left.",
					"Whatever it was, it was over. The silence had a different weight now.",
					"They let out a breath they hadn't known they were holding."}
				: new String[] {"The danger has passed.", "Quiet again."};
			case ELSEWHERE -> noir
				? new String[] {"Elsewhere, " + detail + ".", "Across town, " + detail + "."}
				: new String[] {"Elsewhere: " + detail + "."};
			case JOURNEY -> noir
				? new String[] {
					"Miles behind them now. " + cap(detail) + ", and the road never once thanked them.",
					cap(detail) + " of walking. Some people call that a life.",
					"They'd covered " + detail + ". The map didn't care how."}
				: new String[] {"Travelled " + detail + " so far.", cap(detail) + " covered."};
			case LEVEL -> noir
				? new String[] {
					"Level " + detail + ". Experience is just a bruise that stopped hurting.",
					"They'd learned enough to call it level " + detail + ". The city kept teaching.",
					"Level " + detail + ", and every one of them paid for."}
				: new String[] {"Reached level " + detail + ".", "Level " + detail + "."};
			case BIOME -> noir
				? new String[] {
					"A " + detail + ". New ground, same old trouble underfoot.",
					"The " + detail + " didn't know them. It would learn.",
					"They'd wandered into a " + detail + ". It looked back."}
				: new String[] {"Entered a " + detail + ".", "A new place: " + detail + "."};
			case DEPTH -> noir
				? new String[] {
					"Down here the dark had weight. They carried it anyway.",
					"Far from any sky. The stone kept its own counsel.",
					"The deep took their light and asked for more."}
				: new String[] {"Deep underground.", "Far below the surface."};
			case LOADOUT -> noir
				? new String[] {
					"A little " + detail + " in the pack. In this town that made a person dangerous.",
					"They'd come by " + detail + ". Nobody asked how.",
					"Now they had " + detail + ". The night would have to adjust."}
				: new String[] {"Now carrying " + detail + ".", "Picked up " + detail + "."};
			case WEATHER -> noir
				? new String[] {
					"Rain came down like it had somewhere to be.",
					"The sky opened up and nobody had an umbrella.",
					"Weather rolled in. It had opinions."}
				: new String[] {"It started to rain.", "Weather turned."};
			case NIGHTFALL -> noir
				? new String[] {
					"The light went out of the day, and the night came in without knocking.",
					"Dusk. The kind of dark that takes its time.",
					"Night fell. So did the last of the easy choices."}
				: new String[] {"Night is falling.", "Dusk."};
			case KILLS -> noir
				? new String[] {
					"The count stood at " + detail + ". Nobody had asked for a tally.",
					cap(detail) + " down. The ledger wasn't written in ink.",
					"Another number on a list no one would read: " + detail + "."}
				: new String[] {cap(detail) + " defeated.", "Tally: " + detail + "."};
			case HUNGER -> noir
				? new String[] {
					"Their stomach filed a complaint. Nobody was listening.",
					"Empty belly, long road. An old story.",
					"Hunger had been patient so far. That was ending."}
				: new String[] {"They are getting hungry.", "Running low on food."};
			case OBSERVATION -> noir
				? new String[] {
					cap(detail) + ".",
					"Small things, noticed: " + detail + ".",
					"Nothing happened. Still, " + detail + "."}
				: new String[] {cap(detail) + ".", "Observed: " + detail + "."};
		};
		String text = options[Math.floorMod(variant, options.length)];
		return text.length() > MAX_LENGTH ? text.substring(0, MAX_LENGTH - 1).trim() + "..." : text;
	}

	/** Null where an arc has nothing of its own to say for this kind. */
	private static String[] arcOptions(Kind kind, StoryArc arc, String detail) {
		boolean hero = arc == StoryArc.ZERO_TO_HERO;
		return switch (kind) {
			case STAGE -> hero
				? new String[] {
					cap(detail) + ". A nobody doesn't get to skip a step, and they didn't.",
					"Another step up. " + cap(detail) + " would be remembered, if anyone were keeping track.",
					"They got " + detail + ". Yesterday that would have been impossible."}
				: new String[] {
					cap(detail) + ". The signs had said it would come to them.",
					"They reached " + detail + ", as if it had been waiting.",
					cap(detail) + ". Destiny had a way of making things easy, and then hard."};
			case DEATH -> hero
				? new String[] {
					"They went down. Heroes do. The ones worth following get back up.",
					"Dead, for a moment. Even the underdog's story has a low point."}
				: new String[] {
					"They fell, and the world refused to let it end there.",
					"Death took its look. The prophecy wasn't finished with them."};
			case DIMENSION -> hero
				? new String[] {
					"Somewhere a nobody was standing in " + detail + ". They didn't look like one anymore.",
					"They crossed into " + detail + ". Further than anyone expected."}
				: new String[] {
					"They stepped into " + detail + ", and it knew them.",
					cap(detail) + ". The road had been leading here all along."};
			case LEVEL -> hero
				? new String[] {
					"Level " + detail + ". Nobody started here.",
					"Level " + detail + ". The climb was starting to show."}
				: new String[] {
					"Level " + detail + ". Power gathered, as foretold.",
					"Level " + detail + ". The omens had underestimated them."};
			case DAWN -> hero
				? new String[] {
					"Dawn. One more night survived, one more day closer to someone worth the name.",
					"The sun came up on someone a little stronger than the night before."}
				: new String[] {
					"Dawn. Another night given up, as if the dark had been told to.",
					"The sun rose, and it seemed to rise for them."};
			default -> null;
		};
	}

	private static String cap(String text) {
		return text.isEmpty() ? "It" : Character.toUpperCase(text.charAt(0)) + text.substring(1);
	}
}
