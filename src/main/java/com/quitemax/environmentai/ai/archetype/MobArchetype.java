package com.quitemax.environmentai.ai.archetype;

import com.quitemax.environmentai.ai.action.MobAction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.CrossbowAttackMob;
import net.minecraft.world.entity.monster.RangedAttackMob;

import java.util.List;

/**
 * How a hostile mob fights, which decides which actions make sense for it and how easily a
 * well-equipped target scares it. The same six verbs mean different things for a skeleton and a
 * zombie: "approach" is the wrong answer for something that shoots.
 */
public enum MobArchetype {
	/** Walks up and hits. Bold: it takes an extreme threat (and company) to shake it. */
	MELEE(9.0, List.of(MobAction.ATTACK, MobAction.FLEE, MobAction.APPROACH, MobAction.GUARD)),
	/** Spiders: melee, but willing to back off from a clearly stronger opponent. */
	SKITTISH(6.5, List.of(MobAction.ATTACK, MobAction.FLEE, MobAction.APPROACH, MobAction.GUARD)),
	/** Creepers: ATTACK defers to the vanilla swell-and-explode behaviour. */
	EXPLOSIVE(7.0, List.of(MobAction.ATTACK, MobAction.APPROACH, MobAction.FLEE, MobAction.GUARD)),
	/**
	 * Endermen and the like: passive until provoked, then fixed on the one who provoked them. They
	 * are never intimidated by gear. Left as {@link #MELEE}, a geared player staring at one made it
	 * back away (66 FLEE decisions in a single playtest), which is the opposite of an enderman.
	 */
	RELENTLESS(99.0, List.of(MobAction.ATTACK, MobAction.APPROACH, MobAction.GUARD)),
	/** Skeletons, pillagers, witches, drowned: shoot from range and prefer not to be caught. */
	RANGED(6.0, List.of(
		MobAction.ATTACK, MobAction.KEEP_DISTANCE, MobAction.FLEE, MobAction.APPROACH, MobAction.GUARD
	));

	private final double nerve;
	private final List<MobAction> actionsWithTarget;

	MobArchetype(double nerve, List<MobAction> actionsWithTarget) {
		this.nerve = nerve;
		this.actionsWithTarget = actionsWithTarget;
	}

	/**
	 * Threat score (0-10, see TargetAssessment) at which a lone mob of this kind loses its nerve.
	 * Fellow hostiles nearby add to it.
	 */
	public double nerve() {
		return nerve;
	}

	/** Actions offered to the model when the mob has a target. */
	public List<MobAction> actionsWithTarget() {
		return actionsWithTarget;
	}

	public static MobArchetype of(Mob mob) {
		if (mob instanceof RangedAttackMob || mob instanceof CrossbowAttackMob) {
			return RANGED;
		}
		// By registry path rather than constants: the constants moved out of EntityType in 26.x,
		// and a path match also keeps working for the vanilla ids without importing their classes.
		return switch (EntityType.getKey(mob.getType()).getPath()) {
			case "creeper" -> EXPLOSIVE;
			case "spider", "cave_spider" -> SKITTISH;
			case "enderman" -> RELENTLESS;
			default -> MELEE;
		};
	}
}
