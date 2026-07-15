package com.github.diogogdias.bisfinder.engine;

/**
 * One hitsplat of an attack: the chance it lands and the damage range it rolls. A standard hit rolls
 * uniformly over {@code 0..maxHit}; weirdgloop's expected damage per landed hit for that is
 * {@code maxHit/2 + 1/(maxHit+1)}. A hit with a minimum (some effects guarantee a floor) instead rolls
 * uniformly over {@code minHit..maxHit}, whose mean is {@code (minHit + maxHit)/2}.
 *
 * <p>Expected value is linear, so an attack's expected damage is the sum of its hitsplats' — which is all
 * DPS needs, with no full probability mass to convolve.
 */
public final class Hitsplat
{
	private final double accuracy;
	private final int minHit;
	private final int maxHit;

	public Hitsplat(double accuracy, int minHit, int maxHit)
	{
		this.accuracy = accuracy;
		this.minHit = minHit;
		this.maxHit = maxHit;
	}

	/** A standard hitsplat with no minimum. */
	public static Hitsplat standard(double accuracy, int maxHit)
	{
		return new Hitsplat(accuracy, 0, maxHit);
	}

	public double expectedDamage()
	{
		if (maxHit <= 0 || accuracy <= 0.0)
		{
			return 0.0;
		}
		double meanGivenHit = minHit > 0
			? (minHit + maxHit) / 2.0
			: maxHit / 2.0 + 1.0 / (maxHit + 1.0);
		return accuracy * meanGivenHit;
	}

	public double getAccuracy()
	{
		return accuracy;
	}

	public int getMaxHit()
	{
		return maxHit;
	}
}
