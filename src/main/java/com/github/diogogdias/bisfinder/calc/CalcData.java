package com.github.diogogdias.bisfinder.calc;

import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Spell;
import com.google.gson.stream.JsonReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds the shared game-data datasets (the equipment list, the spell list and the equipment alias map)
 * that the calculator needs global access to.
 *
 * <p>The equipment and spell lists are supplied by the caller (see WikiDataClient); the alias map ships
 * with the plugin as a resource, because the calculator cannot canonicalise item IDs without it.
 */
public final class CalcData
{
	private static final String ALIASES_RESOURCE = "/com/github/diogogdias/bisfinder/calc/equipment_aliases.json";

	private static volatile List<EquipmentPiece> availableEquipment = Collections.emptyList();
	private static volatile Map<Integer, EquipmentPiece> equipmentById = Collections.emptyMap();
	private static volatile List<Spell> spells = Collections.emptyList();
	private static volatile Map<Integer, Integer> equipmentAliases;

	private CalcData()
	{
	}

	public static void setAvailableEquipment(List<EquipmentPiece> equipment)
	{
		Map<Integer, EquipmentPiece> byId = new HashMap<>(equipment.size());
		for (EquipmentPiece piece : equipment)
		{
			byId.putIfAbsent(piece.getId(), piece);
		}

		availableEquipment = Collections.unmodifiableList(new java.util.ArrayList<>(equipment));
		equipmentById = Collections.unmodifiableMap(byId);
	}

	public static List<EquipmentPiece> getAvailableEquipment()
	{
		return availableEquipment;
	}

	/**
	 * @return the equipment piece with the given item ID, or null if unknown
	 */
	public static EquipmentPiece equipmentById(int id)
	{
		return equipmentById.get(id);
	}

	public static void setSpells(List<Spell> allSpells)
	{
		spells = Collections.unmodifiableList(new java.util.ArrayList<>(allSpells));
	}

	public static List<Spell> getSpells()
	{
		return spells;
	}

	/**
	 * Variant item ID -> canonical (base) item ID.
	 */
	public static Map<Integer, Integer> getEquipmentAliases()
	{
		Map<Integer, Integer> aliases = equipmentAliases;
		if (aliases == null)
		{
			synchronized (CalcData.class)
			{
				aliases = equipmentAliases;
				if (aliases == null)
				{
					aliases = loadAliases();
					equipmentAliases = aliases;
				}
			}
		}
		return aliases;
	}

	private static Map<Integer, Integer> loadAliases()
	{
		// Read with the streaming JsonReader rather than a Gson instance: the plugin hub rejects plugins that
		// construct their own Gson, and this static holder has no injector to take the client's Gson from.
		try (InputStream in = CalcData.class.getResourceAsStream(ALIASES_RESOURCE))
		{
			if (in == null)
			{
				return Collections.emptyMap();
			}

			Map<Integer, Integer> parsed = new HashMap<>();
			try (JsonReader reader = new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8)))
			{
				reader.beginObject();
				while (reader.hasNext())
				{
					parsed.put(Integer.parseInt(reader.nextName()), reader.nextInt());
				}
				reader.endObject();
			}
			return Collections.unmodifiableMap(parsed);
		}
		catch (IOException e)
		{
			return Collections.emptyMap();
		}
	}
}
