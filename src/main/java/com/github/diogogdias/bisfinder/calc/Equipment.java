package com.github.diogogdias.bisfinder.calc;

import com.github.diogogdias.bisfinder.calc.dist.CalcMath;
import com.github.diogogdias.bisfinder.calc.model.CombatStyleStance;
import com.github.diogogdias.bisfinder.calc.model.CombatStyleType;
import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.Player;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Value;

/**
 * Aggregates a loadout's equipment bonuses and resolves ammo/canonical item ids.
 */
public final class Equipment
{
	private Equipment()
	{
	}

	/**
	 * Special attack energy cost, keyed by canonical weapon name.
	 */
	public static final Map<String, Integer> WEAPON_SPEC_COSTS = weaponSpecCosts();

	private static Map<String, Integer> weaponSpecCosts()
	{
		Map<String, Integer> m = new HashMap<>();
		m.put("Abyssal dagger", 25);
		m.put("Dragon dagger", 25);
		m.put("Dragon longsword", 25);
		m.put("Dragon mace", 25);
		m.put("Osmumten's fang", 25);
		m.put("Osmumten's fang (or)", 25);
		m.put("Dual macuahuitl", 25);
		m.put("Scorching bow", 25);
		m.put("Dragon knife", 25);
		m.put("Purging staff", 25);
		m.put("Rosewood blowpipe", 25);

		m.put("Dawnbringer", 30);
		m.put("Dragon halberd", 30);
		m.put("Crystal halberd", 30);
		m.put("Burning claws", 30);
		m.put("Arkan blade", 30);

		m.put("Magic longbow", 35);
		m.put("Magic comp bow", 35);

		m.put("Dragon sword", 40);

		m.put("Elder maul", 50);
		m.put("Dragon warhammer", 50);
		m.put("Bandos godsword", 50);
		m.put("Saradomin godsword", 50);
		m.put("Accursed sceptre", 50);
		m.put("Accursed sceptre (a)", 50);
		m.put("Arclight", 50);
		m.put("Emberlight", 50);
		m.put("Tonalztics of ralos", 50);
		m.put("Dragon claws", 50);
		m.put("Voidwaker", 50);
		m.put("Toxic blowpipe", 50);
		m.put("Blazing blowpipe", 50);
		m.put("Webweaver bow", 50);
		m.put("Magic shortbow (i)", 50);
		m.put("Ancient godsword", 50);
		m.put("Armadyl godsword", 50);
		m.put("Zamorak godsword", 50);
		m.put("Abyssal bludgeon", 50);
		m.put("Abyssal whip", 50);
		m.put("Barrelchest anchor", 50);
		m.put("Eye of ayak", 50);
		m.put("Crimson kisten", 50);
		m.put("Sunspear", 50);

		m.put("Magic shortbow", 55);
		m.put("Dark bow", 55);
		m.put("Eldritch nightmare staff", 55);
		m.put("Volatile nightmare staff", 55);
		m.put("Dragon scimitar", 55);

		m.put("Granite hammer", 60);

		m.put("Heavy ballista", 65);
		m.put("Light ballista", 65);
		m.put("Saradomin's blessed sword", 65);

		m.put("Brine sabre", 75);
		m.put("Zaryte crossbow", 75);

		m.put("Saradomin sword", 100);
		m.put("Seercull", 100);
		return Collections.unmodifiableMap(m);
	}

	public enum AmmoApplicability
	{
		/**
		 * Include the ammo slot bonuses in the equipment stats.
		 */
		INCLUDED,
		/**
		 * Allow the ammo but do not include its ranged accuracy and strength bonuses.
		 */
		ALLOWED,
		/**
		 * The ammo is incompatible with the weapon.
		 */
		INVALID
	}

	/**
	 * The aggregated bonuses, offensive and defensive stats, and attack speed of a full loadout.
	 */
	@Value
	public static class EquipmentBonuses
	{
		Player.Bonuses bonuses;
		Player.Offensive offensive;
		Player.Defensive defensive;
		int attackSpeed;
	}

	private static final Map<String, Set<Integer>> COMMON_AMMO_CATEGORIES = commonAmmoCategories();

	/**
	 * A map of item ID -> item IDs of ammo that ranged weapons can use. Empty sets indicate that the item
	 * should not be used with any ammo in the ammo slot at all.
	 */
	private static final Map<Integer, Set<Integer>> AMMO_FOR_RANGED_WEAPONS = ammoForRangedWeapons();

	private static Map<String, Set<Integer>> commonAmmoCategories()
	{
		Map<String, Set<Integer>> ret = new HashMap<>();
		ret.put("bow_t1", ids(
			882, 883, 5616, 5622, 598, 942, 33553, // Bronze arrow + variants
			884, 885, 5617, 5623, 2532, 2533, 33559, // Iron arrow + variants
			22227, 22228, 22229, 22230 // barb assault
		));
		ret.put("cb_t1", ids(
			877, 878, 6061, 6062, 879, 9236 // Bronze bolts + variants, opal bolts + (e)
		));
		ret.put("javelin", ids(
			825, 831, 5642, 5648, // Bronze javelin + variants
			826, 832, 5643, 5649, // Iron javelin + variants
			827, 833, 5644, 5650, // Steel javelin + variants
			828, 834, 5645, 5651, // Mithril javelin + variants
			829, 835, 5646, 5652, // Adamant javelin + variants
			830, 836, 5647, 5653, // Rune javelin + variants
			21318, 21320, 21322, 21324, // Amethyst javelin + variants
			19484, 19486, 19488, 19490 // Dragon javelin + variants
		));

		// Bows
		ret.put("bow_t5", plus(ret.get("bow_t1"), 886, 887, 5618, 5624, 2534, 2535, 33565)); // Steel arrow + variants
		ret.put("bow_t20", plus(ret.get("bow_t5"), 888, 889, 5619, 5625, 2536, 2537, 33571)); // Mithril arrow + variants
		ret.put("bow_t30", plus(ret.get("bow_t20"), 890, 891, 5620, 5626, 2538, 2539, 33577)); // Adamant arrow + variants
		ret.put("bow_t40", plus(ret.get("bow_t30"), 892, 893, 5621, 5627, 78, 2540, 2541, 33583)); // Rune arrow + variants, ice arrows
		ret.put("bow_t50", plus(ret.get("bow_t40"), 21326, 21332, 21334, 21336, 4160, 21328, 21330, 33589, 33601)); // Amethyst arrow + variants, broad arrows
		ret.put("bow_t60", plus(ret.get("bow_t50"), 11212, 11227, 11228, 11229, 11217, 11222, 33595)); // Dragon arrow + variants

		// Bolts
		ret.put("cb_t16", plus(ret.get("cb_t1"), 9139, 9286, 9293, 9300, 9335, 9237)); // Blurite bolts + variants, jade bolts + (e)
		ret.put("cb_t26", plus(ret.get("cb_t16"), 9140, 9287, 9294, 9301, 880, 9238, 9145, 9292, 9299, 9306)); // Iron bolts + variants, pearl bolts + (e), silver bolts
		ret.put("cb_t31", plus(ret.get("cb_t26"), 9141, 9288, 9295, 9302, 9336, 9239)); // Steel bolts + variants, topaz bolts + (e)
		ret.put("cb_t36", plus(ret.get("cb_t31"), 9142, 9289, 9296, 9303, 9337, 9240, 9338, 9241)); // Mithril bolts + variants, sapphire/emerald bolts + (e)
		ret.put("cb_t46", plus(ret.get("cb_t36"), 9143, 9290, 9297, 9304, 9339, 9242, 9340, 9243)); // Adamant bolts + variants, ruby/diamond bolts + (e)
		ret.put("cb_t61", plus(ret.get("cb_t46"), 9144, 9291, 9298, 9305, 11875, 21316, 9341, 9244, 9342, 9245)); // Runite bolts + variants, broad bolts, amethyst broad bolts, dragonstone/onyx bolts + (e)
		ret.put("cb_t64", plus(ret.get("cb_t61"), 21905, 21924, 21926, 21928, 21955, 21932, 21957, 21934, 21959, 21936,
			21961, 21938, 21963, 21940, 21965, 21942, 21967, 21944, 21969, 21946, 21971, 21948, 21973, 21950)); // Dragon bolts + variants, many gem-tipped bolts
		return ret;
	}

	private static Map<Integer, Set<Integer>> ammoForRangedWeapons()
	{
		Map<String, Set<Integer>> c = COMMON_AMMO_CATEGORIES;
		Map<Integer, Set<Integer>> ret = new HashMap<>();
		// todo(wgs): scorching bow arrows
		ret.put(11708, c.get("bow_t1")); // Cursed goblin bow
		ret.put(23357, c.get("bow_t1")); // Rain bow
		ret.put(9705, ids(9706)); // Training bow
		ret.put(841, c.get("bow_t1")); // Shortbow
		ret.put(839, c.get("bow_t1")); // Longbow
		ret.put(843, c.get("bow_t5")); // Oak shortbow
		ret.put(845, c.get("bow_t5")); // Oak longbow
		ret.put(4236, c.get("bow_t5")); // Signed oak bow
		ret.put(849, c.get("bow_t20")); // Willow shortbow
		ret.put(847, c.get("bow_t20")); // Willow longbow
		ret.put(10280, c.get("bow_t20")); // Willow comp bow
		ret.put(853, c.get("bow_t30")); // Maple shortbow
		ret.put(851, c.get("bow_t30")); // Maple longbow
		ret.put(2883, ids(2866, 4773, 4778, 4783, 4788, 4793)); // Ogre bow
		ret.put(4827, ids(2866, 4773, 4778, 4783, 4788, 4793, 4798, 4803)); // Comp ogre bow
		ret.put(857, c.get("bow_t40")); // Yew shortbow
		ret.put(855, c.get("bow_t40")); // Yew longbow
		ret.put(10282, c.get("bow_t40")); // Yew comp bow
		ret.put(28794, c.get("bow_t50")); // Bone shortbow
		ret.put(6724, c.get("bow_t50")); // Seercull
		ret.put(861, c.get("bow_t50")); // Magic shortbow
		ret.put(12788, c.get("bow_t50")); // Magic shortbow (i)
		ret.put(859, c.get("bow_t50")); // Magic longbow
		ret.put(10284, c.get("bow_t50")); // Magic comp bow
		ret.put(11235, c.get("bow_t60")); // Dark bow
		ret.put(27853, c.get("bow_t60")); // Dark bow (bh)
		ret.put(12424, c.get("bow_t60")); // 3rd age bow
		ret.put(27610, c.get("bow_t60")); // Venator bow
		ret.put(27612, c.get("bow_t60")); // Venator bow (uncharged)
		ret.put(20997, c.get("bow_t60")); // Twisted bow
		ret.put(29591, c.get("bow_t60")); // Scorching bow
		ret.put(837, c.get("cb_t1")); // Crossbow
		ret.put(767, c.get("cb_t1")); // Phoenix crossbow
		ret.put(9174, c.get("cb_t1")); // Bronze crossbow
		ret.put(9176, c.get("cb_t16")); // Blurite crossbow
		ret.put(9177, c.get("cb_t26")); // Iron crossbow
		ret.put(9179, c.get("cb_t31")); // Steel crossbow
		ret.put(9181, c.get("cb_t36")); // Mithril crossbow
		ret.put(9183, c.get("cb_t46")); // Adamant crossbow
		ret.put(9185, c.get("cb_t61")); // Rune crossbow
		ret.put(21902, c.get("cb_t64")); // Dragon crossbow
		ret.put(19478, c.get("javelin")); // Light ballista
		ret.put(19481, c.get("javelin")); // Heavy ballista
		ret.put(8880, plus(c.get("cb_t16"), 9140, 9287, 9294, 9301, 8882)); // Dorgeshuun crossbow
		ret.put(10156, ids(10158, 10159)); // Hunters' crossbow
		ret.put(4734, ids(4740)); // Karil's crossbow (undmg)
		ret.put(21012, c.get("cb_t64")); // Dragon hunter crossbow
		ret.put(11785, c.get("cb_t64")); // Armadyl crossbow
		ret.put(26374, c.get("cb_t64")); // Zaryte crossbow
		ret.put(33251, c.get("cb_t64")); // King's barrage
		ret.put(12924, ids()); // Toxic blowpipe (empty)
		ret.put(12926, ids()); // Toxic blowpipe (charged)
		ret.put(22547, ids()); // Craw's bow (empty)
		ret.put(22550, ids()); // Craw's bow (charged)
		ret.put(23983, ids()); // Crystal bow (empty)
		ret.put(23985, ids()); // Crystal bow (inactive)
		ret.put(24123, ids()); // Crystal bow (new)
		ret.put(27652, ids()); // Webweaver bow (empty)
		ret.put(27655, ids()); // Webweaver bow (charged)
		ret.put(25862, ids()); // Bow of faerdhinen (empty)
		ret.put(25865, ids()); // Bow of faerdhinen (charged)
		ret.put(10149, ids(10142)); // Swamp lizard, Guam tar
		ret.put(10146, ids(10143)); // Orange salamander, Marrentill tar
		ret.put(10147, ids(10144)); // Red salamander, Tarromin tar
		ret.put(10148, ids(10145)); // Black salamander, Harralander tar
		ret.put(28834, ids(28837)); // Tecu salamander, Irit tar
		ret.put(28869, ids(28872, 28878)); // Hunters' sunlight crossbow
		ret.put(29000, ids(28991)); // Eclipse atlatl
		ret.put(33245, c.get("bow_t60")); // Nature's recurve
		return ret;
	}

	/**
	 * Returns whether the given ammo item ID is valid ammo for the given ranged weapon ID.
	 *
	 * @param weaponId the item ID of the ranged weapon, or null if no weapon is equipped
	 * @param ammoId the item ID of the ammo (such as bronze arrows), or null if the ammo slot is empty
	 */
	public static AmmoApplicability ammoApplicability(Integer weaponId, Integer ammoId)
	{
		Set<Integer> validAmmo = AMMO_FOR_RANGED_WEAPONS.get(weaponId == null ? -1 : weaponId);

		// The weapon does not use ammo
		if (validAmmo == null || validAmmo.isEmpty())
		{
			return AmmoApplicability.ALLOWED;
		}

		// weapon requires ammo, and we have one that matches the list
		if (ammoId != null && validAmmo.contains(ammoId))
		{
			return AmmoApplicability.INCLUDED;
		}

		// weapon requires ammo, but we don't have a matching one
		return AmmoApplicability.INVALID;
	}

	/**
	 * Returns the canonical (base) item ID for a given item ID.
	 */
	public static int getCanonicalItemId(int itemId)
	{
		Integer canonical = CalcData.getEquipmentAliases().get(itemId);
		return canonical == null ? itemId : canonical;
	}

	public static EquipmentPiece getCanonicalItem(EquipmentPiece equipmentPiece)
	{
		int canonicalId = getCanonicalItemId(equipmentPiece.getId());
		if (equipmentPiece.getId() == canonicalId)
		{
			return equipmentPiece;
		}

		EquipmentPiece canonicalItem = CalcData.equipmentById(canonicalId);
		if (canonicalItem == null)
		{
			return equipmentPiece;
		}

		return new EquipmentPiece(
			canonicalItem.getId(),
			canonicalItem.getName(),
			canonicalItem.getVersion(),
			canonicalItem.getSlot(),
			canonicalItem.getImage(),
			canonicalItem.getWeight(),
			canonicalItem.getSpeed(),
			canonicalItem.getCategory(),
			canonicalItem.isTwoHanded(),
			canonicalItem.getBonuses(),
			canonicalItem.getOffensive(),
			canonicalItem.getDefensive(),
			equipmentPiece.getItemVars()
		);
	}

	public static Player.PlayerEquipment getCanonicalEquipment(Player.PlayerEquipment inputEq)
	{
		Player.PlayerEquipment canonicalized = inputEq;
		for (Map.Entry<String, EquipmentPiece> e : inputEq.bySlot().entrySet())
		{
			EquipmentPiece v = e.getValue();
			EquipmentPiece canonical = getCanonicalItem(v);
			if (v.getId() != canonical.getId())
			{
				canonicalized = canonicalized.withSlot(e.getKey(), canonical);
			}
		}
		return canonicalized;
	}

	/**
	 * Calculates the player's attack speed using current stance and equipment.
	 */
	public static int calculateAttackSpeed(Player player, Monster monster)
	{
		EquipmentPiece weapon = player.getEquipment().getWeapon();
		int attackSpeed = weapon != null ? weapon.getSpeed() : Constants.DEFAULT_ATTACK_SPEED;

		if (player.getStyle().getType() == CombatStyleType.RANGED
			&& player.getStyle().getStance() == CombatStyleStance.RAPID)
		{
			attackSpeed -= 1;
		}
		else if (isCastStance(player))
		{
			if (weapon != null && "Harmonised nightmare staff".equals(weapon.getName())
				&& player.getSpell() != null && "standard".equals(player.getSpell().getSpellbook())
				&& player.getStyle().getStance() != CombatStyleStance.MANUAL_CAST)
			{
				attackSpeed = 4;
			}
			else if (weapon != null && "Twinflame staff".equals(weapon.getName()))
			{
				attackSpeed = 6;
			}
			else
			{
				attackSpeed = 5;
			}
		}

		// Giant rat (Scurrius)
		if (monster.getId() == 7223
			&& player.getStyle().getStance() != CombatStyleStance.MANUAL_CAST)
		{
			if (weapon != null && Arrays.asList("Bone mace", "Bone shortbow", "Bone staff").contains(weapon.getName()))
			{
				attackSpeed = 1;
			}
		}

		return Math.max(attackSpeed, 1);
	}

	public static EquipmentBonuses calculateEquipmentBonusesFromGear(Player player, Monster monster)
	{
		int bonusStr = 0;
		int bonusMagicStr = 0;
		int bonusRangedStr = 0;
		int bonusPrayer = 0;

		int offSlash = 0;
		int offStab = 0;
		int offCrush = 0;
		int offRanged = 0;
		int offMagic = 0;

		int defSlash = 0;
		int defStab = 0;
		int defCrush = 0;
		int defRanged = 0;
		int defMagic = 0;

		// canonicalize all items first, otherwise ammoApplicability etc calls may return incorrect results later
		Player.PlayerEquipment playerEquipment = getCanonicalEquipment(player.getEquipment());
		EquipmentPiece weapon = playerEquipment.getWeapon();
		Integer weaponId = weapon == null ? null : weapon.getId();

		for (Map.Entry<String, EquipmentPiece> entry : playerEquipment.bySlot().entrySet())
		{
			EquipmentPiece piece = entry.getValue();

			// skip over ammo slot's ranged bonuses if it is not used by the bow
			boolean applyRangedStats = !"ammo".equals(piece.getSlot())
				|| ammoApplicability(weaponId, piece.getId()) == AmmoApplicability.INCLUDED;

			bonusStr += piece.getBonuses().getStr();
			bonusMagicStr += piece.getBonuses().getMagicStr();
			bonusPrayer += piece.getBonuses().getPrayer();
			if (applyRangedStats)
			{
				bonusRangedStr += piece.getBonuses().getRangedStr();
			}

			offSlash += piece.getOffensive().getSlash();
			offStab += piece.getOffensive().getStab();
			offCrush += piece.getOffensive().getCrush();
			offMagic += piece.getOffensive().getMagic();
			if (applyRangedStats)
			{
				offRanged += piece.getOffensive().getRanged();
			}

			defSlash += piece.getDefensive().getSlash();
			defStab += piece.getDefensive().getStab();
			defCrush += piece.getDefensive().getCrush();
			defRanged += piece.getDefensive().getRanged();
			defMagic += piece.getDefensive().getMagic();
		}

		if (weaponId != null && Constants.BLOWPIPE_IDS.contains(weaponId))
		{
			Integer dartId = weapon.getItemVars() == null ? null : weapon.getItemVars().getBlowpipeDartId();
			EquipmentPiece dart = dartId == null ? null : CalcData.equipmentById(dartId);
			if (dart != null)
			{
				bonusRangedStr += dart.getBonuses().getRangedStr();
			}
			// else: todo warn user (as in the source)
		}

		if (weapon != null && "Tumeken's shadow".equals(weapon.getName())
			&& player.getStyle().getStance() != CombatStyleStance.MANUAL_CAST)
		{
			int factor = Constants.TOMBS_OF_AMASCUT_MONSTER_IDS.contains(monster.getId()) ? 4 : 3;
			bonusMagicStr = Math.min(1000, bonusMagicStr * factor);
			offMagic *= factor;
		}

		if (weapon != null && "Keris partisan of amascut".equals(weapon.getName())
			&& !Constants.TOMBS_OF_AMASCUT_MONSTER_IDS.contains(monster.getId()))
		{
			bonusStr -= 22;
			offStab -= 50;
		}

		if (weapon != null && ("Dinh's bulwark".equals(weapon.getName()) || "Dinh's blazing bulwark".equals(weapon.getName())))
		{
			int defenceSum = defStab + defSlash + defCrush + defRanged;
			bonusStr += Math.max(0, CalcMath.trunc((double) (defenceSum - 800) / 12) - 38);
		}

		if (player.getSpell() != null && "ancient".equals(player.getSpell().getSpellbook()) && isCastStance(player))
		{
			int virtusPieces = 0;
			virtusPieces += virtus(playerEquipment.getHead());
			virtusPieces += virtus(playerEquipment.getBody());
			virtusPieces += virtus(playerEquipment.getLegs());
			bonusMagicStr += 30 * virtusPieces;
		}

		// void mage is a visible bonus of 5%
		if (nameIs(playerEquipment.getHead(), "Void mage helm")
			&& nameIs(playerEquipment.getBody(), "Elite void top")
			&& nameIs(playerEquipment.getLegs(), "Elite void robe")
			&& nameIs(playerEquipment.getHands(), "Void knight gloves"))
		{
			bonusMagicStr += 50;
		}

		EquipmentPiece cape = playerEquipment.getCape();
		boolean dizanasQuiverCharged = nameIs(cape, "Dizana's max cape")
			|| nameIs(cape, "Blessed dizana's quiver")
			|| (nameIs(cape, "Dizana's quiver") && cape != null && "Charged".equals(cape.getVersion()));

		EquipmentPiece rawWeapon = player.getEquipment().getWeapon();
		EquipmentPiece rawAmmo = player.getEquipment().getAmmo();
		if (dizanasQuiverCharged
			&& ammoApplicability(rawWeapon == null ? null : rawWeapon.getId(), rawAmmo == null ? null : rawAmmo.getId())
			== AmmoApplicability.INCLUDED)
		{
			offRanged += 10;
			bonusRangedStr += 1;
		}

		int attackSpeed = calculateAttackSpeed(player, monster);

		return new EquipmentBonuses(
			new Player.Bonuses(bonusStr, bonusRangedStr, bonusMagicStr, bonusPrayer),
			new Player.Offensive(offStab, offSlash, offCrush, offMagic, offRanged),
			new Player.Defensive(defStab, defSlash, defCrush, defMagic, defRanged),
			attackSpeed
		);
	}

	private static boolean isCastStance(Player player)
	{
		return player.getStyle().getStance() != null && player.getStyle().getStance().isCastStance();
	}

	private static int virtus(EquipmentPiece piece)
	{
		return piece != null && piece.getName() != null && piece.getName().contains("Virtus") ? 1 : 0;
	}

	private static boolean nameIs(EquipmentPiece piece, String name)
	{
		return piece != null && name.equals(piece.getName());
	}

	private static Set<Integer> ids(int... values)
	{
		Set<Integer> set = new LinkedHashSet<>(values.length);
		for (int value : values)
		{
			set.add(value);
		}
		return Collections.unmodifiableSet(set);
	}

	private static Set<Integer> plus(Set<Integer> base, int... values)
	{
		List<Integer> combined = new ArrayList<>(base);
		Set<Integer> set = new LinkedHashSet<>(combined);
		for (int value : values)
		{
			set.add(value);
		}
		return Collections.unmodifiableSet(set);
	}
}
