package com.github.diogogdias.bisfinder.calc.dist;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Port of osrs-dps-calc's src/tests/lib/HitDist.test.ts.
 */
public class HitDistributionTest
{
	private static WeightedHit hit(double probability, int damage, boolean accurate)
	{
		return new WeightedHit(probability, Collections.singletonList(new Hitsplat(damage, accurate)));
	}

	@Test
	public void cumulativeMergesHitsOfTheSameDamage()
	{
		HitDistribution dist = new HitDistribution(Arrays.asList(
			hit(0.25, 0, true),
			hit(0.25, 1, true),
			hit(0.25, 1, true),
			hit(0.25, 2, true)
		));

		List<WeightedHit> cumulative = dist.cumulative().getHits();
		assertEquals(3, cumulative.size());
		assertTrue(cumulative.contains(hit(0.25, 0, true)));
		assertTrue(cumulative.contains(hit(0.5, 1, true)));
		assertTrue(cumulative.contains(hit(0.25, 2, true)));
	}

	@Test
	public void cumulativePreservesInaccurateZeroHits()
	{
		HitDistribution dist = new HitDistribution(Arrays.asList(
			hit(0.5, 0, false),
			hit(0.125, 0, true),
			hit(0.125, 1, true),
			hit(0.125, 1, true),
			hit(0.125, 2, true)
		));

		List<WeightedHit> cumulative = dist.cumulative().getHits();
		assertEquals(4, cumulative.size());
		assertTrue(cumulative.contains(hit(0.5, 0, false)));
		assertTrue(cumulative.contains(hit(0.125, 0, true)));
		assertTrue(cumulative.contains(hit(0.25, 1, true)));
		assertTrue(cumulative.contains(hit(0.125, 2, true)));
	}

	@Test
	public void cumulativePreservesInaccurateNonZeroHits()
	{
		HitDistribution dist = new HitDistribution(Arrays.asList(
			hit(0.5, 0, false),
			hit(0.125, 5, false),
			hit(0.125, 5, true),
			hit(0.125, 1, false),
			hit(0.125, 2, true)
		));

		List<WeightedHit> cumulative = dist.cumulative().getHits();
		assertEquals(5, cumulative.size());
		assertTrue(cumulative.contains(hit(0.5, 0, false)));
		assertTrue(cumulative.contains(hit(0.125, 5, false)));
		assertTrue(cumulative.contains(hit(0.125, 5, true)));
		assertTrue(cumulative.contains(hit(0.125, 1, false)));
		assertTrue(cumulative.contains(hit(0.125, 2, true)));
	}
}
