package com.github.diogogdias.bisfinder.calc;

import com.github.diogogdias.bisfinder.calc.dist.CalcMath;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.MonsterAttribute;
import com.github.diogogdias.bisfinder.calc.model.MonsterInputs;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of osrs-dps-calc's src/lib/MonsterScaling.ts, including the transformers it composes
 * (src/lib/scaling/{ChambersOfXeric,TheatreOfBlood,TombsOfAmascut,Vardorvis,Phases,DefenceReduction}.ts).
 * The order of operations is the same as ORDER_OF_OPERATIONS in the source.
 */
public final class MonsterScaling
{
	private MonsterScaling()
	{
	}

	public static Monster scaleMonster(Monster m)
	{
		m = applyCoxScaling(m);
		m = applyTobScaling(m);
		m = applyToaScaling(m);
		m = applyVardScaling(m);
		m = applyMonsterPhases(m);
		m = applyDefenceReductions(m);
		return m;
	}

	/**
	 * To save a lot of unneeded compute work if hp is the only thing that changes.
	 */
	public static Monster scaleMonsterHpOnly(Monster m)
	{
		if ("Vardorvis".equals(m.getName()))
		{
			return applyVardScaling(m);
		}

		return m;
	}

	// ---------------------------------------------------------------------------------------------
	// Chambers of Xeric
	// ---------------------------------------------------------------------------------------------

	private static final int CM_SCALE_PERCENT = 50;

	private static final String ATK = "atk";
	private static final String DEF = "def";
	private static final String HP = "hp";
	private static final String MAGIC = "magic";
	private static final String RANGED = "ranged";
	private static final String STR = "str";

	/**
	 * Mutable stand-in for the TS code's `Partial<MonsterSkills>` changesets and its string indexing into
	 * Monster['skills'].
	 */
	private static final class MutableSkills
	{
		private final Map<String, Integer> values = new HashMap<>();

		private MutableSkills(Monster.Skills skills)
		{
			values.put(ATK, skills.getAtk());
			values.put(DEF, skills.getDef());
			values.put(HP, skills.getHp());
			values.put(MAGIC, skills.getMagic());
			values.put(RANGED, skills.getRanged());
			values.put(STR, skills.getStr());
		}

		private int get(String skill)
		{
			return values.get(skill);
		}

		private void set(String skill, int value)
		{
			values.put(skill, value);
		}

		private Monster.Skills toSkills()
		{
			return new Monster.Skills(
				get(ATK), get(DEF), get(HP), get(MAGIC), get(RANGED), get(STR));
		}
	}

	private static final class SkillMeta
	{
		private final List<String> offensives;
		private final int baseOffensive;
		private final List<String> defensives;
		private final int baseDefensive;
		private final int baseHp;

		private SkillMeta(List<String> offensives, int baseOffensive, List<String> defensives, int baseDefensive, int baseHp)
		{
			this.offensives = offensives;
			this.baseOffensive = baseOffensive;
			this.defensives = defensives;
			this.baseDefensive = baseDefensive;
			this.baseHp = baseHp;
		}
	}

	private static SkillMeta getSkillMeta(Monster m)
	{
		boolean magicIsDefensive = Constants.COX_MAGIC_IS_DEFENSIVE_IDS.contains(m.getId());

		List<String> offensivesAll = new ArrayList<>(Arrays.asList(ATK, STR, RANGED));
		List<String> defensivesAll = new ArrayList<>(Arrays.asList(DEF));
		if (magicIsDefensive)
		{
			defensivesAll.add(MAGIC);
		}
		else
		{
			offensivesAll.add(MAGIC);
		}

		MutableSkills skills = new MutableSkills(m.getSkills());

		// don't scale skills set at 1, they should remain at 1
		List<String> offensives = new ArrayList<>();
		for (String k : offensivesAll)
		{
			if (skills.get(k) != 1)
			{
				offensives.add(k);
			}
		}
		List<String> defensives = new ArrayList<>();
		for (String k : defensivesAll)
		{
			if (skills.get(k) != 1)
			{
				defensives.add(k);
			}
		}

		// determine the "base" stat of remaining skills (they should all be the same since they're linked)
		int baseOffensive = maxOf(skills, offensives);
		int baseDefensive = maxOf(skills, defensives);

		MonsterInputs inputs = m.getInputs();
		int baseHp = Constants.GUARDIAN_IDS.contains(m.getId())
			? 151 + CalcMath.trunc((double) inputs.getPartySumMiningLevel() / inputs.getPartySize())
			: m.getSkills().getHp();

		return new SkillMeta(offensives, baseOffensive, defensives, baseDefensive, baseHp);
	}

	/**
	 * d3.max over an empty list is undefined, which the TS source coalesces to 1.
	 */
	private static int maxOf(MutableSkills skills, List<String> keys)
	{
		if (keys.isEmpty())
		{
			return 1;
		}

		int max = Integer.MIN_VALUE;
		for (String k : keys)
		{
			max = Math.max(max, skills.get(k));
		}
		return max;
	}

	/**
	 * Things intended to be fought solo (even though everything is technically multi), like scavenger
	 * beasts and vespine soldiers.
	 */
	private static Monster applySinglesCoxScaling(Monster m)
	{
		MonsterInputs inputs = m.getInputs();
		SkillMeta meta = getSkillMeta(m);

		// scaling factors based on clamped inputs
		int hpScaler = Math.max(Math.min(inputs.getPartyMaxCombatLevel(), 126), 60);
		int statScaler = Math.max(Math.min(inputs.getPartyMaxHpLevel(), 99), 55);

		// increase everything for cm
		if (inputs.isFromCoxCm())
		{
			statScaler = CalcMath.addPercent(statScaler, CM_SCALE_PERCENT);
			hpScaler = CalcMath.addPercent(hpScaler, CM_SCALE_PERCENT);
		}

		MutableSkills skills = new MutableSkills(m.getSkills());
		skills.set(HP, Math.max(CalcMath.trunc((double) meta.baseHp * hpScaler / 126), 5));
		for (String o : meta.offensives)
		{
			skills.set(o, Math.max(CalcMath.trunc((double) meta.baseOffensive * statScaler / 99), 1));
		}
		for (String d : meta.defensives)
		{
			skills.set(d, Math.max(CalcMath.trunc((double) meta.baseDefensive * statScaler / 99), 1));
		}

		return m.toBuilder().skills(skills.toSkills()).build();
	}

	/**
	 * Everything intended to be fought in a group (most things overall) uses this.
	 */
	private static Monster applyMultiCoxScaling(Monster m)
	{
		MonsterInputs inputs = m.getInputs();
		SkillMeta meta = getSkillMeta(m);
		int id = m.getId();

		// clamp a bunch of input values
		int partySize = Math.min(Math.max(inputs.getPartySize(), 1), 100);
		int partySizeM1 = partySize - 1;
		int highestComLevel = Math.max(Math.min(inputs.getPartyMaxCombatLevel(), 126), 60);
		int highestHp = Math.max(Math.min(55 + CalcMath.trunc(44.0 * inputs.getPartyMaxHpLevel() / 99), 99), 55);

		// scale base stats by party member stats
		int offensive = CalcMath.trunc((double) meta.baseOffensive * highestHp / 99);
		int defensive = CalcMath.trunc((double) meta.baseDefensive * highestHp / 99);
		int hp = CalcMath.trunc((double) meta.baseHp * highestComLevel / 126);

		// scale everything based on party size in varying ways
		int offensiveScalePct = 100 + CalcMath.iSqrt(partySizeM1) * 7 + partySizeM1;
		offensive = CalcMath.trunc((double) offensive * offensiveScalePct / 100);

		int defensiveScalePct = 100 + CalcMath.iSqrt(partySizeM1) + CalcMath.trunc((double) partySizeM1 * 7 / 10);
		defensive = CalcMath.trunc((double) defensive * defensiveScalePct / 100);

		hp += hp * CalcMath.trunc((double) partySize * 50 / 100);

		// increase all stats for cm (with some exceptions)
		if (inputs.isFromCoxCm())
		{
			offensive = CalcMath.addPercent(offensive, CM_SCALE_PERCENT);

			if (!Constants.GLOWING_CRYSTAL_IDS.contains(id))
			{
				hp = CalcMath.addPercent(hp, CM_SCALE_PERCENT);
			}

			if (Constants.GLOWING_CRYSTAL_IDS.contains(id))
			{
				// not scaled
			}
			else if (Constants.TEKTON_IDS.contains(id))
			{
				// tekton gets special treatment to make specs easier
				if (partySize < 4)
				{
					// especially for small party sizes
					defensive = CalcMath.addPercent(defensive, 20);
				}
				else
				{
					defensive = CalcMath.addPercent(defensive, 35);
				}
			}
			else
			{
				defensive = CalcMath.addPercent(defensive, CM_SCALE_PERCENT);
			}
		}

		// make the changeset (and clamp for sanity)
		MutableSkills skills = new MutableSkills(m.getSkills());
		skills.set(HP, Math.max(Math.min(hp, 30_000), 50));
		for (String o : meta.offensives)
		{
			skills.set(o, Math.max(Math.min(offensive, 5_000), 50));
		}
		for (String d : meta.defensives)
		{
			skills.set(d, Math.max(Math.min(defensive, 20_000), 50));
		}

		return m.toBuilder().skills(skills.toSkills()).build();
	}

	private static Monster applyOlmScaling(Monster m)
	{
		boolean lhand = Constants.OLM_MELEE_HAND_IDS.contains(m.getId());
		boolean rhand = Constants.OLM_MAGE_HAND_IDS.contains(m.getId());

		MonsterInputs inputs = m.getInputs();

		// partySize - 3 * extraPhases, basically
		int partySizeScaleFactor = Math.min(inputs.getPartySize() - 1, 50)
			- 3 * CalcMath.trunc((double) Math.min(inputs.getPartySize(), 50) / 8);

		Monster base = applyMultiCoxScaling(m);
		int magic = rhand ? CalcMath.trunc((double) base.getSkills().getMagic() / 2) : base.getSkills().getMagic();
		int hp = (lhand || rhand)
			? 600 + 300 * partySizeScaleFactor
			: 800 + 400 * partySizeScaleFactor;

		Monster.Skills s = base.getSkills();
		return base.toBuilder()
			.skills(new Monster.Skills(s.getAtk(), s.getDef(), hp, magic, s.getRanged(), s.getStr()))
			.build();
	}

	private static Monster applyCoxScaling(Monster m)
	{
		if (!m.getAttributes().contains(MonsterAttribute.XERICIAN))
		{
			return m;
		}

		if (Constants.COX_USE_SINGLES_SCALING_IDS.contains(m.getId()))
		{
			return applySinglesCoxScaling(m);
		}

		if (Constants.OLM_IDS.contains(m.getId()))
		{
			return applyOlmScaling(m);
		}

		return applyMultiCoxScaling(m);
	}

	// ---------------------------------------------------------------------------------------------
	// Theatre of Blood
	// ---------------------------------------------------------------------------------------------

	private static Monster applyTobScaling(Monster m)
	{
		MonsterInputs inputs = m.getInputs();

		// tob only scales hp and nothing else
		if (Constants.TOB_MONSTER_IDS.contains(m.getId()))
		{
			int partySize = Math.min(5, Math.max(3, inputs.getPartySize()));
			return withHp(m, CalcMath.trunc((double) m.getSkills().getHp() * (partySize + 3) / 8));
		}

		if (Constants.TOB_EM_MONSTER_IDS.contains(m.getId()))
		{
			int partySize = Math.min(5, Math.max(1, inputs.getPartySize()));
			int numerator;
			switch (partySize)
			{
				case 1:
					numerator = 10;
					break;
				case 2:
					numerator = 19;
					break;
				case 3:
					numerator = 27;
					break;
				case 4:
					numerator = 34;
					break;
				default:
					numerator = 40;
					break;
			}
			return withHp(m, CalcMath.trunc((double) m.getSkills().getHp() * numerator / 40));
		}

		return m;
	}

	// ---------------------------------------------------------------------------------------------
	// Tombs of Amascut
	// ---------------------------------------------------------------------------------------------

	private static Monster applyToaScaling(Monster m)
	{
		MonsterInputs inputs = m.getInputs();

		// toa multiplies rolled values, not stats, except for hp
		if (!Constants.TOMBS_OF_AMASCUT_MONSTER_IDS.contains(m.getId()))
		{
			return m;
		}

		int newHp = m.getSkills().getHp();
		if (Constants.TOA_WARDEN_CORE_EJECTED_IDS.contains(m.getId()))
		{
			newHp = 4500;
		}

		int invoFactor = CalcMath.trunc(
			(double) (Constants.TOA_WARDEN_CORE_EJECTED_IDS.contains(m.getId()) ? 1 : 4)
				* inputs.getToaInvocationLevel() / 10);
		newHp += CalcMath.trunc((double) newHp * invoFactor / 100);

		int pathLevel = Math.min(6, Math.max(0, inputs.getToaPathLevel()));
		if (Constants.TOMBS_OF_AMASCUT_PATH_MONSTER_IDS.contains(m.getId()) && pathLevel >= 1)
		{
			int pathLevelFactor = 3 + 5 * inputs.getToaPathLevel();
			newHp = CalcMath.trunc((double) newHp * (100 + pathLevelFactor) / 100);
		}

		int partySize = Math.min(8, Math.max(1, inputs.getPartySize()));
		if (partySize >= 2)
		{
			int partyFactor = 9 * (partySize >= 3 ? 2 : 1);
			if (partySize >= 4)
			{
				partyFactor += 6 * (partySize - 3);
			}

			newHp = CalcMath.trunc((double) newHp * (10 + partyFactor) / 10);
		}

		// some rounding, for once
		if (newHp > 100)
		{
			int roundTo = newHp > 300 ? 10 : 5;
			newHp = CalcMath.trunc((double) (newHp + CalcMath.trunc((double) roundTo / 2)) / roundTo) * roundTo;
		}

		return withHp(m, newHp);
	}

	// ---------------------------------------------------------------------------------------------
	// Vardorvis
	// ---------------------------------------------------------------------------------------------

	private static Monster applyVardScaling(Monster m)
	{
		if (!"Vardorvis".equals(m.getName()))
		{
			return m;
		}

		int maxHp;
		int strStart;
		int strEnd;
		int defStart;
		int defEnd;
		if ("Quest".equals(m.getVersion()))
		{
			maxHp = 500;
			strStart = 210;
			strEnd = 280;
			defStart = 180;
			defEnd = 130;
		}
		else if ("Awakened".equals(m.getVersion()))
		{
			maxHp = 1400;
			strStart = 391;
			strEnd = 522;
			defStart = 268;
			defEnd = 181;
		}
		else
		{
			maxHp = 700;
			strStart = 270;
			strEnd = 360;
			defStart = 215;
			defEnd = 145;
		}

		// vard's strength and defence scale linearly throughout the fight based on hp
		int currHp = m.getInputs().getMonsterCurrentHp();
		Monster.Skills s = m.getSkills();
		return m.toBuilder()
			.skills(new Monster.Skills(
				s.getAtk(),
				CalcMath.lerp(currHp, maxHp, 0, defStart, defEnd),
				s.getHp(),
				s.getMagic(),
				s.getRanged(),
				CalcMath.lerp(currHp, maxHp, 0, strStart, strEnd)
			))
			.build();
	}

	// ---------------------------------------------------------------------------------------------
	// Phases
	// ---------------------------------------------------------------------------------------------

	private static Monster applyMonsterPhases(Monster m)
	{
		String phase = m.getInputs().getPhase();
		if (phase == null || phase.isEmpty())
		{
			return m;
		}

		if (Constants.ARAXXOR_IDS.contains(m.getId()) && "Enraged".equals(phase))
		{
			Monster.Skills s = m.getSkills();
			return m.toBuilder()
				.skills(new Monster.Skills(
					s.getAtk(),
					s.getDef() + 35,
					s.getHp(),
					s.getMagic() + 28,
					s.getRanged() + 31,
					s.getStr()
				))
				.build();
		}

		// yama goes to 60 magic defence if the tank is using magic
		if (Constants.YAMA_IDS.contains(m.getId()) && !"Enraged".equals(m.getVersion()))
		{
			int mdef = "Tank using magic".equals(phase) ? 60 : -30;
			Monster.Defensive d = m.getDefensive();
			return m.toBuilder()
				.defensive(new Monster.Defensive(
					d.getStab(), d.getSlash(), d.getCrush(), mdef,
					d.getHeavy(), d.getStandard(), d.getLight(), d.getFlatArmour()
				))
				.build();
		}

		return m;
	}

	// ---------------------------------------------------------------------------------------------
	// Defence reductions
	// ---------------------------------------------------------------------------------------------

	public static int getDefenceFloor(Monster m)
	{
		int id = m.getId();
		if (Constants.VERZIK_IDS.contains(id) || Constants.VARDORVIS_IDS.contains(id))
		{
			return m.getSkills().getDef();
		}
		if (Constants.SOTETSEG_IDS.contains(id))
		{
			return 100;
		}
		if (Constants.NIGHTMARE_IDS.contains(id))
		{
			return 120;
		}
		if (Constants.AKKHA_IDS.contains(id))
		{
			return 70;
		}
		if (Constants.BABA_IDS.contains(id))
		{
			return 60;
		}
		if (Constants.KEPHRI_UNSHIELDED_IDS.contains(id) || Constants.KEPHRI_SHIELDED_IDS.contains(id))
		{
			return 60;
		}
		if (Constants.ZEBAK_IDS.contains(id))
		{
			return 50;
		}
		if (Constants.P3_WARDEN_IDS.contains(id))
		{
			return 120;
		}
		if (Constants.TOA_OBELISK_IDS.contains(id))
		{
			return 60;
		}
		if (Constants.NEX_IDS.contains(id))
		{
			return 250;
		}
		if (Constants.ARAXXOR_IDS.contains(id))
		{
			return 90;
		}
		if (Constants.HUEYCOATL_IDS.contains(id))
		{
			return 120;
		}
		if (Constants.YAMA_IDS.contains(id))
		{
			return 145;
		}

		// no limit
		return 0;
	}

	private static Monster applyDefenceReductions(Monster m)
	{
		Monster.Skills baseSkills = m.getSkills();
		int defenceFloor = getDefenceFloor(m);
		MonsterInputs.DefenceReductions reductions = m.getInputs().getDefenceReductions();

		MutableSkills skills = new MutableSkills(m.getSkills());

		if (reductions.isAccursed())
		{
			applySkill(skills, defenceFloor, DEF, CalcMath.trunc((double) skills.get(DEF) * 17 / 20));
			applySkill(skills, defenceFloor, MAGIC, CalcMath.trunc((double) skills.get(MAGIC) * 17 / 20));
		}
		else if (reductions.isVulnerability())
		{
			applySkill(skills, defenceFloor, DEF, CalcMath.trunc((double) skills.get(DEF) * 9 / 10));
		}

		for (int i = 0; i < reductions.getElderMaul(); i++)
		{
			applySkill(skills, defenceFloor, DEF, skills.get(DEF) - CalcMath.trunc((double) skills.get(DEF) * 35 / 100));
		}
		for (int i = 0; i < reductions.getDwh(); i++)
		{
			applySkill(skills, defenceFloor, DEF, skills.get(DEF) - CalcMath.trunc((double) skills.get(DEF) * 3 / 10));
		}

		boolean isDemon = m.getAttributes().contains(MonsterAttribute.DEMON);
		reduceArclight(skills, defenceFloor, baseSkills, reductions.getArclight(), isDemon ? 2 : 1, 20);
		reduceArclight(skills, defenceFloor, baseSkills, reductions.getEmberlight(), isDemon ? 3 : 1, 20);

		for (int i = 0; i < reductions.getTonalztic(); i++)
		{
			applySkill(skills, defenceFloor, DEF, skills.get(DEF) - CalcMath.trunc((double) skills.get(MAGIC) / 10));
		}

		if (reductions.getSeercull() > 0)
		{
			applySkill(skills, defenceFloor, MAGIC, skills.get(MAGIC) - reductions.getSeercull());
		}

		int bgsDmg = reductions.getBgs();
		if (bgsDmg > 0)
		{
			// order matters here
			for (String k : Arrays.asList(DEF, STR, ATK, MAGIC, RANGED))
			{
				int startLevel = skills.get(k);
				applySkill(skills, defenceFloor, k, startLevel - bgsDmg);
				if (skills.get(k) > 0)
				{
					// if a skill fails to drain to 0, even if because of a drain floor,
					// the bgs does not propagate further
					bgsDmg = 0;
				}
				else
				{
					bgsDmg -= startLevel;
				}
			}
		}

		Monster out = m.toBuilder().skills(skills.toSkills()).build();

		if (reductions.getAyak() > 0 && out.getDefensive().getMagic() > 0)
		{
			int newMagicDef = Math.max(0, out.getDefensive().getMagic() - reductions.getAyak());
			Monster.Defensive d = out.getDefensive();
			out = out.toBuilder()
				.defensive(new Monster.Defensive(
					d.getStab(), d.getSlash(), d.getCrush(), newMagicDef,
					d.getHeavy(), d.getStandard(), d.getLight(), d.getFlatArmour()
				))
				.build();
		}

		return out;
	}

	private static void reduceArclight(MutableSkills skills, int defenceFloor, Monster.Skills baseSkills,
		int iter, int num, int den)
	{
		if (iter == 0)
		{
			return;
		}

		applySkill(skills, defenceFloor, ATK,
			skills.get(ATK) - (iter * (CalcMath.trunc((double) num * baseSkills.getAtk() / den) + 1)));
		applySkill(skills, defenceFloor, STR,
			skills.get(STR) - (iter * (CalcMath.trunc((double) num * baseSkills.getStr() / den) + 1)));
		applySkill(skills, defenceFloor, DEF,
			skills.get(DEF) - (iter * (CalcMath.trunc((double) num * baseSkills.getDef() / den) + 1)));
	}

	/**
	 * Port of the newSkills() closure: every write is floored, at the defence floor for def and at 0 for
	 * everything else.
	 */
	private static void applySkill(MutableSkills skills, int defenceFloor, String skill, int value)
	{
		int floor = DEF.equals(skill) ? defenceFloor : 0;
		skills.set(skill, Math.max(floor, value));
	}

	private static Monster withHp(Monster m, int hp)
	{
		Monster.Skills s = m.getSkills();
		return m.toBuilder()
			.skills(new Monster.Skills(s.getAtk(), s.getDef(), hp, s.getMagic(), s.getRanged(), s.getStr()))
			.build();
	}
}
