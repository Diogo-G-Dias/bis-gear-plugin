package com.github.diogogdias.bisfinder.calc;

import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.Spell;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Loads the OSRS Wiki calculator's own data files from test resources, so the ported engine can be
 * checked against the numbers their test suite asserts.
 */
final class WikiTestData
{
	private static final Gson GSON = new Gson();

	private static List<EquipmentPiece> equipment;
	private static List<Monster> monsters;
	private static List<Spell> spells;

	private WikiTestData()
	{
	}

	static synchronized void load()
	{
		if (equipment != null)
		{
			return;
		}

		equipment = read("/wiki/equipment.json", new TypeToken<List<EquipmentPiece>>()
		{
		}.getType());
		monsters = read("/wiki/monsters.json", new TypeToken<List<Monster>>()
		{
		}.getType());
		spells = read("/wiki/spells.json", new TypeToken<List<Spell>>()
		{
		}.getType());

		CalcData.setAvailableEquipment(equipment);
		CalcData.setSpells(spells);
	}

	static EquipmentPiece equipmentById(int id)
	{
		load();
		return equipment.stream()
			.filter(e -> e.getId() == id)
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("no equipment with id " + id));
	}

	/** The first equipment piece with this name; names are not unique across versions. */
	static EquipmentPiece equipmentByName(String name)
	{
		load();
		return equipment.stream()
			.filter(e -> name.equals(e.getName()))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("no equipment named " + name));
	}

	/** Their getTestMonsterById: the first monster with this id, as monster ids are not unique. */
	static Monster monsterById(int id)
	{
		load();
		return monsters.stream()
			.filter(m -> m.getId() == id)
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("no monster with id " + id));
	}

	static Spell spellByName(String name)
	{
		load();
		return spells.stream()
			.filter(s -> s.getName().equals(name))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("no spell named " + name));
	}

	private static <T> T read(String resource, Type type)
	{
		try (InputStream in = WikiTestData.class.getResourceAsStream(resource))
		{
			if (in == null)
			{
				throw new IllegalStateException("missing test resource " + resource);
			}
			return GSON.fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
		}
		catch (IOException e)
		{
			throw new IllegalStateException("could not read " + resource, e);
		}
	}
}
