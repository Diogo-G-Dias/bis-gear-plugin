package com.github.diogogdias.bisfinder.engine;

import org.junit.Assert;
import org.junit.Test;

/**
 * Hand-computed checks of the shared roll formulas. These pin the arithmetic transcription; game-level
 * correctness is covered later by parity tests against the reference outputs.
 */
public class RollMathTest
{
	private static final double EPS = 1e-9;

	@Test
	public void effectiveLevel()
	{
		// No prayer, no stance: 99 + 0 + 8.
		Assert.assertEquals(107, RollMath.effectiveLevel(99, 1.0, 0));
		// Piety strength (+23%), aggressive (+3): floor(99*1.23)=121, +3 +8.
		Assert.assertEquals(132, RollMath.effectiveLevel(99, 1.23, 3));
	}

	@Test
	public void maxHit()
	{
		// floor((132*64 + 320) / 640) = floor(8768/640) = 13.
		Assert.assertEquals(13, RollMath.maxHitFromEffective(132, 0));
		// floor((132*164 + 320) / 640) = floor(21968/640) = 34.
		Assert.assertEquals(34, RollMath.maxHitFromEffective(132, 100));
		Assert.assertEquals(0, RollMath.maxHitFromEffective(0, 0));
	}

	@Test
	public void rolls()
	{
		Assert.assertEquals(6848L, RollMath.attackRoll(107, 0));
		Assert.assertEquals(21648L, RollMath.attackRoll(132, 100));
		Assert.assertEquals(6848L, RollMath.defenceRoll(107, 0));
	}

	@Test
	public void accuracyWhenAttackExceedsDefence()
	{
		// atk > def: 1 - (def+2)/(2*(atk+1)).
		double expected = 1.0 - (1002.0) / (2.0 * 21649.0);
		Assert.assertEquals(expected, RollMath.normalAccuracy(21648, 1000), EPS);
	}

	@Test
	public void accuracyWhenDefenceExceedsAttack()
	{
		// atk <= def: atk/(2*(def+1)).
		double expected = 1000.0 / (2.0 * 21649.0);
		Assert.assertEquals(expected, RollMath.normalAccuracy(1000, 21648), EPS);
	}

	@Test
	public void accuracyIsBoundedAndMonotonic()
	{
		double low = RollMath.normalAccuracy(5000, 20000);
		double mid = RollMath.normalAccuracy(20000, 20000);
		double high = RollMath.normalAccuracy(40000, 20000);
		Assert.assertTrue(low >= 0.0 && high <= 1.0);
		Assert.assertTrue(low < mid && mid < high);
	}
}
