package com.github.diogogdias.bisfinder.engine;

import com.github.diogogdias.bisfinder.calc.Constants;
import com.github.diogogdias.bisfinder.calc.dist.CalcMath;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.MonsterAttribute;
import com.github.diogogdias.bisfinder.calc.model.MonsterInputs;

/**
 * Chambers of Xeric party-size and Challenge-Mode scaling of the target's defence, from the public wiki
 * formulas. Only defensive stats affect our DPS (the monster's offence and HP do not), so only Defence -
 * and Magic where the monster defends with it - are scaled. Party member levels default to maxed (99 hp /
 * 126 combat), the common endgame case, so only party size and Challenge Mode come from the user.
 *
 * <p>Olm's defence is the same multi formula (its specials only touch HP and offence). Tombs of Amascut
 * invocation scaling is applied separately, at roll time, inside {@link DpsEngine}.
 *
 * <p>Not verified against an oracle (the ported engine was removed); spot-check against dps.osrs.wiki.
 */
public final class RaidScaling
{
	private static final int CM_PERCENT = 50;

	private RaidScaling()
	{
	}

	/** Returns the target with its defensive stats scaled for the Chambers of Xeric inputs, or unchanged. */
	public static Monster scaleCox(Monster monster)
	{
		if (monster == null || !monster.getAttributes().contains(MonsterAttribute.XERICIAN))
		{
			return monster;
		}

		Monster.Skills skills = monster.getSkills();
		boolean magicDefensive = Constants.COX_MAGIC_IS_DEFENSIVE_IDS.contains(monster.getId());

		// Skills sitting at 1 stay at 1; the remaining defensive skills are "linked" and share one base.
		boolean scaleDef = skills.getDef() != 1;
		boolean scaleMagic = magicDefensive && skills.getMagic() != 1;
		if (!scaleDef && !scaleMagic)
		{
			return monster;
		}
		int baseDefensive = Math.max(scaleDef ? skills.getDef() : 1, scaleMagic ? skills.getMagic() : 1);

		int scaled = Constants.COX_USE_SINGLES_SCALING_IDS.contains(monster.getId())
			? singlesDefence(monster, baseDefensive)
			: multiDefence(monster, baseDefensive);

		Monster.Skills updated = new Monster.Skills(
			skills.getAtk(),
			scaleDef ? scaled : skills.getDef(),
			skills.getHp(),
			scaleMagic ? scaled : skills.getMagic(),
			skills.getRanged(),
			skills.getStr());
		return monster.toBuilder().skills(updated).build();
	}

	/** Group scaling (most of the raid, including Olm): party-member levels, then party size, then CM. */
	private static int multiDefence(Monster monster, int baseDefensive)
	{
		MonsterInputs inputs = monster.getInputs();
		int partySize = clamp(inputs.getPartySize(), 1, 100);
		int partySizeM1 = partySize - 1;
		int highestHp = clamp(55 + CalcMath.trunc(44.0 * inputs.getPartyMaxHpLevel() / 99), 55, 99);

		int defensive = CalcMath.trunc((double) baseDefensive * highestHp / 99);
		int scalePct = 100 + CalcMath.iSqrt(partySizeM1) + CalcMath.trunc((double) partySizeM1 * 7 / 10);
		defensive = CalcMath.trunc((double) defensive * scalePct / 100);

		if (inputs.isFromCoxCm())
		{
			// Tekton's defence is bumped less than the flat +50%, more so in small parties.
			defensive = Constants.TEKTON_IDS.contains(monster.getId())
				? CalcMath.addPercent(defensive, partySize < 4 ? 20 : 35)
				: CalcMath.addPercent(defensive, CM_PERCENT);
		}
		return clamp(defensive, 50, 20_000);
	}

	/** Solo-intended monsters (scavenger beasts, vespine soldiers) scale off party levels only. */
	private static int singlesDefence(Monster monster, int baseDefensive)
	{
		MonsterInputs inputs = monster.getInputs();
		int statScaler = clamp(inputs.getPartyMaxHpLevel(), 55, 99);
		if (inputs.isFromCoxCm())
		{
			statScaler = CalcMath.addPercent(statScaler, CM_PERCENT);
		}
		return Math.max(CalcMath.trunc((double) baseDefensive * statScaler / 99), 1);
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
