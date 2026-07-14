package com.github.diogogdias.bisfinder.calc.dist;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of the HitDistribution class from osrs-dps-calc (src/lib/HitDist.ts).
 */
public class HitDistribution
{
	private final List<WeightedHit> hits;

	public HitDistribution(Collection<WeightedHit> hits)
	{
		this.hits = new ArrayList<>(hits);
	}

	public List<WeightedHit> getHits()
	{
		return hits;
	}

	public void addHit(WeightedHit w)
	{
		this.hits.add(w);
	}

	public void addHits(Collection<WeightedHit> w)
	{
		this.hits.addAll(w);
	}

	public HitDistribution zip(HitDistribution other)
	{
		List<WeightedHit> zipped = new ArrayList<>(this.hits.size() * other.hits.size());
		for (WeightedHit a : this.hits)
		{
			for (WeightedHit b : other.hits)
			{
				zipped.add(a.zip(b));
			}
		}
		return new HitDistribution(zipped);
	}

	public HitDistribution wideTransform(HitTransformer t)
	{
		return wideTransform(t, TransformOpts.DEFAULT_TRANSFORM_OPTS);
	}

	public HitDistribution wideTransform(HitTransformer t, TransformOpts opts)
	{
		HitDistribution d = new HitDistribution(new ArrayList<>());
		for (WeightedHit h : this.hits)
		{
			for (WeightedHit transformed : h.transform(t, opts).getHits())
			{
				d.addHit(transformed);
			}
		}
		return d;
	}

	public HitDistribution transform(HitTransformer t)
	{
		return transform(t, TransformOpts.DEFAULT_TRANSFORM_OPTS);
	}

	public HitDistribution transform(HitTransformer t, TransformOpts opts)
	{
		return this.wideTransform(t, opts).flatten();
	}

	public HitDistribution scaleProbability(double factor)
	{
		List<WeightedHit> scaled = new ArrayList<>(this.hits.size());
		for (WeightedHit h : this.hits)
		{
			scaled.add(h.scale(factor));
		}
		return new HitDistribution(scaled);
	}

	public HitDistribution scaleDamage(int factor)
	{
		return scaleDamage(factor, 1);
	}

	public HitDistribution scaleDamage(int factor, int divisor)
	{
		List<WeightedHit> scaled = new ArrayList<>(this.hits.size());
		for (WeightedHit h : this.hits)
		{
			List<Hitsplat> splats = new ArrayList<>(h.getHitsplats().size());
			for (Hitsplat s : h.getHitsplats())
			{
				splats.add(new Hitsplat(
					CalcMath.trunc((double) s.getDamage() * (double) factor / (double) divisor),
					s.isAccurate()
				));
			}
			scaled.add(new WeightedHit(h.getProbability(), splats));
		}
		return new HitDistribution(scaled);
	}

	/**
	 * Merges the probabilities of hits with identical damage values.
	 */
	public HitDistribution flatten()
	{
		Map<BigInteger, Double> acc = new LinkedHashMap<>();
		Map<BigInteger, List<Hitsplat>> hitLists = new LinkedHashMap<>();
		for (WeightedHit hit : this.hits)
		{
			BigInteger hash = hit.getHash();
			Double prev = acc.get(hash);
			if (prev == null)
			{
				acc.put(hash, hit.getProbability());
				hitLists.put(hash, hit.getHitsplats());
			}
			else
			{
				acc.put(hash, prev + hit.getProbability());
			}
		}

		HitDistribution d = new HitDistribution(new ArrayList<>());
		for (Map.Entry<BigInteger, Double> e : acc.entrySet())
		{
			double prob = e.getValue();
			if (prob > 0)
			{
				d.addHit(new WeightedHit(prob, hitLists.get(e.getKey())));
			}
		}
		return d;
	}

	/**
	 * Converts multi-hits into a single cumulative damage total.
	 */
	public HitDistribution cumulative()
	{
		HitDistribution d = new HitDistribution(new ArrayList<>());
		Map<Integer, Double> acc = new LinkedHashMap<>();
		for (WeightedHit hit : this.hits)
		{
			// if 1 splat is accurate, treat the whole hit as accurate
			// if inaccurate, take the bitwise inverse so that we have a number key that's distinct, we'll undo it later
			int key = hit.anyAccurate() ? hit.getSum() : ~hit.getSum();
			Double prev = acc.get(key);
			if (prev == null)
			{
				acc.put(key, hit.getProbability());
			}
			else
			{
				acc.put(key, prev + hit.getProbability());
			}
		}

		for (Map.Entry<Integer, Double> e : acc.entrySet())
		{
			int key = e.getKey();
			double prob = e.getValue();
			boolean accurate = key >= 0;
			int dmg = accurate ? key : ~key;
			if (prob > 0)
			{
				d.addHit(new WeightedHit(prob, Collections.singletonList(new Hitsplat(dmg, accurate))));
			}
		}

		return d;
	}

	public double expectedHit()
	{
		double acc = 0;
		for (WeightedHit h : this.hits)
		{
			acc += h.getExpectedValue();
		}
		return acc;
	}

	public int size()
	{
		return this.hits.size();
	}

	public int getMin()
	{
		int m = Integer.MAX_VALUE;
		for (WeightedHit h : this.hits)
		{
			m = Math.min(m, h.getSum());
		}
		// d3.min returns undefined for an empty input; downstream sums coerce that to 0
		return this.hits.isEmpty() ? 0 : m;
	}

	public int getMax()
	{
		int m = Integer.MIN_VALUE;
		for (WeightedHit h : this.hits)
		{
			m = Math.max(m, h.getSum());
		}
		// d3.max returns undefined for an empty input; downstream sums coerce that to 0
		return this.hits.isEmpty() ? 0 : m;
	}

	public List<DelayedHit> withProbabilisticDelays(WeaponDelayProvider delayProvider)
	{
		List<DelayedHit> hitsWithDelays = new ArrayList<>();
		for (WeightedHit wh : this.hits)
		{
			for (ProbabilisticDelay pd : delayProvider.apply(wh))
			{
				hitsWithDelays.add(new DelayedHit(
					new WeightedHit(
						wh.getProbability() * pd.getProbability(),
						Collections.singletonList(new Hitsplat(wh.getSum(), wh.anyAccurate()))
					),
					pd.getDelay()
				));
			}
		}

		// dedupe the results and merge entries
		List<DelayedHit> d = new ArrayList<>();
		Map<Integer, Double> acc = new LinkedHashMap<>();
		for (DelayedHit dh : hitsWithDelays)
		{
			WeightedHit wh = dh.getHit();
			int key = (wh.getSum() & 0xFFFFFF) | (dh.getDelay() << 24);
			Double prev = acc.get(key);
			if (prev == null)
			{
				acc.put(key, wh.getProbability());
			}
			else
			{
				acc.put(key, prev + wh.getProbability());
			}
		}

		for (Map.Entry<Integer, Double> e : acc.entrySet())
		{
			int key = e.getKey();
			double prob = e.getValue();
			int delay = (key & 0x8F000000) >> 24;
			int dmg = key & 0xFFFFFF;
			d.add(new DelayedHit(new WeightedHit(prob, Collections.singletonList(new Hitsplat(dmg, true))), delay));
		}

		return d;
	}

	public static HitDistribution linear(double accuracy, int minimum, int maximum)
	{
		HitDistribution d = new HitDistribution(new ArrayList<>());
		double hitProb = accuracy / (maximum - minimum + 1);
		for (int i = minimum; i <= maximum; i++)
		{
			d.addHit(new WeightedHit(hitProb, Collections.singletonList(new Hitsplat(i))));
		}
		d.addHit(new WeightedHit(1 - accuracy, Collections.singletonList(Hitsplat.INACCURATE)));
		return d;
	}

	public static HitDistribution single(double accuracy, List<Hitsplat> hitsplats)
	{
		HitDistribution d = new HitDistribution(Collections.singletonList(
			new WeightedHit(accuracy, hitsplats)
		));
		if (accuracy != 1.0)
		{
			d.addHit(new WeightedHit(1 - accuracy, Collections.singletonList(Hitsplat.INACCURATE)));
		}
		return d;
	}
}
