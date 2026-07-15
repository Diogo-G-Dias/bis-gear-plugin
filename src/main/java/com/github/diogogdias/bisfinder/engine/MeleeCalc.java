package com.github.diogogdias.bisfinder.engine;

/**
 * Melee max hit and hit chance, built directly from the OSRS Wiki DPS formulas
 * (https://oldschool.runescape.wiki/w/Damage_per_second/Melee). No higher-level effects (weapon
 * passives, slayer/salve multipliers, specs) are modelled here yet — those are layered on separately.
 */
public final class MeleeCalc
{
	/** NPCs add a flat 9 to their Defence level to get an effective defence for the roll. */
	private static final int NPC_EFFECTIVE_DEFENCE_BONUS = 9;

	/** Void melee: effective attack and strength levels are increased by 10%, rounded down. */
	private static final Factor VOID_MELEE = new Factor(11, 10);

	private MeleeCalc()
	{
	}

	/**
	 * Effective attack or strength level: {@code floor(currentLevel * prayer) + stanceBonus + 8}, then a
	 * 10% void increase when the void set is worn.
	 */
	public static int effectiveLevel(int level, int boost, Factor prayer, int stanceBonus, boolean voidBonus)
	{
		int current = level + boost;
		int effective = prayer.applyFloor(current) + stanceBonus + 8;
		if (voidBonus)
		{
			effective = VOID_MELEE.applyFloor(effective);
		}
		return effective;
	}

	public static int maxHit(int effectiveStrength, int strengthBonus)
	{
		return RollMath.maxHitFromEffective(effectiveStrength, strengthBonus);
	}

	public static long monsterDefenceRoll(int monsterDefenceLevel, int monsterStyleDefenceBonus)
	{
		return RollMath.defenceRoll(monsterDefenceLevel + NPC_EFFECTIVE_DEFENCE_BONUS, monsterStyleDefenceBonus);
	}

	public static double hitChance(int effectiveAttack, int attackBonus,
		int monsterDefenceLevel, int monsterStyleDefenceBonus)
	{
		long attackRoll = RollMath.attackRoll(effectiveAttack, attackBonus);
		long defenceRoll = monsterDefenceRoll(monsterDefenceLevel, monsterStyleDefenceBonus);
		return RollMath.normalAccuracy(attackRoll, defenceRoll);
	}
}
