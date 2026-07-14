package com.github.diogogdias.bisfinder.calc.model;

import com.github.diogogdias.bisfinder.calc.Constants;
import com.github.diogogdias.bisfinder.calc.Equipment;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.Builder;
import lombok.Value;

/**
 * Port of Player from osrs-dps-calc (src/types/Player.ts).
 *
 * <p>Builder defaults mirror generateEmptyPlayer (src/state.tsx), which is what the wiki calculator's
 * own test harness builds players from - including its slightly surprising buff defaults
 * (onSlayerTask and kandarinDiary are both true there).
 *
 * <p>The aggregate stats (bonuses / offensive / defensive / attackSpeed) are NOT summed here: they must
 * be produced by {@link Equipment#calculateEquipmentBonusesFromGear}, which is what
 * {@link #withGearBonuses(Monster)} does.
 */
@Value
@Builder(toBuilder = true)
public class Player
{
	@Builder.Default
	String name = "Loadout 1";

	@Builder.Default
	PlayerCombatStyle style = PlayerCombatStyle.getCombatStylesForCategory(EquipmentCategory.NONE).get(0);

	/**
	 * Base skill levels, before boosts.
	 */
	@Builder.Default
	PlayerSkills skills = new PlayerSkills(99, 99, 99, 99, 99, 99, 99, 99, 99);

	/**
	 * Added on top of {@link #skills} to get the player's current levels. May be negative.
	 */
	@Builder.Default
	PlayerSkills boosts = PlayerSkills.none();

	@Builder.Default
	PlayerEquipment equipment = PlayerEquipment.builder().build();

	@Builder.Default
	int attackSpeed = Constants.DEFAULT_ATTACK_SPEED;

	@Builder.Default
	List<Prayer> prayers = Collections.emptyList();

	@Builder.Default
	Bonuses bonuses = Bonuses.none();

	@Builder.Default
	Offensive offensive = Offensive.none();

	@Builder.Default
	Defensive defensive = Defensive.none();

	@Builder.Default
	PlayerBuffs buffs = PlayerBuffs.builder()
		.onSlayerTask(true)
		.kandarinDiary(true)
		.build();

	/**
	 * Null when the player is not casting a spell.
	 */
	@Builder.Default
	Spell spell = null;

	/**
	 * Returns a copy of this player with its aggregate stats and attack speed recomputed from its gear.
	 * The monster is required because some gear bonuses depend on it (Tumeken's shadow, Keris partisan of
	 * amascut).
	 */
	public Player withGearBonuses(Monster monster)
	{
		Equipment.EquipmentBonuses totals = Equipment.calculateEquipmentBonusesFromGear(this, monster);
		return this.toBuilder()
			.bonuses(totals.getBonuses())
			.offensive(totals.getOffensive())
			.defensive(totals.getDefensive())
			.attackSpeed(totals.getAttackSpeed())
			.build();
	}

	/**
	 * Port of PlayerBonuses (src/types/Player.ts).
	 */
	@Value
	public static class Bonuses
	{
		int str;
		int rangedStr;
		int magicStr;
		int prayer;

		public static Bonuses none()
		{
			return new Bonuses(0, 0, 0, 0);
		}
	}

	/**
	 * Port of PlayerOffensive (src/types/Player.ts).
	 */
	@Value
	public static class Offensive
	{
		int stab;
		int slash;
		int crush;
		int magic;
		int ranged;

		public static Offensive none()
		{
			return new Offensive(0, 0, 0, 0, 0);
		}

		/**
		 * Explicit replacement for the TS `player.offensive[style.type]` index.
		 */
		public int forStyle(CombatStyleType type)
		{
			if (type == null)
			{
				return 0;
			}

			switch (type)
			{
				case STAB:
					return stab;
				case SLASH:
					return slash;
				case CRUSH:
					return crush;
				case MAGIC:
					return magic;
				case RANGED:
					return ranged;
				default:
					return 0;
			}
		}
	}

	/**
	 * Port of PlayerDefensive (src/types/Player.ts).
	 */
	@Value
	public static class Defensive
	{
		int stab;
		int slash;
		int crush;
		int magic;
		int ranged;

		public static Defensive none()
		{
			return new Defensive(0, 0, 0, 0, 0);
		}
	}

	/**
	 * Port of PlayerEquipment (src/types/Player.ts). Any slot may be null.
	 */
	@Value
	@Builder(toBuilder = true)
	public static class PlayerEquipment
	{
		public static final List<String> SLOTS = Collections.unmodifiableList(java.util.Arrays.asList(
			"head", "cape", "neck", "ammo", "weapon", "body", "shield", "legs", "hands", "feet", "ring"
		));

		@Builder.Default
		EquipmentPiece head = null;

		@Builder.Default
		EquipmentPiece cape = null;

		@Builder.Default
		EquipmentPiece neck = null;

		@Builder.Default
		EquipmentPiece ammo = null;

		@Builder.Default
		EquipmentPiece weapon = null;

		@Builder.Default
		EquipmentPiece body = null;

		@Builder.Default
		EquipmentPiece shield = null;

		@Builder.Default
		EquipmentPiece legs = null;

		@Builder.Default
		EquipmentPiece hands = null;

		@Builder.Default
		EquipmentPiece feet = null;

		@Builder.Default
		EquipmentPiece ring = null;

		/**
		 * Slot name -> piece, in the same slot order as the TS PlayerEquipment interface. Empty slots are
		 * omitted, mirroring the TS code which skips null entries everywhere it iterates.
		 */
		public Map<String, EquipmentPiece> bySlot()
		{
			Map<String, EquipmentPiece> ret = new LinkedHashMap<>();
			putIfPresent(ret, "head", head);
			putIfPresent(ret, "cape", cape);
			putIfPresent(ret, "neck", neck);
			putIfPresent(ret, "ammo", ammo);
			putIfPresent(ret, "weapon", weapon);
			putIfPresent(ret, "body", body);
			putIfPresent(ret, "shield", shield);
			putIfPresent(ret, "legs", legs);
			putIfPresent(ret, "hands", hands);
			putIfPresent(ret, "feet", feet);
			putIfPresent(ret, "ring", ring);
			return ret;
		}

		public List<EquipmentPiece> all()
		{
			return new ArrayList<>(bySlot().values());
		}

		public PlayerEquipment withSlot(String slot, EquipmentPiece piece)
		{
			PlayerEquipmentBuilder b = this.toBuilder();
			switch (slot)
			{
				case "head":
					return b.head(piece).build();
				case "cape":
					return b.cape(piece).build();
				case "neck":
					return b.neck(piece).build();
				case "ammo":
					return b.ammo(piece).build();
				case "weapon":
					return b.weapon(piece).build();
				case "body":
					return b.body(piece).build();
				case "shield":
					return b.shield(piece).build();
				case "legs":
					return b.legs(piece).build();
				case "hands":
					return b.hands(piece).build();
				case "feet":
					return b.feet(piece).build();
				case "ring":
					return b.ring(piece).build();
				default:
					throw new IllegalArgumentException("Unknown equipment slot: " + slot);
			}
		}

		private static void putIfPresent(Map<String, EquipmentPiece> map, String slot, EquipmentPiece piece)
		{
			if (piece != null)
			{
				map.put(slot, piece);
			}
		}
	}
}
