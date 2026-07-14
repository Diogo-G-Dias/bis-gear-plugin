package com.github.diogogdias.bisfinder.calc.dist;

import org.junit.Test;

import static com.github.diogogdias.bisfinder.calc.dist.HitTransformers.multiplyTransformer;
import static org.junit.Assert.assertEquals;

/**
 * Port of osrs-dps-calc's src/tests/calc/Transformers.test.ts.
 */
public class HitTransformersTest
{
	private static int applied(HitTransformer t, Hitsplat h)
	{
		return t.apply(h).getHits().get(0).getHitsplats().get(0).getDamage();
	}

	@Test
	public void multiplyTransformerAppliesTruncation()
	{
		assertEquals(6, applied(multiplyTransformer(5, 4), new Hitsplat(5)));
	}

	@Test
	public void multiplyTransformerDoesNotReduceValuesBelowTheMinimum()
	{
		assertEquals(2, applied(multiplyTransformer(1, 5, 2), new Hitsplat(3)));
	}

	@Test
	public void multiplyTransformerDoesNotRaiseValuesFromBelowTheMinimumToAboveTheMinimum()
	{
		assertEquals(1, applied(multiplyTransformer(1, 5, 2), new Hitsplat(1)));
	}
}
