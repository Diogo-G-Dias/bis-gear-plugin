package com.github.diogogdias.bisfinder.calc.dist;

import java.util.ArrayList;
import java.util.Collections;

/**
 * Port of the standalone hit transformer factory functions from osrs-dps-calc (src/lib/HitDist.ts).
 */
public final class HitTransformers
{
	private HitTransformers()
	{
	}

	public static HitTransformer flatLimitTransformer(int maximum)
	{
		return flatLimitTransformer(maximum, 0);
	}

	public static HitTransformer flatLimitTransformer(int maximum, int minimum)
	{
		return (h) -> new HitDistribution(Collections.singletonList(
			new WeightedHit(1.0, Collections.singletonList(
				new Hitsplat(Math.max(minimum, Math.min(h.getDamage(), maximum)), h.isAccurate())
			))
		));
	}

	public static HitTransformer linearMinTransformer(int maximum)
	{
		return linearMinTransformer(maximum, 0);
	}

	public static HitTransformer linearMinTransformer(int maximum, int offset)
	{
		return (h) ->
		{
			HitDistribution d = new HitDistribution(new ArrayList<>());
			double prob = 1.0 / (maximum + 1);
			for (int i = 0; i <= maximum; i++)
			{
				d.addHit(new WeightedHit(
					prob,
					Collections.singletonList(new Hitsplat(Math.min(h.getDamage(), i + offset), h.isAccurate()))
				));
			}
			return d.flatten();
		};
	}

	public static HitTransformer cappedRerollTransformer(int limit, int rollMax)
	{
		return cappedRerollTransformer(limit, rollMax, 0);
	}

	public static HitTransformer cappedRerollTransformer(int limit, int rollMax, int offset)
	{
		return (h) ->
		{
			if (h.getDamage() <= limit)
			{
				return new HitDistribution(Collections.singletonList(
					new WeightedHit(1.0, Collections.singletonList(h))
				));
			}

			HitDistribution d = new HitDistribution(new ArrayList<>());
			double prob = 1.0 / (rollMax + 1);
			for (int i = 0; i <= rollMax; i++)
			{
				d.addHit(new WeightedHit(
					prob,
					Collections.singletonList(new Hitsplat(h.getDamage() > limit ? i + offset : h.getDamage(), h.isAccurate()))
				));
			}
			return d.flatten();
		};
	}

	public static HitTransformer multiplyTransformer(int numerator)
	{
		return multiplyTransformer(numerator, 1, 0);
	}

	public static HitTransformer multiplyTransformer(int numerator, int divisor)
	{
		return multiplyTransformer(numerator, divisor, 0);
	}

	public static HitTransformer multiplyTransformer(int numerator, int divisor, int minimum)
	{
		return (h) ->
		{
			int dmg = CalcMath.trunc((double) numerator * (double) h.getDamage() / (double) divisor);
			if (minimum != 0)
			{
				if (h.getDamage() >= minimum)
				{
					// if the value started above the minimum, make sure it doesn't drop below
					dmg = Math.max(minimum, dmg);
				}
				else
				{
					// if the value started below the minimum, make sure it isn't reduced, but respect increases
					dmg = Math.max(h.getDamage(), dmg);
				}
			}
			return new HitDistribution(Collections.singletonList(
				new WeightedHit(1.0, Collections.singletonList(new Hitsplat(dmg, h.isAccurate())))
			));
		};
	}

	public static HitTransformer divisionTransformer(int divisor)
	{
		return divisionTransformer(divisor, 0);
	}

	public static HitTransformer divisionTransformer(int divisor, int minimum)
	{
		return multiplyTransformer(1, divisor, minimum);
	}

	public static HitTransformer flatAddTransformer(int addend)
	{
		return flatAddTransformer(addend, 0);
	}

	public static HitTransformer flatAddTransformer(int addend, int minimum)
	{
		return (h) -> new HitDistribution(Collections.singletonList(
			new WeightedHit(1.0, Collections.singletonList(
				new Hitsplat(Math.max(minimum, h.getDamage() + addend), h.isAccurate())
			))
		));
	}
}
