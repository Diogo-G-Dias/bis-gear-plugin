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
 * How resistant a monster is to burn damage. Null in the JSON means the monster is fully immune to
 * burn (see the wiki calculator, which treats a missing value as "no burn").
 */
@JsonAdapter(BurnImmunity.Adapter.class)
public enum BurnImmunity
{
	WEAK("Weak"),
	NORMAL("Normal"),
	STRONG("Strong"),

	/**
	 * Any severity the wiki adds that this plugin does not know about yet. Never appears in the JSON.
	 */
	UNKNOWN("Unknown");

	private static final Map<String, BurnImmunity> BY_JSON_VALUE = new HashMap<>();

	static
	{
		for (BurnImmunity immunity : values())
		{
			BY_JSON_VALUE.put(immunity.jsonValue.toLowerCase(Locale.ROOT), immunity);
		}
	}

	@Getter
	private final String jsonValue;

	BurnImmunity(String jsonValue)
	{
		this.jsonValue = jsonValue;
	}

	/**
	 * Tolerant lookup: null stays null (the monster cannot be burnt), unrecognised values map to
	 * {@link #UNKNOWN} rather than throwing.
	 */
	public static BurnImmunity fromJson(String value)
	{
		if (value == null)
		{
			return null;
		}

		return BY_JSON_VALUE.getOrDefault(value.toLowerCase(Locale.ROOT), UNKNOWN);
	}

	static final class Adapter extends TypeAdapter<BurnImmunity>
	{
		@Override
		public void write(JsonWriter out, BurnImmunity value) throws IOException
		{
			out.value(value == null ? null : value.getJsonValue());
		}

		@Override
		public BurnImmunity read(JsonReader in) throws IOException
		{
			if (in.peek() == JsonToken.NULL)
			{
				in.nextNull();
				return null;
			}

			// Qualified: an unqualified fromJson() would resolve to TypeAdapter.fromJson(String).
			return BurnImmunity.fromJson(in.nextString());
		}
	}
}
