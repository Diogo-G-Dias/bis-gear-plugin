package com.github.diogogdias.bisfinder.calc.model;

/**
 * Ported from osrs-dps-calc (src/types/PlayerCombatStyle.ts).
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
