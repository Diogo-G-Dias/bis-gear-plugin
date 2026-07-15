package com.github.diogogdias.bisfinder.engine;

/**
 * Ranged max hit and hit chance, from the OSRS Wiki DPS formulas
 * (https://oldschool.runescape.wiki/w/Damage_per_second/Ranged). The effective-level and roll maths are
 * the same shape as melee; the inputs are the ranged strength/attack bonuses and the monster's ranged
 * defence for the ammo's damage type (standard / light / heavy).
 */
public final class RangedCalc
{
	private static final int NPC_EFFECTIVE_DEFENCE_BONUS = 9;

	/** Regular void ranging: +10% ranged attack and strength, rounded down. */
	private static final Factor VOID_RANGED = new Factor(11, 10);

	private RangedCalc()
	{
	}

	public static int effectiveLevel(int rangedLevel, int boost, Factor prayer, int stanceBonus, boolean voidBonus)
	{
		int current = rangedLevel + boost;
		int effective = prayer.applyFloor(current) + stanceBonus + 8;
		if (voidBonus)
		{
			effective = VOID_RANGED.applyFloor(effective);
		}
		return effective;
	}

	public static int maxHit(int effectiveRangedStrength, int rangedStrengthBonus)
	{
		return RollMath.maxHitFromEffective(effectiveRangedStrength, rangedStrengthBonus);
	}

	public static double hitChance(int effectiveRangedAttack, int rangedAttackBonus,
		int monsterDefenceLevel, int monsterRangedDefenceBonus)
	{
		long attackRoll = RollMath.attackRoll(effectiveRangedAttack, rangedAttackBonus);
		long defenceRoll = RollMath.defenceRoll(monsterDefenceLevel + NPC_EFFECTIVE_DEFENCE_BONUS,
			monsterRangedDefenceBonus);
		return RollMath.normalAccuracy(attackRoll, defenceRoll);
	}
}
