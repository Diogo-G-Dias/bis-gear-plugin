package com.github.diogogdias.bisfinder.calc.model;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.Getter;

/**
 * The attributes a monster can have.
 *
 * @see <a href="https://oldschool.runescape.wiki/w/Monster_attribute">Monster attribute</a>
 */
@JsonAdapter(MonsterAttribute.Adapter.class)
public enum MonsterAttribute
{
	DEMON("demon"),
	DRAGON("dragon"),
	FIERY("fiery"),
	FLYING("flying"),
	GOLEM("golem"),
	KALPHITE("kalphite"),
	LEAFY("leafy"),
	PENANCE("penance"),
	RAT("rat"),
	SHADE("shade"),
	SPECTRAL("spectral"),
	UNDEAD("undead"),
	VAMPYRE_1("vampyre1"),
	VAMPYRE_2("vampyre2"),
	VAMPYRE_3("vampyre3"),
	XERICIAN("xerician"),

	/**
	 * Any attribute the wiki adds that this plugin does not know about yet. Never appears in the JSON.
	 */
	UNKNOWN("unknown");

	private static final List<MonsterAttribute> VAMPYRES = Arrays.asList(VAMPYRE_1, VAMPYRE_2, VAMPYRE_3);
	private static final Map<String, MonsterAttribute> BY_JSON_VALUE = new HashMap<>();

	static
	{
		for (MonsterAttribute attribute : values())
		{
			BY_JSON_VALUE.put(attribute.jsonValue.toLowerCase(Locale.ROOT), attribute);
		}
	}

	@Getter
	private final String jsonValue;

	MonsterAttribute(String jsonValue)
	{
		this.jsonValue = jsonValue;
	}

	/**
	 * Tolerant lookup: unknown attributes map to {@link #UNKNOWN} rather than throwing, so new wiki
	 * monsters do not break parsing.
	 */
	public static MonsterAttribute fromJson(String value)
	{
		if (value == null)
		{
			return UNKNOWN;
		}

		return BY_JSON_VALUE.getOrDefault(value.toLowerCase(Locale.ROOT), UNKNOWN);
	}

	public boolean isVampyre()
	{
		return VAMPYRES.contains(this);
	}

	static final class Adapter extends TypeAdapter<MonsterAttribute>
	{
		@Override
		public void write(JsonWriter out, MonsterAttribute value) throws IOException
		{
			out.value(value == null ? null : value.getJsonValue());
		}

		@Override
		public MonsterAttribute read(JsonReader in) throws IOException
		{
			if (in.peek() == JsonToken.NULL)
			{
				in.nextNull();
				return UNKNOWN;
			}

			// Qualified: an unqualified fromJson() would resolve to TypeAdapter.fromJson(String).
			return MonsterAttribute.fromJson(in.nextString());
		}
	}
}
