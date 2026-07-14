package com.github.diogogdias.bisfinder.calc.dist;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Port of the WeightedHit class from osrs-dps-calc (src/lib/HitDist.ts).
 */
public class WeightedHit
{
	private final double probability;
	private final List<Hitsplat> hitsplats;

	/**
	 * Lazily computed damage sum, mirroring the `_sum` memo field in the TS source.
	 */
	private Integer sum;

	public WeightedHit(double probability, List<Hitsplat> hitsplats)
	{
		this.probability = probability;
		this.hitsplats = hitsplats;
	}

	public double getProbability()
	{
		return probability;
	}

	public List<Hitsplat> getHitsplats()
	{
		return hitsplats;
	}

	public WeightedHit scale(double factor)
	{
		return new WeightedHit(
			this.probability * factor,
			new ArrayList<>(this.hitsplats)
		);
	}

	public WeightedHit zip(WeightedHit other)
	{
		List<Hitsplat> combined = new ArrayList<>(this.hitsplats.size() + other.hitsplats.size());
		combined.addAll(this.hitsplats);
		combined.addAll(other.hitsplats);
		return new WeightedHit(
			this.probability * other.probability,
			combined
		);
	}

	/**
	 * Splits this hit into [head, tail], where head keeps this hit's probability and the first
	 * hitsplat, and tail is a probability-1.0 hit holding the remaining hitsplats.
	 */
	public WeightedHit[] shift()
	{
		return new WeightedHit[]{
			new WeightedHit(this.probability, Collections.singletonList(this.hitsplats.get(0))),
			new WeightedHit(1.0, new ArrayList<>(this.hitsplats.subList(1, this.hitsplats.size()))),
		};
	}

	public HitDistribution transform(HitTransformer t)
	{
		return transform(t, TransformOpts.DEFAULT_TRANSFORM_OPTS);
	}

	public HitDistribution transform(HitTransformer t, TransformOpts opts)
	{
		if (this.hitsplats.size() == 1)
		{
			return this.hitsplats.get(0).transform(t, opts)
				.scaleProbability(this.probability);
		}

		// recursively zip first hitsplat with remaining hitsplats
		WeightedHit[] shifted = this.shift();
		WeightedHit head = shifted[0];
		WeightedHit tail = shifted[1];
		return head.transform(t, opts)
			.zip(tail.transform(t, opts));
	}

	public boolean anyAccurate()
	{
		for (Hitsplat h : this.hitsplats)
		{
			if (h.isAccurate())
			{
				return true;
			}
		}
		return false;
	}

	public int getSum()
	{
		if (this.sum == null)
		{
			int acc = 0;
			for (Hitsplat h : this.hitsplats)
			{
				acc += h.getDamage();
			}
			this.sum = acc;
		}
		return this.sum;
	}

	public double getExpectedValue()
	{
		return this.probability * this.getSum();
	}

	public BigInteger getHash()
	{
		BigInteger acc = BigInteger.ZERO;
		for (Hitsplat hitsplat : this.hitsplats)
		{
			acc = acc.shiftLeft(8);
			acc = acc.or(BigInteger.valueOf(hitsplat.getDamage()));
			acc = acc.shiftLeft(1);
			acc = acc.or(hitsplat.isAccurate() ? BigInteger.ONE : BigInteger.ZERO);
		}
		return acc;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof WeightedHit))
		{
			return false;
		}
		WeightedHit other = (WeightedHit) o;
		return Double.compare(probability, other.probability) == 0
			&& hitsplats.equals(other.hitsplats);
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(probability, hitsplats);
	}

	@Override
	public String toString()
	{
		String damages = this.hitsplats.stream()
			.map((h) -> Integer.toString(h.getDamage()))
			.collect(Collectors.joining(","));
		return "WeightedHit(p: " + this.probability + ", h: [" + damages + "], s: " + this.getSum() + ")";
	}
}
