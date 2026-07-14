package com.github.diogogdias.bisfinder.calc.dist;

import java.util.Collections;
import java.util.Objects;

/**
 * Port of the Hitsplat class from osrs-dps-calc (src/lib/HitDist.ts).
 */
public class Hitsplat
{
	public static final Hitsplat INACCURATE = new Hitsplat(0, false);

	private final int damage;
	private final boolean accurate;

	public Hitsplat(int damage)
	{
		this(damage, true);
	}

	public Hitsplat(int damage, boolean accurate)
	{
		this.damage = damage;
		this.accurate = accurate;
	}

	public int getDamage()
	{
		return damage;
	}

	public boolean isAccurate()
	{
		return accurate;
	}

	public HitDistribution transform(HitTransformer t)
	{
		return transform(t, TransformOpts.DEFAULT_TRANSFORM_OPTS);
	}

	public HitDistribution transform(HitTransformer t, TransformOpts opts)
	{
		if (!this.accurate && !opts.isTransformInaccurate())
		{
			return new HitDistribution(Collections.singletonList(
				new WeightedHit(1.0, Collections.singletonList(this))
			));
		}
		return t.apply(this);
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof Hitsplat))
		{
			return false;
		}
		Hitsplat other = (Hitsplat) o;
		return damage == other.damage && accurate == other.accurate;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(damage, accurate);
	}

	@Override
	public String toString()
	{
		return "Hitsplat(" + damage + ", " + accurate + ")";
	}
}
