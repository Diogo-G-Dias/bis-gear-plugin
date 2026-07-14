package com.github.diogogdias.bisfinder.calc.dist;

/**
 * Port of the ProbabilisticDelay tuple type from osrs-dps-calc (src/lib/HitDist.ts).
 */
public final class ProbabilisticDelay
{
	private final double probability;
	private final int delay;

	public ProbabilisticDelay(double probability, int delay)
	{
		this.probability = probability;
		this.delay = delay;
	}

	public double getProbability()
	{
		return probability;
	}

	public int getDelay()
	{
		return delay;
	}
}
