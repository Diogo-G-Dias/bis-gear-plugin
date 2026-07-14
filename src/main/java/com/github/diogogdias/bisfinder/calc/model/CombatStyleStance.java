package com.github.diogogdias.bisfinder.calc.model;

/**
 * Ported from osrs-dps-calc (src/types/PlayerCombatStyle.ts).
 *
 * <p>MANUAL_CAST is a pseudo stance, as it is in the calculator this is ported from.
 */
public enum CombatStyleStance
{
	ACCURATE("Accurate"),
	AGGRESSIVE("Aggressive"),
	AUTOCAST("Autocast"),
	CONTROLLED("Controlled"),
	DEFENSIVE("Defensive"),
	DEFENSIVE_AUTOCAST("Defensive Autocast"),
	LONGRANGE("Longrange"),
	RAPID("Rapid"),
	MANUAL_CAST("Manual Cast");

	private final String stanceName;

	CombatStyleStance(String stanceName)
	{
		this.stanceName = stanceName;
	}

	public String getStanceName()
	{
		return stanceName;
	}

	public boolean isCastStance()
	{
		return this == AUTOCAST || this == DEFENSIVE_AUTOCAST || this == MANUAL_CAST;
	}
}
