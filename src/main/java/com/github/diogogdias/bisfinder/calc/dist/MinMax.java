package com.github.diogogdias.bisfinder.calc.dist;

import java.util.Objects;

/**
 * Port of the MinMax tuple type from osrs-dps-calc (src/lib/Math.ts): [min, max].
 */
public final class MinMax
{
	private final int min;
	private final int max;

	private MinMax(int min, int max)
	{
		this.min = min;
		this.max = max;
	}

	public static MinMax of(int min, int max)
	{
		return new MinMax(min, max);
	}

	public int getMin()
	{
		return min;
	}

	public int getMax()
	{
		return max;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof MinMax))
		{
			return false;
		}
		MinMax other = (MinMax) o;
		return min == other.min && max == other.max;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(min, max);
	}

	@Override
	public String toString()
	{
		return "[" + min + ", " + max + "]";
	}
}
