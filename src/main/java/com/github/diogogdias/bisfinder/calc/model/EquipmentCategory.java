package com.github.diogogdias.bisfinder.calc.model;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;

/**
 * The combat style category of a piece of equipment, mirroring the wiki calculator's EquipmentCategory
 * enum.
 *
 * @see <a href="https://oldschool.runescape.wiki/w/Property:Combat_style">Combat style</a>
 */
@JsonAdapter(EquipmentCategory.Adapter.class)
public enum EquipmentCategory
{
	NONE(""),
	TWO_HANDED_SWORD("2h Sword"),
	AXE("Axe"),
	BANNER("Banner"),
	BLADED_STAFF("Bladed Staff"),
	BLASTER("Blaster"),
	BLUDGEON("Bludgeon"),
	BLUNT("Blunt"),
	BOW("Bow"),
	BULWARK("Bulwark"),
	CHINCHOMPA("Chinchompas"),
	CLAW("Claw"),
	CROSSBOW("Crossbow"),
	DAGGER("Dagger"),
	FLAIL("Flail"),
	GUN("Gun"),
	MULTI_MELEE("Multi-Melee"),
	PARTISAN("Partisan"),
	PICKAXE("Pickaxe"),
	POLEARM("Polearm"),
	POLESTAFF("Polestaff"),
	POWERED_STAFF("Powered Staff"),
	POWERED_WAND("Powered Wand"),
	SALAMANDER("Salamander"),
	SCYTHE("Scythe"),
	SLASH_SWORD("Slash Sword"),
	SPEAR("Spear"),
	SPIKED("Spiked"),
	STAB_SWORD("Stab Sword"),
	STAFF("Staff"),
	THROWN("Thrown"),
	UNARMED("Unarmed"),
	WHIP("Whip"),

	/**
	 * Any category the wiki adds that this plugin does not know about yet. Never appears in the JSON.
	 */
	UNKNOWN("Unknown");

	private static final Map<String, EquipmentCategory> BY_JSON_VALUE = new HashMap<>();

	static
	{
		for (EquipmentCategory category : values())
		{
			BY_JSON_VALUE.put(key(category.jsonValue), category);
		}
	}

	@Getter
	private final String jsonValue;

	EquipmentCategory(String jsonValue)
	{
		this.jsonValue = jsonValue;
	}

	/**
	 * Tolerant lookup: unknown or missing categories map to {@link #UNKNOWN} / {@link #NONE} rather than
	 * throwing, so new wiki items do not break parsing.
	 */
	public static EquipmentCategory fromJson(String value)
	{
		if (value == null || value.isEmpty())
		{
			return NONE;
		}

		return BY_JSON_VALUE.getOrDefault(key(value), UNKNOWN);
	}

	private static String key(String value)
	{
		return value.toLowerCase(Locale.ROOT);
	}

	static final class Adapter extends TypeAdapter<EquipmentCategory>
	{
		@Override
		public void write(JsonWriter out, EquipmentCategory value) throws IOException
		{
			out.value(value == null ? null : value.getJsonValue());
		}

		@Override
		public EquipmentCategory read(JsonReader in) throws IOException
		{
			if (in.peek() == JsonToken.NULL)
			{
				in.nextNull();
				return NONE;
			}

			// Qualified: an unqualified fromJson() would resolve to TypeAdapter.fromJson(String).
			return EquipmentCategory.fromJson(in.nextString());
		}
	}
}
