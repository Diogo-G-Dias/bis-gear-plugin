package com.github.diogogdias.bisfinder.engine;

/**
 * The shared, game-defined combat rolls: effective levels, max hit, attack/defence rolls and the standard
 * accuracy formula. These are dictated by the game's own maths — there is only one correct way to write
 * them — and are documented on the OSRS Wiki (Damage per second/Melee, /Ranged, /Magic).
 *
 * <p>The roll and accuracy expressions here follow the {@code best-dps} RuneLite plugin, which is
 * BSD-2-Clause licensed:
 * <pre>
 *   Copyright (c) 2026, guccifurs (best-dps). All rights reserved.
 *   Redistributed under the BSD 2-Clause License; see NOTICE.md.
 * </pre>
 *
 * <p>This class deliberately contains no higher-level modelling (hit distributions, procs, specs): those
 * are built separately from the public wiki formulas.
 */
public final class RollMath
{
	public static final double SECONDS_PER_TICK = 0.6;

	private RollMath()
	{
	}

	/** {@code floor(level * prayerMultiplier) + stanceBonus + 8}. */
	public static int effectiveLevel(int level, double prayerMultiplier, int stanceBonus)
	{
		return (int) Math.floor(level * prayerMultiplier) + stanceBonus + 8;
	}

	/** Standard max hit: {@code floor((effStr * (strengthBonus + 64) + 320) / 640)}. */
	public static int maxHitFromEffective(int effectiveStrength, int strengthBonus)
	{
		return Math.max(0, (int) Math.floor((effectiveStrength * (strengthBonus + 64.0) + 320.0) / 640.0));
	}

	/**
	 * Osmumten's fang accuracy: it rolls the hit twice and keeps the better, giving a higher effective
	 * chance to land than a normal attack. Formula from the {@code best-dps} BSD-2 plugin.
	 */
	public static double fangAccuracy(long attackRoll, long defenceRoll)
	{
		double atk = attackRoll;
		double def = defenceRoll;
		if (atk < 0)
		{
			atk = Math.min(0, atk + 2);
		}
		if (def < 0)
		{
			def = Math.min(0, def + 2);
		}
		if (atk >= 0 && def >= 0)
		{
			return atk > def
				? 1.0 - (def + 2.0) * (2.0 * def + 3.0) / (atk + 1.0) / (atk + 1.0) / 6.0
				: atk * (4.0 * atk + 5.0) / 6.0 / (atk + 1.0) / (def + 1.0);
		}
		if (atk >= 0)
		{
			return 1.0 - 1.0 / (-def + 1.0) / (atk + 1.0);
		}
		if (def >= 0)
		{
			return 0.0;
		}
		double reversedAttack = -def;
		double reversedDefence = -atk;
		return reversedAttack < reversedDefence
			? reversedAttack * (reversedDefence * 6.0 - 2.0 * reversedAttack + 5.0) / 6.0 / (reversedDefence + 1.0) / (reversedDefence + 1.0)
			: 1.0 - (reversedDefence + 2.0) * (2.0 * reversedDefence + 3.0) / 6.0 / (reversedDefence + 1.0) / (reversedAttack + 1.0);
	}

	/**
	 * Expected damage from a standard attack, given the chance it lands. A landed hit rolls uniformly over
	 * {@code 0..maxHit}; the mean weirdgloop's calculator uses is {@code maxHit/2 + 1/(maxHit+1)}, which
	 * this reproduces (matching the {@code best-dps} BSD-2 plugin's {@code normalExpectedHit}).
	 */
	public static double standardExpectedHit(double accuracy, int maxHit)
	{
		if (maxHit <= 0 || accuracy <= 0.0)
		{
			return 0.0;
		}
		return accuracy * (maxHit / 2.0 + 1.0 / (maxHit + 1.0));
	}

	/** Damage per second from an expected per-attack hit and the weapon's attack speed in ticks. */
	public static double dps(double expectedHit, int attackSpeedTicks)
	{
		return expectedHit / (attackSpeedTicks * SECONDS_PER_TICK);
	}

	public static long attackRoll(int effectiveAttack, int attackBonus)
	{
		return (long) effectiveAttack * (attackBonus + 64L);
	}

	public static long defenceRoll(int effectiveDefence, int defenceBonus)
	{
		return (long) effectiveDefence * (defenceBonus + 64L);
	}

	/**
	 * Chance for a hit to land, comparing the attacker's roll to the defender's. Handles negative rolls
	 * (from stacked defence reductions) with the game's documented reflection at zero.
	 */
	public static double normalAccuracy(long attackRoll, long defenceRoll)
	{
		double atk = attackRoll;
		double def = defenceRoll;
		if (atk < 0)
		{
			atk = Math.min(0, atk + 2);
		}
		if (def < 0)
		{
			def = Math.min(0, def + 2);
		}
		if (atk >= 0 && def >= 0)
		{
			return atk > def
				? 1.0 - ((def + 2.0) / (2.0 * (atk + 1.0)))
				: atk / (2.0 * (def + 1.0));
		}
		if (atk >= 0)
		{
			return 1.0 - 1.0 / ((-def + 1.0) * (atk + 1.0));
		}
		if (def >= 0)
		{
			return 0.0;
		}
		double swappedAtk = -def;
		double swappedDef = -atk;
		return swappedAtk > swappedDef
			? 1.0 - ((swappedDef + 2.0) / (2.0 * (swappedAtk + 1.0)))
			: swappedAtk / (2.0 * (swappedDef + 1.0));
	}
}
