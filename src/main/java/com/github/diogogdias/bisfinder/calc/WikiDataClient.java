package com.github.diogogdias.bisfinder.calc;

import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.Spell;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Reads equipment and monster data from the OSRS Wiki DPS calculator's published JSON. Both files are
 * cached on disk, so a normal session makes no network calls at all.
 *
 * <p>Unlike GearScape's data, the IDs here are real in-game item/NPC IDs, so bank items map directly.
 *
 * <p>All methods block and must be called off the client thread.
 */
@Slf4j
@Singleton
public class WikiDataClient
{
	private static final HttpUrl BASE =
		HttpUrl.get("https://raw.githubusercontent.com/weirdgloop/osrs-dps-calc/main/cdn/json/");
	private static final Duration CACHE_TTL = Duration.ofDays(7);
	private static final File CACHE_DIR = new File(RuneLite.RUNELITE_DIR, "bis-finder");

	private static final String EQUIPMENT_FILE = "wiki-equipment.json";
	private static final String MONSTERS_FILE = "wiki-monsters.json";
	private static final String SPELLS_FILE = "wiki-spells.json";

	private static final Type EQUIPMENT_TYPE = new TypeToken<List<EquipmentPiece>>()
	{
	}.getType();
	private static final Type MONSTERS_TYPE = new TypeToken<List<Monster>>()
	{
	}.getType();
	private static final Type SPELLS_TYPE = new TypeToken<List<Spell>>()
	{
	}.getType();

	private final OkHttpClient httpClient;
	private final Gson gson;

	private List<EquipmentPiece> equipment;
	private Map<Integer, EquipmentPiece> equipmentById;
	private List<Monster> monsters;
	private Map<Integer, Monster> monstersById;
	private List<Spell> spells;

	@Inject
	public WikiDataClient(OkHttpClient httpClient, Gson gson)
	{
		this.httpClient = httpClient;
		// The JSON mixes camelCase and snake_case; the models carry @SerializedName where they differ, so
		// no naming policy must be applied on top.
		this.gson = gson.newBuilder()
			.setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
			.create();
	}

	public synchronized List<EquipmentPiece> equipment() throws IOException
	{
		if (equipment == null)
		{
			List<EquipmentPiece> loaded = fetch("equipment.json", EQUIPMENT_FILE, EQUIPMENT_TYPE);
			Map<Integer, EquipmentPiece> byId = new HashMap<>(loaded.size());
			for (EquipmentPiece piece : loaded)
			{
				byId.putIfAbsent(piece.getId(), piece);
			}

			equipment = Collections.unmodifiableList(loaded);
			equipmentById = byId;
		}

		return equipment;
	}

	public synchronized List<Monster> monsters() throws IOException
	{
		if (monsters == null)
		{
			List<Monster> loaded = fetch("monsters.json", MONSTERS_FILE, MONSTERS_TYPE);
			Map<Integer, Monster> byId = new HashMap<>(loaded.size());
			for (Monster monster : loaded)
			{
				// A few NPC IDs appear more than once (one entry per version) - keep the first.
				byId.putIfAbsent(monster.getId(), monster);
			}

			monsters = Collections.unmodifiableList(loaded);
			monstersById = byId;
		}

		return monsters;
	}

	public synchronized List<Spell> spells() throws IOException
	{
		if (spells == null)
		{
			spells = Collections.unmodifiableList(fetch("spells.json", SPELLS_FILE, SPELLS_TYPE));
		}

		return spells;
	}

	/**
	 * Loads everything the calculator needs and publishes it to {@link CalcData}, which the ported engine
	 * reads for item canonicalisation, blowpipe darts and elemental spell max hits.
	 */
	public synchronized void loadAll() throws IOException
	{
		CalcData.setAvailableEquipment(equipment());
		CalcData.setSpells(spells());
		monsters();
	}

	/**
	 * @return the equipment piece with the given in-game item ID, or null if the wiki has no data for it
	 */
	public EquipmentPiece byId(int id) throws IOException
	{
		equipment();
		return equipmentById.get(id);
	}

	/**
	 * @return the monster with the given in-game NPC ID, or null if the wiki has no data for it. Where an
	 * NPC ID has several versions, the first one in the file is returned.
	 */
	public Monster monsterById(int id) throws IOException
	{
		monsters();
		return monstersById.get(id);
	}

	private <T> T fetch(String remoteFile, String cacheFile, Type type) throws IOException
	{
		File cached = new File(CACHE_DIR, cacheFile);
		T fromCache = readCache(cached, type);
		if (fromCache != null)
		{
			return fromCache;
		}

		HttpUrl url = BASE.newBuilder().addPathSegment(remoteFile).build();
		Request request = new Request.Builder().url(url).build();
		try (Response response = httpClient.newCall(request).execute())
		{
			ResponseBody body = response.body();
			if (!response.isSuccessful() || body == null)
			{
				throw new IOException("Wiki data request returned " + response.code() + " for " + url);
			}

			String json = body.string();
			writeCache(cached, json);
			return gson.fromJson(json, type);
		}
		catch (JsonSyntaxException e)
		{
			throw new IOException("Could not parse wiki data for " + url, e);
		}
	}

	private <T> T readCache(File file, Type type)
	{
		if (!file.isFile())
		{
			return null;
		}

		Instant modified = Instant.ofEpochMilli(file.lastModified());
		if (modified.plus(CACHE_TTL).isBefore(Instant.now()))
		{
			return null;
		}

		try
		{
			return gson.fromJson(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8), type);
		}
		catch (IOException | JsonSyntaxException e)
		{
			log.debug("Discarding unreadable cache file {}", file, e);
			return null;
		}
	}

	private void writeCache(File file, String json)
	{
		try
		{
			Files.createDirectories(CACHE_DIR.toPath());
			Files.write(file.toPath(), json.getBytes(StandardCharsets.UTF_8));
		}
		catch (IOException e)
		{
			log.debug("Could not cache {}", file, e);
		}
	}
}
