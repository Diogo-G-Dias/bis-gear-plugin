package com.github.diogogdias.bisfinder.calc.model;

/**
 * The combat-style stance names from the OSRS Wiki DPS data.
 *
 * <p>MANUAL_CAST is a pseudo stance, matching the wiki calculator.
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
