package com.github.diogogdias.bisfinder.calc.model;

import com.google.gson.annotations.SerializedName;
import lombok.Value;
import lombok.With;

/**
 * A single piece of equipment from the OSRS Wiki DPS calculator's equipment.json. The {@code id} is a
 * real in-game item ID, so it maps directly onto bank/inventory items.
 *
 * @see <a href="https://github.com/weirdgloop/osrs-dps-calc/blob/main/cdn/json/equipment.json">equipment.json</a>
 */
@Value
public class EquipmentPiece
{
	int id;
	String name;
	/**
	 * The item's variant, e.g. "Charged" or "Nightmare Zone". Empty string when the item has no variants.
	 */
	String version;
	/**
	 * One of: head, cape, neck, ammo, weapon, body, shield, legs, hands, feet, ring.
	 */
	String slot;
	String image;
	double weight;
	/**
	 * Attack speed in ticks. Zero for non-weapons.
	 */
	int speed;
	EquipmentCategory category;
	boolean isTwoHanded;
	Bonuses bonuses;
	Offensive offensive;
	Defensive defensive;
	/**
	 * Not present in equipment.json: per-item user choices (currently only the blowpipe's dart). Null
	 * when the item has none.
	 */
	@With
	ItemVars itemVars;

	@Value
	public static class ItemVars
	{
		Integer blowpipeDartId;
		String blowpipeDartName;
	}

	@Value
	public static class Bonuses
	{
		int str;
		@SerializedName("ranged_str")
		int rangedStr;
		@SerializedName("magic_str")
		int magicStr;
		int prayer;
	}

	@Value
	public static class Offensive
	{
		int stab;
		int slash;
		int crush;
		int magic;
		int ranged;
	}

	@Value
	public static class Defensive
	{
		int stab;
		int slash;
		int crush;
		int magic;
		int ranged;
	}
}
