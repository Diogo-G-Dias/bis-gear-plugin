package com.github.diogogdias.bisfinder.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.github.diogogdias.bisfinder.calc.CalcData;
import com.github.diogogdias.bisfinder.calc.model.CombatStyleType;
import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.Player;
import com.github.diogogdias.bisfinder.calc.model.PlayerBuffs;
import com.github.diogogdias.bisfinder.calc.model.PlayerCombatStyle;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * The target-specific rules that sit outside the damage maths: a style the target cannot be hurt by scores
 * nothing, and the revenant-ether weapons are powered up in the Wilderness.
 */
public class SpecialCasesTest
{
	private static List<Monster> monsters;
	private static List<EquipmentPiece> equipment;

	@BeforeClass
	public static void load()
	{
		monsters = read("/wiki/monsters.json", new TypeToken<List<Monster>>()
		{
		}.getType());
		equipment = read("/wiki/equipment.json", new TypeToken<List<EquipmentPiece>>()
		{
		}.getType());
		CalcData.setAvailableEquipment(equipment);
	}

	// --- style immunity -----------------------------------------------------------------------------

	/**
	 * Zulrah stopped being melee-immune on 7 May 2025 - it is now purely a matter of reach, and a halberd has
	 * it. A whip still cannot touch it, and a salamander's scorch is not long enough either.
	 */
	@Test
	public void onlyHalberdsReachZulrah()
	{
		for (Monster form : allNamed("Zulrah"))
		{
			String at = " at Zulrah (" + form.getVersion() + ")";
			assertEquals("a whip cannot reach" + at,
				0.0, dps(weapon("Abyssal whip"), CombatStyleType.SLASH, form, false), 0.0);
			assertEquals("a salamander cannot reach" + at,
				0.0, dps(weapon("Black salamander"), CombatStyleType.SLASH, form, false), 0.0);
			assertTrue("a noxious halberd reaches" + at,
				dps(weapon("Noxious halberd"), CombatStyleType.SLASH, form, false) > 0);
		}
	}

	@Test
	public void zulrahCanStillBeRanged()
	{
		Monster zulrah = named("Zulrah", "Serpentine");
		assertTrue("ranged still scores at Zulrah",
			dps(weapon("Twisted bow"), CombatStyleType.RANGED, zulrah, false) > 0);
	}

	/** Unlike Zulrah, the Kraken and the Leviathan have no halberd exception - melee cannot touch them at all. */
	@Test
	public void krakenAndLeviathanCannotBeMeleedEvenWithAHalberd()
	{
		Monster kraken = byId(494);
		assertEquals(0.0, dps(weapon("Abyssal whip"), CombatStyleType.SLASH, kraken, false), 0.0);
		assertEquals("a halberd is no help at the Kraken",
			0.0, dps(weapon("Noxious halberd"), CombatStyleType.SLASH, kraken, false), 0.0);
		assertTrue("ranged still scores at the Kraken",
			dps(weapon("Twisted bow"), CombatStyleType.RANGED, kraken, false) > 0);

		Monster leviathan = byId(12214);
		assertEquals("a halberd is no help at the Leviathan",
			0.0, dps(weapon("Noxious halberd"), CombatStyleType.SLASH, leviathan, false), 0.0);
	}

	@Test
	public void tektonCannotBeRanged()
	{
		Monster tekton = named("Tekton");
		assertEquals("Tekton shrugs off arrows",
			0.0, dps(weapon("Twisted bow"), CombatStyleType.RANGED, tekton, false), 0.0);
		assertTrue("melee still scores at Tekton",
			dps(weapon("Abyssal whip"), CombatStyleType.SLASH, tekton, false) > 0);
	}

	/**
	 * Halberds have hit every flying enemy since 25 June 2025, so the pre-change "salamanders only" rule must
	 * not be resurrected. A salamander's scorch reaches too; a whip still does not.
	 */
	@Test
	public void aviansiesAreMeleeableByReachAndSalamanderOnly()
	{
		Monster aviansie = byId(3169);
		assertEquals("a whip cannot reach a flying aviansie",
			0.0, dps(weapon("Abyssal whip"), CombatStyleType.SLASH, aviansie, false), 0.0);
		assertTrue("a halberd reaches a flying aviansie",
			dps(weapon("Crystal halberd"), CombatStyleType.SLASH, aviansie, false) > 0);
		assertTrue("a salamander's scorch reaches a flying aviansie",
			dps(weapon("Black salamander"), CombatStyleType.SLASH, aviansie, false) > 0);
	}

	// --- wilderness weapons -------------------------------------------------------------------------

	@Test
	public void chargedWildernessWeaponsGainFiftyPercentInTheWilderness()
	{
		assertScales("Craw's bow", CombatStyleType.RANGED);
		assertScales("Webweaver bow", CombatStyleType.RANGED);
		assertScales("Viggora's chainmace", CombatStyleType.CRUSH);
		assertScales("Ursine chainmace", CombatStyleType.CRUSH);
	}

	/** The max hit is +50%, and the extra accuracy makes the DPS gain strictly larger than that. */
	private void assertScales(String name, CombatStyleType type)
	{
		Monster target = named("Chaos Fanatic");
		EquipmentPiece charged = weapon(name, "Charged");

		int outside = maxHit(charged, type, target, false);
		int inside = maxHit(charged, type, target, true);
		assertEquals(name + " max hit is +50% in the Wilderness", outside + outside / 2, inside);

		assertTrue(name + " gains DPS in the Wilderness",
			dps(charged, type, target, true) > dps(charged, type, target, false));
	}

	@Test
	public void unchargedWildernessWeaponsGainNothing()
	{
		Monster target = named("Chaos Fanatic");
		EquipmentPiece uncharged = weapon("Craw's bow", "Uncharged");
		assertEquals("an uncharged Craw's bow is a plain bow",
			maxHit(uncharged, CombatStyleType.RANGED, target, false),
			maxHit(uncharged, CombatStyleType.RANGED, target, true));
	}

	@Test
	public void ordinaryWeaponsGainNothingInTheWilderness()
	{
		Monster target = named("Chaos Fanatic");
		EquipmentPiece whip = weapon("Abyssal whip");
		assertEquals("a whip is no better in the Wilderness",
			dps(whip, CombatStyleType.SLASH, target, false),
			dps(whip, CombatStyleType.SLASH, target, true), 1e-9);
	}

	// --- helpers ------------------------------------------------------------------------------------

	private static double dps(EquipmentPiece weapon, CombatStyleType type, Monster monster, boolean wildy)
	{
		return DpsEngine.dps(player(weapon, type, wildy, monster), monster);
	}

	private static int maxHit(EquipmentPiece weapon, CombatStyleType type, Monster monster, boolean wildy)
	{
		return DpsEngine.estimate(player(weapon, type, wildy, monster), monster).getMaxHit();
	}

	private static Player player(EquipmentPiece weapon, CombatStyleType type, boolean wildy, Monster monster)
	{
		return Player.builder()
			.style(styleFor(weapon, type))
			.equipment(Player.PlayerEquipment.builder().weapon(weapon).build())
			.buffs(PlayerBuffs.builder().inWilderness(wildy).build())
			.build()
			.withGearBonuses(monster);
	}

	private static PlayerCombatStyle styleFor(EquipmentPiece weapon, CombatStyleType type)
	{
		return PlayerCombatStyle.getCombatStylesForCategory(weapon.getCategory()).stream()
			.filter(s -> s.getType() == type && s.getStance() != null)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no " + type + " style for " + weapon.getName()));
	}

	private static EquipmentPiece weapon(String name)
	{
		return equipment.stream()
			.filter(p -> name.equals(p.getName()) && "weapon".equals(p.getSlot()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no weapon " + name));
	}

	private static EquipmentPiece weapon(String name, String version)
	{
		return equipment.stream()
			.filter(p -> name.equals(p.getName()) && version.equals(p.getVersion()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no weapon " + name + " (" + version + ")"));
	}

	private static Monster named(String name)
	{
		return monsters.stream().filter(m -> name.equals(m.getName())).findFirst()
			.orElseThrow(() -> new IllegalStateException("no monster " + name));
	}

	private static Monster named(String name, String version)
	{
		return monsters.stream()
			.filter(m -> name.equals(m.getName()) && version.equals(m.getVersion()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no monster " + name + " (" + version + ")"));
	}

	private static List<Monster> allNamed(String name)
	{
		List<Monster> found = monsters.stream()
			.filter(m -> name.equals(m.getName()))
			.collect(java.util.stream.Collectors.toList());
		if (found.isEmpty())
		{
			throw new IllegalStateException("no monster " + name);
		}
		return found;
	}

	private static Monster byId(int id)
	{
		return monsters.stream().filter(m -> m.getId() == id).findFirst()
			.orElseThrow(() -> new IllegalStateException("no monster " + id));
	}

	private static <T> T read(String resource, Type type)
	{
		try (InputStream in = SpecialCasesTest.class.getResourceAsStream(resource))
		{
			return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
}
