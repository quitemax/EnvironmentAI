package com.quitemax.environmentai.ai.squad;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turns a group of hostile mobs hunting one target into roles. Pure geometry on the horizontal
 * plane, no game types, so every rule can be exercised outside the game.
 *
 * <p>The shape of a squad:
 * <ul>
 *   <li>a <b>captain</b>: the sturdiest member, kept once chosen for as long as it lives. It stays
 *       back and the rest take their nerve from it (see {@link SquadCoordinator});</li>
 *   <li>a <b>pinner</b>: the nearest melee member, which simply closes in and holds the target's
 *       attention;</li>
 *   <li>up to {@value #MAX_FLANKERS} <b>flankers</b>: melee members sent to the target's sides and
 *       back, so the player has to turn;</li>
 *   <li>the rest of the melee members <b>hold</b> a ring and join as the front thins out;</li>
 *   <li>ranged members <b>spread</b> around the target so they do not all share one line of fire.</li>
 * </ul>
 */
public final class SquadPlanner {
	public static final int MIN_SIZE = 3;
	public static final int MAX_SIZE = 6;
	public static final int MAX_FLANKERS = 3;

	private static final double FLANK_RADIUS = 2.5;
	private static final double HOLD_RADIUS = 7.0;
	private static final double SPREAD_RADIUS = 9.0;
	private static final double SIDE_ANGLE = Math.PI / 2;
	/** Each further flanker on a side swings this much further round, toward the back. */
	private static final double FLANK_STEP = Math.toRadians(25);

	public enum Role {
		/** Closes in directly; no destination of its own. */
		PIN,
		FLANK,
		HOLD,
		SPREAD
	}

	public record Member(UUID id, double x, double z, double maxHealth, boolean ranged) {
	}

	/** A role and, for roles that go somewhere, the point to go to. {@code captain} marks the leader. */
	public record Assignment(Role role, double x, double z, boolean captain) {
	}

	public record Plan(UUID captain, Map<UUID, Assignment> assignments) {
		public static Plan empty() {
			return new Plan(null, Map.of());
		}
	}

	private SquadPlanner() {
	}

	/**
	 * @param previousCaptain the captain from the last plan, kept while it is still a member
	 */
	public static Plan plan(double targetX, double targetZ, List<Member> input, UUID previousCaptain) {
		if (input.size() < MIN_SIZE) {
			return Plan.empty();
		}
		List<Member> members = new ArrayList<>(input);
		members.sort(Comparator.comparingDouble((Member m) -> distance(m, targetX, targetZ)).thenComparing(Member::id));
		if (members.size() > MAX_SIZE) {
			members = new ArrayList<>(members.subList(0, MAX_SIZE));
		}

		Member captain = pickCaptain(members, previousCaptain);
		Map<UUID, Assignment> out = new HashMap<>();

		List<Member> melee = new ArrayList<>();
		List<Member> ranged = new ArrayList<>();
		for (Member member : members) {
			if (member == captain) {
				continue;
			}
			(member.ranged() ? ranged : melee).add(member);
		}

		// Melee front: nearest pins, the next few flank, the rest hold.
		double pinnerBearing = melee.isEmpty()
			? bearing(members.get(0), targetX, targetZ)
			: bearing(melee.get(0), targetX, targetZ);
		int flankers = 0;
		for (int i = 0; i < melee.size(); i++) {
			Member member = melee.get(i);
			if (i == 0) {
				out.put(member.id(), new Assignment(Role.PIN, 0, 0, false));
			} else if (i <= MAX_FLANKERS) {
				int index = i - 1;
				double side = index % 2 == 0 ? 1.0 : -1.0;
				double angle = pinnerBearing + side * (SIDE_ANGLE + FLANK_STEP * (index / 2));
				out.put(member.id(), onRing(Role.FLANK, targetX, targetZ, angle, FLANK_RADIUS, false));
				flankers++;
			} else {
				out.put(member.id(), onRing(Role.HOLD, targetX, targetZ, bearing(member, targetX, targetZ),
					HOLD_RADIUS, false));
			}
		}

		// Ranged members spread evenly round the target, starting from where the first one stands.
		List<Member> shooters = new ArrayList<>(ranged);
		if (captain != null && captain.ranged()) {
			shooters.add(captain);
		}
		if (!shooters.isEmpty()) {
			double base = bearing(shooters.get(0), targetX, targetZ);
			for (int k = 0; k < shooters.size(); k++) {
				double angle = base + 2 * Math.PI * k / shooters.size();
				out.put(shooters.get(k).id(), onRing(Role.SPREAD, targetX, targetZ, angle, SPREAD_RADIUS,
					shooters.get(k) == captain));
			}
		}

		// A melee captain directs from the ring, and only while others are doing the fighting.
		if (captain != null && !captain.ranged()) {
			int fighters = melee.size() + ranged.size();
			if (fighters >= 2) {
				out.put(captain.id(), onRing(Role.HOLD, targetX, targetZ, bearing(captain, targetX, targetZ),
					HOLD_RADIUS - 1.0, true));
			} else {
				out.put(captain.id(), new Assignment(Role.PIN, 0, 0, true));
			}
		}
		return new Plan(captain == null ? null : captain.id(), out);
	}

	private static Member pickCaptain(List<Member> members, UUID previousCaptain) {
		if (previousCaptain != null) {
			for (Member member : members) {
				if (member.id().equals(previousCaptain)) {
					return member;
				}
			}
		}
		// The sturdiest; ties go to the lower id so the choice is deterministic.
		Member best = null;
		for (Member member : members) {
			if (best == null || member.maxHealth() > best.maxHealth()
				|| (member.maxHealth() == best.maxHealth() && member.id().compareTo(best.id()) < 0)) {
				best = member;
			}
		}
		return best;
	}

	private static Assignment onRing(Role role, double tx, double tz, double angle, double radius, boolean captain) {
		return new Assignment(role, tx + Math.cos(angle) * radius, tz + Math.sin(angle) * radius, captain);
	}

	private static double bearing(Member member, double tx, double tz) {
		return Math.atan2(member.z() - tz, member.x() - tx);
	}

	private static double distance(Member member, double tx, double tz) {
		return Math.hypot(member.x() - tx, member.z() - tz);
	}
}
