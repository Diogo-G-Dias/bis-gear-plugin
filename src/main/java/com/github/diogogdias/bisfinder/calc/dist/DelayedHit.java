package com.github.diogogdias.bisfinder.calc.dist;

/**
 * Port of the DelayedHit tuple type from osrs-dps-calc (src/lib/HitDist.ts).
 */
public final class DelayedHit
{
	private final WeightedHit hit;
	private final int delay;

	public DelayedHit(WeightedHit hit, int delay)
	{
		this.hit = hit;
		this.delay = delay;
	}

	public WeightedHit getHit()
	{
		return hit;
	}

	public int getDelay()
	{
		return delay;
	}
}
