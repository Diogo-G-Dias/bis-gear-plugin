package com.github.diogogdias.bisfinder.calc;

import com.github.diogogdias.bisfinder.calc.model.BurnImmunity;
import com.github.diogogdias.bisfinder.calc.model.CombatStyleStance;
import com.github.diogogdias.bisfinder.calc.model.CombatStyleType;
import com.github.diogogdias.bisfinder.calc.model.EquipmentCategory;
import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.MonsterAttribute;
import com.github.diogogdias.bisfinder.calc.model.MonsterInputs;
import com.github.diogogdias.bisfinder.calc.model.Player;
import com.github.diogogdias.bisfinder.calc.model.PlayerCombatStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Port of osrs-dps-calc's src/lib/BaseCalc.ts.
 *
 * <p>The CalcDetails instrumentation (track / trackFactor / trackAdd / ...) is not ported: every call
 * site is replaced by the plain arithmetic it wrapped, in the same order and with the same truncation.
 */
public abstract class BaseCalc
{
	protected final CalcOpts opts;

	/**
	 * The player that we're using for this calculation.
	 */
	protected Player player;

	/**
	 * The monster that we're using for this calculation.
	 */
	protected Monster monster;

	/**
	 * The original monster passed in to the calculator before scaling was applied.
	 */
	protected Monster baseMonster;

	/**
	 * Names of all equipped items (for quick checks).
	 */
	protected List<String> allEquippedItems = new ArrayList<>();

	protected final List<UserIssue> userIssues = new ArrayList<>();

	protected BaseCalc(Player player, Monster monster, CalcOpts opts)
	{
		this.opts = opts == null ? CalcOpts.defaults() : opts;

		this.player = player;
		this.baseMonster = monster;
		this.monster = (this.opts.isDisableMonsterScaling() || this.opts.isNoInit())
			? monster
			: MonsterScaling.scaleMonster(monster);

		if (!this.opts.isNoInit())
		{
			canonicalizeEquipment();
			this.allEquippedItems = new ArrayList<>();
			for (EquipmentPiece piece : this.player.getEquipment().all())
			{
				this.allEquippedItems.add(piece.getName() == null ? "" : piece.getName());
			}
			sanitizeInputs();
		}
	}

	public List<UserIssue> getUserIssues()
	{
		return userIssues;
	}

	protected void canonicalizeEquipment()
	{
		this.player = this.player.toBuilder()
			.equipment(Equipment.getCanonicalEquipment(this.player.getEquipment()))
			.build();
	}

	public static double getNormalAccuracyRoll(int atkIn, int defIn)
	{
		int atk = atkIn;
		int def = defIn;

		if (atk < 0)
		{
			atk = Math.min(0, atk + 2);
		}
		if (def < 0)
		{
			def = Math.min(0, def + 2);
		}

		if (atk >= 0 && def >= 0)
		{
			return stdRoll(atk, def);
		}
		if (atk >= 0 && def < 0)
		{
			return 1 - 1.0 / (-def + 1) / (atk + 1);
		}
		if (atk < 0 && def >= 0)
		{
			return 0;
		}
		if (atk < 0 && def < 0)
		{
			return stdRoll(-def, -atk);
		}
		return 0;
	}

	private static double stdRoll(int attack, int defence)
	{
		return (attack > defence)
			? 1 - ((double) (defence + 2) / (2.0 * (attack + 1)))
			: (double) attack / (2.0 * (defence + 1));
	}

	public static double getFangAccuracyRoll(int atkIn, int defIn)
	{
		int atk = atkIn;
		int def = defIn;

		if (atk < 0)
		{
			atk = Math.min(0, atk + 2);
		}
		if (def < 0)
		{
			def = Math.min(0, def + 2);
		}

		if (atk >= 0 && def >= 0)
		{
			return fangStdRoll(atk, def, def);
		}
		if (atk >= 0 && def < 0)
		{
			return 1 - 1.0 / (-def + 1) / (atk + 1);
		}
		if (atk < 0 && def >= 0)
		{
			return 0;
		}
		if (atk < 0 && def < 0)
		{
			return fangRvRoll(-def, -atk, def);
		}
		return 0;
	}

	/**
	 * Note: the TS closure compares against the outer `def`, not the `defence` parameter - preserved here
	 * as the extra `outerDef` argument.
	 */
	private static double fangStdRoll(int attack, int defence, int outerDef)
	{
		return (attack > outerDef)
			? 1 - (double) (defence + 2) * (2.0 * defence + 3) / (attack + 1) / (attack + 1) / 6
			: (double) attack * (4.0 * attack + 5) / 6 / (attack + 1) / (defence + 1);
	}

	private static double fangRvRoll(int attack, int defence, int outerDef)
	{
		return (attack < outerDef)
			? (double) attack * (defence * 6.0 - 2 * attack + 5) / 6 / (defence + 1) / (defence + 1)
			: 1 - (double) (defence + 2) * (2.0 * defence + 3) / 6 / (defence + 1) / (attack + 1);
	}

	public static double getConflictionGauntletsAccuracyRoll(int atk, int def)
	{
		double singleRoll = getNormalAccuracyRoll(atk, def);
		double doubleRoll = getFangAccuracyRoll(atk, def);
		return doubleRoll / (1 + doubleRoll - singleRoll);
	}

	/**
	 * Helper function that calculates the hit chance for mechanics that force a specific attack roll, but
	 * still use a random defence roll.
	 */
	public static double getFixedAttackHitChance(int atkIn, int defIn)
	{
		int atk = atkIn;
		int def = defIn;

		if (atk < 0)
		{
			atk = Math.min(0, atk + 2);
		}
		if (def < 0)
		{
			def = Math.min(0, def + 2);
		}

		if (atk >= 0 && def >= 0)
		{
			return fixedStdRoll(atk, def);
		}
		if (atk >= 0 && def < 0)
		{
			return 1;
		}
		if (atk < 0 && def >= 0)
		{
			return 0;
		}
		if (atk < 0 && def < 0)
		{
			return fixedStdRoll(-def, -atk);
		}
		return 0;
	}

	private static double fixedStdRoll(int attack, int defence)
	{
		return (attack > defence) ? 1 : (double) attack / (defence + 1);
	}

	/**
	 * Whether ANY of the provided items are equipped.
	 */
	protected boolean wearing(Collection<String> items)
	{
		for (String i : items)
		{
			if (allEquippedItems.contains(i))
			{
				return true;
			}
		}
		return false;
	}

	protected boolean wearing(String item)
	{
		return allEquippedItems.contains(item);
	}

	/**
	 * Whether ALL passed items are equipped.
	 */
	protected boolean wearingAll(Collection<String> items)
	{
		return allEquippedItems.containsAll(items);
	}

	protected EquipmentPiece weapon()
	{
		return player.getEquipment().getWeapon();
	}

	protected EquipmentPiece ammo()
	{
		return player.getEquipment().getAmmo();
	}

	protected CombatStyleType styleType()
	{
		return player.getStyle().getType();
	}

	protected CombatStyleStance stance()
	{
		return player.getStyle().getStance();
	}

	/**
	 * Whether the player is using either a slash, crush, or stab combat style.
	 */
	protected boolean isUsingMeleeStyle()
	{
		CombatStyleType type = styleType();
		return type != null && type.isMelee();
	}

	protected boolean isWearingVoidRobes()
	{
		return wearing(Arrays.asList("Void knight top", "Void knight top (or)", "Elite void top", "Elite void top (or)"))
			&& wearing(Arrays.asList("Void knight robe", "Void knight robe (or)", "Elite void robe", "Elite void robe (or)"))
			&& wearing("Void knight gloves");
	}

	protected boolean isWearingEliteVoidRobes()
	{
		return wearing(Arrays.asList("Elite void top", "Elite void top (or)"))
			&& wearing(Arrays.asList("Elite void robe", "Elite void robe (or)"))
			&& wearing("Void knight gloves");
	}

	protected boolean isWearingMeleeVoid()
	{
		return isWearingVoidRobes() && wearing(Arrays.asList("Void melee helm", "Void melee helm (or)"));
	}

	protected boolean isWearingEliteRangedVoid()
	{
		return isWearingEliteVoidRobes() && wearing(Arrays.asList("Void ranger helm", "Void ranger helm (or)"));
	}

	protected boolean isWearingEliteMagicVoid()
	{
		return isWearingEliteVoidRobes() && wearing(Arrays.asList("Void mage helm", "Void mage helm (or)"));
	}

	protected boolean isWearingRangedVoid()
	{
		return isWearingVoidRobes() && wearing(Arrays.asList("Void ranger helm", "Void ranger helm (or)"));
	}

	protected boolean isWearingMagicVoid()
	{
		return isWearingVoidRobes() && wearing(Arrays.asList("Void mage helm", "Void mage helm (or)"));
	}

	protected boolean isWearingSlayerHelmet()
	{
		return wearing(Arrays.asList("Slayer helmet", "Slayer helmet (i)"));
	}

	protected boolean isWearingBlackMask()
	{
		return isWearingImbuedBlackMask() || wearing(Arrays.asList("Black mask", "Slayer helmet"));
	}

	protected boolean isWearingImbuedBlackMask()
	{
		return wearing(Arrays.asList("Black mask (i)", "Slayer helmet (i)", "V's helm"));
	}

	protected boolean isWearingSmokeStaff()
	{
		return wearing(Arrays.asList("Smoke battlestaff", "Mystic smoke staff", "Twinflame staff"));
	}

	protected boolean isWearingTzhaarWeapon()
	{
		return wearing(Arrays.asList("Tzhaar-ket-em", "Tzhaar-ket-om", "Tzhaar-ket-om (t)", "Toktz-xil-ak",
			"Toktz-xil-ek", "Toktz-mej-tal"));
	}

	protected boolean isWearingObsidian()
	{
		return wearingAll(Arrays.asList("Obsidian helmet", "Obsidian platelegs", "Obsidian platebody"));
	}

	protected boolean isWearingBerserkerNecklace()
	{
		return wearing(Arrays.asList("Berserker necklace", "Berserker necklace (or)"));
	}

	protected boolean isWearingCrystalBow()
	{
		if (wearing("Crystal bow"))
		{
			return true;
		}
		for (String ei : allEquippedItems)
		{
			if (ei.contains("Bow of faerdhinen"))
			{
				return true;
			}
		}
		return false;
	}

	protected boolean isWearingFang()
	{
		return wearing(Arrays.asList("Osmumten's fang", "Osmumten's fang (or)"));
	}

	protected boolean isWearingAccursedSceptre()
	{
		return wearing(Arrays.asList("Accursed sceptre", "Accursed sceptre (a)"));
	}

	protected boolean isWearingBlowpipe()
	{
		return wearing(Arrays.asList("Toxic blowpipe", "Blazing blowpipe"));
	}

	protected boolean isWearingGodsword()
	{
		return wearing(Arrays.asList("Ancient godsword", "Armadyl godsword", "Bandos godsword", "Saradomin godsword",
			"Zamorak godsword"));
	}

	protected boolean isWearingScythe()
	{
		if (wearing("Scythe of vitur"))
		{
			return true;
		}
		for (String ei : allEquippedItems)
		{
			if (ei.contains("of vitur"))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Standard roll two-hit weapons.
	 */
	protected boolean isWearingTwoHitWeapon()
	{
		return wearing(Arrays.asList("Torag's hammers", "Sulphur blades", "Glacial temotli", "Earthbound tecpatl"));
	}

	protected boolean isWearingKeris()
	{
		for (String ei : allEquippedItems)
		{
			if (ei.contains("Keris"))
			{
				return true;
			}
		}
		return false;
	}

	protected boolean isWearingDharok()
	{
		return wearingAll(Arrays.asList("Dharok's helm", "Dharok's platebody", "Dharok's platelegs", "Dharok's greataxe"));
	}

	protected boolean isWearingVeracs()
	{
		return wearingAll(Arrays.asList("Verac's helm", "Verac's brassard", "Verac's plateskirt", "Verac's flail"));
	}

	protected boolean isWearingKarils()
	{
		return wearingAll(Arrays.asList("Karil's coif", "Karil's leathertop", "Karil's leatherskirt", "Karil's crossbow",
			"Amulet of the damned"));
	}

	protected boolean isWearingAhrims()
	{
		return wearingAll(Arrays.asList("Ahrim's staff", "Ahrim's hood", "Ahrim's robetop", "Ahrim's robeskirt",
			"Amulet of the damned"));
	}

	protected boolean isWearingTorags()
	{
		return wearingAll(Arrays.asList("Torag's helm", "Torag's platebody", "Torag's platelegs", "Torag's hammers",
			"Amulet of the damned"));
	}

	protected boolean isWearingBloodMoonSet()
	{
		return wearingAll(Arrays.asList("Dual macuahuitl", "Blood moon helm", "Blood moon chestplate",
			"Blood moon tassets"));
	}

	protected boolean isWearingSilverWeapon()
	{
		if (styleType() == CombatStyleType.RANGED && wearing(Arrays.asList(
			"Silver bolts#Unpoisoned",
			"Silver bolts#Poison",
			"Silver bolts#Poison+",
			"Silver bolts#Poison++",
			"Blisterwood stake")))
		{
			return true;
		}

		return isUsingMeleeStyle() && wearing(Arrays.asList(
			"Blessed axe",
			"Ivandis flail",
			"Blisterwood flail",
			"Hallowed flail",
			"Silver sickle",
			"Silver sickle (b)",
			"Emerald sickle",
			"Emerald sickle (b)",
			"Enchanted emerald sickle (b)",
			"Ruby sickle (b)",
			"Enchanted ruby sickle (b)",
			"Blisterwood sickle",
			"Silverlight",
			"Darklight",
			"Arclight",
			"Rod of ivandis",
			"Wolfbane"));
	}

	/**
	 * Whether the player is using a vampyrebane weapon capable of full damage against t2 or t3 vampyres.
	 */
	protected boolean wearingVampyrebane(MonsterAttribute tier)
	{
		boolean t2 = tier == MonsterAttribute.VAMPYRE_2;
		EquipmentPiece weapon = weapon();
		if (styleType() == CombatStyleType.RANGED && weapon != null && "Blisterwood stake".equals(weapon.getName()))
		{
			return true;
		}

		List<String> weapons = new ArrayList<>();
		if (t2)
		{
			weapons.add("Rod of ivandis");
		}
		weapons.addAll(Arrays.asList("Ivandis flail", "Blisterwood sickle", "Blisterwood flail", "Hallowed flail",
			"Sunspear"));

		return (t2 || isUsingMeleeStyle()) && wearing(weapons);
	}

	protected boolean isWearingMsb()
	{
		return wearing(Arrays.asList("Magic shortbow", "Magic shortbow (i)"));
	}

	protected boolean isWearingMlb()
	{
		return wearing(Arrays.asList("Magic longbow", "Magic comp bow"));
	}

	protected boolean isWearingLeafBladedWeapon()
	{
		if (isUsingMeleeStyle() && wearing(Arrays.asList("Leaf-bladed battleaxe", "Leaf-bladed spear",
			"Leaf-bladed sword")))
		{
			return true;
		}

		if (player.getSpell() != null && "Magic Dart".equals(player.getSpell().getName()))
		{
			return true;
		}

		return wearing(Arrays.asList("Broad arrows", "Broad bolts", "Amethyst broad bolts"))
			&& styleType() == CombatStyleType.RANGED;
	}

	protected boolean isWearingCorpbaneWeapon()
	{
		EquipmentPiece weapon = weapon();
		boolean isStab = styleType() == CombatStyleType.STAB;
		if (weapon == null)
		{
			return false;
		}

		if (isWearingFang())
		{
			return isStab;
		}

		if (weapon.getName() != null && weapon.getName().endsWith("halberd"))
		{
			return isStab;
		}

		if (weapon.getName() != null && weapon.getName().contains("spear") && !"Blue moon spear".equals(weapon.getName()))
		{
			return isStab;
		}

		return styleType() == CombatStyleType.MAGIC;
	}

	protected boolean isRevWeaponBuffApplicable()
	{
		EquipmentPiece weapon = weapon();
		if (!player.getBuffs().isInWilderness() || weapon == null || !"Charged".equals(weapon.getVersion()))
		{
			return false;
		}

		CombatStyleType type = styleType();
		if (type == CombatStyleType.MAGIC)
		{
			return wearing(Arrays.asList("Accursed sceptre", "Accursed sceptre (a)", "Thammaron's sceptre",
				"Thammaron's sceptre (a)"));
		}
		if (type == CombatStyleType.RANGED)
		{
			return wearing(Arrays.asList("Craw's bow", "Webweaver bow"));
		}
		return wearing(Arrays.asList("Ursine chainmace", "Viggora's chainmace"));
	}

	protected boolean isWearingRatBoneWeapon()
	{
		return wearing(Arrays.asList("Bone mace", "Bone shortbow", "Bone staff"));
	}

	protected boolean isChargeSpellApplicable()
	{
		if (!player.getBuffs().isChargeSpell() || player.getSpell() == null)
		{
			return false;
		}

		switch (player.getSpell().getName())
		{
			case "Saradomin Strike":
				return wearing(Arrays.asList("Saradomin cape", "Imbued saradomin cape", "Saradomin max cape",
					"Imbued saradomin max cape"));
			case "Claws of Guthix":
				return wearing(Arrays.asList("Guthix cape", "Imbued guthix cape", "Guthix max cape",
					"Imbued guthix max cape"));
			case "Flames of Zamorak":
				return wearing(Arrays.asList("Zamorak cape", "Imbued zamorak cape", "Zamorak max cape",
					"Imbued zamorak max cape"));
			default:
				return false;
		}
	}

	protected boolean isWearingJusticiarArmour()
	{
		return wearingAll(Arrays.asList("Justiciar faceguard", "Justiciar chestguard", "Justiciar legguards"));
	}

	protected boolean isUsingDemonbane()
	{
		CombatStyleType type = styleType();
		if (type == CombatStyleType.MAGIC)
		{
			return player.getSpell() != null && player.getSpell().getName().contains("Demonbane");
		}
		if (type == CombatStyleType.RANGED)
		{
			return wearing("Scorching bow");
		}
		return wearing(Arrays.asList("Silverlight", "Darklight", "Arclight", "Emberlight", "Bone claws",
			"Burning claws"));
	}

	protected boolean isUsingAbyssal()
	{
		return isUsingMeleeStyle()
			&& wearing(Arrays.asList("Abyssal bludgeon", "Abyssal dagger", "Abyssal whip", "Abyssal tentacle"));
	}

	protected boolean isWearingOgreBow()
	{
		return wearing(Arrays.asList("Ogre bow", "Comp ogre bow"));
	}

	protected boolean tdUnshieldedBonusApplies()
	{
		if (!"Tormented Demon".equals(monster.getName()) || !"Unshielded".equals(monster.getInputs().getPhase()))
		{
			return false;
		}

		CombatStyleType type = styleType();
		if (type == CombatStyleType.MAGIC)
		{
			return player.getSpell() != null;
		}
		if (type == CombatStyleType.RANGED)
		{
			return PlayerCombatStyle.getRangedDamageType(weapon().getCategory())
				== PlayerCombatStyle.RangedDamageType.HEAVY;
		}
		return type == CombatStyleType.CRUSH;
	}

	protected boolean isAmmoInvalid()
	{
		EquipmentPiece weapon = weapon();
		EquipmentPiece ammo = ammo();
		return Equipment.ammoApplicability(
			weapon == null ? null : weapon.getId(),
			ammo == null ? null : ammo.getId()) == Equipment.AmmoApplicability.INVALID;
	}

	protected boolean isImmuneToNormalBurns()
	{
		return burnImmunity() == BurnImmunity.NORMAL || isImmuneToStrongBurns();
	}

	protected boolean isImmuneToStrongBurns()
	{
		return burnImmunity() == BurnImmunity.STRONG
			|| Constants.IMMUNE_TO_BURN_DAMAGE_NPC_IDS.contains(monster.getId());
	}

	private BurnImmunity burnImmunity()
	{
		return monster.getImmunities() == null ? null : monster.getImmunities().getBurn();
	}

	protected boolean isSlayerMonster()
	{
		return monster.isSlayerMonster() || monster.getId() == -1;
	}

	protected void addIssue(UserIssue.Type type, String message)
	{
		userIssues.add(new UserIssue(type, message, opts.getLoadoutName()));
	}

	protected int demonbaneVulnerability()
	{
		MonsterInputs inputs = monster.getInputs();
		if (monster.getId() == -1 && inputs.getDemonbaneVulnerability() != null)
		{
			return inputs.getDemonbaneVulnerability();
		}
		if ("Duke Sucellus".equals(monster.getName()))
		{
			return 70;
		}
		if (Constants.YAMA_IDS.contains(monster.getId()))
		{
			return 120;
		}
		if (Constants.YAMA_VOID_FLARE_IDS.contains(monster.getId()))
		{
			return 200;
		}

		return 100;
	}

	private void sanitizeInputs()
	{
		if (monster.getAttributes().contains(MonsterAttribute.DEMON))
		{
			// make sure demonbane effectiveness is set and uses the right value
			monster = monster.toBuilder()
				.inputs(monster.getInputs().toBuilder()
					.demonbaneVulnerability(demonbaneVulnerability())
					.build())
				.build();
		}

		// make sure monsterCurrentHp is set and valid
		int currentHp = monster.getInputs().getMonsterCurrentHp();
		if (currentHp == 0 || currentHp > monster.getSkills().getHp())
		{
			monster = monster.toBuilder()
				.inputs(monster.getInputs().toBuilder()
					.monsterCurrentHp(monster.getSkills().getHp())
					.build())
				.build();
		}

		// specs are never manual cast, although the base loadout can be at the same time
		if (opts.isUsingSpecialAttack())
		{
			if (stance() == CombatStyleStance.MANUAL_CAST)
			{
				EquipmentPiece w = weapon();
				player = player.toBuilder()
					.style(PlayerCombatStyle.getCombatStylesForCategory(
						w == null || w.getCategory() == null ? EquipmentCategory.UNARMED : w.getCategory()).get(0))
					.spell(null)
					.build();
			}

			// these staves use a built-in spell for their spec
			EquipmentPiece w = weapon();
			String weaponName = w == null ? "" : w.getName();
			if (Arrays.asList("Accursed sceptre (a)", "Eldritch nightmare staff", "Volatile nightmare staff")
				.contains(weaponName == null ? "" : weaponName))
			{
				player = player.toBuilder()
					.style(PlayerCombatStyle.getCombatStylesForCategory(EquipmentCategory.POWERED_STAFF).get(0))
					.spell(null)
					.build();
			}
		}

		// we should do clone-edits here to prevent affecting ui state
		if (stance() == null || !stance().isCastStance())
		{
			player = player.toBuilder().spell(null).build();
		}

		if (stance() != CombatStyleStance.MANUAL_CAST && isAmmoInvalid())
		{
			EquipmentPiece ammo = ammo();
			if (ammo != null && ammo.getName() != null)
			{
				addIssue(UserIssue.Type.EQUIPMENT_WRONG_AMMO, "This ammo does not work with your current weapon.");
			}
			else
			{
				addIssue(UserIssue.Type.EQUIPMENT_MISSING_AMMO, "Your weapon requires ammo to use.");
			}
		}

		// Certain spells require specific weapons to be equipped
		String spellName = player.getSpell() == null ? null : player.getSpell().getName();
		if (("Iban Blast".equals(spellName) && !wearing(Arrays.asList("Iban's staff", "Iban's staff (u)")))
			|| ("Saradomin Strike".equals(spellName) && !wearing(Arrays.asList("Saradomin staff", "Staff of light")))
			|| ("Claws of Guthix".equals(spellName) && !wearing(Arrays.asList("Guthix staff", "Void knight mace",
			"Staff of balance")))
			|| ("Flames of Zamarok".equals(spellName) && !wearing(Arrays.asList("Zamorak staff", "Staff of the dead",
			"Toxic staff of the dead", "Thammaron's sceptre (a)", "Accursed sceptre (a)")))
			|| ("Magic Dart".equals(spellName) && !wearing(Arrays.asList("Slayer's staff", "Slayer's staff (e)",
			"Staff of the dead", "Toxic staff of the dead", "Staff of light", "Staff of balance"))))
		{
			player = player.toBuilder().spell(null).build();
			addIssue(UserIssue.Type.SPELL_WRONG_WEAPON, "This spell needs a specific weapon equipped to cast.");
		}

		// Certain spells can only be cast on specific monsters
		if ((spellName != null && spellName.contains("Demonbane")
			&& !monster.getAttributes().contains(MonsterAttribute.DEMON))
			|| ("Crumble Undead".equals(spellName)
			&& !monster.getAttributes().contains(MonsterAttribute.UNDEAD)))
		{
			player = player.toBuilder().spell(null).build();
			addIssue(UserIssue.Type.SPELL_WRONG_MONSTER, "This spell cannot be cast on the selected monster.");
		}

		// some weapons are only available to use against certain monsters
		String version = monster.getVersion();
		if ((wearing("Dawnbringer")
			&& (!"Verzik Vitur".equals(monster.getName()) || version == null || !version.contains("Phase 1")))
			|| (wearing("Holy water") && !monster.getAttributes().contains(MonsterAttribute.DEMON)))
		{
			addIssue(UserIssue.Type.WEAPON_WRONG_MONSTER, "This weapon cannot be used against the select monster.");
		}

		// Some set effects are currently not accounted for
		if (wearingAll(Arrays.asList("Blue moon helm", "Blue moon chestplate", "Blue moon tassets", "Blue moon spear"))
			|| wearingAll(Arrays.asList("Eclipse moon helm", "Eclipse moon chestplate", "Eclipse moon tassets",
			"Eclipse atlatl")))
		{
			addIssue(UserIssue.Type.EQUIPMENT_SET_EFFECT_UNSUPPORTED,
				"The calculator currently does not account for your equipment set effect.");
		}
		if (wearing("Ring of recoil") || wearing("Ring of suffering (i)") || wearing("Ring of suffering"))
		{
			addIssue(UserIssue.Type.RING_RECOIL_UNSUPPORTED, "The calculator does not account for recoil damage.");
		}
		if (wearing("Echo boots"))
		{
			addIssue(UserIssue.Type.FEET_RECOIL_UNSUPPORTED, "The calculator does not account for recoil damage.");
		}
	}

	protected boolean weaponCategoryIs(EquipmentCategory category)
	{
		EquipmentPiece weapon = weapon();
		return weapon != null && weapon.getCategory() == category;
	}
}
