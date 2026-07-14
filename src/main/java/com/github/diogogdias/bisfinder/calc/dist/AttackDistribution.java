package com.github.diogogdias.bisfinder.calc.dist;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Port of the AttackDistribution class from osrs-dps-calc (src/lib/HitDist.ts).
 */
public class AttackDistribution
{
	/**
	 * A single bar of {@link #asHistogram}. Replaces the TS ChartEntry type, which is UI-only.
	 */
	public static final class HistogramEntry
	{
		private final String name;
		private final double value;

		public HistogramEntry(String name, double value)
		{
			this.name = name;
			this.value = value;
		}

		public String getName()
		{
			return name;
		}

		public double getValue()
		{
			return value;
		}
	}

	private final List<HitDistribution> dists;

	private HitDistribution zipped;
	private HitDistribution singleHitsplat;

	public AttackDistribution(Collection<HitDistribution> dists)
	{
		this.dists = new ArrayList<>(dists);
	}

	public List<HitDistribution> getDists()
	{
		return dists;
	}

	public HitDistribution getZipped()
	{
		if (this.zipped == null)
		{
			HitDistribution acc = this.dists.get(0);
			for (int i = 1; i < this.dists.size(); i++)
			{
				acc = acc.zip(this.dists.get(i));
			}
			this.zipped = acc;
		}
		return this.zipped;
	}

	public HitDistribution getSingleHitsplat()
	{
		if (this.singleHitsplat == null)
		{
			HitDistribution acc = this.dists.get(0);
			for (int i = 1; i < this.dists.size(); i++)
			{
				acc = acc.zip(this.dists.get(i)).cumulative();
			}
			this.singleHitsplat = acc;
		}
		return this.singleHitsplat;
	}

	public void addDist(HitDistribution d)
	{
		this.dists.add(d);
	}

	public AttackDistribution transform(HitTransformer t)
	{
		return transform(t, TransformOpts.DEFAULT_TRANSFORM_OPTS);
	}

	public AttackDistribution transform(HitTransformer t, TransformOpts opts)
	{
		return this.map((d) -> d.transform(t, opts));
	}

	public AttackDistribution flatten()
	{
		return this.map(HitDistribution::flatten);
	}

	public AttackDistribution scaleProbability(double factor)
	{
		return this.map((d) -> d.scaleProbability(factor));
	}

	public AttackDistribution scaleDamage(int factor)
	{
		return scaleDamage(factor, 1);
	}

	public AttackDistribution scaleDamage(int factor, int divisor)
	{
		return this.map((d) -> d.scaleDamage(factor, divisor));
	}

	public int getMin()
	{
		int acc = 0;
		for (HitDistribution d : this.dists)
		{
			acc += d.getMin();
		}
		return acc;
	}

	public int getMax()
	{
		int acc = 0;
		for (HitDistribution d : this.dists)
		{
			acc += d.getMax();
		}
		return acc;
	}

	public double getExpectedDamage()
	{
		double acc = 0;
		for (HitDistribution d : this.dists)
		{
			acc += d.expectedHit();
		}
		return acc;
	}

	public List<HistogramEntry> asHistogram()
	{
		return asHistogram(false);
	}

	public List<HistogramEntry> asHistogram(boolean hideMisses)
	{
		HitDistribution dist = this.getSingleHitsplat();

		Map<Integer, Double> hitMap = new LinkedHashMap<>();
		for (WeightedHit h : dist.getHits())
		{
			if (!hideMisses || h.anyAccurate())
			{
				hitMap.merge(h.getSum(), h.getProbability(), Double::sum);
			}
		}

		List<HistogramEntry> ret = new ArrayList<>();
		for (int i = 0; i <= dist.getMax(); i++)
		{
			Double prob = hitMap.get(i);
			if (prob == null)
			{
				ret.add(new HistogramEntry(Integer.toString(i), 0));
			}
			else
			{
				ret.add(new HistogramEntry(Integer.toString(i), prob));
			}
		}

		return ret;
	}

	private AttackDistribution map(Function<HitDistribution, HitDistribution> m)
	{
		List<HitDistribution> mapped = new ArrayList<>(this.dists.size());
		for (HitDistribution d : this.dists)
		{
			mapped.add(m.apply(d));
		}
		return new AttackDistribution(mapped);
	}
}
