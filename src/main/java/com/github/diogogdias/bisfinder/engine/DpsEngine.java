package com.github.diogogdias.bisfinder.engine;

import com.github.diogogdias.bisfinder.calc.Constants;
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
import java.util.Locale;

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

		if (!meleeCanReach(player, monster) || immuneToStyle(player, monster))
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
		int max = weaponMod.damage.applyFloor(baseMax);
		long roll = weaponMod.accuracy.applyFloor(baseRoll);

		// The salve / black mask bonus stacks on top of a weapon or set bonus (DHL, Inquisitor).
		Modifier task = taskModifier(player, monster, type);
		max = task.damage.applyFloor(max);
		roll = task.accuracy.applyFloor(roll);

		Modifier bane = baneModifier(player, monster);
		max = bane.damage.applyFloor(max);
		roll = bane.accuracy.applyFloor(roll);

		Modifier wilderness = wilderness(player);
		max = wilderness.damage.applyFloor(max);
		roll = wilderness.accuracy.applyFloor(roll);
		max = monsterDamageFactor(player, monster).applyFloor(max);

		// Colossal blade: flat +2 max hit per tile of the target's size, capped at +10.
		if ("Colossal blade".equals(nameOf(player.getEquipment().getWeapon())))
		{
			max += Math.min(monster.getSize() * 2, 10);
		}

		max += ratbaneMaxBonus(player, monster);

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
		if (immuneToStyle(player, monster))
		{
			return Estimate.ZERO;
		}

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

		// The salve (imbued only) or an imbued black mask on task, stacked on the weapon's own modifier.
		Modifier task = taskModifier(player, monster, CombatStyleType.RANGED);
		max = task.damage.applyFloor(max);
		roll = task.accuracy.applyFloor(roll);

		Modifier bane = baneModifier(player, monster);
		max = bane.damage.applyFloor(max);
		roll = bane.accuracy.applyFloor(roll);
		max += ratbaneMaxBonus(player, monster);

		Modifier wilderness = wilderness(player);
		max = wilderness.damage.applyFloor(max);
		roll = wilderness.accuracy.applyFloor(roll);
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
		if (immuneToStyle(player, monster))
		{
			return Estimate.ZERO;
		}

		int magicLevel = player.getSkills().getMagic();
		int currentMagic = magicLevel + player.getBoosts().getMagic();

		EquipmentPiece weapon = player.getEquipment().getWeapon();
		boolean poweredStaff = weapon != null
			&& weapon.getCategory() == com.github.diogogdias.bisfinder.calc.model.EquipmentCategory.POWERED_STAFF;
		int stanceBonus = poweredStaff ? magicPoweredStance(player.getStyle().getStance()) : 0;

		int effMagic = MagicCalc.effectiveLevel(magicLevel, player.getBoosts().getMagic(),
			accuracyPrayer(player.getPrayers()), stanceBonus, false);

		int baseMax = player.getSpell() != null
			? spellBaseMax(player, currentMagic)
			: poweredStaffBaseMax(nameOf(weapon), currentMagic);
		if (baseMax <= 0)
		{
			// No spell and an unmodelled powered staff (trident, sanguinesti, ...): cannot score yet.
			return Estimate.ZERO;
		}
		int magicDamage = player.getBonuses().getMagicStr() + magicDamagePrayer(player.getPrayers());
		int weakness = elementalWeaknessSeverity(player, monster);

		// The salve (imbued) or an imbued black mask on task adds to the magic-damage pool rather than
		// multiplying the max hit, and multiplies the attack roll via taskModifier below.
		magicDamage += taskMagicDamage(player, monster);

		int max = MagicCalc.maxHit(baseMax, magicDamage) + (int) Math.floor((double) baseMax * weakness / 100.0);
		long roll = RollMath.attackRoll(effMagic, player.getOffensive().getMagic());
		if (weakness > 0)
		{
			roll = new Factor(100 + weakness, 100).applyFloor(roll);
		}

		Modifier task = taskModifier(player, monster, CombatStyleType.MAGIC);
		roll = task.accuracy.applyFloor(roll);

		// The Dragon hunter wand is a staff, so its dragonbane bonus used to sit in the melee path - where it
		// only ever fired if you bashed a dragon with it, never when casting, which is the whole point of it.
		if ("Dragon hunter wand".equals(nameOf(weapon)) && hasAttribute(monster, MonsterAttribute.DRAGON))
		{
			max = new Factor(6, 5).applyFloor(max);
			roll = new Factor(6, 5).applyFloor(roll);
		}

		Modifier wilderness = wilderness(player);
		max = wilderness.damage.applyFloor(max);
		roll = wilderness.accuracy.applyFloor(roll);
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

	/**
	 * A spell's base max hit. Almost every spell carries its own in the data, but Magic Dart's is listed as 0
	 * because it scales with the caster: floor(magic * 0.1) + 10, or floor(magic * 0.166) + 13 with a Slayer's
	 * staff (e) on task. Without this it scored nothing, which mattered once leafy creatures started refusing
	 * every spell but this one.
	 */
	private static int spellBaseMax(Player player, int currentMagic)
	{
		Spell spell = player.getSpell();
		if (!"Magic Dart".equals(spell.getName()))
		{
			return spell.getMaxHit();
		}

		boolean enchantedOnTask = "Slayer's staff (e)".equals(nameOf(player.getEquipment().getWeapon()))
			&& player.getBuffs().isOnSlayerTask();
		return enchantedOnTask
			? (int) Math.floor(currentMagic * 0.166) + 13
			: (int) Math.floor(currentMagic * 0.1) + 10;
	}

	/**
	 * Base max hit of a powered staff from the visible (boosted) magic level, before magic-damage bonuses.
	 * Returns -1 for a staff whose formula is not modelled, which scores the setup at zero rather than
	 * guessing.
	 *
	 * <p>Every formula here was taken from the staff's own wiki page and reconciled against a worked example
	 * on that page (e.g. the swamp trident's "starting at 24 damage with level 78 Magic" → 78/3-2 = 24).
	 * That check matters: the wiki renders these as stacked fractions, which flatten into ambiguous text when
	 * scraped — "⌊8(Magic Level)+9637⌋" is really ⌊(8·magic+96)/37⌋. A formula that cannot be reconciled
	 * against a stated data point is left out rather than guessed at; see docs/architecture/engine-gaps.md.
	 */
	private static int poweredStaffBaseMax(String weapon, int magicLevel)
	{
		// The seas/swamp tridents each have (e), (o) and (e) (o) variants that only change the look.
		if (weapon.startsWith("Trident of the seas"))
		{
			return magicLevel / 3 - 5;
		}
		if (weapon.startsWith("Trident of the swamp"))
		{
			return magicLevel / 3 - 2;
		}

		switch (weapon)
		{
			case "Tumeken's shadow":
				return magicLevel / 3 + 1;
			case "Sanguinesti staff":
			case "Holy sanguinesti staff":
				// The holy kit is cosmetic: "Though its stats do not change".
				return magicLevel / 3 - 1;
			case "Thammaron's sceptre":
			case "Thammaron's sceptre (a)":
				return magicLevel / 3 - 8;
			case "Accursed sceptre":
			case "Accursed sceptre (a)":
				return magicLevel / 3 - 6;
			case "Eye of ayak":
				return magicLevel / 3 - 6;
			case "Warped sceptre":
				return (8 * magicLevel + 96) / 37;
			default:
				return -1;
		}
	}

	// --- gear / context multipliers -----------------------------------------------------------------

	/**
	 * The weapon/set multiplier: the Dragon hunter lance's dragonbane, or the Inquisitor set's crush bonus.
	 *
	 * <p>The black mask is deliberately not here. It is a separate source that stacks multiplicatively with
	 * these ("This stacks multiplicatively with both Void Knight equipment and the Slayer helm (i)"), so
	 * folding it into this either/or chain let a Dragon hunter lance silently swallow it on a dragon task.
	 * See {@link #taskModifier}.
	 */
	private static Modifier meleeWeaponModifier(Player player, Monster monster)
	{
		String weaponName = nameOf(player.getEquipment().getWeapon());
		CombatStyleType type = player.getStyle().getType();

		if (weaponName.equals("Dragon hunter lance") && hasAttribute(monster, MonsterAttribute.DRAGON))
		{
			return Modifier.both(6, 5);
		}

		if (type == CombatStyleType.CRUSH && hasFullInquisitor(player))
		{
			// The Inquisitor's mace triples the set's crush bonus, from +2.5% to +7.5%.
			boolean mace = weaponName.equals("Inquisitor's mace");
			return Modifier.both(mace ? 43 : 41, 40);
		}

		return Modifier.NONE;
	}

	/**
	 * The salve amulet's undead bonus, or the black mask's on-task bonus - never both. Mod Ash: "Salve
	 * effects override the Slayer helm / black mask effects (because they're either identical or bigger)."
	 *
	 * <p>The black mask is +16.67% to melee for every variant, and a further +15% to Ranged and +15% to
	 * Magic for the imbued ones only. Magic is handled separately, because a magic damage bonus is added to
	 * the damage pool rather than multiplied onto the max hit; this returns only its accuracy there.
	 */
	private static Modifier taskModifier(Player player, Monster monster, CombatStyleType type)
	{
		Modifier salve = type.isMelee() ? meleeSalve(player, monster) : salveImbued(player, monster);
		if (salve != null)
		{
			return salve;
		}

		if (!onTaskWithBlackMask(player))
		{
			return Modifier.NONE;
		}

		if (type.isMelee())
		{
			return Modifier.both(7, 6);
		}

		// Ranged and Magic only get a bonus from an imbued mask.
		if (!isImbued(nameOf(player.getEquipment().getHead())))
		{
			return Modifier.NONE;
		}
		return type == CombatStyleType.MAGIC
			? new Modifier(new Factor(23, 20), Factor.identity())
			: Modifier.both(23, 20);
	}

	/**
	 * The extra magic damage (in tenths of a percent, as the damage pool counts it) from the salve or an
	 * imbued black mask on task. Mirrors {@link #taskModifier}: the salve wins when both are worn.
	 */
	private static int taskMagicDamage(Player player, Monster monster)
	{
		Modifier salve = salveImbued(player, monster);
		if (salve != null)
		{
			return salve == SALVE_EI ? 200 : 150;
		}
		return onTaskWithBlackMask(player) && isImbued(nameOf(player.getEquipment().getHead())) ? 150 : 0;
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

	/** The revenant-ether weapons, whose charged form is powered up in the Wilderness. */
	private static final List<String> WILDERNESS_WEAPONS = Arrays.asList(
		"Craw's bow", "Webweaver bow",
		"Viggora's chainmace", "Ursine chainmace",
		"Thammaron's sceptre", "Thammaron's sceptre (a)",
		"Accursed sceptre", "Accursed sceptre (a)");

	/**
	 * A charged revenant-ether weapon hits NPCs in the Wilderness 50% harder and 50% more accurately. The
	 * bonus needs the charge: an uncharged one is a plain weapon, and the wiki data tells the two apart by
	 * version rather than by name.
	 *
	 * <p>Only the bows and the chainmaces can reach this today. The two sceptres are powered staves, which
	 * {@link #poweredStaffBaseMax} does not model yet, so magic bails out before the multiplier is read; the
	 * magic side is wired up so that modelling them is all it takes.
	 */
	private static Modifier wilderness(Player player)
	{
		if (!player.getBuffs().isInWilderness())
		{
			return Modifier.NONE;
		}
		EquipmentPiece weapon = player.getEquipment().getWeapon();
		if (weapon == null || !WILDERNESS_WEAPONS.contains(weapon.getName())
			|| !"Charged".equals(weapon.getVersion()))
		{
			return Modifier.NONE;
		}
		return Modifier.both(3, 2);
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

	private static boolean isSalamander(Player player)
	{
		EquipmentPiece weapon = player.getEquipment().getWeapon();
		return weapon != null
			&& weapon.getCategory() == com.github.diogogdias.bisfinder.calc.model.EquipmentCategory.SALAMANDER;
	}

	/**
	 * Whether melee can physically reach the target, which is a separate question from whether melee hurts it.
	 *
	 * <p>A halberd strikes from a tile away, and so does a salamander's scorch. That reach is what makes a
	 * flying enemy meleeable (since 25 June 2025, for every flying enemy rather than just aviansies), and it
	 * is the only way to touch Zulrah, which swims out of range of anything shorter.
	 */
	private static boolean meleeCanReach(Player player, Monster monster)
	{
		if (Constants.ZULRAH_IDS.contains(monster.getId()))
		{
			// A salamander's scorch is not enough here: the wiki is explicit that only halberds reach Zulrah.
			return isReachWeapon(player);
		}
		if (hasAttribute(monster, MonsterAttribute.FLYING))
		{
			return isReachWeapon(player) || isSalamander(player);
		}
		return true;
	}

	/**
	 * Whether this style simply cannot hurt the target, and so scores nothing rather than a number the
	 * player could never achieve. The Kraken and the Leviathan are out of melee's reach even with a halberd;
	 * Tekton and Dusk shrug off arrows.
	 *
	 * <p>{@link Constants#IMMUNE_TO_NON_SALAMANDER_MELEE_DAMAGE_NPC_IDS} is deliberately not consulted: it
	 * predates the 25 June 2025 change that let halberds hit every flying enemy, so enforcing it would wrongly
	 * zero a halberd at aviansies. Reach is handled by {@link #meleeCanReach} instead.
	 */
	private static boolean immuneToStyle(Player player, Monster monster)
	{
		int id = monster.getId();
		CombatStyleType type = player.getStyle().getType();

		if (hasAttribute(monster, MonsterAttribute.LEAFY) && !canDamageLeafy(player, type))
		{
			return true;
		}

		if (type == CombatStyleType.MAGIC)
		{
			return Constants.IMMUNE_TO_MAGIC_DAMAGE_NPC_IDS.contains(id);
		}
		if (!type.isMelee())
		{
			return Constants.IMMUNE_TO_RANGED_DAMAGE_NPC_IDS.contains(id);
		}
		return Constants.IMMUNE_TO_MELEE_DAMAGE_NPC_IDS.contains(id);
	}

	private static boolean hasFullInquisitor(Player player)
	{
		Player.PlayerEquipment e = player.getEquipment();
		return nameOf(e.getHead()).equals("Inquisitor's great helm")
			&& nameOf(e.getBody()).equals("Inquisitor's hauberk")
			&& nameOf(e.getLegs()).equals("Inquisitor's plateskirt");
	}

	/**
	 * A black mask or any slayer helmet, on task.
	 *
	 * <p>The helmet is matched on "slayer helmet" anywhere in the name rather than as a prefix: the plain
	 * "Slayer helmet" is the exception, and the fourteen themed ones the game actually hands out (Purple,
	 * Hydra, Araxyte, Oathplate, Tzkal, ...) all put their colour first. Matching the prefix meant a player
	 * in a coloured helm got no on-task bonus at all.
	 */
	private static boolean onTaskWithBlackMask(Player player)
	{
		if (!player.getBuffs().isOnSlayerTask())
		{
			return false;
		}
		String head = nameOf(player.getEquipment().getHead());
		return head.startsWith("Black mask")
			|| head.toLowerCase(Locale.ROOT).contains("slayer helmet");
	}

	/** The imbued variants, which alone carry the Ranged and Magic bonuses, are the ones suffixed "(i)". */
	private static boolean isImbued(String head)
	{
		return head.endsWith("(i)");
	}

	/** The leaf-bladed weapons, the only melee a Kurask or Turoth can be hurt by. */
	private static final List<String> LEAF_BLADED = Arrays.asList(
		"Leaf-bladed battleaxe", "Leaf-bladed spear", "Leaf-bladed sword");

	/** Broad-tipped ammunition, the only ranged a Kurask or Turoth can be hurt by. */
	private static final List<String> BROAD_AMMO = Arrays.asList(
		"Broad arrows", "Broad bolts", "Amethyst broad bolts");

	/**
	 * The rat bone weapons from Scurrius. Listed rather than matched on a "Bone " prefix, which would drag in
	 * the Bone dagger, club and spear - Dorgeshuun and barbarian gear with no ratbane effect at all.
	 */
	private static final List<String> RATBANE = Arrays.asList("Bone mace", "Bone shortbow", "Bone staff");

	/**
	 * A weapon's bonus against the kind of thing it was built to kill. Each is the weapon's own multiplier,
	 * applied only when the target carries the matching attribute.
	 *
	 * <p>Duke Sucellus is the lone demon that resists demonbane, by 30% - which is what turns Arclight's 70%
	 * into 49% there. (The wiki data has a {@code demonbaneVulnerability} input for this, but nothing fills
	 * it in: the code that did was the deleted calculator's {@code sanitizeInputs}.)
	 */
	private static Modifier baneModifier(Player player, Monster monster)
	{
		String weapon = nameOf(player.getEquipment().getWeapon());

		if (hasAttribute(monster, MonsterAttribute.DEMON))
		{
			int accuracy;
			int damage;
			switch (weapon)
			{
				case "Silverlight":
				case "Silverlight (dyed)":
				case "Darklight":
					accuracy = 0;
					damage = 60;
					break;
				case "Arclight":
				case "Emberlight":
					accuracy = 70;
					damage = 70;
					break;
				case "Scorching bow":
					accuracy = 30;
					damage = 30;
					break;
				case "Burning claws":
					accuracy = 5;
					damage = 5;
					break;
				default:
					accuracy = 0;
					damage = 0;
					break;
			}

			if ("Duke Sucellus".equals(monster.getName()))
			{
				accuracy = accuracy * 7 / 10;
				damage = damage * 7 / 10;
			}
			if (damage > 0 || accuracy > 0)
			{
				return new Modifier(new Factor(100 + accuracy, 100), new Factor(100 + damage, 100));
			}
		}

		// Every keris keeps the original's 33% against kalphites and scabarites, except the partisan of
		// amascut: "this variant has a damage bonus of 15%, down from the base weapon's 33%", traded for far
		// better stats inside Tombs of Amascut. The partisan of breaching adds 33% accuracy on top.
		// The 1/51 triple-damage proc every keris has is not modelled - see docs/architecture/engine-gaps.md.
		if (hasAttribute(monster, MonsterAttribute.KALPHITE) && weapon.startsWith("Keris"))
		{
			int damage = "Keris partisan of amascut".equals(weapon) ? 15 : 33;
			int accuracy = "Keris partisan of breaching".equals(weapon) ? 33 : 0;
			return new Modifier(new Factor(100 + accuracy, 100), new Factor(100 + damage, 100));
		}

		if (hasAttribute(monster, MonsterAttribute.GOLEM) && "Barronite mace".equals(weapon))
		{
			return Modifier.both(23, 20);
		}

		// Only the battleaxe: "Unlike the leaf-bladed battleaxe, the sword does not have a 17.5% damage buff".
		if (hasAttribute(monster, MonsterAttribute.LEAFY) && "Leaf-bladed battleaxe".equals(weapon))
		{
			return new Modifier(Factor.identity(), new Factor(47, 40));
		}

		return Modifier.NONE;
	}

	/** Ratbane weapons add a flat 10 to the max hit against rats, rather than a percentage. */
	private static int ratbaneMaxBonus(Player player, Monster monster)
	{
		return hasAttribute(monster, MonsterAttribute.RAT)
			&& RATBANE.contains(nameOf(player.getEquipment().getWeapon())) ? 10 : 0;
	}

	/**
	 * Whether this style can hurt a leafy creature at all: "they can only be damaged with leaf-bladed
	 * weapons, broad ammunition, or the Magic Dart spell; they are immune to any other types of attacks."
	 */
	private static boolean canDamageLeafy(Player player, CombatStyleType type)
	{
		if (type == CombatStyleType.MAGIC)
		{
			Spell spell = player.getSpell();
			return spell != null && "Magic Dart".equals(spell.getName());
		}
		if (type.isMelee())
		{
			return LEAF_BLADED.contains(nameOf(player.getEquipment().getWeapon()));
		}
		return BROAD_AMMO.contains(nameOf(player.getEquipment().getAmmo()));
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
