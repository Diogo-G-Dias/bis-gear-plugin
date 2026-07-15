package com.github.diogogdias.bisfinder.calc.model;

/**
 * The combat-style attack types from the OSRS Wiki DPS data.
 *
 * <p>These map directly onto the monster's defensive bonuses.
 */
public enum CombatStyleType
{
	STAB,
	SLASH,
	CRUSH,
	MAGIC,
	RANGED;

	public boolean isMelee()
	{
		return this == STAB || this == SLASH || this == CRUSH;
	}
}
