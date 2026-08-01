package com.github.diogogdias.bisfinder.calc;

import com.github.diogogdias.bisfinder.calc.model.EquipmentCategory;
import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.MonsterAttribute;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Parses the wiki calculator's equipment.json and monsters.json off disk (no network) through the Gson
 * models, to catch shape drift between the models and the real data.
 *
 * <p>Point {@code -Dbisfinder.wikiJsonDir=...} at a directory containing equipment.json and
 * monsters.json to use a different copy.
 */
public class WikiDataParsingTest
{
	private static final String DEFAULT_JSON_DIR =
		"C:\\Users\\User\\AppData\\Local\\Temp\\claude\\C--Users-User-runelite-plugins-bis-finder"
			+ "\\528c8f01-6022-495b-94a5-8035a2d3d2ab\\scratchpad\\osrs-dps-calc\\cdn\\json";

	private static final Type EQUIPMENT_TYPE = new TypeToken<List<EquipmentPiece>>()
	{
	}.getType();
	private static final Type MONSTERS_TYPE = new TypeToken<List<Monster>>()
	{
	}.getType();

	private static List<EquipmentPiece> equipment;
	private static List<Monster> monsters;

	@BeforeClass
	public static void parse() throws IOException
	{
		Path dir = Paths.get(System.getProperty("bisfinder.wikiJsonDir", DEFAULT_JSON_DIR));

		// This test validates the models against a full local weirdgloop dataset, which only exists when a dev
		// points -Dbisfinder.wikiJsonDir at a checkout. Skip (don't fail) when it is absent, so the suite is
		// green on CI and any other machine - the default path is one dev's now-deleted scratchpad clone.
		Assume.assumeTrue("wiki JSON dir not present: " + dir + " (set -Dbisfinder.wikiJsonDir to run)",
			Files.isReadable(dir.resolve("equipment.json")) && Files.isReadable(dir.resolve("monsters.json")));

		Gson gson = new Gson();

		try (Reader reader = Files.newBufferedReader(dir.resolve("equipment.json"), StandardCharsets.UTF_8))
		{
			equipment = gson.fromJson(reader, EQUIPMENT_TYPE);
		}

		try (Reader reader = Files.newBufferedReader(dir.resolve("monsters.json"), StandardCharsets.UTF_8))
		{
			monsters = gson.fromJson(reader, MONSTERS_TYPE);
		}
	}

	@Test
	public void testCounts()
	{
		assertEquals(5329, equipment.size());
		assertEquals(2852, monsters.size());
	}

	@Test
	public void testAbyssalWhip()
	{
		EquipmentPiece whip = equipment.stream()
			.filter(e -> e.getId() == 4151)
			.findFirst()
			.orElse(null);

		assertNotNull(whip);
		assertEquals("Abyssal whip", whip.getName());
		assertEquals("weapon", whip.getSlot());
		assertEquals(EquipmentCategory.WHIP, whip.getCategory());
		assertEquals(4, whip.getSpeed());
		assertEquals(82, whip.getOffensive().getSlash());
		assertEquals(82, whip.getBonuses().getStr());
		assertEquals(0, whip.getBonuses().getRangedStr());
		assertTrue(!whip.isTwoHanded());
	}

	@Test
	public void testVorkath()
	{
		Monster vorkath = monsters.stream()
			.filter(m -> m.getId() == 8058 && Objects.equals(m.getVersion(), "Dragon Slayer II"))
			.findFirst()
			.orElse(null);

		assertNotNull(vorkath);
		assertEquals("Vorkath", vorkath.getName());
		assertTrue(vorkath.getAttributes().contains(MonsterAttribute.DRAGON));
		assertTrue(vorkath.getAttributes().contains(MonsterAttribute.UNDEAD));
		assertEquals(204, vorkath.getDefensive().getMagic());
		assertEquals(460, vorkath.getSkills().getHp());
		assertTrue(vorkath.isSlayerMonster());
		assertEquals("fire", vorkath.getWeakness().getElement());
		assertEquals(40, vorkath.getWeakness().getSeverity());
	}

	@Test
	public void testNoUnknownEnumValues()
	{
		assertTrue(equipment.stream().noneMatch(e -> e.getCategory() == EquipmentCategory.UNKNOWN));
		assertTrue(monsters.stream()
			.flatMap(m -> m.getAttributes().stream())
			.noneMatch(a -> a == MonsterAttribute.UNKNOWN));
	}

	@Test
	public void testNullableFieldsAreTolerated()
	{
		// 1638 monsters have no weakness, 34 have no style, and burn immunity is null for most.
		assertTrue(monsters.stream().anyMatch(m -> m.getWeakness() == null));
		assertTrue(monsters.stream().allMatch(m -> m.getStyle() != null));
		assertTrue(monsters.stream().anyMatch(m -> m.getImmunities().getBurn() == null));
		// max_hit is a string in the JSON, but a bare number for some monsters.
		assertEquals("30 (Magic)", monsters.stream()
			.filter(m -> m.getId() == 8058 && Objects.equals(m.getVersion(), "Dragon Slayer II"))
			.findFirst()
			.orElseThrow(AssertionError::new)
			.getMaxHit());
	}
}
