package com.github.diogogdias.bisfinder.calc.dist;

import java.util.Objects;

/**
 * Port of the Factor tuple type from osrs-dps-calc (src/lib/Math.ts): [factor, divisor].
 */
public final class Factor
{
	private final int factor;
	private final int divisor;

	private Factor(int factor, int divisor)
	{
		this.factor = factor;
		this.divisor = divisor;
	}

	public static Factor of(int factor, int divisor)
	{
		return new Factor(factor, divisor);
	}

	public int getFactor()
	{
		return factor;
	}

	public int getDivisor()
	{
		return divisor;
	}

	@Override
	public boolean equals(Object o)
	{
		if (this == o)
		{
			return true;
		}
		if (!(o instanceof Factor))
		{
			return false;
		}
		Factor other = (Factor) o;
		return factor == other.factor && divisor == other.divisor;
	}

	@Override
	public int hashCode()
	{
		return Objects.hash(factor, divisor);
	}

	@Override
	public String toString()
	{
		return "[" + factor + ", " + divisor + "]";
	}
}
