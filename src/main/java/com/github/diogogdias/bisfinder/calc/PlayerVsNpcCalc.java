package com.github.diogogdias.bisfinder.calc;

import com.github.diogogdias.bisfinder.calc.dist.AttackDistribution;
import com.github.diogogdias.bisfinder.calc.dist.Bolts;
import com.github.diogogdias.bisfinder.calc.dist.CalcMath;
import com.github.diogogdias.bisfinder.calc.dist.Claws;
import com.github.diogogdias.bisfinder.calc.dist.Factor;
import com.github.diogogdias.bisfinder.calc.dist.HitDistribution;
import com.github.diogogdias.bisfinder.calc.dist.HitTransformer;
import com.github.diogogdias.bisfinder.calc.dist.HitTransformers;
import com.github.diogogdias.bisfinder.calc.dist.Hitsplat;
import com.github.diogogdias.bisfinder.calc.dist.MinMax;
import com.github.diogogdias.bisfinder.calc.dist.TransformOpts;
import com.github.diogogdias.bisfinder.calc.dist.WeightedHit;
import com.github.diogogdias.bisfinder.calc.model.CombatStyleStance;
import com.github.diogogdias.bisfinder.calc.model.CombatStyleType;
import com.github.diogogdias.bisfinder.calc.model.EquipmentCategory;
import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.MonsterAttribute;
import com.github.diogogdias.bisfinder.calc.model.MonsterInputs;
import com.github.diogogdias.bisfinder.calc.model.Player;
import com.github.diogogdias.bisfinder.calc.model.PlayerCombatStyle;
import com.github.diogogdias.bisfinder.calc.model.Prayer;
import com.github.diogogdias.bisfinder.calc.model.Spell;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Port of osrs-dps-calc's src/lib/PlayerVsNPCCalc.ts, covering normal and special attacks.
 *
 * <p>Out of scope, and omitted with TODO(v2) markers where a branch was dropped: TTK
 * (getTtk / getHtk / getTtkDistribution / distAtHp / getWeaponDelayProvider).
 *
 * <p>The CalcDetails instrumentation is replaced by the plain arithmetic it wrapped, with the same order
 * of operations and the same truncation points.
 */
public class PlayerVsNpcCalc extends BaseCalc
{
	private static final List<String> PARTIALLY_IMPLEMENTED_SPECS = Collections.singletonList(
		"Ancient godsword"
	);

	/**
	 * https://oldschool.runescape.wiki/w/Category:Weapons_with_Special_attacks
	 * Some entries are intentionally omitted as they are not dps-related.
	 */
	private static final List<String> UNIMPLEMENTED_SPECS = Arrays.asList(
		"Abyssal tentacle",
		"Ancient mace",
		"Armadyl crossbow",
		"Blue moon spear",
		"Bone dagger",
		"Brine sabre",
		"Darklight",
		"Dinh's bulwark",
		"Dorgeshuun crossbow",
		"Dragon 2h sword",
		"Dragon crossbow",
		"Dragon hasta",
		"Dragon spear",
		"Dragon thrownaxe",
		"Eclipse atlatl",
		"Excalibur",
		"Granite maul",
		"Rune claws",
		"Staff of balance",
		"Staff of light",
		"Staff of the dead",
		"Toxic staff of the dead",
		"Ursine chainmace",
		"Zamorakian hasta",
		"Zamorakian spear"
	);

	private AttackDistribution memoizedDist;

	private final Map<CombatStyleType, HitTransformer> npcTransformCache = new EnumMap<>(CombatStyleType.class);

	/**
	 * Cache for the null style type, which an EnumMap cannot hold.
	 */
	private HitTransformer nullStyleTransformCache;

	public PlayerVsNpcCalc(Player player, Monster monster)
	{
		this(player, monster, CalcOpts.defaults());
	}

	public PlayerVsNpcCalc(Player player, Monster monster, CalcOpts opts)
	{
		super(player, monster, opts);

		if (!this.opts.isNoInit() && isSpecSupported() == FeatureStatus.UNIMPLEMENTED)
		{
			addIssue(UserIssue.Type.EQUIPMENT_SPEC_UNSUPPORTED,
				"This loadout's weapon special attack is not yet supported in the calculator.");
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Accuracy rolls
	// ---------------------------------------------------------------------------------------------

	/**
	 * Get the NPC defence roll for this loadout, which is based on the player's current combat style.
	 */
	public int getNPCDefenceRoll()
	{
		if (opts.getOverrides().getDefenceRoll() != null)
		{
			return opts.getOverrides().getDefenceRoll();
		}

		CombatStyleType defenceStyle = styleType();
		if (opts.isUsingSpecialAttack())
		{
			if (wearing(Arrays.asList(
				"Dragon claws",
				"Dragon dagger",
				"Dragon halberd",
				"Dragon longsword",
				"Dragon scimitar",
				"Crystal halberd",
				"Abyssal dagger",
				"Saradomin sword",
				"Arkan blade")) || isWearingGodsword())
			{
				defenceStyle = CombatStyleType.SLASH;
			}
			else if (wearing(Arrays.asList("Arclight", "Emberlight", "Dragon sword")))
			{
				defenceStyle = CombatStyleType.STAB;
			}
			else if (wearing(Arrays.asList("Voidwaker", "Saradomin's blessed sword")))
			{
				// doesn't really matter for voidwaker since it's 100% accuracy but eh
				defenceStyle = CombatStyleType.MAGIC;
			}
			else if (wearing(Arrays.asList(
				"Dragon mace",
				"Crimson kisten")))
			{
				defenceStyle = CombatStyleType.CRUSH;
			}
		}

		int level = (defenceStyle == CombatStyleType.MAGIC
			&& !Constants.USES_DEFENCE_LEVEL_FOR_MAGIC_DEFENCE_NPC_IDS.contains(monster.getId()))
			? monster.getSkills().getMagic()
			: monster.getSkills().getDef();
		int effectiveLevel = level + 9;

		int bonus;
		if (defenceStyle == CombatStyleType.RANGED)
		{
			PlayerCombatStyle.RangedDamageType rangedType =
				PlayerCombatStyle.getRangedDamageType(weapon().getCategory());
			Monster.Defensive d = monster.getDefensive();
			bonus = rangedType == PlayerCombatStyle.RangedDamageType.MIXED
				? CalcMath.trunc((double) (d.getLight() + d.getStandard() + d.getHeavy()) / 3)
				: monsterDefensiveRanged(rangedType);
		}
		else
		{
			bonus = monsterDefensive(defenceStyle == null ? CombatStyleType.CRUSH : defenceStyle);
		}

		int statBonus = (defenceStyle != null ? bonus : 0) + 64;
		int defenceRoll = CalcMath.applyFactor(effectiveLevel, Factor.of(statBonus, 1));

		boolean isCustomMonster = monster.getId() == -1;

		if (((Constants.TOMBS_OF_AMASCUT_MONSTER_IDS.contains(monster.getId())
			&& !Constants.KEPHRI_OVERLORD_IDS.contains(monster.getId())) || isCustomMonster)
			&& monster.getInputs().getToaInvocationLevel() != 0)
		{
			defenceRoll = CalcMath.applyFactor(defenceRoll,
				Factor.of(250 + monster.getInputs().getToaInvocationLevel(), 250));
		}

		return defenceRoll;
	}

	/**
	 * Explicit replacement for the TS `monster.defensive[type]` index.
	 */
	private int monsterDefensive(CombatStyleType type)
	{
		Monster.Defensive d = monster.getDefensive();
		switch (type)
		{
			case STAB:
				return d.getStab();
			case SLASH:
				return d.getSlash();
			case CRUSH:
				return d.getCrush();
			case MAGIC:
				return d.getMagic();
			default:
				// 'ranged' is never indexed directly in the source; it splits by ranged damage type
				return d.getStandard();
		}
	}

	private int monsterDefensiveRanged(PlayerCombatStyle.RangedDamageType type)
	{
		Monster.Defensive d = monster.getDefensive();
		switch (type)
		{
			case LIGHT:
				return d.getLight();
			case STANDARD:
				return d.getStandard();
			case HEAVY:
				return d.getHeavy();
			default:
				throw new IllegalArgumentException("Not a directly indexable ranged damage type: " + type);
		}
	}

	private int getPlayerMaxMeleeAttackRoll()
	{
		PlayerCombatStyle style = player.getStyle();

		int effectiveLevel = player.getSkills().getAtk() + player.getBoosts().getAtk();

		for (Prayer p : getCombatPrayers(PrayerFilter.ACCURACY))
		{
			effectiveLevel = CalcMath.applyFactor(effectiveLevel, p.getFactorAccuracy());
		}

		int stanceBonus = 8;
		if (style.getStance() == CombatStyleStance.ACCURATE)
		{
			stanceBonus += 3;
		}
		else if (style.getStance() == CombatStyleStance.CONTROLLED)
		{
			stanceBonus += 1;
		}

		effectiveLevel += stanceBonus;

		if (isWearingMeleeVoid())
		{
			effectiveLevel = CalcMath.applyFactor(effectiveLevel, Factor.of(11, 10));
		}

		int gearBonus = (style.getType() != null ? player.getOffensive().forStyle(style.getType()) : 0) + 64;
		int baseRoll = CalcMath.applyFactor(effectiveLevel, Factor.of(gearBonus, 1));
		int attackRoll = baseRoll;

		List<MonsterAttribute> mattrs = monster.getAttributes();

		// These bonuses do not stack with each other
		if (wearing("Amulet of avarice") && monster.getName().startsWith("Revenant"))
		{
			Factor factor = Factor.of(player.getBuffs().isForinthrySurge() ? 27 : 24, 20);
			attackRoll = CalcMath.applyFactor(attackRoll, factor);
		}
		else if (wearing(Arrays.asList("Salve amulet (e)", "Salve amulet(ei)")) && mattrs.contains(MonsterAttribute.UNDEAD))
		{
			attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(6, 5));
		}
		else if (wearing(Arrays.asList("Salve amulet", "Salve amulet(i)")) && mattrs.contains(MonsterAttribute.UNDEAD))
		{
			attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(7, 6));
		}
		else if (isWearingBlackMask() && isSlayerMonster() && player.getBuffs().isOnSlayerTask())
		{
			attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(7, 6));
		}

		if (isWearingTzhaarWeapon() && isWearingObsidian())
		{
			int obsidianBonus = CalcMath.applyFactor(baseRoll, Factor.of(1, 10));
			attackRoll = attackRoll + obsidianBonus;
		}

		if (isRevWeaponBuffApplicable())
		{
			attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(3, 2));
		}
		if (wearing(Arrays.asList("Arclight", "Emberlight")) && mattrs.contains(MonsterAttribute.DEMON))
		{
			attackRoll = addFactor(attackRoll, demonbaneFactor(70));
		}
		if (wearing(Arrays.asList("Bone claws", "Burning claws")) && mattrs.contains(MonsterAttribute.DEMON))
		{
			attackRoll = addFactor(attackRoll, demonbaneFactor(5));
		}
		if (mattrs.contains(MonsterAttribute.DRAGON))
		{
			if (wearing("Dragon hunter lance"))
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(6, 5));
			}
			else if (wearing("Dragon hunter wand"))
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(7, 4));
			}
		}
		if (wearing("Keris partisan of breaching") && mattrs.contains(MonsterAttribute.KALPHITE))
		{
			attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(133, 100));
		}
		if (wearing("Keris partisan of the sun")
			&& Constants.TOMBS_OF_AMASCUT_MONSTER_IDS.contains(monster.getId())
			&& monster.getInputs().getMonsterCurrentHp() < CalcMath.trunc((double) monster.getSkills().getHp() / 4))
		{
			attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(5, 4));
		}
		if (wearing(Arrays.asList("Blisterwood flail", "Blisterwood sickle")) && isVampyre(mattrs))
		{
			attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(105, 100));
		}
		if (wearing(Arrays.asList("Hallowed flail", "Sunspear")) && isVampyre(mattrs))
		{
			attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(125, 100));
		}
		if (isWearingSilverWeapon() && wearing("Efaritay's aid") && isVampyre(mattrs))
		{
			attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(23, 20));
		}

		if (wearing("Granite hammer") && mattrs.contains(MonsterAttribute.GOLEM))
		{
			attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(13, 10));
		}

		// Inquisitor's armour set gives bonuses when using the crush attack style
		if (style.getType() == CombatStyleType.CRUSH)
		{
			int inqPieces = countEquipped(Arrays.asList(
				"Inquisitor's great helm",
				"Inquisitor's hauberk",
				"Inquisitor's plateskirt"));

			// When wearing the full set, the bonus is enhanced
			if (inqPieces > 0)
			{
				if (wearing("Inquisitor's mace"))
				{
					// 2.5% per piece, no full-set bonus
					inqPieces *= 5;
				}
				else if (inqPieces == 3)
				{
					// 1.0% extra for full set when not using inq mace
					inqPieces = 5;
				}
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(200 + inqPieces, 200));
			}
		}

		if (opts.isUsingSpecialAttack())
		{
			if (isWearingGodsword())
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(2, 1));
			}
			else if (isWearingFang() || wearing("Arkan blade") || wearing("Granite hammer"))
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(3, 2));
			}
			else if (wearing(Arrays.asList("Elder maul", "Dragon mace", "Dragon sword", "Dragon scimitar",
				"Abyssal whip")))
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(5, 4));
			}
			else if (wearing("Dragon dagger"))
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(23, 20));
			}
			else if (wearing("Abyssal dagger"))
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(5, 4));
			}
			else if (wearing("Soulreaper axe"))
			{
				int stacks = Math.max(0, Math.min(5, player.getBuffs().getSoulreaperStacks()));
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(100 + 6 * stacks, 100));
			}
			else if (wearing("Brine sabre"))
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(2, 1));
			}
			else if (wearing("Barrelchest anchor"))
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(2, 1));
			}
			else if (isSunspearFinisher())
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(7, 10));
			}
		}

		return attackRoll;
	}

	/**
	 * Get the player's max melee hit.
	 */
	private MinMax getPlayerMaxMeleeHit()
	{
		PlayerCombatStyle style = player.getStyle();

		int baseLevel = player.getSkills().getStr() + player.getBoosts().getStr();
		int effectiveLevel = baseLevel;

		for (Prayer p : getCombatPrayers(PrayerFilter.STRENGTH))
		{
			if ("Burst of Strength".equals(p.getPrayerName()) && effectiveLevel <= 20)
			{
				effectiveLevel = effectiveLevel + 1;
			}
			else
			{
				effectiveLevel = CalcMath.applyFactor(effectiveLevel, p.getFactorStrength());
			}
		}

		if (wearing("Soulreaper axe") && !opts.isUsingSpecialAttack())
		{
			// does not stack multiplicatively with prayers
			int stacks = Math.max(0, Math.min(5, player.getBuffs().getSoulreaperStacks()));
			int bonus = CalcMath.applyFactor(baseLevel, Factor.of(stacks * 6, 100));
			effectiveLevel = effectiveLevel + bonus;
		}

		int stanceBonus = 8;
		if (style.getStance() == CombatStyleStance.AGGRESSIVE)
		{
			stanceBonus += 3;
		}
		else if (style.getStance() == CombatStyleStance.CONTROLLED)
		{
			stanceBonus += 1;
		}

		effectiveLevel += stanceBonus;

		if (isWearingMeleeVoid())
		{
			effectiveLevel = CalcMath.applyFactor(effectiveLevel, Factor.of(11, 10));
		}

		int gearBonus = player.getBonuses().getStr() + 64;
		int baseMax = maxHitFromEffective(effectiveLevel, gearBonus);
		int minHit = 0;
		int maxHit = baseMax;

		if (wearing("Crystal blessing"))
		{
			int crystalPieces = (wearing("Crystal helm") ? 1 : 0)
				+ (wearing("Crystal legs") ? 2 : 0)
				+ (wearing("Crystal body") ? 3 : 0);
			maxHit = CalcMath.trunc((double) maxHit * (40 + crystalPieces) / 40);
		}

		List<MonsterAttribute> mattrs = monster.getAttributes();

		// These bonuses do not stack with each other
		if (wearing("Amulet of avarice") && monster.getName().startsWith("Revenant"))
		{
			Factor factor = Factor.of(player.getBuffs().isForinthrySurge() ? 27 : 24, 20);
			maxHit = CalcMath.applyFactor(maxHit, factor);
		}
		else if (wearing(Arrays.asList("Salve amulet (e)", "Salve amulet(ei)")) && mattrs.contains(MonsterAttribute.UNDEAD))
		{
			maxHit = CalcMath.applyFactor(maxHit, Factor.of(6, 5));
		}
		else if (wearing(Arrays.asList("Salve amulet", "Salve amulet(i)")) && mattrs.contains(MonsterAttribute.UNDEAD))
		{
			maxHit = CalcMath.applyFactor(maxHit, Factor.of(7, 6));
		}
		else if (isWearingBlackMask() && isSlayerMonster() && player.getBuffs().isOnSlayerTask())
		{
			maxHit = CalcMath.applyFactor(maxHit, Factor.of(7, 6));
		}

		if (wearing(Arrays.asList("Arclight", "Emberlight")) && mattrs.contains(MonsterAttribute.DEMON))
		{
			maxHit = addFactor(maxHit, demonbaneFactor(70));
		}
		if (wearing(Arrays.asList("Bone claws", "Burning claws")) && mattrs.contains(MonsterAttribute.DEMON))
		{
			maxHit = addFactor(maxHit, demonbaneFactor(5));
		}
		if (isWearingTzhaarWeapon() && isWearingObsidian())
		{
			int obsidianBonus = CalcMath.applyFactor(baseMax, Factor.of(1, 10));
			maxHit = maxHit + obsidianBonus;
		}
		if (wearing("Dragon hunter lance") && mattrs.contains(MonsterAttribute.DRAGON))
		{
			maxHit = CalcMath.applyFactor(maxHit, Factor.of(6, 5));
		}
		if (wearing("Dragon hunter wand") && mattrs.contains(MonsterAttribute.DRAGON))
		{
			// still applies to dhw when wand bashing
			maxHit = CalcMath.applyFactor(maxHit, Factor.of(7, 5));
		}
		if (isWearingKeris() && mattrs.contains(MonsterAttribute.KALPHITE))
		{
			if (wearing("Keris partisan of amascut"))
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(115, 100));
			}
			else
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(133, 100));
			}
		}
		if (wearing("Barronite mace") && mattrs.contains(MonsterAttribute.GOLEM))
		{
			maxHit = CalcMath.applyFactor(maxHit, Factor.of(23, 20));
		}
		if (wearing("Granite hammer") && mattrs.contains(MonsterAttribute.GOLEM))
		{
			maxHit = CalcMath.applyFactor(maxHit, Factor.of(13, 10));
		}
		if (isRevWeaponBuffApplicable())
		{
			maxHit = CalcMath.applyFactor(maxHit, Factor.of(3, 2));
		}
		if (wearing(Arrays.asList("Silverlight", "Darklight", "Silverlight (dyed)"))
			&& mattrs.contains(MonsterAttribute.DEMON))
		{
			maxHit = addFactor(maxHit, demonbaneFactor(60));
		}
		if (wearing("Leaf-bladed battleaxe") && mattrs.contains(MonsterAttribute.LEAFY))
		{
			maxHit = CalcMath.applyFactor(maxHit, Factor.of(47, 40));
		}
		if (wearing("Colossal blade"))
		{
			maxHit = maxHit + Math.min(monster.getSize() * 2, 10);
		}

		if (isWearingRatBoneWeapon() && mattrs.contains(MonsterAttribute.RAT))
		{
			// applies before inq
			maxHit = maxHit + 10;
		}

		// Inquisitor's armour set gives bonuses when using the crush attack style
		if (style.getType() == CombatStyleType.CRUSH)
		{
			int inqPieces = countEquipped(Arrays.asList(
				"Inquisitor's great helm",
				"Inquisitor's hauberk",
				"Inquisitor's plateskirt"));

			if (inqPieces > 0)
			{
				if (wearing("Inquisitor's mace"))
				{
					inqPieces *= 5;
				}
				else if (inqPieces == 3)
				{
					inqPieces = 5;
				}
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(200 + inqPieces, 200));
			}
		}

		if (isWearingFang())
		{
			int shrink = CalcMath.trunc((double) maxHit * 3 / 20);
			minHit = shrink;
			if (!opts.isUsingSpecialAttack())
			{
				maxHit = maxHit - shrink;
			}
			// not reduced during spec, but min hit is changed as usual
		}

		if (opts.isUsingSpecialAttack())
		{
			if (isWearingGodsword())
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(11, 10));
			}

			if (wearing(Arrays.asList("Bandos godsword", "Saradomin sword")))
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(11, 10));
			}
			else if (wearing(Arrays.asList("Armadyl godsword", "Dragon sword", "Dragon longsword",
				"Saradomin's blessed sword")))
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(5, 4));
			}
			else if (wearing(Arrays.asList("Dragon mace", "Dragon warhammer", "Arkan blade")))
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(3, 2));
			}
			else if (wearing("Voidwaker"))
			{
				minHit = CalcMath.applyFactor(maxHit, Factor.of(1, 2));
				maxHit = maxHit + minHit;
			}
			else if (wearing(Arrays.asList("Dragon halberd", "Crystal halberd")))
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(11, 10));
			}
			else if (wearing("Dragon dagger"))
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(23, 20));
			}
			else if (wearing("Abyssal dagger"))
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(17, 20));
			}
			else if (wearing("Abyssal bludgeon"))
			{
				int prayerMissing = Math.max(-player.getBoosts().getPrayer(), 0);
				maxHit = CalcMath.trunc((double) maxHit * (100 + (prayerMissing / 2.0)) / 100);
			}
			else if (wearing("Barrelchest anchor"))
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(110, 100));
			}
			else if (isWearingBloodMoonSet())
			{
				minHit = CalcMath.applyFactor(maxHit, Factor.of(1, 4));
				maxHit = maxHit + minHit;
			}
			else if (wearing("Soulreaper axe"))
			{
				int stacks = Math.max(0, Math.min(5, player.getBuffs().getSoulreaperStacks()));
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(100 + 6 * stacks, 100));
			}
		}

		if ("Respiratory system".equals(monster.getName()))
		{
			minHit = minHit + CalcMath.trunc((double) maxHit / 2);
		}

		return MinMax.of(minHit, maxHit);
	}

	private int getPlayerMaxRangedAttackRoll()
	{
		PlayerCombatStyle style = player.getStyle();

		int effectiveLevel = player.getSkills().getRanged() + player.getBoosts().getRanged();
		for (Prayer p : getCombatPrayers(PrayerFilter.ACCURACY))
		{
			effectiveLevel = CalcMath.applyFactor(effectiveLevel, p.getFactorAccuracy());
		}

		if (style.getStance() == CombatStyleStance.ACCURATE)
		{
			effectiveLevel += 3;
		}

		effectiveLevel += 8;

		if (isWearingRangedVoid())
		{
			effectiveLevel = CalcMath.trunc((double) effectiveLevel * 11 / 10);
		}

		int attackRoll = effectiveLevel * (player.getOffensive().getRanged() + 64);

		if (isWearingCrystalBow())
		{
			int crystalPieces = (wearing("Crystal helm") ? 1 : 0)
				+ (wearing("Crystal legs") ? 2 : 0)
				+ (wearing("Crystal body") ? 3 : 0);
			attackRoll = CalcMath.trunc((double) attackRoll * (20 + crystalPieces) / 20);
		}

		List<MonsterAttribute> mattrs = monster.getAttributes();

		if (wearing("Amulet of avarice") && monster.getName().startsWith("Revenant"))
		{
			Factor factor = Factor.of(player.getBuffs().isForinthrySurge() ? 27 : 24, 20);
			attackRoll = CalcMath.applyFactor(attackRoll, factor);
		}
		else if (wearing("Salve amulet(ei)") && mattrs.contains(MonsterAttribute.UNDEAD))
		{
			attackRoll = CalcMath.trunc((double) attackRoll * 6 / 5);
		}
		else if (wearing("Salve amulet(i)") && mattrs.contains(MonsterAttribute.UNDEAD))
		{
			attackRoll = CalcMath.trunc((double) attackRoll * 7 / 6);
		}
		else if (isWearingImbuedBlackMask() && isSlayerMonster() && player.getBuffs().isOnSlayerTask())
		{
			attackRoll = CalcMath.trunc((double) attackRoll * 23 / 20);
		}

		if (wearing("Twisted bow"))
		{
			int cap = mattrs.contains(MonsterAttribute.XERICIAN) ? 350 : 250;
			int tbowMagic = Math.min(cap, Math.max(monster.getSkills().getMagic(), monster.getOffensive().getMagic()));
			attackRoll = tbowScaling(attackRoll, tbowMagic, true);
			if (Constants.P2_WARDEN_IDS.contains(monster.getId()))
			{
				// Game update on 2023-06-21 caused this bonus to be applied twice at P2 Wardens
				attackRoll = tbowScaling(attackRoll, tbowMagic, true);
			}
		}
		if (isRevWeaponBuffApplicable())
		{
			attackRoll = CalcMath.trunc((double) attackRoll * 3 / 2);
		}
		if (wearing("Dragon hunter crossbow") && mattrs.contains(MonsterAttribute.DRAGON))
		{
			attackRoll = CalcMath.trunc((double) attackRoll * 13 / 10);
		}
		if (weaponCategoryIs(EquipmentCategory.CHINCHOMPA))
		{
			int distance = Math.min(7, Math.max(1, player.getBuffs().getChinchompaDistance()));

			int numerator = 4;
			if ("Short fuse".equals(style.getName()))
			{
				if (distance >= 7)
				{
					numerator = 2;
				}
				else if (distance >= 4)
				{
					numerator = 3;
				}
			}
			else if ("Medium fuse".equals(style.getName()))
			{
				if (distance < 4 || distance >= 7)
				{
					numerator = 3;
				}
			}
			else if ("Long fuse".equals(style.getName()))
			{
				if (distance < 4)
				{
					numerator = 2;
				}
				else if (distance < 7)
				{
					numerator = 3;
				}
			}

			attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(numerator, 4));
		}

		if (wearing("Scorching bow") && mattrs.contains(MonsterAttribute.DEMON))
		{
			attackRoll = addFactor(attackRoll, demonbaneFactor(30));
		}

		if (opts.isUsingSpecialAttack())
		{
			if (wearing(Arrays.asList("Zaryte crossbow", "Webweaver bow")) || isWearingBlowpipe())
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(2, 1));
			}
			else if (isWearingMsb())
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(10, 7));
			}
			else if (wearing(Arrays.asList("Heavy ballista", "Light ballista")))
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(5, 4));
			}
			else if (wearing("Rosewood blowpipe"))
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(4, 5));
			}
		}

		if (Constants.TITAN_BOSS_IDS.contains(monster.getId())
			&& "Out of Melee Range".equals(monster.getInputs().getPhase()))
		{
			attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(6, 1));
		}

		return attackRoll;
	}

	/**
	 * Get the player's max ranged hit.
	 */
	private MinMax getPlayerMaxRangedHit()
	{
		PlayerCombatStyle style = player.getStyle();

		int effectiveLevel = player.getSkills().getRanged() + player.getBoosts().getRanged();
		boolean scalesWithStr = wearing(Arrays.asList("Eclipse atlatl", "Hunter's spear"));
		if (scalesWithStr)
		{
			// atlatl uses strength instead of ranged skill, melee strength bonus, and melee buff from
			// slayer helmet/salve, but works with ranged void
			effectiveLevel = player.getSkills().getStr() + player.getBoosts().getStr();
		}

		if (wearing("Holy water"))
		{
			if (!monster.getAttributes().contains(MonsterAttribute.DEMON))
			{
				// can't be used against non-demons
				return MinMax.of(0, 0);
			}

			// similar to msb + mlb + seercull below
			effectiveLevel = effectiveLevel + 10;

			int str = 64 + weapon().getBonuses().getRangedStr();
			int holyMaxHit = maxHitFromEffective(effectiveLevel, str);

			if (monster.getAttributes().contains(MonsterAttribute.DEMON))
			{
				holyMaxHit = addFactor(holyMaxHit, demonbaneFactor(60));
			}
			if ("Nezikchened".equals(monster.getName()))
			{
				holyMaxHit = holyMaxHit + 5;
			}

			return MinMax.of(0, holyMaxHit);
		}

		if ((opts.isUsingSpecialAttack() && (isWearingMsb() || isWearingMlb() || wearing("Seercull")))
			|| isWearingOgreBow())
		{
			// why +10 when that's not used anywhere else? who knows
			effectiveLevel += 10;

			// ignores other gear
			EquipmentPiece ammo = ammo();
			int bonusStr = ammo == null ? 0 : ammo.getBonuses().getRangedStr();
			int ogreMaxHit = CalcMath.trunc(((double) effectiveLevel * (bonusStr + 64) + 320) / 640);

			// end early, it ignores all other gear and bonuses
			return MinMax.of(0, ogreMaxHit);
		}

		for (Prayer p : getCombatPrayers(PrayerFilter.STRENGTH))
		{
			if ("Sharp Eye".equals(p.getPrayerName()) && effectiveLevel <= 20)
			{
				// force 1 level gain
				effectiveLevel = effectiveLevel + 1;
			}
			else
			{
				effectiveLevel = CalcMath.applyFactor(effectiveLevel, p.getFactorStrength());
			}
		}

		if (style.getStance() == CombatStyleStance.ACCURATE)
		{
			effectiveLevel += 3;
		}

		effectiveLevel += 8;

		if (isWearingEliteRangedVoid())
		{
			effectiveLevel = CalcMath.trunc((double) effectiveLevel * 9 / 8);
		}
		else if (isWearingRangedVoid())
		{
			effectiveLevel = CalcMath.trunc((double) effectiveLevel * 11 / 10);
		}

		int bonusStr = scalesWithStr ? player.getBonuses().getStr() : player.getBonuses().getRangedStr();
		int baseMax = maxHitFromEffective(effectiveLevel, 64 + bonusStr);
		int minHit = 0;
		int maxHit = baseMax;

		if (isWearingCrystalBow())
		{
			int crystalPieces = (wearing("Crystal helm") ? 1 : 0)
				+ (wearing("Crystal legs") ? 2 : 0)
				+ (wearing("Crystal body") ? 3 : 0);
			maxHit = CalcMath.trunc((double) maxHit * (40 + crystalPieces) / 40);
		}

		List<MonsterAttribute> mattrs = monster.getAttributes();
		boolean needRevWeaponBonus = isRevWeaponBuffApplicable();
		boolean needDragonbane = wearing("Dragon hunter crossbow") && mattrs.contains(MonsterAttribute.DRAGON);
		boolean needDemonbane = wearing("Scorching bow") && mattrs.contains(MonsterAttribute.DEMON);

		if (wearing("Amulet of avarice") && monster.getName().startsWith("Revenant"))
		{
			Factor factor = Factor.of(player.getBuffs().isForinthrySurge() ? 27 : 24, 20);
			maxHit = CalcMath.applyFactor(maxHit, factor);
		}
		else if ((wearing("Salve amulet(ei)") || (scalesWithStr && wearing("Salve amulet (e)")))
			&& mattrs.contains(MonsterAttribute.UNDEAD))
		{
			maxHit = CalcMath.trunc((double) maxHit * 6 / 5);
		}
		else if ((wearing("Salve amulet(i)") || (scalesWithStr && wearing("Salve amulet")))
			&& mattrs.contains(MonsterAttribute.UNDEAD))
		{
			maxHit = CalcMath.trunc((double) maxHit * 7 / 6);
		}
		else if (scalesWithStr && isWearingBlackMask() && isSlayerMonster() && player.getBuffs().isOnSlayerTask())
		{
			maxHit = CalcMath.trunc((double) maxHit * 7 / 6);
		}
		else if (isWearingImbuedBlackMask() && isSlayerMonster() && player.getBuffs().isOnSlayerTask())
		{
			int numerator = 23;
			// these are additive with slayer only
			if (needRevWeaponBonus)
			{
				needRevWeaponBonus = false;
				numerator += 10;
			}
			if (needDragonbane)
			{
				needDragonbane = false;
				numerator += 5;
			}
			if (needDemonbane)
			{
				needDemonbane = false;
				numerator += 6;
			}
			maxHit = CalcMath.applyFactor(maxHit, Factor.of(numerator, 20));
		}

		if (wearing("Twisted bow"))
		{
			int cap = mattrs.contains(MonsterAttribute.XERICIAN) ? 350 : 250;
			int tbowMagic = Math.min(cap, Math.max(monster.getSkills().getMagic(), monster.getOffensive().getMagic()));
			maxHit = tbowScaling(maxHit, tbowMagic, false);
		}

		// multiplicative if not with slayer helm
		if (needRevWeaponBonus)
		{
			maxHit = CalcMath.trunc((double) maxHit * 3 / 2);
		}
		if (needDragonbane)
		{
			maxHit = CalcMath.trunc((double) maxHit * 5 / 4);
		}
		if (needDemonbane)
		{
			maxHit = addFactor(maxHit, demonbaneFactor(30));
		}

		if (isWearingRatBoneWeapon() && mattrs.contains(MonsterAttribute.RAT))
		{
			maxHit = maxHit + 10;
		}

		if (wearing("Tonalztics of ralos"))
		{
			// rolls 75% of max hit, but can hit twice
			// double hit is implemented in hit distribution
			maxHit = CalcMath.applyFactor(maxHit, Factor.of(3, 4));
		}

		if (opts.isUsingSpecialAttack())
		{
			if (isWearingBlowpipe())
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(3, 2));
			}
			else if (wearing("Webweaver bow"))
			{
				int maxReduction = CalcMath.trunc((double) maxHit * 6 / 10);
				maxHit = maxHit - maxReduction;
			}
			else if (wearing(Arrays.asList("Heavy ballista", "Light ballista")))
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(5, 4));
			}
			else if (wearing("Rosewood blowpipe"))
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(11, 10));
			}
		}

		if (opts.isUsingSpecialAttack())
		{
			if (wearing("Dark bow"))
			{
				boolean descentOfDragons = wearing("Dragon arrow");
				minHit = descentOfDragons ? 8 : 5;
				int dmgFactor = descentOfDragons ? 15 : 13;
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(dmgFactor, 10));
			}
		}

		if (Constants.P2_WARDEN_IDS.contains(monster.getId()))
		{
			MinMax wardens = applyP2WardensDamageModifier(maxHit);
			minHit = wardens.getMin();
			maxHit = wardens.getMax();
		}

		if ("Respiratory system".equals(monster.getName()))
		{
			minHit = minHit + CalcMath.trunc((double) maxHit / 2);
		}

		return MinMax.of(minHit, maxHit);
	}

	private int getPlayerMaxMagicAttackRoll()
	{
		PlayerCombatStyle style = player.getStyle();

		int effectiveLevel = player.getSkills().getMagic() + player.getBoosts().getMagic();
		for (Prayer p : getCombatPrayers(PrayerFilter.ACCURACY))
		{
			effectiveLevel = CalcMath.applyFactor(effectiveLevel, p.getFactorAccuracy());
		}

		if (style.getStance() == CombatStyleStance.ACCURATE)
		{
			effectiveLevel += 2;
		}

		effectiveLevel += 9;

		if (isWearingMagicVoid())
		{
			effectiveLevel = CalcMath.trunc((double) effectiveLevel * 29 / 20);
		}

		List<MonsterAttribute> mattrs = monster.getAttributes();
		int magicBonus = player.getOffensive().getMagic();

		int baseRoll = effectiveLevel * (magicBonus + 64);
		int attackRoll = baseRoll;

		int additiveBonus = 0;
		boolean blackMaskBonus = false;
		if (wearing("Amulet of avarice") && monster.getName().startsWith("Revenant"))
		{
			additiveBonus = additiveBonus + (player.getBuffs().isForinthrySurge() ? 35 : 20);
		}
		else if (wearing("Salve amulet(ei)") && mattrs.contains(MonsterAttribute.UNDEAD))
		{
			additiveBonus = additiveBonus + 20;
		}
		else if (wearing("Salve amulet(i)") && mattrs.contains(MonsterAttribute.UNDEAD))
		{
			additiveBonus = additiveBonus + 15;
		}
		else if (isWearingImbuedBlackMask() && isSlayerMonster() && player.getBuffs().isOnSlayerTask())
		{
			blackMaskBonus = true;
		}

		if (wearing("Efaritay's aid") && isVampyre(mattrs) && isWearingSilverWeapon())
		{
			additiveBonus = additiveBonus + 15;
		}

		if (isWearingSmokeStaff() && player.getSpell() != null && "standard".equals(player.getSpell().getSpellbook()))
		{
			additiveBonus = additiveBonus + 10;
		}

		if (additiveBonus != 0)
		{
			attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(100 + additiveBonus, 100));
		}

		if (mattrs.contains(MonsterAttribute.DRAGON))
		{
			// this still applies to dhl and dhcb when autocasting
			if (wearing("Dragon hunter crossbow"))
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(13, 10));
			}
			else if (wearing("Dragon hunter lance"))
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(6, 5));
			}
			else if (wearing("Dragon hunter wand"))
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(7, 4));
			}
		}

		if (blackMaskBonus)
		{
			attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(23, 20));
		}

		if (player.getSpell() != null && player.getSpell().getName().contains("Demonbane")
			&& mattrs.contains(MonsterAttribute.DEMON))
		{
			int demonbanePercent = player.getBuffs().isMarkOfDarknessSpell() ? 40 : 20;
			if (wearing("Purging staff"))
			{
				demonbanePercent *= 2;
			}
			attackRoll = addFactor(attackRoll, demonbaneFactor(demonbanePercent));
		}
		if (isRevWeaponBuffApplicable())
		{
			attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(3, 2));
		}
		if (wearing("Tome of water")
			&& ("water".equals(getSpellement()) || (player.getSpell() != null && player.getSpell().isBindSpell())))
		{
			attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(6, 5));
		}

		if (opts.isUsingSpecialAttack())
		{
			if (isWearingAccursedSceptre())
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(3, 2));
			}
			else if (wearing("Volatile nightmare staff"))
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(3, 2));
			}
			else if (wearing("Eye of ayak"))
			{
				attackRoll = CalcMath.applyFactor(attackRoll, Factor.of(2, 1));
			}
		}

		String spellement = getSpellement();
		Monster.Weakness weakness = getMonsterWeakness();
		if (spellement != null && weakness != null && spellement.equals(weakness.getElement()))
		{
			int bonus = CalcMath.applyFactor(baseRoll, Factor.of(weakness.getSeverity(), 100));
			attackRoll = attackRoll + bonus;
		}

		return attackRoll;
	}

	/**
	 * Get the player's max magic hit.
	 */
	private MinMax getPlayerMaxMagicHit()
	{
		int minHit = 0;
		int maxHit = 0;
		int magicLevel = player.getSkills().getMagic() + player.getBoosts().getMagic();
		Spell spell = player.getSpell();

		List<MonsterAttribute> mattrs = monster.getAttributes();

		if (spell != null)
		{
			maxHit = Spell.maxHit(spell, magicLevel, CalcData.getSpells());
			if ("Magic Dart".equals(spell.getName()))
			{
				if (wearing("Slayer's staff (e)") && isSlayerMonster() && player.getBuffs().isOnSlayerTask())
				{
					maxHit = CalcMath.trunc(13 + magicLevel / 6.0);
				}
				else
				{
					maxHit = CalcMath.trunc(10 + magicLevel / 10.0);
				}
			}
		}
		else if (wearing("Starter staff"))
		{
			maxHit = 8;
		}
		else if (wearing(Arrays.asList("Trident of the seas", "Trident of the seas (e)")))
		{
			maxHit = Math.max(1, CalcMath.trunc(magicLevel / 3.0 - 5));
		}
		else if (wearing("Thammaron's sceptre"))
		{
			maxHit = Math.max(1, CalcMath.trunc(magicLevel / 3.0 - 8));
		}
		else if (wearing("Accursed sceptre") || (wearing("Accursed sceptre (a)") && opts.isUsingSpecialAttack()))
		{
			maxHit = Math.max(1, CalcMath.trunc(magicLevel / 3.0 - 6));
		}
		else if (wearing(Arrays.asList("Trident of the swamp", "Trident of the swamp (e)")))
		{
			maxHit = Math.max(1, CalcMath.trunc(magicLevel / 3.0 - 2));
		}
		else if (wearing(Arrays.asList("Sanguinesti staff", "Holy sanguinesti staff")))
		{
			maxHit = Math.max(1, CalcMath.trunc(magicLevel / 3.0 - 1));
		}
		else if (wearing("Dawnbringer"))
		{
			maxHit = Math.max(1, CalcMath.trunc(magicLevel / 6.0 - 1));
			if (opts.isUsingSpecialAttack())
			{
				// guaranteed hit between 75-150, ignores bonuses
				return MinMax.of(75, 150);
			}
		}
		else if (wearing("Tumeken's shadow"))
		{
			maxHit = Math.max(1, CalcMath.trunc(magicLevel / 3.0) + 1);
		}
		else if (wearing("Eye of ayak"))
		{
			maxHit = Math.max(1, CalcMath.trunc(magicLevel / 3.0) - 6);
		}
		else if (wearing("Warped sceptre"))
		{
			maxHit = Math.max(1, CalcMath.trunc((8.0 * magicLevel + 96) / 37));
		}
		else if (wearing("Bone staff"))
		{
			// although the +10 is technically a ratbane bonus, the weapon can't be used against non-rats
			maxHit = Math.max(1, CalcMath.trunc(magicLevel / 3.0) - 5) + 10;
		}
		else if (wearing("Eldritch nightmare staff") && opts.isUsingSpecialAttack())
		{
			maxHit = Math.max(1, Math.min(44, CalcMath.trunc((99.0 + 44 * magicLevel) / 99)));
		}
		else if (wearing("Volatile nightmare staff") && opts.isUsingSpecialAttack())
		{
			maxHit = Math.max(1, Math.min(58, CalcMath.trunc((99.0 + 58 * magicLevel) / 99)));
		}
		else if (wearing(Arrays.asList("Crystal staff (basic)", "Corrupted staff (basic)")))
		{
			maxHit = 23;
		}
		else if (wearing(Arrays.asList("Crystal staff (attuned)", "Corrupted staff (attuned)")))
		{
			maxHit = 31;
		}
		else if (wearing(Arrays.asList("Crystal staff (perfected)", "Corrupted staff (perfected)")))
		{
			maxHit = 39;
		}
		else if (wearing("Swamp lizard"))
		{
			maxHit = CalcMath.trunc(((double) magicLevel * (56 + 64) + 320) / 640);
		}
		else if (wearing("Orange salamander"))
		{
			maxHit = CalcMath.trunc(((double) magicLevel * (59 + 64) + 320) / 640);
		}
		else if (wearing("Red salamander"))
		{
			maxHit = CalcMath.trunc(((double) magicLevel * (77 + 64) + 320) / 640);
		}
		else if (wearing("Black salamander"))
		{
			maxHit = CalcMath.trunc(((double) magicLevel * (92 + 64) + 320) / 640);
		}
		else if (wearing("Tecu salamander"))
		{
			maxHit = CalcMath.trunc(((double) magicLevel * (104 + 64) + 320) / 640);
		}

		if (maxHit == 0)
		{
			// at this point either they've selected a 0-dmg spell
			// or they picked a staff-casting option without choosing a spell
			return MinMax.of(0, 0);
		}

		if (opts.isUsingSpecialAttack() && wearing("Eye of ayak"))
		{
			maxHit = CalcMath.applyFactor(maxHit, Factor.of(13, 10));
		}

		if (wearing("Chaos gauntlets") && spell != null && spell.getName().toLowerCase(java.util.Locale.ROOT)
			.contains("bolt"))
		{
			maxHit += 3;
		}
		if (isChargeSpellApplicable())
		{
			maxHit += 10;
		}

		// We need the basehit value for the elemental bonus later.
		int baseMax = maxHit;
		int magicDmgBonus = player.getBonuses().getMagicStr();

		if (isWearingSmokeStaff() && spell != null && "standard".equals(spell.getSpellbook()))
		{
			magicDmgBonus += 100;
		}

		boolean blackMaskBonus = false;
		if (wearing("Salve amulet(ei)") && mattrs.contains(MonsterAttribute.UNDEAD))
		{
			magicDmgBonus += 200;
		}
		else if (wearing("Salve amulet(i)") && mattrs.contains(MonsterAttribute.UNDEAD))
		{
			magicDmgBonus += 150;
		}
		else if (wearing("Amulet of avarice") && monster.getName().startsWith("Revenant"))
		{
			magicDmgBonus += player.getBuffs().isForinthrySurge() ? 350 : 200;
		}
		else if (isWearingImbuedBlackMask() && isSlayerMonster() && player.getBuffs().isOnSlayerTask())
		{
			blackMaskBonus = true;
		}

		for (Prayer p : getCombatPrayers(PrayerFilter.MAGIC_DAMAGE))
		{
			magicDmgBonus += p.getMagicDamageBonus();
		}

		maxHit = addFactor(maxHit, Factor.of(magicDmgBonus, 1000));

		if (blackMaskBonus)
		{
			maxHit = CalcMath.trunc((double) maxHit * 23 / 20);
		}

		if (mattrs.contains(MonsterAttribute.DRAGON))
		{
			// this still applies to dhl and dhcb when autocasting
			if (wearing("Dragon hunter lance"))
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(6, 5));
			}
			else if (wearing("Dragon hunter wand"))
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(7, 5));
			}
			else if (wearing("Dragon hunter crossbow"))
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(5, 4));
			}
		}

		if (isRevWeaponBuffApplicable())
		{
			maxHit = CalcMath.trunc((double) maxHit * 3 / 2);
		}

		if (opts.isUsingSpecialAttack())
		{
			if (isWearingAccursedSceptre())
			{
				maxHit = CalcMath.applyFactor(maxHit, Factor.of(3, 2));
			}
		}

		String spellement = getSpellement();
		Monster.Weakness weakness = getMonsterWeakness();
		if (spellement != null && weakness != null && spellement.equals(weakness.getElement()))
		{
			maxHit += CalcMath.trunc((double) baseMax * (weakness.getSeverity() / 100.0));
		}

		if (player.getBuffs().isUsingSunfireRunes() && spell != null && spell.canUseSunfireRunes())
		{
			// sunfire runes are applied pre-tome
			minHit = CalcMath.applyFactor(maxHit, Factor.of(1, 10));
		}

		EquipmentPiece shield = player.getEquipment().getShield();
		boolean chargedShield = shield != null && "Charged".equals(shield.getVersion());
		if ((wearing("Tome of fire") && chargedShield && "fire".equals(getSpellement()))
			|| (wearing("Tome of water") && chargedShield && "water".equals(getSpellement()))
			|| (wearing("Tome of earth") && chargedShield && "earth".equals(getSpellement())))
		{
			maxHit = CalcMath.applyFactor(maxHit, Factor.of(11, 10));
		}

		if (Constants.P2_WARDEN_IDS.contains(monster.getId()))
		{
			MinMax wardens = applyP2WardensDamageModifier(maxHit);
			minHit = wardens.getMin();
			maxHit = wardens.getMax();
		}

		if ("Respiratory system".equals(monster.getName()))
		{
			minHit = minHit + CalcMath.trunc((double) maxHit / 2);
		}

		return MinMax.of(minHit, maxHit);
	}

	// ---------------------------------------------------------------------------------------------
	// Prayers
	// ---------------------------------------------------------------------------------------------

	private enum PrayerFilter
	{
		STRENGTH,
		ACCURACY,
		MAGIC_DAMAGE
	}

	private List<Prayer> getCombatPrayers()
	{
		return getCombatPrayers(PrayerFilter.STRENGTH);
	}

	/**
	 * Get the "combat" prayers for the current combat style. These are prayers that aren't overheads.
	 */
	private List<Prayer> getCombatPrayers(PrayerFilter filter)
	{
		Prayer.PrayerStyle wanted;
		if (isUsingMeleeStyle())
		{
			wanted = Prayer.PrayerStyle.MELEE;
		}
		else if (styleType() == CombatStyleType.RANGED)
		{
			wanted = Prayer.PrayerStyle.RANGED;
		}
		else
		{
			wanted = Prayer.PrayerStyle.MAGIC;
		}

		List<Prayer> ret = new ArrayList<>();
		for (Prayer p : player.getPrayers())
		{
			if (p.getStyle() != wanted)
			{
				continue;
			}

			boolean applicable;
			switch (filter)
			{
				case ACCURACY:
					applicable = p.getFactorAccuracy() != null;
					break;
				case MAGIC_DAMAGE:
					applicable = p.getMagicDamageBonus() != 0;
					break;
				case STRENGTH:
				default:
					applicable = p.getFactorStrength() != null;
					break;
			}

			if (applicable)
			{
				ret.add(p);
			}
		}

		return ret;
	}

	// ---------------------------------------------------------------------------------------------
	// Min/max hit and attack roll
	// ---------------------------------------------------------------------------------------------

	/**
	 * Get the min and max hit for this loadout, which is based on the player's current combat style.
	 */
	public MinMax getMinAndMax()
	{
		if (stance() != CombatStyleStance.MANUAL_CAST && isAmmoInvalid())
		{
			return MinMax.of(0, 0);
		}

		CombatStyleType style = styleType();

		MinMax minMax = MinMax.of(0, 0);
		if (isUsingMeleeStyle())
		{
			minMax = getPlayerMaxMeleeHit();
		}
		if (style == CombatStyleType.RANGED)
		{
			minMax = getPlayerMaxRangedHit();
		}
		if (style == CombatStyleType.MAGIC)
		{
			minMax = getPlayerMaxMagicHit();
		}

		int min = minMax.getMin();
		int max = minMax.getMax();

		if (min > max)
		{
			max = min;
		}

		// some cursed (literally, cursed amulet of magic) stuff throws this off
		if (min <= 0)
		{
			min = 0;
		}
		if (max <= 0)
		{
			max = 0;
		}

		return MinMax.of(min, max);
	}

	/**
	 * Get the max attack roll for this loadout, which is based on the player's current combat style.
	 */
	public int getMaxAttackRoll()
	{
		if (opts.getOverrides().getAttackRoll() != null)
		{
			return opts.getOverrides().getAttackRoll();
		}

		if (stance() != CombatStyleStance.MANUAL_CAST && isAmmoInvalid())
		{
			return 0;
		}

		CombatStyleType style = styleType();
		int atkRoll = 0;
		if (isUsingMeleeStyle())
		{
			atkRoll = getPlayerMaxMeleeAttackRoll();
		}
		if (style == CombatStyleType.RANGED)
		{
			atkRoll = getPlayerMaxRangedAttackRoll();
		}
		if (style == CombatStyleType.MAGIC)
		{
			atkRoll = getPlayerMaxMagicAttackRoll();
		}

		return atkRoll;
	}

	public double getHitChance()
	{
		if (opts.getOverrides().getAccuracy() != null)
		{
			return opts.getOverrides().getAccuracy();
		}

		if (Constants.GUARANTEED_ACCURACY_MONSTERS.contains(monster.getId()))
		{
			return 1.0;
		}

		if (Constants.DOOM_OF_MOKHAIOTL_IDS.contains(monster.getId())
			&& !"Normal".equals(monster.getInputs().getPhase()))
		{
			return 1.0;
		}

		if (Constants.VERZIK_P1_IDS.contains(monster.getId()) && wearing("Dawnbringer"))
		{
			return 1.0;
		}

		if (Constants.P2_WARDEN_IDS.contains(monster.getId()))
		{
			return 1.0;
		}

		// Giant rat (Scurrius)
		if (monster.getId() == 7223 && stance() != CombatStyleStance.MANUAL_CAST)
		{
			return 1.0;
		}

		if ("Tormented Demon".equals(monster.getName()) && !"Shielded".equals(monster.getInputs().getPhase()))
		{
			return 1.0;
		}

		// Ice elemental (Royal Titans) / Fire elemental (Royal Titans)
		if (Constants.TITAN_ELEMENTAL_IDS.contains(monster.getId()) && styleType() == CombatStyleType.MAGIC)
		{
			double accuracy = Math.min(1.0, Math.max(0, player.getOffensive().getMagic()) / 100.0 + 0.3);
			if (isWearingEliteMagicVoid() || isWearingMagicVoid())
			{
				accuracy = Math.min(1.0, accuracy * 1.45);
			}
			return accuracy;
		}

		// Eclipse Moon clone phase
		if (Constants.ECLIPSE_MOON_IDS.contains(monster.getId()) && "Clone".equals(monster.getVersion())
			&& isUsingMeleeStyle())
		{
			return 1.0;
		}

		if (styleType() == CombatStyleType.MAGIC
			&& Constants.ALWAYS_MAX_HIT_MONSTERS_MAGIC.contains(monster.getId()))
		{
			return 1.0;
		}
		if (styleType() == CombatStyleType.RANGED
			&& Constants.ALWAYS_MAX_HIT_MONSTERS_RANGED.contains(monster.getId()))
		{
			return 1.0;
		}
		if (isUsingMeleeStyle() && Constants.ALWAYS_MAX_HIT_MONSTERS_MELEE.contains(monster.getId()))
		{
			return 1.0;
		}

		if (opts.isUsingSpecialAttack() && wearing(Arrays.asList("Voidwaker", "Dawnbringer")))
		{
			return 1.0;
		}

		if (opts.isUsingSpecialAttack() && (wearing("Seercull") || isWearingMlb()))
		{
			if (isAmmoInvalid())
			{
				return 0.0;
			}
			return 1.0;
		}

		int atk = getMaxAttackRoll();
		int def = getNPCDefenceRoll();

		double hitChance = getNormalAccuracyRoll(atk, def);

		if (isSunspearFinisher())
		{
			return getFixedAttackHitChance(atk, def);
		}

		boolean fangAccuracy = isWearingFang() && styleType() == CombatStyleType.STAB;
		if (fangAccuracy)
		{
			if (Constants.TOMBS_OF_AMASCUT_MONSTER_IDS.contains(monster.getId()))
			{
				hitChance = 1 - Math.pow(1 - hitChance, 2);
			}
			else
			{
				hitChance = getFangAccuracyRoll(atk, def);
			}
		}

		EquipmentPiece weapon = weapon();
		if (wearing("Confliction gauntlets") && styleType() == CombatStyleType.MAGIC
			&& (weapon == null || !weapon.isTwoHanded()))
		{
			hitChance = getConflictionGauntletsAccuracyRoll(atk, def);
		}

		return hitChance;
	}

	// ---------------------------------------------------------------------------------------------
	// Distributions
	// ---------------------------------------------------------------------------------------------

	public double getDoTExpected()
	{
		double ret = 0;
		if (opts.isUsingSpecialAttack())
		{
			if (wearing(Arrays.asList("Bone claws", "Burning claws")) && !isImmuneToNormalBurns())
			{
				ret = Claws.burningClawDoT(getHitChance());
			}
			else if (wearing("Scorching bow") && !isImmuneToNormalBurns())
			{
				ret = monster.getAttributes().contains(MonsterAttribute.DEMON) ? 5 : 1;
			}
			else if (wearing("Arkan blade") && !isImmuneToNormalBurns())
			{
				ret = 10 * getHitChance();
			}
		}

		return ret;
	}

	public int getDoTMax()
	{
		int ret = 0;
		if (opts.isUsingSpecialAttack())
		{
			if (wearing(Arrays.asList("Bone claws", "Burning claws")) && !isImmuneToNormalBurns())
			{
				ret = 29;
			}
			else if (wearing("Scorching bow") && !isImmuneToNormalBurns())
			{
				ret = monster.getAttributes().contains(MonsterAttribute.DEMON) ? 5 : 1;
			}
			else if (wearing("Arkan blade") && !isImmuneToNormalBurns())
			{
				ret = 10;
			}
		}

		return ret;
	}

	public int getMax()
	{
		return getDistribution().getMax() + getDoTMax();
	}

	public double getExpectedDamage()
	{
		return getDistribution().getExpectedDamage() + getDoTExpected();
	}

	public AttackDistribution getDistribution()
	{
		if (memoizedDist == null)
		{
			memoizedDist = getDistributionImpl();
		}

		return memoizedDist;
	}

	private AttackDistribution getDistributionImpl()
	{
		AttackDistribution attackerDist = getAttackerDist();

		CombatStyleType styleType = styleType();
		if (opts.isUsingSpecialAttack() && wearing("Voidwaker"))
		{
			styleType = CombatStyleType.MAGIC;
		}

		return attackerDist.transform(applyNpcTransforms(styleType));
	}

	private AttackDistribution getAttackerDist()
	{
		List<MonsterAttribute> mattrs = monster.getAttributes();
		double acc = getHitChance();
		MinMax minMax = getMinAndMax();
		int min = minMax.getMin();
		int max = minMax.getMax();
		CombatStyleType style = styleType();

		if (max == 0)
		{
			return new AttackDistribution(Collections.singletonList(
				new HitDistribution(Collections.singletonList(
					new WeightedHit(1.0, Collections.singletonList(Hitsplat.INACCURATE))))));
		}

		// standard linear
		HitDistribution standardHitDist = HitDistribution.linear(acc, min, max);
		AttackDistribution dist = new AttackDistribution(Collections.singletonList(standardHitDist));

		// Monsters that always die in one hit no matter what
		if (Constants.ONE_HIT_MONSTERS.contains(monster.getId()))
		{
			return new AttackDistribution(Collections.singletonList(
				HitDistribution.single(1.0, Collections.singletonList(new Hitsplat(monster.getSkills().getHp())))));
		}

		if (wearing("Sunspear") && opts.isUsingSpecialAttack())
		{
			int effectDmg = CalcMath.applyFactor(max, Factor.of(7, 10));
			return new AttackDistribution(Collections.singletonList(
				HitDistribution.single(acc, Collections.singletonList(new Hitsplat(effectDmg)))));
		}

		if ("Respiratory system".equals(monster.getName()) && isUsingDemonbane())
		{
			return new AttackDistribution(Collections.singletonList(
				HitDistribution.single(acc, Collections.singletonList(new Hitsplat(monster.getSkills().getHp())))));
		}

		EquipmentPiece weapon = weapon();
		if (style == CombatStyleType.RANGED && wearing("Tonalztics of ralos")
			&& weapon != null && "Charged".equals(weapon.getVersion()))
		{
			// roll two independent hits
			if (!opts.isUsingSpecialAttack())
			{
				dist = new AttackDistribution(Arrays.asList(standardHitDist, standardHitDist));
			}
			else
			{
				// the defence reduction from the first hit applies to the second hit,
				// so we need a full subcalc with the new defence value to determine the dist
				MonsterInputs.DefenceReductions baseReductions = baseMonster.getInputs().getDefenceReductions();
				Monster loweredDefMonster = MonsterScaling.scaleMonster(baseMonster.toBuilder()
					.inputs(baseMonster.getInputs().toBuilder()
						.defenceReductions(baseReductions.toBuilder()
							.tonalztic(baseReductions.getTonalztic() + 1)
							.build())
						.build())
					.build());

				double loweredDefHitAccuracy = noInitSubCalc(player, loweredDefMonster, opts).getHitChance();

				HitDistribution loweredDefHitDist = HitDistribution.linear(loweredDefHitAccuracy, min, max);
				dist = dist.transform((firstHit) ->
				{
					HitDistribution firstHitDist = HitDistribution.single(1.0,
						Collections.singletonList(firstHit));
					HitDistribution secondHitDist = firstHit.isAccurate() ? loweredDefHitDist : standardHitDist;
					return firstHitDist.zip(secondHitDist);
				});
			}
		}

		if (isUsingMeleeStyle() && wearing("Crimson kisten") && opts.isUsingSpecialAttack())
		{
			HitDistribution effectDist = new HitDistribution(new ArrayList<>());
			effectDist.addHit(new WeightedHit(Math.pow(1 - acc, 4),
				Collections.singletonList(Hitsplat.INACCURATE)));

			for (int i = 0; i < 4; i++)
			{
				// chance that exactly (i+1) accuracy rolls passed
				double chanceThisProc = CalcMath.binomal(acc, i + 1, 4);

				// Jagex stated the max hit would be reduced by 1 if all four accuracy rolls passed
				// This is not implemented in the game.
				int effectMin = CalcMath.trunc((double) max * (70 + (i * 20)) / 100);
				int effectMax = CalcMath.trunc((double) max * (110 + (i * 20)) / 100);

				effectDist.addHits(
					HitDistribution.linear(1.0, effectMin, effectMax)
						.scaleProbability(chanceThisProc)
						.getHits());
			}

			dist = new AttackDistribution(Collections.singletonList(effectDist));
		}

		if (isUsingMeleeStyle() && wearing("Gadderhammer") && mattrs.contains(MonsterAttribute.SHADE))
		{
			List<WeightedHit> hits = new ArrayList<>();
			hits.addAll(standardHitDist.scaleProbability(0.95).scaleDamage(5, 4).getHits());
			hits.addAll(standardHitDist.scaleProbability(0.05).scaleDamage(2).getHits());
			dist = new AttackDistribution(Collections.singletonList(new HitDistribution(hits)));
		}

		if (style == CombatStyleType.RANGED && wearing("Dark bow"))
		{
			dist = new AttackDistribution(Arrays.asList(standardHitDist, standardHitDist));
			if (opts.isUsingSpecialAttack())
			{
				dist = dist.transform(HitTransformers.flatLimitTransformer(48, min));
			}
		}

		boolean accurateZeroApplicable = true;
		if (opts.isUsingSpecialAttack())
		{
			if (wearing("Dragon claws"))
			{
				accurateZeroApplicable = false;
				dist = Claws.dClawDist(acc, max);
			}
			else if (wearing(Arrays.asList("Bone claws", "Burning claws")))
			{
				accurateZeroApplicable = false;
				dist = Claws.burningClawSpec(acc, max);
			}
		}

		if (opts.isUsingSpecialAttack() && wearing(Arrays.asList("Dragon halberd", "Crystal halberd"))
			&& monster.getSize() > 1)
		{
			int secondHitAttackRoll = CalcMath.trunc((double) getMaxAttackRoll() * 3 / 4);
			double secondHitAcc = noInitSubCalc(player, monster,
				opts.toBuilder()
					.overrides(opts.getOverrides().toBuilder().attackRoll(secondHitAttackRoll).build())
					.build())
				.getHitChance();

			dist = new AttackDistribution(Arrays.asList(
				standardHitDist,
				HitDistribution.linear(secondHitAcc, min, max)));
		}

		// simple multi-hit specs
		if (opts.isUsingSpecialAttack())
		{
			int hitCount = 1;
			if (wearing(Arrays.asList("Dragon dagger", "Dragon knife", "Rosewood blowpipe")) || isWearingMsb())
			{
				hitCount = 2;
			}
			else if (wearing("Webweaver bow"))
			{
				hitCount = 4;
			}

			if (hitCount != 1)
			{
				List<HitDistribution> hits = new ArrayList<>();
				for (int i = 0; i < hitCount; i++)
				{
					hits.add(standardHitDist);
				}
				dist = new AttackDistribution(hits);
			}
		}

		if (opts.isUsingSpecialAttack() && wearing("Abyssal dagger"))
		{
			HitDistribution secondHit = HitDistribution.linear(1.0, min, max);
			dist = dist.transform(
				(h) -> new HitDistribution(Collections.singletonList(
					new WeightedHit(1.0, Collections.singletonList(h)))).zip(secondHit),
				new TransformOpts(false));
		}

		if (opts.isUsingSpecialAttack() && wearing("Saradomin sword"))
		{
			HitDistribution magicHit = HitDistribution.linear(1.0, 1, 16);
			dist = dist.transform((h) ->
			{
				if (h.isAccurate() && !Constants.IMMUNE_TO_MAGIC_DAMAGE_NPC_IDS.contains(monster.getId()))
				{
					return new HitDistribution(Collections.singletonList(
						new WeightedHit(1.0, Collections.singletonList(h)))).zip(magicHit);
				}
				return new HitDistribution(Collections.singletonList(
					new WeightedHit(1.0, Arrays.asList(h, Hitsplat.INACCURATE))));
			});
		}

		if (opts.isUsingSpecialAttack() && wearing("Granite hammer"))
		{
			dist = dist.transform(HitTransformers.flatAddTransformer(5), new TransformOpts(true));
		}

		// the purging staff spec has no dist effect here (todo(wgs) upstream)

		if (isUsingMeleeStyle() && isWearingVeracs())
		{
			List<WeightedHit> hits = new ArrayList<>();
			hits.addAll(standardHitDist.scaleProbability(0.75).getHits());
			hits.addAll(HitDistribution.linear(1.0, 1, max + 1).scaleProbability(0.25).getHits());
			dist = new AttackDistribution(Collections.singletonList(new HitDistribution(hits)));
		}

		if (style == CombatStyleType.RANGED && isWearingKarils())
		{
			// 25% chance to deal a second hitsplat at half the damage of the first (flat, not rolled)
			dist = dist.transform(
				(h) -> new HitDistribution(Arrays.asList(
					new WeightedHit(0.75, Collections.singletonList(h)),
					new WeightedHit(0.25, Arrays.asList(h, new Hitsplat(CalcMath.trunc((double) h.getDamage() / 2))))
				)),
				new TransformOpts(false));
		}

		boolean isMaggotKingMeleePunish = isUsingMeleeStyle()
			&& Constants.MAGGOT_KING_ID.contains(monster.getId())
			&& "Melee Punish".equals(monster.getInputs().getPhase());
		double firstHitAcc = acc;
		if (isMaggotKingMeleePunish)
		{
			int normalDefRoll = getNPCDefenceRoll();
			int reducedDefRoll = styleType() == CombatStyleType.CRUSH
				? CalcMath.trunc((double) normalDefRoll * 15 / 100)
				: normalDefRoll;
			firstHitAcc = noInitSubCalc(player, monster,
				opts.toBuilder()
					.overrides(opts.getOverrides().toBuilder().defenceRoll(reducedDefRoll).build())
					.build())
				.getHitChance();
		}

		if (isUsingMeleeStyle() && isWearingScythe())
		{
			List<HitDistribution> hits = new ArrayList<>();
			for (int i = 0; i < Math.min(Math.max(monster.getSize(), 1), 3); i++)
			{
				int splatMax = CalcMath.trunc((double) max / Math.pow(2, i));

				if (isMaggotKingMeleePunish && i == 0)
				{
					splatMax = CalcMath.applyFactor(splatMax, Factor.of(150, 100));
				}

				double splatAcc = i == 0 ? firstHitAcc : acc;

				hits.add(HitDistribution.linear(splatAcc, min, Math.max(min, splatMax)));
			}
			dist = new AttackDistribution(hits);
		}

		if (isUsingMeleeStyle() && wearing("Dual macuahuitl"))
		{
			int firstMax = CalcMath.trunc((double) max / 2);
			int secondMax = max - firstMax;
			if (isMaggotKingMeleePunish)
			{
				firstMax = CalcMath.applyFactor(firstMax, Factor.of(150, 100));
			}
			AttackDistribution firstHit = new AttackDistribution(Collections.singletonList(
				HitDistribution.linear(firstHitAcc, min, Math.max(min, firstMax))));
			HitDistribution secondHit = HitDistribution.linear(acc, min, Math.max(min, secondMax));
			dist = firstHit.transform((h) ->
			{
				if (h.isAccurate())
				{
					return new HitDistribution(Collections.singletonList(
						new WeightedHit(1.0, Collections.singletonList(h)))).zip(secondHit);
				}
				return new HitDistribution(Collections.singletonList(
					new WeightedHit(1.0, Arrays.asList(h, Hitsplat.INACCURATE))));
			});
		}

		if (isUsingMeleeStyle() && isWearingTwoHitWeapon())
		{
			int firstMax = CalcMath.trunc((double) max / 2);
			int secondMax = max - firstMax;
			if (isMaggotKingMeleePunish)
			{
				firstMax = CalcMath.applyFactor(firstMax, Factor.of(150, 100));
			}
			dist = new AttackDistribution(Arrays.asList(
				HitDistribution.linear(firstHitAcc, min, Math.max(min, firstMax)),
				HitDistribution.linear(firstHitAcc, min, Math.max(min, secondMax))
			));
		}

		if (isUsingMeleeStyle() && isWearingKeris() && mattrs.contains(MonsterAttribute.KALPHITE))
		{
			List<WeightedHit> hits = new ArrayList<>();
			hits.addAll(standardHitDist.scaleProbability(50.0 / 51.0).getHits());
			hits.addAll(standardHitDist.scaleProbability(1.0 / 51.0).scaleDamage(3).getHits());
			dist = new AttackDistribution(Collections.singletonList(new HitDistribution(hits)));
		}

		if (isUsingMeleeStyle() && Constants.GUARDIAN_IDS.contains(monster.getId())
			&& weaponCategoryIs(EquipmentCategory.PICKAXE))
		{
			// just the level required to wield
			Map<String, Integer> pickBonuses = new HashMap<>();
			pickBonuses.put("Bronze pickaxe", 1);
			pickBonuses.put("Iron pickaxe", 1);
			pickBonuses.put("Steel pickaxe", 6);
			pickBonuses.put("Black pickaxe", 11);
			pickBonuses.put("Mithril pickaxe", 21);
			pickBonuses.put("Adamant pickaxe", 31);
			pickBonuses.put("Rune pickaxe", 41);
			pickBonuses.put("Gilded pickaxe", 41);
			// crystal is same as dpick

			Integer bonus = pickBonuses.get(weapon().getName());
			int pickBonus = bonus == null ? 61 : bonus; // there's a lot of dpick variants
			int factor = 50 + player.getSkills().getMining() + pickBonus;
			int divisor = 150;

			dist = dist.transform(HitTransformers.multiplyTransformer(factor, divisor));
		}

		if (player.getBuffs().isMarkOfDarknessSpell() && player.getSpell() != null
			&& player.getSpell().getName().contains("Demonbane") && mattrs.contains(MonsterAttribute.DEMON))
		{
			int demonbaneFactor = wearing("Purging staff") ? 50 : 25;
			dist = dist.transform((h) -> HitDistribution.single(1.0, Collections.singletonList(new Hitsplat(
				h.getDamage() + CalcMath.trunc(
					(double) CalcMath.trunc((double) h.getDamage() * demonbaneFactor / 100)
						* demonbaneVulnerability() / 100),
				h.isAccurate()))));
		}

		if (styleType() == CombatStyleType.MAGIC && isWearingAhrims())
		{
			dist = dist.transform((h) -> new HitDistribution(Arrays.asList(
				new WeightedHit(0.75, Collections.singletonList(h)),
				new WeightedHit(0.25, Collections.singletonList(
					new Hitsplat(CalcMath.trunc((double) h.getDamage() * 13 / 10), h.isAccurate())))
			)));
		}

		if (tdUnshieldedBonusApplies())
		{
			int bonusDmg = Math.max(0, getAttackSpeed() * getAttackSpeed() - 16);
			dist = dist.transform(HitTransformers.flatAddTransformer(bonusDmg), new TransformOpts(false));
		}

		if (isUsingMeleeStyle() && isWearingDharok())
		{
			int newMax = player.getSkills().getHp();
			int curr = player.getSkills().getHp() + player.getBoosts().getHp();
			dist = dist.scaleDamage(10000 + (newMax - curr) * newMax, 10000);
		}

		if (isUsingMeleeStyle() && isWearingBerserkerNecklace() && isWearingTzhaarWeapon())
		{
			dist = dist.scaleDamage(6, 5);
		}

		if (!(isWearingScythe() || isWearingTwoHitWeapon() || wearing("Dual macuahuitl")) && isMaggotKingMeleePunish)
		{
			dist = new AttackDistribution(Collections.singletonList(
				HitDistribution.linear(firstHitAcc, min, Math.max(min, max))));
			dist = dist.scaleDamage(150, 100);
		}

		// all this vampyre stuff was tested methodically by @jmyaeger, many thanks!
		if (isVampyre(mattrs))
		{
			// efaritay's bonus only applies if we can deal uncapped damage
			boolean efaritay = wearing("Efaritay's aid");

			if (wearing(Arrays.asList("Blisterwood flail", "Hallowed flail", "Blisterwood stake")))
			{
				dist = efaritay ? dist.scaleDamage(11, 10) : dist;
				dist = dist.scaleDamage(125, 100);
			}
			else if (wearing("Sunspear"))
			{
				dist = efaritay ? dist.scaleDamage(11, 10) : dist;
				dist = dist.scaleDamage(150, 100);
			}
			else if (wearing("Blisterwood sickle"))
			{
				dist = efaritay ? dist.scaleDamage(11, 10) : dist;
				dist = dist.scaleDamage(23, 20);
			}
			else if (wearing("Ivandis flail"))
			{
				dist = efaritay ? dist.scaleDamage(11, 10) : dist;
				dist = dist.scaleDamage(6, 5);
			}
			else if (wearing("Rod of ivandis") && !mattrs.contains(MonsterAttribute.VAMPYRE_3))
			{
				dist = efaritay ? dist.scaleDamage(11, 10) : dist;
				dist = dist.scaleDamage(11, 10);
			}
			else if (isWearingSilverWeapon() && mattrs.contains(MonsterAttribute.VAMPYRE_1))
			{
				dist = efaritay ? dist.scaleDamage(11, 10) : dist;
				dist = dist.scaleDamage(11, 10);
			}
		}

		// bolt effects
		Bolts.BoltContext boltContext = new Bolts.BoltContext(
			player.getSkills().getRanged() + player.getBoosts().getRanged(),
			max,
			wearing("Zaryte crossbow"),
			opts.isUsingSpecialAttack(),
			player.getBuffs().isKandarinDiary(),
			monster
		);
		if (styleType() == CombatStyleType.RANGED && weaponCategoryIs(EquipmentCategory.CROSSBOW))
		{
			if (wearing(Arrays.asList("Opal bolts (e)", "Opal dragon bolts (e)")))
			{
				dist = dist.transform(Bolts.opalBolts(boltContext));
			}
			else if (wearing(Arrays.asList("Pearl bolts (e)", "Pearl dragon bolts (e)")))
			{
				dist = dist.transform(Bolts.pearlBolts(boltContext));
			}
			else if (wearing(Arrays.asList("Diamond bolts (e)", "Diamond dragon bolts (e)")))
			{
				dist = dist.transform(Bolts.diamondBolts(boltContext));
			}
			else if (wearing(Arrays.asList("Dragonstone bolts (e)", "Dragonstone dragon bolts (e)")))
			{
				dist = dist.transform(Bolts.dragonstoneBolts(boltContext));
			}
			else if (wearing(Arrays.asList("Onyx bolts (e)", "Onyx dragon bolts (e)"))
				&& !mattrs.contains(MonsterAttribute.UNDEAD))
			{
				dist = dist.transform(Bolts.onyxBolts(boltContext));
			}
		}

		if (player.getSpell() != null && player.getSpell().getMaxHit() == 0)
		{
			// don't raise things like bind
			accurateZeroApplicable = false;
		}

		EquipmentPiece ammo = ammo();
		if (styleType() == CombatStyleType.RANGED && ammo != null && ammo.getName() != null
			&& ammo.getName().contains("Seeking"))
		{
			dist = dist.transform(
				(h) -> HitDistribution.single(1.0, Collections.singletonList(
					new Hitsplat(Math.max(h.getDamage(), 3), h.isAccurate()))),
				new TransformOpts(false));
		}

		// raise accurate 0s to 1
		if (accurateZeroApplicable)
		{
			dist = dist.transform(
				(h) -> HitDistribution.single(1.0, Collections.singletonList(
					new Hitsplat(Math.max(h.getDamage(), 1)))),
				new TransformOpts(false));
		}

		if (styleType() == CombatStyleType.MAGIC && player.getSpell() != null
			&& "standard".equals(player.getSpell().getSpellbook()))
		{
			String spellName = player.getSpell().getName();
			boolean twinflameCompat = spellName.contains("Bolt") || spellName.contains("Blast")
				|| spellName.contains("Wave");
			if (wearing("Twinflame staff") && twinflameCompat)
			{
				dist = dist.transform((h) -> HitDistribution.single(1.0, Arrays.asList(
					new Hitsplat(h.getDamage()),
					new Hitsplat(CalcMath.trunc((double) h.getDamage() * 4 / 10))
				)));
			}
		}

		// we apply corp earlier than other limiters, and rubies later than other bolts,
		// since corp takes full ruby bolt effect damage but reduced damage from bolts otherwise
		if ("Corporeal Beast".equals(monster.getName()) && !isWearingCorpbaneWeapon())
		{
			dist = dist.transform(HitTransformers.divisionTransformer(2));
		}

		if (styleType() == CombatStyleType.RANGED && weaponCategoryIs(EquipmentCategory.CROSSBOW))
		{
			int currentHp = player.getSkills().getHp() + player.getBoosts().getHp();
			if (wearing(Arrays.asList("Ruby bolts (e)", "Ruby dragon bolts (e)")) && currentHp >= 10)
			{
				dist = dist.transform(Bolts.rubyBolts(boltContext));
			}
		}

		if (styleType() == CombatStyleType.MAGIC && wearing("Brimstone ring")
			&& opts.getOverrides().getDefenceRoll() == null)
		{
			double effectChance = 0.25;
			int effectDef = CalcMath.applyFactor(getNPCDefenceRoll(), Factor.of(9, 10));
			AttackDistribution effectDist = noInitSubCalc(player, monster,
				opts.toBuilder()
					.loadoutName(opts.getLoadoutName() + "/brimstone")
					.overrides(opts.getOverrides().toBuilder().defenceRoll(effectDef).build())
					.build())
				.getAttackerDist();

			List<HitDistribution> zippedDists = new ArrayList<>();
			for (int i = 0; i < dist.getDists().size(); i++)
			{
				List<WeightedHit> hits = new ArrayList<>();
				hits.addAll(dist.getDists().get(i).scaleProbability(1 - effectChance).getHits());
				hits.addAll(effectDist.getDists().get(i).scaleProbability(effectChance).getHits());
				zippedDists.add(new HitDistribution(hits));
			}
			dist = new AttackDistribution(zippedDists).flatten();
		}

		// monsters that are always max hit no matter what
		if ((styleType() == CombatStyleType.MAGIC
			&& Constants.ALWAYS_MAX_HIT_MONSTERS_MAGIC.contains(monster.getId()))
			|| (isUsingMeleeStyle() && Constants.ALWAYS_MAX_HIT_MONSTERS_MELEE.contains(monster.getId()))
			|| (styleType() == CombatStyleType.RANGED
			&& Constants.ALWAYS_MAX_HIT_MONSTERS_RANGED.contains(monster.getId())))
		{
			if (Constants.YAMA_VOID_FLARE_IDS.contains(monster.getId())
				&& player.getBuffs().isMarkOfDarknessSpell()
				&& player.getSpell() != null && player.getSpell().getName().contains("Demonbane"))
			{
				int demonbaneFactor = wearing("Purging staff") ? 50 : 25;
				int dmg = max + CalcMath.trunc(
					(double) CalcMath.trunc((double) max * demonbaneFactor / 100) * demonbaneVulnerability() / 100);
				return new AttackDistribution(Collections.singletonList(
					HitDistribution.single(1.0, Collections.singletonList(new Hitsplat(dmg)))));
			}

			return new AttackDistribution(Collections.singletonList(
				HitDistribution.single(1.0, Collections.singletonList(new Hitsplat(dist.getMax())))));
		}

		return dist;
	}

	public HitTransformer applyNpcTransforms(CombatStyleType styleType)
	{
		HitTransformer cached = styleType == null ? nullStyleTransformCache : npcTransformCache.get(styleType);
		if (cached != null)
		{
			return cached;
		}

		if (isImmune(styleType))
		{
			HitTransformer immune = (h) -> HitDistribution.single(1.0,
				Collections.singletonList(Hitsplat.INACCURATE));
			cacheTransform(styleType, immune);
			return immune;
		}

		List<MonsterAttribute> mattrs = monster.getAttributes();
		List<HitTransformer> effects = new ArrayList<>();
		List<TransformOpts> effectOpts = new ArrayList<>();

		if ("Zulrah".equals(monster.getName()))
		{
			addEffect(effects, effectOpts, HitTransformers.cappedRerollTransformer(50, 5, 45), null);
		}
		if ("Fragment of Seren".equals(monster.getName()))
		{
			addEffect(effects, effectOpts, HitTransformers.linearMinTransformer(2, 22), null);
		}
		if (("Kraken".equals(monster.getName()) || "Cave kraken".equals(monster.getName()))
			&& styleType == CombatStyleType.RANGED)
		{
			addEffect(effects, effectOpts, HitTransformers.divisionTransformer(7, 1), null);
		}
		if (Constants.VERZIK_P1_IDS.contains(monster.getId()) && !wearing("Dawnbringer"))
		{
			int limit = isUsingMeleeStyle() ? 10 : 3;
			addEffect(effects, effectOpts, HitTransformers.linearMinTransformer(limit), null);
		}
		if (Constants.TEKTON_IDS.contains(monster.getId()) && styleType == CombatStyleType.MAGIC)
		{
			addEffect(effects, effectOpts, HitTransformers.divisionTransformer(5, 1), null);
		}
		if (Constants.GLOWING_CRYSTAL_IDS.contains(monster.getId()) && styleType == CombatStyleType.MAGIC)
		{
			addEffect(effects, effectOpts, HitTransformers.divisionTransformer(3), null);
		}
		if ((Constants.OLM_MELEE_HAND_IDS.contains(monster.getId())
			|| Constants.OLM_HEAD_IDS.contains(monster.getId())) && styleType == CombatStyleType.MAGIC)
		{
			addEffect(effects, effectOpts, HitTransformers.divisionTransformer(3), null);
		}
		if ((Constants.OLM_MAGE_HAND_IDS.contains(monster.getId())
			|| Constants.OLM_MELEE_HAND_IDS.contains(monster.getId())) && styleType == CombatStyleType.RANGED)
		{
			addEffect(effects, effectOpts, HitTransformers.divisionTransformer(3), null);
		}
		if (Constants.ICE_DEMON_IDS.contains(monster.getId()) && !"fire".equals(getSpellement()) && !isUsingDemonbane())
		{
			addEffect(effects, effectOpts, HitTransformers.divisionTransformer(3), null);
		}
		if ("Slagilith".equals(monster.getName()) && !weaponCategoryIs(EquipmentCategory.PICKAXE))
		{
			addEffect(effects, effectOpts, HitTransformers.divisionTransformer(3), null);
		}
		if (Constants.NIGHTMARE_TOTEM_IDS.contains(monster.getId()) && styleType == CombatStyleType.MAGIC)
		{
			addEffect(effects, effectOpts, HitTransformers.multiplyTransformer(2), null);
		}
		if (Arrays.asList("Slash Bash", "Zogre", "Skogre").contains(monster.getName()))
		{
			EquipmentPiece ammo = ammo();
			EquipmentPiece weapon = weapon();
			if (player.getSpell() != null && "Crumble Undead".equals(player.getSpell().getName()))
			{
				addEffect(effects, effectOpts, HitTransformers.divisionTransformer(2), null);
			}
			else if (styleType() != CombatStyleType.RANGED
				|| ammo == null || ammo.getName() == null || !ammo.getName().contains(" brutal")
				|| weapon == null || !"Comp ogre bow".equals(weapon.getName()))
			{
				addEffect(effects, effectOpts, HitTransformers.divisionTransformer(4), null);
			}
		}
		if (Constants.BA_ATTACKER_MONSTERS.contains(monster.getId()) && player.getBuffs().getBaAttackerLevel() != 0)
		{
			addEffect(effects, effectOpts,
				HitTransformers.flatAddTransformer(player.getBuffs().getBaAttackerLevel()),
				new TransformOpts(true));
		}
		if ("Tormented Demon".equals(monster.getName()))
		{
			if (!"Unshielded".equals(monster.getInputs().getPhase()) && !isUsingDemonbane() && !isUsingAbyssal())
			{
				// 20% damage reduction when not using demonbane or abyssal
				addEffect(effects, effectOpts, HitTransformers.multiplyTransformer(4, 5, 1), null);
			}
		}
		if (mattrs.contains(MonsterAttribute.VAMPYRE_2))
		{
			if (!wearingVampyrebane(MonsterAttribute.VAMPYRE_2) && wearing("Efaritay's aid"))
			{
				addEffect(effects, effectOpts, HitTransformers.divisionTransformer(2), null);
			}
			else if (isWearingSilverWeapon())
			{
				addEffect(effects, effectOpts, HitTransformers.flatLimitTransformer(10), null);
			}
		}
		if (Constants.HUEYCOATL_TAIL_IDS.contains(monster.getId()))
		{
			boolean crush = styleType == CombatStyleType.CRUSH
				&& player.getOffensive().getCrush() > player.getOffensive().getSlash()
				&& player.getOffensive().getCrush() > player.getOffensive().getStab();
			boolean earth = "earth".equals(getSpellement());

			// crush and earth spells have a higher limiter
			addEffect(effects, effectOpts, HitTransformers.linearMinTransformer((crush || earth) ? 9 : 4), null);

			// and crush also gets misses turned into 1s
			if (crush)
			{
				addEffect(effects, effectOpts, (h) ->
				{
					if (h.getDamage() > 0)
					{
						return HitDistribution.single(1.0, Collections.singletonList(h));
					}
					return HitDistribution.single(1.0, Collections.singletonList(new Hitsplat(1)));
				}, null);
			}
		}
		if (Constants.HUEYCOATL_PHASE_IDS.contains(monster.getId())
			&& "With Pillar".equals(monster.getInputs().getPhase()))
		{
			addEffect(effects, effectOpts, HitTransformers.multiplyTransformer(13, 10), null);
		}

		if (Constants.ABYSSAL_SIRE_TRANSITION_IDS.contains(monster.getId())
			&& "Transition".equals(monster.getInputs().getPhase()))
		{
			addEffect(effects, effectOpts, HitTransformers.divisionTransformer(2), null);
		}

		int flatArmour = monster.getDefensive().getFlatArmour();
		if (flatArmour != 0 && styleType != CombatStyleType.MAGIC)
		{
			addEffect(effects, effectOpts, HitTransformers.flatAddTransformer(-flatArmour), new TransformOpts(false));
		}

		HitTransformer transformer = (hitsplat) ->
		{
			HitDistribution d = HitDistribution.single(1.0, Collections.singletonList(hitsplat));
			for (int i = 0; i < effects.size(); i++)
			{
				TransformOpts o = effectOpts.get(i);
				d = d.wideTransform(effects.get(i), o == null ? TransformOpts.DEFAULT_TRANSFORM_OPTS : o);
			}

			return d.flatten();
		};
		cacheTransform(styleType, transformer);
		return transformer;
	}

	private void addEffect(List<HitTransformer> effects, List<TransformOpts> effectOpts, HitTransformer t,
		TransformOpts o)
	{
		effects.add(t);
		effectOpts.add(o);
	}

	private void cacheTransform(CombatStyleType styleType, HitTransformer transformer)
	{
		if (styleType == null)
		{
			nullStyleTransformCache = transformer;
		}
		else
		{
			npcTransformCache.put(styleType, transformer);
		}
	}

	public boolean isImmune(CombatStyleType styleType)
	{
		int monsterId = monster.getId();
		List<MonsterAttribute> mattrs = monster.getAttributes();

		if (Constants.IMMUNE_TO_MAGIC_DAMAGE_NPC_IDS.contains(monsterId) && styleType == CombatStyleType.MAGIC)
		{
			return true;
		}
		if (Constants.IMMUNE_TO_RANGED_DAMAGE_NPC_IDS.contains(monsterId) && styleType == CombatStyleType.RANGED)
		{
			return true;
		}
		if (Constants.IMMUNE_TO_MELEE_DAMAGE_NPC_IDS.contains(monsterId) && isUsingMeleeStyle())
		{
			if (Constants.ZULRAH_IDS.contains(monsterId) && weaponCategoryIs(EquipmentCategory.POLEARM))
			{
				return false;
			}
			return true;
		}
		if (mattrs.contains(MonsterAttribute.FLYING) && isUsingMeleeStyle())
		{
			// Vespula is immune to melee despite flying attribute.
			if (Constants.VESPULA_IDS.contains(monsterId))
			{
				return true;
			}
			if (weaponCategoryIs(EquipmentCategory.POLEARM) || weaponCategoryIs(EquipmentCategory.SALAMANDER))
			{
				return false;
			}
			return true;
		}
		if (Constants.IMMUNE_TO_NON_SALAMANDER_MELEE_DAMAGE_NPC_IDS.contains(monsterId)
			&& isUsingMeleeStyle()
			&& !weaponCategoryIs(EquipmentCategory.SALAMANDER))
		{
			return true;
		}
		if (mattrs.contains(MonsterAttribute.VAMPYRE_3) && !wearingVampyrebane(MonsterAttribute.VAMPYRE_3))
		{
			return true;
		}
		if (mattrs.contains(MonsterAttribute.VAMPYRE_2) && !wearingVampyrebane(MonsterAttribute.VAMPYRE_2)
			&& !wearing("Efaritay's aid") && !isWearingSilverWeapon())
		{
			return true;
		}
		if (Constants.GUARDIAN_IDS.contains(monsterId)
			&& (!isUsingMeleeStyle() || !weaponCategoryIs(EquipmentCategory.PICKAXE)))
		{
			return true;
		}
		if (mattrs.contains(MonsterAttribute.LEAFY) && !isWearingLeafBladedWeapon())
		{
			return true;
		}
		if (Constants.DOOM_OF_MOKHAIOTL_IDS.contains(monsterId)
			&& "Shielded".equals(monster.getInputs().getPhase()) && !isUsingDemonbane())
		{
			return true;
		}
		if (!mattrs.contains(MonsterAttribute.RAT) && isWearingRatBoneWeapon())
		{
			return true;
		}

		EquipmentPiece ammo = ammo();
		if ("Fire Warrior of Lesarkus".equals(monster.getName())
			&& (styleType != CombatStyleType.RANGED || ammo == null || !"Ice arrows".equals(ammo.getName())))
		{
			return true;
		}
		if ("Fareed".equals(monster.getName()))
		{
			if ((styleType == CombatStyleType.MAGIC && !"water".equals(getSpellement()))
				|| (styleType == CombatStyleType.RANGED
				&& (ammo == null || ammo.getName() == null || !ammo.getName().contains("arrow"))))
			{
				return true;
			}
		}
		// Eclipse moon clone is immune to non-melee attacks
		if (Constants.ECLIPSE_MOON_IDS.contains(monster.getId()) && "Clone".equals(monster.getVersion())
			&& !isUsingMeleeStyle())
		{
			return true;
		}

		return false;
	}

	// ---------------------------------------------------------------------------------------------
	// Speed and dps
	// ---------------------------------------------------------------------------------------------

	/**
	 * Returns the player's attack speed.
	 *
	 * <p>The TS source reads player.attackSpeed and only falls back to calculateAttackSpeed when it is
	 * undefined; the Java field is a primitive int, so it is always used as-is.
	 */
	public int getAttackSpeed()
	{
		return player.getAttackSpeed();
	}

	public double getExpectedAttackSpeed()
	{
		if (isWearingBloodMoonSet())
		{
			double acc = getHitChance();
			double procChance = opts.isUsingSpecialAttack()
				? 1 - Math.pow(1 - acc, 2) // always if hit
				: (acc / 3) + ((acc * acc) * 2 / 9); // 1/3 per hit
			return getAttackSpeed() - procChance;
		}

		if (tdUnshieldedBonusApplies())
		{
			return getAttackSpeed() - 1;
		}

		if (opts.isUsingSpecialAttack() && wearing("Eye of ayak"))
		{
			return 5;
		}

		return getAttackSpeed();
	}

	/**
	 * Returns the expected damage per tick, based on the player's attack speed.
	 */
	public double getDpt()
	{
		return getExpectedDamage() / getExpectedAttackSpeed();
	}

	/**
	 * Returns the damage-per-second calculation.
	 */
	public double getDps()
	{
		return getDpt() / Constants.SECONDS_PER_TICK;
	}

	// TODO(v2): TTK - getHtk / getTtk / getTtkDistribution / distAtHp / getWeaponDelayProvider are omitted.

	public double getSpecDps()
	{
		if (wearing("Soulreaper axe"))
		{
			// assumes using spec every time you reach the current stack count
			double ticksPerSpec = (double) getAttackSpeed() * player.getBuffs().getSoulreaperStacks();
			return getDps() * getExpectedAttackSpeed() / ticksPerSpec;
		}

		Integer specCost = getSpecCost();
		if (specCost == null || specCost == 0)
		{
			// expected a spec cost for this weapon but none was provided
			return 0;
		}

		double ticksToRegen = wearing("Lightbearer") ? 25 : 50;
		double ticksPerSpec = specCost * (ticksToRegen / 10);
		return getDps() * getExpectedAttackSpeed() / ticksPerSpec;
	}

	/**
	 * The special attack energy cost of the equipped weapon, or null when it has no supported spec.
	 */
	public Integer getSpecCost()
	{
		EquipmentPiece weapon = weapon();
		if (weapon == null || weapon.getName() == null)
		{
			return null;
		}

		return Equipment.WEAPON_SPEC_COSTS.get(weapon.getName());
	}

	public FeatureStatus isSpecSupported()
	{
		EquipmentPiece weapon = weapon();
		String weaponName = weapon == null ? null : weapon.getName();
		if (weaponName == null)
		{
			return FeatureStatus.NOT_APPLICABLE;
		}

		if (wearing("Dual macuahuitl") && !isWearingBloodMoonSet())
		{
			return FeatureStatus.NOT_APPLICABLE;
		}
		if (wearing("Soulreaper axe"))
		{
			return player.getBuffs().getSoulreaperStacks() == 0
				? FeatureStatus.NOT_APPLICABLE
				: FeatureStatus.IMPLEMENTED;
		}
		if (wearing("Brine sabre"))
		{
			return Constants.UNDERWATER_MONSTERS.contains(monster.getId())
				? FeatureStatus.IMPLEMENTED
				: FeatureStatus.NOT_APPLICABLE;
		}

		if (PARTIALLY_IMPLEMENTED_SPECS.contains(weaponName))
		{
			return FeatureStatus.PARTIALLY_IMPLEMENTED;
		}

		if (getSpecCost() != null)
		{
			return FeatureStatus.IMPLEMENTED;
		}

		if (UNIMPLEMENTED_SPECS.contains(weaponName))
		{
			return FeatureStatus.UNIMPLEMENTED;
		}

		return FeatureStatus.NOT_APPLICABLE;
	}

	/**
	 * A calculator for this loadout's special attack, or null when the weapon has no supported spec.
	 */
	public PlayerVsNpcCalc getSpecCalc()
	{
		switch (isSpecSupported())
		{
			case IMPLEMENTED:
			case PARTIALLY_IMPLEMENTED:
				return new PlayerVsNpcCalc(player, baseMonster, opts.toBuilder()
					.loadoutName(opts.getLoadoutName() + "/spec")
					.usingSpecialAttack(true)
					.build());

			default:
				return null;
		}
	}

	// ---------------------------------------------------------------------------------------------
	// Helpers
	// ---------------------------------------------------------------------------------------------

	/**
	 * A computational shortcut for internal class use only.
	 */
	private PlayerVsNpcCalc noInitSubCalc(Player p, Monster m, CalcOpts subOpts)
	{
		PlayerVsNpcCalc subCalc = new PlayerVsNpcCalc(p, m, subOpts.toBuilder().noInit(true).build());
		subCalc.baseMonster = this.baseMonster;
		subCalc.allEquippedItems = this.allEquippedItems;

		return subCalc;
	}

	/**
	 * @param weaponDemonbane as a percent out of 100
	 */
	private Factor demonbaneFactor(int weaponDemonbane)
	{
		Integer vulnerability = monster.getInputs().getDemonbaneVulnerability();
		int vuln = vulnerability == null ? 100 : vulnerability;
		int percent = CalcMath.applyFactor(weaponDemonbane, Factor.of(vuln, 100));
		return Factor.of(percent, 100);
	}

	private static int tbowScaling(int current, int magic, boolean accuracyMode)
	{
		int factor = accuracyMode ? 10 : 14;
		int base = accuracyMode ? 140 : 250;

		int t2 = CalcMath.trunc((3.0 * magic - factor) / 100);
		int t3 = CalcMath.trunc(Math.pow(CalcMath.trunc(3.0 * magic / 10) - (10.0 * factor), 2) / 100);

		int bonus = base + t2 - t3;
		return CalcMath.trunc((double) current * bonus / 100);
	}

	/**
	 * Port of applyP2WardensDamageModifier: the TS signature destructures [, max] and ignores the min.
	 */
	private MinMax applyP2WardensDamageModifier(int max)
	{
		// 1/3 of enemy defence is removed from accuracy
		int reducedNpcDefence = CalcMath.trunc((double) getNPCDefenceRoll() / 3);
		int accuracyDelta = Math.max(getMaxAttackRoll() - reducedNpcDefence, 0);

		// remaining accuracy provides a % dmg modifier from 15% - 40% based on lerp from 0 to 42k MAR
		int modifier = Math.max(Math.min(CalcMath.iLerp(15, 40, 0, 42_000, accuracyDelta), 40), 15);

		int maxPctRange = 20;
		return MinMax.of(
			// these apply the % separately
			CalcMath.trunc((double) max * modifier / 100),
			CalcMath.trunc((double) max * (modifier + maxPctRange) / 100)
		);
	}

	private boolean isSunspearFinisher()
	{
		int max = getMinAndMax().getMax();
		return wearing("Sunspear")
			&& opts.isUsingSpecialAttack()
			&& monster.getInputs().getMonsterCurrentHp() <= CalcMath.trunc((double) max * 7 / 10);
	}

	private String getSpellement()
	{
		Spell spell = player.getSpell();
		if (spell == null)
		{
			return null;
		}

		return spell.getElement();
	}

	private Monster.Weakness getMonsterWeakness()
	{
		String spellement = getSpellement();
		Monster.Weakness baseWeakness = monster.getWeakness();
		if (spellement == null)
		{
			return null;
		}

		boolean usingRightSpell = baseWeakness != null && spellement.equals(baseWeakness.getElement());
		int baseSeverity = usingRightSpell ? baseWeakness.getSeverity() : 0;
		return new Monster.Weakness(spellement, baseSeverity);
	}

	private static boolean isVampyre(List<MonsterAttribute> mattrs)
	{
		for (MonsterAttribute a : mattrs)
		{
			if (a.isVampyre())
			{
				return true;
			}
		}
		return false;
	}

	private int countEquipped(List<String> items)
	{
		int count = 0;
		for (String item : allEquippedItems)
		{
			if (items.contains(item))
			{
				count++;
			}
		}
		return count;
	}

	/**
	 * Port of trackMaxHitFromEffective: Math.trunc((effectiveLevel * gearBonus + 320) / 640).
	 */
	private static int maxHitFromEffective(int effectiveLevel, int gearBonus)
	{
		return CalcMath.trunc(((double) effectiveLevel * gearBonus + 320) / 640);
	}

	/**
	 * Port of trackAddFactor: base + Math.trunc(base * f[0] / f[1]).
	 */
	private static int addFactor(int base, Factor factor)
	{
		return base + CalcMath.applyFactor(base, factor);
	}
}
