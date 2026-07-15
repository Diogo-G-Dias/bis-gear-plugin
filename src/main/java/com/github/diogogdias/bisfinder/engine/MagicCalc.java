package com.github.diogogdias.bisfinder.engine;

/**
 * Magic max hit and hit chance, from the OSRS Wiki DPS formulas
 * (https://oldschool.runescape.wiki/w/Damage_per_second/Magic and /Maximum_magic_hit).
 *
 * <p>Two things differ from melee/ranged: the effective level adds 9 (not 8) and applies the magic void
 * multiplier of 1.45; and the monster's defence roll uses its Magic level, not its Defence level. Magic
 * max hit does not use the effective level at all for standard spells — it scales the spell's base max by
 * the summed magic-damage bonus (gear + prayer + void), each in thousandths.
 *
 * <p>Only the standard multiplier is modelled here. Shadow, salve, elemental weakness, slayer, tomes and
 * the other conditional multipliers are layered on separately.
 */
public final class MagicCalc
{
	private static final int NPC_EFFECTIVE_DEFENCE_BONUS = 9;

	/** Magic void raises the effective magic level (accuracy) by 45%, rounded down. */
	private static final Factor VOID_MAGIC = new Factor(145, 100);

	private MagicCalc()
	{
	}

	/**
	 * {@code floor(floor((magic + boost) * prayer) * void) + stanceBonus + 9}. The stance bonus is only
	 * non-zero on powered staves (+3 Accurate, +1 Longrange).
	 */
	public static int effectiveLevel(int magicLevel, int boost, Factor prayer, int stanceBonus, boolean voidMagic)
	{
		int effective = prayer.applyFloor(magicLevel + boost);
		if (voidMagic)
		{
			effective = VOID_MAGIC.applyFloor(effective);
		}
		return effective + stanceBonus + 9;
	}

	/**
	 * {@code floor(baseSpellMax * (1 + magicDamageThousandths / 1000))}, where the bonus is the sum of the
	 * gear magic-strength, the prayer's magic-damage bonus and any (elite) void magic damage.
	 */
	public static int maxHit(int baseSpellMax, int magicDamageThousandths)
	{
		return (int) Math.floor(baseSpellMax * (1.0 + magicDamageThousandths / 1000.0));
	}

	public static double hitChance(int effectiveMagic, int magicAttackBonus,
		int monsterMagicLevel, int monsterMagicDefenceBonus)
	{
		long attackRoll = RollMath.attackRoll(effectiveMagic, magicAttackBonus);
		long defenceRoll = RollMath.defenceRoll(monsterMagicLevel + NPC_EFFECTIVE_DEFENCE_BONUS,
			monsterMagicDefenceBonus);
		return RollMath.normalAccuracy(attackRoll, defenceRoll);
	}
}
