package com.github.diogogdias.bisfinder.engine;

/**
 * An integer multiplier expressed as {@code numerator / denominator}, applied with truncation toward
 * negative infinity — the way the game applies prayer bonuses, void, and similar level multipliers.
 */
public final class Factor
{
	private final long numerator;
	private final long denominator;

	public Factor(long numerator, long denominator)
	{
		if (denominator <= 0)
		{
			throw new IllegalArgumentException("denominator must be positive");
		}
		this.numerator = numerator;
		this.denominator = denominator;
	}

	public static Factor identity()
	{
		return new Factor(1, 1);
	}

	/** {@code floor(value * numerator / denominator)}. */
	public int applyFloor(int value)
	{
		return (int) Math.floorDiv((long) value * numerator, denominator);
	}

	/** {@code floor(value * numerator / denominator)} for roll-sized values. */
	public long applyFloor(long value)
	{
		return Math.floorDiv(value * numerator, denominator);
	}
}
