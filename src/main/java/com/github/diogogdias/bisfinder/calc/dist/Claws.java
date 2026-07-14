package com.github.diogogdias.bisfinder.calc.dist;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Port of osrs-dps-calc's src/lib/dists/claws.ts (dragon claws and burning claws special attacks).
 */
public final class Claws
{
	private Claws()
	{
	}

	/**
	 * Port of the generateTotals closure: [chancePerDmg, low, high].
	 */
	private static final class Totals
	{
		private final double chancePerDmg;
		private final int low;
		private final int high;

		private Totals(double chancePerDmg, int low, int high)
		{
			this.chancePerDmg = chancePerDmg;
			this.low = low;
			this.high = high;
		}
	}

	private static Totals generateTotals(int accRoll, int totalRolls, double acc, int max, int highOffset)
	{
		int low = CalcMath.trunc((double) max * (totalRolls - accRoll) / 4);
		int high = max + low + highOffset;
		double chancePreviousRollsFail = Math.pow(1 - acc, accRoll);
		double chanceThisRollPasses = chancePreviousRollsFail * acc;
		double chancePerDmg = chanceThisRollPasses / (high - low + 1);

		return new Totals(chancePerDmg, low, high);
	}

	public static AttackDistribution dClawDist(double acc, int max)
	{
		HitDistribution dist = new HitDistribution(new ArrayList<>());
		for (int accRoll = 0; accRoll < 4; accRoll++)
		{
			Totals totals = generateTotals(accRoll, 4, acc, max, -1);
			for (int dmg = totals.low; dmg <= totals.high; dmg++)
			{
				switch (accRoll)
				{
					case 0:
						dist.addHit(new WeightedHit(totals.chancePerDmg, Arrays.asList(
							new Hitsplat(CalcMath.trunc((double) dmg / 2)),
							new Hitsplat(CalcMath.trunc((double) dmg / 4)),
							new Hitsplat(CalcMath.trunc((double) dmg / 8)),
							new Hitsplat(CalcMath.trunc((double) dmg / 8) + 1)
						)));
						break;

					case 1:
						dist.addHit(new WeightedHit(totals.chancePerDmg, Arrays.asList(
							new Hitsplat(CalcMath.trunc((double) dmg / 2)),
							new Hitsplat(CalcMath.trunc((double) dmg / 4)),
							new Hitsplat(CalcMath.trunc((double) dmg / 4) + 1),
							Hitsplat.INACCURATE
						)));
						break;

					case 2:
						dist.addHit(new WeightedHit(totals.chancePerDmg, Arrays.asList(
							new Hitsplat(CalcMath.trunc((double) dmg / 2)),
							new Hitsplat(CalcMath.trunc((double) dmg / 2) + 1),
							Hitsplat.INACCURATE,
							Hitsplat.INACCURATE
						)));
						break;

					default:
						dist.addHit(new WeightedHit(totals.chancePerDmg, Arrays.asList(
							new Hitsplat(dmg + 1),
							Hitsplat.INACCURATE,
							Hitsplat.INACCURATE,
							Hitsplat.INACCURATE
						)));
						break;
				}
			}
		}

		double chanceAllFail = Math.pow(1 - acc, 4);
		dist.addHit(new WeightedHit(chanceAllFail * 2 / 3, Arrays.asList(
			new Hitsplat(1, false),
			new Hitsplat(1, false),
			Hitsplat.INACCURATE,
			Hitsplat.INACCURATE
		)));
		dist.addHit(new WeightedHit(chanceAllFail / 3, Arrays.asList(
			Hitsplat.INACCURATE,
			Hitsplat.INACCURATE,
			Hitsplat.INACCURATE,
			Hitsplat.INACCURATE
		)));
		return new AttackDistribution(Collections.singletonList(dist));
	}

	public static AttackDistribution burningClawSpec(double acc, int max)
	{
		HitDistribution dist = new HitDistribution(new ArrayList<>());
		for (int accRoll = 0; accRoll < 3; accRoll++)
		{
			Totals totals = generateTotals(accRoll, 3, acc, max, 0);
			for (int dmg = totals.low; dmg <= totals.high; dmg++)
			{
				switch (accRoll)
				{
					case 0:
						dist.addHit(new WeightedHit(totals.chancePerDmg, Arrays.asList(
							new Hitsplat(CalcMath.trunc((double) dmg / 2)),
							new Hitsplat(CalcMath.trunc((double) dmg / 4)),
							new Hitsplat(CalcMath.trunc((double) dmg / 4))
						)));
						break;

					case 1:
						dist.addHit(new WeightedHit(totals.chancePerDmg, Arrays.asList(
							new Hitsplat(CalcMath.trunc((double) dmg / 2) - 1),
							new Hitsplat(CalcMath.trunc((double) dmg / 2) - 1),
							new Hitsplat(2)
						)));
						break;

					default:
						dist.addHit(new WeightedHit(totals.chancePerDmg, Arrays.asList(
							new Hitsplat(dmg - 2),
							new Hitsplat(1),
							new Hitsplat(1)
						)));
						break;
				}
			}
		}

		double chanceAllFail = Math.pow(1 - acc, 3);
		dist.addHit(new WeightedHit(chanceAllFail / 5, Arrays.asList(
			Hitsplat.INACCURATE,
			Hitsplat.INACCURATE,
			Hitsplat.INACCURATE
		)));
		dist.addHit(new WeightedHit(2 * chanceAllFail / 5, Arrays.asList(
			new Hitsplat(1, false),
			Hitsplat.INACCURATE,
			Hitsplat.INACCURATE
		)));
		dist.addHit(new WeightedHit(2 * chanceAllFail / 5, Arrays.asList(
			new Hitsplat(1, false),
			new Hitsplat(1, false),
			Hitsplat.INACCURATE
		)));
		return new AttackDistribution(Collections.singletonList(dist));
	}

	/**
	 * Truth table; 1 = burn, 0 = no burn.
	 */
	private static final int[][] BURN_MATRIX = {
		{0, 0, 0},
		{0, 0, 1},
		{0, 1, 0},
		{0, 1, 1},
		{1, 0, 0},
		{1, 0, 1},
		{1, 1, 0},
		{1, 1, 1},
	};

	private static final List<Double> BURN_EXPECTED = burnExpected();

	private static List<Double> burnExpected()
	{
		List<Double> ret = new ArrayList<>();
		for (int accRoll = 0; accRoll < 3; accRoll++)
		{
			double total = 0;
			for (int[] row : BURN_MATRIX)
			{
				double burnChance = 0.15 * (accRoll + 1);
				double burn1 = row[0] == 1 ? burnChance : (1 - burnChance);
				double burn2 = row[1] == 1 ? burnChance : (1 - burnChance);
				double burn3 = row[2] == 1 ? burnChance : (1 - burnChance);
				double chanceOfRow = burn1 * burn2 * burn3;

				int damage = (row[0] + row[1] + row[2]) * 10;
				if (row[0] == 1 && row[1] == 1)
				{
					// there's a (presumed) bug here, where if the first two hitsplats apply burn,
					// then they overlap and miss 1 damage on the first tick.
					damage -= 1;
				}

				total += chanceOfRow * damage;
			}
			ret.add(total);
		}
		return Collections.unmodifiableList(ret);
	}

	/**
	 * 10 damage burn x3 hitsplats, 15/30/45% chance per splat dependent on which roll hits.
	 */
	public static double burningClawDoT(double acc)
	{
		double accumulator = 0;

		for (int accRoll = 0; accRoll < 3; accRoll++)
		{
			double prevRollsFail = Math.pow(1 - acc, accRoll);
			double thisRollHits = prevRollsFail * acc;

			accumulator += thisRollHits * BURN_EXPECTED.get(accRoll);
		}
		return accumulator;
	}
}
