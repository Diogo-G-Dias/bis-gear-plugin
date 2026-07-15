package com.github.diogogdias.bisfinder.engine;

import com.github.diogogdias.bisfinder.calc.model.CombatStyleStance;
import com.github.diogogdias.bisfinder.calc.model.CombatStyleType;
import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.MonsterAttribute;
import com.github.diogogdias.bisfinder.calc.model.Player;
import com.github.diogogdias.bisfinder.calc.model.Prayer;
import com.github.diogogdias.bisfinder.calc.model.Spell;
import java.util.Arrays;
import java.util.List;

/**
 * Assembles a full attack from the proven pieces: effective levels (with prayer / stance / void), base
 * max hit and roll, gear/context multipliers, then a hit distribution, then DPS. Reads factual constants
 * (prayer multipliers) off the data model at runtime rather than restating them.
 *
 * <p>Melee only for now; ranged and magic reuse the same shape. It consumes a {@link Player} whose gear
 * bonuses are already aggregated ({@link Player#withGearBonuses}); the aggregation is a separate layer.
 */
public final class DpsEngine
{
	private DpsEngine()
	{
	}

	/** Dispatches to the style-specific DPS. Returns 0 for setups the engine cannot yet score. */
	public static double dps(Player player, Monster monster)
	{
		if (player == null || player.getStyle() == null)
		{
			return 0;
		}
		double dps;
		switch (player.getStyle().getType())
		{
			case MAGIC:
				dps = magicDps(player, monster);
				break;
			case RANGED:
				dps = rangedDps(player, monster);
				break;
			default:
				dps = meleeDps(player, monster);
				break;
		}
		return Double.isFinite(dps) ? dps : 0;
	}

	/** DPS plus the headline numbers the panel shows: the (summed) max hit and the hit chance. */
	public static Estimate estimate(Player player, Monster monster)
	{
		if (player == null || player.getStyle() == null)
		{
			return Estimate.ZERO;
		}
		switch (player.getStyle().getType())
		{
			case MAGIC:
				return magicEstimate(player, monster);
			case RANGED:
				return rangedEstimate(player, monster);
			default:
				return meleeEstimate(player, monster);
		}
	}

	/** A scored attack: the DPS, the max hit shown (summed over a multi-hit weapon's splats) and hit chance. */
	public static final class Estimate
	{
		static final Estimate ZERO = new Estimate(0, 0, 0);

		private final double dps;
		private final int maxHit;
		private final double accuracy;

		Estimate(double dps, int maxHit, double accuracy)
		{
			this.dps = Double.isFinite(dps) ? dps : 0;
			this.maxHit = maxHit;
			this.accuracy = accuracy;
		}

		static Estimate of(AttackDistribution distribution, int attackSpeedTicks)
		{
			int maxHit = 0;
			for (Hitsplat hitsplat : distribution.getHitsplats())
			{
				maxHit += hitsplat.getMaxHit();
			}
			double accuracy = distribution.getHitsplats().isEmpty()
				? 0 : distribution.getHitsplats().get(0).getAccuracy();
			return new Estimate(distribution.dps(attackSpeedTicks), maxHit, accuracy);
		}

		public double getDps()
		{
			return dps;
		}

		public int getMaxHit()
		{
			return maxHit;
		}

		public double getAccuracy()
		{
			return accuracy;
		}
	}

	public static double meleeDps(Player player, Monster monster)
	{
		return meleeEstimate(player, monster).getDps();
	}

	public static Estimate meleeEstimate(Player player, Monster monster)
	{
		CombatStyleType type = player.getStyle().getType();
		CombatStyleStance stance = player.getStyle().getStance();

		// Flying monsters can only be meleed with a halberd's reach.
		if (hasAttribute(monster, MonsterAttribute.FLYING) && !isReachWeapon(player))
		{
			return Estimate.ZERO;
		}

		int effStrength = MeleeCalc.effectiveLevel(
			player.getSkills().getStr(), player.getBoosts().getStr(),
			strengthPrayer(player.getPrayers()), strengthStance(stance), hasVoidMelee(player));
		effStrength += soulreaperStrengthBonus(player);
		int effAttack = MeleeCalc.effectiveLevel(
			player.getSkills().getAtk(), player.getBoosts().getAtk(),
			accuracyPrayer(player.getPrayers()), attackStance(stance), hasVoidMelee(player));

		int baseMax = MeleeCalc.maxHit(effStrength, player.getBonuses().getStr());
		long baseRoll = RollMath.attackRoll(effAttack, player.getOffensive().forStyle(type));

		Modifier weaponMod = meleeWeaponModifier(player, monster);
		Modifier salve = meleeSalve(player, monster);
		int max = weaponMod.damage.applyFloor(baseMax);
		long roll = weaponMod.accuracy.applyFloor(baseRoll);
		if (salve != null)
		{
			// Salve stacks on top of a weapon/set bonus (DHL, Inquisitor) against the undead.
			max = salve.damage.applyFloor(max);
			roll = salve.accuracy.applyFloor(roll);
		}
		max = monsterDamageFactor(player, monster).applyFloor(max);

		// Colossal blade: flat +2 max hit per tile of the target's size, capped at +10.
		if ("Colossal blade".equals(nameOf(player.getEquipment().getWeapon())))
		{
			max += Math.min(monster.getSize() * 2, 10);
		}

		long defenceRoll = raidScaled(monster,
			RollMath.defenceRoll(effectiveDefenceLevel(monster) + 9, monsterDefence(monster, type)));

		// Osmumten's fang always deals 15%-85% of max, but its twice-rolled accuracy only applies on stab.
		if (isFang(player.getEquipment().getWeapon()))
		{
			int fangMin = (int) Math.floor(max * 0.15);
			double fangAcc = type == CombatStyleType.STAB
				? RollMath.fangAccuracy(roll, defenceRoll)
				: RollMath.normalAccuracy(roll, defenceRoll);
			return Estimate.of(AttackDistribution.single(new Hitsplat(fangAcc, fangMin, max - fangMin)),
				player.getAttackSpeed());
		}

		double accuracy = RollMath.normalAccuracy(roll, defenceRoll);
		return Estimate.of(hitStructure(player, monster, accuracy, max), player.getAttackSpeed());
	}

	/** A gear/context bonus: separate accuracy and damage multipliers, each folded with a floor. */
	private static final class Modifier
	{
		static final Modifier NONE = new Modifier(Factor.identity(), Factor.identity());

		final Factor accuracy;
		final Factor damage;

		Modifier(Factor accuracy, Factor damage)
		{
			this.accuracy = accuracy;
			this.damage = damage;
		}

		static Modifier both(int numerator, int denominator)
		{
			Factor f = new Factor(numerator, denominator);
			return new Modifier(f, f);
		}
	}

	public static double rangedDps(Player player, Monster monster)
	{
		return rangedEstimate(player, monster).getDps();
	}

	public static Estimate rangedEstimate(Player player, Monster monster)
	{
		CombatStyleStance stance = player.getStyle().getStance();
		boolean voidSet = hasVoidRanged(player);

		// Eclipse atlatl / Hunter's spear scale their max hit off Strength (skill and gear bonus), not Ranged.
		boolean scalesWithStr = isStrScalingRanged(player);
		int strLevel = scalesWithStr ? player.getSkills().getStr() : player.getSkills().getRanged();
		int strBoost = scalesWithStr ? player.getBoosts().getStr() : player.getBoosts().getRanged();
		int strBonus = scalesWithStr ? player.getBonuses().getStr() : player.getBonuses().getRangedStr();

		int effStrength = RangedCalc.effectiveLevel(
			strLevel, strBoost, strengthPrayer(player.getPrayers()), rangedStrengthStance(stance), voidSet);
		if (hasEliteVoidRanged(player))
		{
			// Elite void's +2.5% ranged damage is applied to the effective strength, then floored.
			effStrength = new Factor(41, 40).applyFloor(effStrength);
		}
		int effAttack = RangedCalc.effectiveLevel(
			player.getSkills().getRanged(), player.getBoosts().getRanged(),
			accuracyPrayer(player.getPrayers()), rangedAttackStance(stance), voidSet);

		Modifier modifier = rangedModifier(player, monster);
		int max = modifier.damage.applyFloor(RangedCalc.maxHit(effStrength, strBonus));
		long roll = modifier.accuracy.applyFloor(RollMath.attackRoll(effAttack, player.getOffensive().getRanged()));

		// Salve (imbued only) stacks on top of the weapon's own modifier against the undead.
		Modifier salve = salveImbued(player, monster);
		if (salve != null)
		{
			max = salve.damage.applyFloor(max);
			roll = salve.accuracy.applyFloor(roll);
		}
		max = monsterDamageFactor(player, monster).applyFloor(max);

		// Tonalztics of ralos throws a glaive that rolls 75% of max hit (it can hit twice, handled below).
		if ("Tonalztics of ralos".equals(nameOf(player.getEquipment().getWeapon())))
		{
			max = new Factor(3, 4).applyFloor(max);
		}

		long defenceRoll = raidScaled(monster,
			RollMath.defenceRoll(effectiveDefenceLevel(monster) + 9, rangedDefence(player, monster)));
		double accuracy = RollMath.normalAccuracy(roll, defenceRoll);

		double expectedHit = boltProcExpectedHit(player, accuracy, max);
		if (expectedHit < 0)
		{
			expectedHit = RollMath.standardExpectedHit(accuracy, max);
		}
		// Dark bow fires two arrows per attack, and a charged Tonalztics of ralos throws two glaives - both
		// are two identical standard hits (the uncharged Tonalztics and everything else roll one).
		EquipmentPiece weapon = player.getEquipment().getWeapon();
		int hits = "Dark bow".equals(nameOf(weapon))
			|| (weapon != null && "Tonalztics of ralos".equals(weapon.getName()) && "Charged".equals(weapon.getVersion()))
			? 2 : 1;
		double dps = RollMath.dps(expectedHit * hits, player.getAttackSpeed());
		return new Estimate(dps, max * hits, accuracy);
	}

	private static boolean isStrScalingRanged(Player player)
	{
		String weapon = nameOf(player.getEquipment().getWeapon());
		return weapon.equals("Eclipse atlatl") || weapon.equals("Hunter's spear");
	}

	private static final List<String> MAGIC_DEF_USES_DEFENCE_LEVEL = Arrays.asList(
		"Verzik Vitur", "Ice demon", "Fragment of Seren", "Baboon Brawler");

	private static boolean usesDefenceLevelForMagicDefence(Monster monster)
	{
		return MAGIC_DEF_USES_DEFENCE_LEVEL.contains(monster.getName());
	}

	/**
	 * Expected hit for an enchanted-bolt proc, or -1 when the ammo has no modelled proc. A proc replaces a
	 * fraction of attacks (raised by the Kandarin hard diary) with a special hit.
	 */
	private static double boltProcExpectedHit(Player player, double accuracy, int max)
	{
		String ammo = nameOf(player.getEquipment().getAmmo());
		double normal = RollMath.standardExpectedHit(accuracy, max);

		if (ammo.equals("Diamond bolts (e)") || ammo.equals("Diamond dragon bolts (e)"))
		{
			double chance = player.getBuffs().isKandarinDiary() ? 0.11 : 0.10;
			// +15% damage and the proc always hits.
			double proc = RollMath.standardExpectedHit(1.0, new Factor(23, 20).applyFloor(max));
			return (1.0 - chance) * normal + chance * proc;
		}
		return -1.0;
	}

	public static double magicDps(Player player, Monster monster)
	{
		return magicEstimate(player, monster).getDps();
	}

	public static Estimate magicEstimate(Player player, Monster monster)
	{
		int magicLevel = player.getSkills().getMagic();
		int currentMagic = magicLevel + player.getBoosts().getMagic();

		EquipmentPiece weapon = player.getEquipment().getWeapon();
		boolean poweredStaff = weapon != null
			&& weapon.getCategory() == com.github.diogogdias.bisfinder.calc.model.EquipmentCategory.POWERED_STAFF;
		int stanceBonus = poweredStaff ? magicPoweredStance(player.getStyle().getStance()) : 0;

		int effMagic = MagicCalc.effectiveLevel(magicLevel, player.getBoosts().getMagic(),
			accuracyPrayer(player.getPrayers()), stanceBonus, false);

		int baseMax = player.getSpell() != null
			? player.getSpell().getMaxHit()
			: poweredStaffBaseMax(nameOf(weapon), currentMagic);
		if (baseMax <= 0)
		{
			// No spell and an unmodelled powered staff (trident, sanguinesti, ...): cannot score yet.
			return Estimate.ZERO;
		}
		int magicDamage = player.getBonuses().getMagicStr() + magicDamagePrayer(player.getPrayers());
		int weakness = elementalWeaknessSeverity(player, monster);

		// Salve (imbued) against the undead adds to the magic-damage pool and multiplies the attack roll.
		Modifier salve = salveImbued(player, monster);
		if (salve != null)
		{
			magicDamage += salve == SALVE_EI ? 200 : 150;
		}

		int max = MagicCalc.maxHit(baseMax, magicDamage) + (int) Math.floor((double) baseMax * weakness / 100.0);
		long roll = RollMath.attackRoll(effMagic, player.getOffensive().getMagic());
		if (weakness > 0)
		{
			roll = new Factor(100 + weakness, 100).applyFloor(roll);
		}
		if (salve != null)
		{
			roll = salve.accuracy.applyFloor(roll);
		}
		// Most NPCs defend magic with their Magic level, but a few (Verzik, Ice demon, Fragment of Seren,
		// Baboon brawler) use their Defence level instead.
		int magicDefLevel = usesDefenceLevelForMagicDefence(monster)
			? monster.getSkills().getDef() : monster.getSkills().getMagic();
		long defenceRoll = raidScaled(monster,
			RollMath.defenceRoll(magicDefLevel + 9, monster.getDefensive().getMagic()));
		double accuracy = RollMath.normalAccuracy(roll, defenceRoll);

		return Estimate.of(AttackDistribution.single(Hitsplat.standard(accuracy, max)), player.getAttackSpeed());
	}

	// --- prayers ------------------------------------------------------------------------------------

	private static Factor strengthPrayer(List<Prayer> prayers)
	{
		for (Prayer prayer : prayers)
		{
			if (prayer.getFactorStrength() != null)
			{
				return new Factor(prayer.getFactorStrength().getFactor(), prayer.getFactorStrength().getDivisor());
			}
		}
		return Factor.identity();
	}

	private static Factor accuracyPrayer(List<Prayer> prayers)
	{
		for (Prayer prayer : prayers)
		{
			if (prayer.getFactorAccuracy() != null)
			{
				return new Factor(prayer.getFactorAccuracy().getFactor(), prayer.getFactorAccuracy().getDivisor());
			}
		}
		return Factor.identity();
	}

	// --- stance -------------------------------------------------------------------------------------

	private static int attackStance(CombatStyleStance stance)
	{
		switch (stance)
		{
			case ACCURATE:
				return 3;
			case CONTROLLED:
				return 1;
			default:
				return 0;
		}
	}

	private static int strengthStance(CombatStyleStance stance)
	{
		switch (stance)
		{
			case AGGRESSIVE:
				return 3;
			case CONTROLLED:
				return 1;
			default:
				return 0;
		}
	}

	private static int rangedAttackStance(CombatStyleStance stance)
	{
		return stance == CombatStyleStance.ACCURATE ? 3 : 0;
	}

	private static int rangedStrengthStance(CombatStyleStance stance)
	{
		// Ranged uses one level for both rolls, so Accurate's +3 boosts max hit too (not just accuracy).
		return stance == CombatStyleStance.ACCURATE ? 3 : 0;
	}

	private static int magicDamagePrayer(List<Prayer> prayers)
	{
		int total = 0;
		for (Prayer prayer : prayers)
		{
			total += prayer.getMagicDamageBonus();
		}
		return total;
	}

	/** Severity of the monster's elemental weakness if the spell's element matches it, else 0. */
	private static int elementalWeaknessSeverity(Player player, Monster monster)
	{
		Spell spell = player.getSpell();
		Monster.Weakness weakness = monster.getWeakness();
		if (spell == null || spell.getElement() == null || weakness == null || weakness.getElement() == null)
		{
			return 0;
		}
		return spell.getElement().equalsIgnoreCase(weakness.getElement()) ? weakness.getSeverity() : 0;
	}

	private static int magicPoweredStance(CombatStyleStance stance)
	{
		// Powered staves use a +8 effective-level base (vs +9 for cast spells), so relative to MagicCalc's
		// +9 the Accurate stance's +3 nets to +2 and Longrange's +1 nets to 0.
		switch (stance)
		{
			case ACCURATE:
				return 2;
			case LONGRANGE:
				return 0;
			default:
				return -1;
		}
	}

	/** Base max hit of a powered staff from the current magic level (before magic-damage bonuses). */
	private static int poweredStaffBaseMax(String weapon, int magicLevel)
	{
		switch (weapon)
		{
			case "Tumeken's shadow":
				return magicLevel / 3 + 1;
			default:
				return -1;
		}
	}

	// --- gear / context multipliers -----------------------------------------------------------------

	/** The weapon/set multiplier: DHL, Inquisitor, or the slayer helmet (which yields to salve). */
	private static Modifier meleeWeaponModifier(Player player, Monster monster)
	{
		String weaponName = nameOf(player.getEquipment().getWeapon());
		CombatStyleType type = player.getStyle().getType();

		if ((weaponName.equals("Dragon hunter lance") || weaponName.equals("Dragon hunter wand"))
			&& hasAttribute(monster, MonsterAttribute.DRAGON))
		{
			return Modifier.both(6, 5);
		}

		if (type == CombatStyleType.CRUSH && hasFullInquisitor(player))
		{
			// The Inquisitor's mace triples the set's crush bonus, from +2.5% to +7.5%.
			boolean mace = weaponName.equals("Inquisitor's mace");
			return Modifier.both(mace ? 43 : 41, 40);
		}

		// The slayer helmet's bonus is suppressed by a salve amulet against the undead.
		if (onTaskWithBlackMask(player) && meleeSalve(player, monster) == null)
		{
			return Modifier.both(7, 6);
		}

		return Modifier.NONE;
	}

	/** Melee salve against the undead: enchanted is 20%, base/imbued is 15%. */
	private static Modifier meleeSalve(Player player, Monster monster)
	{
		if (!isUndead(monster))
		{
			return null;
		}
		switch (nameOf(player.getEquipment().getNeck()))
		{
			case "Salve amulet(ei)":
			case "Salve amulet (e)":
				return Modifier.both(6, 5);
			case "Salve amulet(i)":
			case "Salve amulet":
				return Modifier.both(23, 20);
			default:
				return null;
		}
	}

	private static Modifier rangedModifier(Player player, Monster monster)
	{
		String weapon = nameOf(player.getEquipment().getWeapon());
		if (weapon.equals("Dragon hunter crossbow") && hasAttribute(monster, MonsterAttribute.DRAGON))
		{
			return new Modifier(new Factor(13, 10), new Factor(5, 4));
		}
		if (weapon.equals("Twisted bow"))
		{
			return twistedBow(monster);
		}
		return Modifier.NONE;
	}

	/**
	 * Twisted bow accuracy and damage scale with the target's Magic level, per the wiki formula. The target's
	 * effective magic is capped at 250, or 350 against Xerician (Chambers of Xeric) monsters. The resulting
	 * percentage is NOT clamped to its base — near the cap the accuracy bonus reaches 141%.
	 */
	private static Modifier twistedBow(Monster monster)
	{
		int cap = hasAttribute(monster, MonsterAttribute.XERICIAN) ? 350 : 250;
		int magic = Math.min(cap, Math.max(monster.getSkills().getMagic(), monster.getOffensive().getMagic()));
		return new Modifier(new Factor(tbowScale(magic, true), 100), new Factor(tbowScale(magic, false), 100));
	}

	/** Percentage (out of 100) the Twisted bow multiplies its accuracy (factor 10/140) or damage (14/250) by. */
	private static int tbowScale(int magic, boolean accuracyMode)
	{
		int factor = accuracyMode ? 10 : 14;
		int base = accuracyMode ? 140 : 250;
		int t2 = (3 * magic - factor) / 100;
		int inner = 3 * magic / 10 - 10 * factor;
		int t3 = (inner * inner) / 100;
		return base + t2 - t3;
	}

	private static boolean hasEliteVoidRanged(Player player)
	{
		Player.PlayerEquipment e = player.getEquipment();
		return nameOf(e.getHead()).equals("Void ranger helm")
			&& nameOf(e.getBody()).equals("Elite void top")
			&& nameOf(e.getLegs()).equals("Elite void robe")
			&& nameOf(e.getHands()).equals("Void knight gloves");
	}

	private static final Modifier SALVE_EI = Modifier.both(6, 5);
	private static final Modifier SALVE_I = Modifier.both(23, 20);

	/** Imbued salve (works for every style) against the undead: (ei) is 20%, (i) is 15%. */
	private static Modifier salveImbued(Player player, Monster monster)
	{
		if (!isUndead(monster))
		{
			return null;
		}
		String neck = nameOf(player.getEquipment().getNeck());
		if (neck.equals("Salve amulet(ei)"))
		{
			return SALVE_EI;
		}
		if (neck.equals("Salve amulet(i)"))
		{
			return SALVE_I;
		}
		return null;
	}

	/**
	 * The monster's Defence level after the reductions the user has entered (Advanced options). Percentage
	 * reductions take a cut of the current level in turn; flat reductions (BGS) subtract a fixed amount.
	 */
	private static int effectiveDefenceLevel(Monster monster)
	{
		int def = monster.getSkills().getDef();
		com.github.diogogdias.bisfinder.calc.model.MonsterInputs.DefenceReductions dr =
			monster.getInputs().getDefenceReductions();
		for (int i = 0; i < dr.getElderMaul(); i++)
		{
			def -= def * 35 / 100;
		}
		for (int i = 0; i < dr.getDwh(); i++)
		{
			def -= def * 30 / 100;
		}
		if (dr.isAccursed())
		{
			def -= def * 15 / 100;
		}
		if (dr.isVulnerability())
		{
			def -= def * 10 / 100;
		}
		def -= dr.getBgs();
		return Math.max(0, def);
	}

	/** Tombs of Amascut scales the defence roll by +2% for every 5 raid (invocation) levels. */
	private static long raidScaled(Monster monster, long defenceRoll)
	{
		int invocation = monster.getInputs().getToaInvocationLevel();
		if (invocation <= 0)
		{
			return defenceRoll;
		}
		return defenceRoll * (100 + (invocation / 5) * 2) / 100;
	}

	/** Monster-specific damage reduction (Bitterkoekje/wiki mechanics), applied to the max hit. */
	private static Factor monsterDamageFactor(Player player, Monster monster)
	{
		if ("Corporeal Beast".equals(monster.getName()))
		{
			// Takes half damage from everything except spears and the crystal halberd.
			EquipmentPiece weapon = player.getEquipment().getWeapon();
			boolean spear = weapon != null
				&& (weapon.getCategory() == com.github.diogogdias.bisfinder.calc.model.EquipmentCategory.SPEAR
				|| "Crystal halberd".equals(weapon.getName()));
			return spear ? Factor.identity() : new Factor(1, 2);
		}
		return Factor.identity();
	}

	/** Soulreaper axe: each soul stack adds 6% of the current strength level to the effective strength. */
	private static int soulreaperStrengthBonus(Player player)
	{
		String weapon = nameOf(player.getEquipment().getWeapon());
		if (!"Soulreaper axe".equals(weapon) && !"Soulreaper axe (o)".equals(weapon))
		{
			return 0;
		}
		int strengthLevel = player.getSkills().getStr() + player.getBoosts().getStr();
		return strengthLevel * 6 * player.getBuffs().getSoulreaperStacks() / 100;
	}

	private static boolean isReachWeapon(Player player)
	{
		EquipmentPiece weapon = player.getEquipment().getWeapon();
		return weapon != null
			&& weapon.getCategory() == com.github.diogogdias.bisfinder.calc.model.EquipmentCategory.POLEARM;
	}

	private static boolean hasFullInquisitor(Player player)
	{
		Player.PlayerEquipment e = player.getEquipment();
		return nameOf(e.getHead()).equals("Inquisitor's great helm")
			&& nameOf(e.getBody()).equals("Inquisitor's hauberk")
			&& nameOf(e.getLegs()).equals("Inquisitor's plateskirt");
	}

	private static boolean onTaskWithBlackMask(Player player)
	{
		if (!player.getBuffs().isOnSlayerTask())
		{
			return false;
		}
		String head = nameOf(player.getEquipment().getHead());
		return head.startsWith("Black mask") || head.startsWith("Slayer helmet");
	}

	// --- hit structure ------------------------------------------------------------------------------

	private static final List<String> TWO_HIT_WEAPONS = Arrays.asList(
		"Torag's hammers", "Sulphur blades", "Glacial temotli", "Earthbound tecpatl");

	private static AttackDistribution hitStructure(Player player, Monster monster, double accuracy, int max)
	{
		String weapon = nameOf(player.getEquipment().getWeapon());
		// Scythe of vitur (incl. Holy/Sanguine variants): hits once per tile of the target's size, up to
		// three times, at 100% / 50% / 25% of max.
		if (weapon.contains("of vitur"))
		{
			int hits = Math.min(monster.getSize(), 3);
			if (hits >= 3)
			{
				return AttackDistribution.of(Hitsplat.standard(accuracy, max),
					Hitsplat.standard(accuracy, max / 2), Hitsplat.standard(accuracy, max / 4));
			}
			if (hits == 2)
			{
				return AttackDistribution.of(Hitsplat.standard(accuracy, max),
					Hitsplat.standard(accuracy, max / 2));
			}
			return AttackDistribution.single(Hitsplat.standard(accuracy, max));
		}
		// Two-hit weapons split the max into two independent splats (firstMax + secondMax = max), each with
		// the weapon's accuracy. The two smaller splats each carry their own +1/(max+1) term.
		if (TWO_HIT_WEAPONS.contains(weapon))
		{
			int firstMax = max / 2;
			int secondMax = max - firstMax;
			return AttackDistribution.of(Hitsplat.standard(accuracy, firstMax),
				Hitsplat.standard(accuracy, secondMax));
		}
		// Dual macuahuitl (without the Blood moon set): the second splat only rolls if the first lands, so
		// its expected damage is discounted by the first hit's accuracy.
		if (weapon.equals("Dual macuahuitl") && !hasBloodMoonSet(player))
		{
			int firstMax = max / 2;
			int secondMax = max - firstMax;
			return AttackDistribution.of(Hitsplat.standard(accuracy, firstMax),
				new Hitsplat(accuracy * accuracy, 0, secondMax));
		}
		return AttackDistribution.single(Hitsplat.standard(accuracy, max));
	}

	// --- helpers ------------------------------------------------------------------------------------

	private static final List<String> VOID_BODY = Arrays.asList("Void knight top", "Elite void top");
	private static final List<String> VOID_LEGS = Arrays.asList("Void knight robe", "Elite void robe");

	private static boolean hasVoidMelee(Player player)
	{
		Player.PlayerEquipment e = player.getEquipment();
		return nameOf(e.getHead()).equals("Void melee helm")
			&& VOID_BODY.contains(nameOf(e.getBody()))
			&& VOID_LEGS.contains(nameOf(e.getLegs()))
			&& nameOf(e.getHands()).equals("Void knight gloves");
	}

	private static boolean hasVoidRanged(Player player)
	{
		Player.PlayerEquipment e = player.getEquipment();
		return nameOf(e.getHead()).equals("Void ranger helm")
			&& VOID_BODY.contains(nameOf(e.getBody()))
			&& VOID_LEGS.contains(nameOf(e.getLegs()))
			&& nameOf(e.getHands()).equals("Void knight gloves");
	}

	/** Monster ranged defence for the ammo's damage type: crossbows heavy, thrown light, otherwise standard. */
	private static int rangedDefence(Player player, Monster monster)
	{
		EquipmentPiece weapon = player.getEquipment().getWeapon();
		if (weapon != null)
		{
			switch (weapon.getCategory())
			{
				case CROSSBOW:
					return monster.getDefensive().getHeavy();
				case THROWN:
				case CHINCHOMPA:
					return monster.getDefensive().getLight();
				default:
					break;
			}
		}
		return monster.getDefensive().getStandard();
	}

	private static int monsterDefence(Monster monster, CombatStyleType type)
	{
		switch (type)
		{
			case STAB:
				return monster.getDefensive().getStab();
			case SLASH:
				return monster.getDefensive().getSlash();
			case CRUSH:
				return monster.getDefensive().getCrush();
			default:
				throw new IllegalArgumentException("not a melee type: " + type);
		}
	}

	private static boolean isUndead(Monster monster)
	{
		return hasAttribute(monster, MonsterAttribute.UNDEAD);
	}

	private static boolean hasAttribute(Monster monster, MonsterAttribute attribute)
	{
		return monster.getAttributes().contains(attribute);
	}

	private static String nameOf(EquipmentPiece piece)
	{
		return piece == null ? "" : piece.getName();
	}

	private static boolean isFang(EquipmentPiece weapon)
	{
		String name = nameOf(weapon);
		return name.equals("Osmumten's fang") || name.equals("Osmumten's fang (or)");
	}

	private static boolean hasBloodMoonSet(Player player)
	{
		Player.PlayerEquipment e = player.getEquipment();
		return nameOf(e.getWeapon()).equals("Dual macuahuitl")
			&& nameOf(e.getHead()).equals("Blood moon helm")
			&& nameOf(e.getBody()).equals("Blood moon chestplate")
			&& nameOf(e.getLegs()).equals("Blood moon tassets");
	}
}
