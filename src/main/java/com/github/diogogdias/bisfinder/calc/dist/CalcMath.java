package com.github.diogogdias.bisfinder.calc.dist;

/**
 * Small integer/float helpers for the combat maths.
 *
 * All helpers compute values in double (floating point) arithmetic and then truncate toward zero,
 * matching JavaScript's Math.trunc.
 */
public final class CalcMath
{
	private CalcMath()
	{
	}

	/**
	 * Equivalent of JavaScript's Math.trunc: truncates toward zero.
	 */
	public static int trunc(double x)
	{
		return (int) x;
	}

	/**
	 * Applies a [factor, divisor] pair the same way the TS calculator does:
	 * Math.trunc(x * f[0] / f[1]), in double arithmetic.
	 */
	public static int applyFactor(int x, Factor f)
	{
		return trunc((double) x * (double) f.getFactor() / (double) f.getDivisor());
	}

	public static int lerp(int curr, int srcStart, int srcEnd, int dstStart, int dstEnd)
	{
		// todo replace usages with iLerp
		// does this need to be int-based?
		int srcRange = srcEnd - srcStart;
		int dstRange = dstEnd - dstStart;
		int currNorm = curr - srcStart;

		return trunc(((double) currNorm * (double) dstRange / (double) srcRange) + (double) dstStart);
	}

	public static int iSqrt(double x)
	{
		return trunc(Math.sqrt(x));
	}

	public static int iLerp(int x1, int x2, int y1, int y2, int yc)
	{
		return x1 + trunc((double) (x2 - x1) * (double) (yc - y1) / (double) (y2 - y1));
	}

	public static int addPercent(int x, int pct)
	{
		return x + trunc((double) x * (double) pct / 100.0);
	}

	/**
	 * Count of permutations of k selections from n items.
	 *
	 * @param k number of selections
	 * @param n number of items to choose from
	 */
	public static double choose(int k, int n)
	{
		double acc = 1;
		for (int i = 1; i <= k; i++)
		{
			acc *= n - i + 1;
			acc /= i;
		}

		return acc;
	}

	/**
	 * Chance of exactly k events happening in n trials.
	 *
	 * @param p probability of an event happening
	 * @param k number of events
	 * @param n number of trials
	 */
	public static double binomal(double p, int k, int n)
	{
		double permutations = choose(k, n);
		double chanceAllHappen = Math.pow(p, k);
		double chanceExtrasDontHappen = Math.pow(1 - p, n - k);
		return permutations * chanceAllHappen * chanceExtrasDontHappen;
	}
}
