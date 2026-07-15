package com.github.diogogdias.bisfinder;

import com.github.diogogdias.bisfinder.calc.CalcData;
import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.PlayerBuffs;
import com.github.diogogdias.bisfinder.calc.model.PlayerSkills;
import com.github.diogogdias.bisfinder.calc.model.Spell;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** The panel shows one setup per style, and the weapon's special attack alongside each. */
public class PerStyleTest
{
	private static List<EquipmentPiece> equipment;
	private static List<Monster> monsters;
	private static List<Spell> spells;
	private static Set<Integer> bank;

	@BeforeClass
	public static void loadData()
	{
		equipment = read("/wiki/equipment.json", new TypeToken<List<EquipmentPiece>>()
		{
		}.getType());
		monsters = read("/wiki/monsters.json", new TypeToken<List<Monster>>()
		{
		}.getType());
		spells = read("/wiki/spells.json", new TypeToken<List<Spell>>()
		{
		}.getType());
		List<Integer> ids = read("/wiki/sample-bank.json", new TypeToken<List<Integer>>()
		{
		}.getType());
		bank = ids.stream().collect(Collectors.toSet());

		CalcData.setAvailableEquipment(equipment);
		CalcData.setSpells(spells);
	}

	@Test
	public void returnsOneSetupPerStyleBestFirst()
	{
		Monster bloat = monster("Pestilent Bloat", "Normal");

		List<BisOptimizer.Result> results = new BisOptimizer().findBestPerStyle(
			equipment, spells, bank, maxed(), PlayerBuffs.builder().build(), bloat,
			BisOptimizer.Options.auto());

		Assert.assertFalse(results.isEmpty());

		for (BisOptimizer.Result result : results)
		{
			System.out.println(String.format("Bloat %s: %s -> %.2f dps%s",
				result.getStyleGroup(),
				result.getPlayer().getEquipment().getWeapon().getName(),
				result.getDps(),
				result.getSpec() == null ? "" : String.format(" (spec %d max, +%.2f dps)",
					result.getSpec().getMaxHit(), result.getSpec().getDps())));
		}

		// Strongest first, and each attack style (stab/slash/crush/ranged/magic) appears at most once.
		java.util.Set<com.github.diogogdias.bisfinder.calc.model.CombatStyleType> seen = new java.util.HashSet<>();
		java.util.Set<BisOptimizer.Style> groups = new java.util.HashSet<>();
		for (int i = 0; i < results.size(); i++)
		{
			if (i > 0)
			{
				Assert.assertTrue("results must be strongest first",
					results.get(i - 1).getDps() >= results.get(i).getDps());
			}
			Assert.assertTrue("an attack style must not appear twice",
				seen.add(results.get(i).getStyle().getType()));
			groups.add(results.get(i).getStyleGroup());
		}

		// A maxed bank offers all three combat groups; melee is now split into its per-attack-style setups.
		Assert.assertEquals("all three combat groups should be offered",
			java.util.EnumSet.allOf(BisOptimizer.Style.class), groups);
	}

	/**
	 * A magic-only bank still resolves to the right weapon and a scored magic setup. Special-attack output is
	 * deferred in the clean-room engine (the panel hides the spec block until it is reimplemented), so the
	 * result's spec is expected to be null for now.
	 */
	@Test
	public void magicOnlyBankResolvesToTheRightWeapon()
	{
		Monster target = monster("Abyssal demon", "Standard");

		Set<Integer> owned = idsFor("Eye of ayak", "Ancestral hat", "Ancestral robe top",
			"Ancestral robe bottom", "Occult necklace", "Tormented bracelet", "Eternal boots",
			"Imbued zamorak cape", "Magus ring");

		List<BisOptimizer.Result> results = new BisOptimizer().findBestPerStyle(
			equipment, spells, owned, maxed(), PlayerBuffs.builder().build(), target,
			BisOptimizer.Options.auto());

		Assert.assertEquals("only magic is possible with this bank", 1, results.size());

		BisOptimizer.Result magic = results.get(0);
		Assert.assertEquals("Eye of ayak", magic.getPlayer().getEquipment().getWeapon().getName());
		Assert.assertEquals(BisOptimizer.Style.MAGIC, magic.getStyleGroup());
		Assert.assertTrue("the magic setup should be scored", magic.getDps() > 0);
		Assert.assertNull("special attacks are deferred in the clean-room engine", magic.getSpec());
	}

	private static Set<Integer> idsFor(String... names)
	{
		List<String> wanted = java.util.Arrays.asList(names);
		return equipment.stream()
			.filter(piece -> wanted.contains(piece.getName()))
			.map(EquipmentPiece::getId)
			.collect(Collectors.toSet());
	}

	private static PlayerSkills maxed()
	{
		return new PlayerSkills(99, 99, 99, 99, 99, 99, 99, 99, 99);
	}

	private static Monster monster(String name, String version)
	{
		return monsters.stream()
			.filter(m -> name.equals(m.getName()) && version.equals(m.getVersion()))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("no monster " + name));
	}

	private static <T> T read(String resource, Type type)
	{
		try (InputStream in = PerStyleTest.class.getResourceAsStream(resource))
		{
			if (in == null)
			{
				throw new IllegalStateException("missing " + resource);
			}
			return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
		}
		catch (Exception e)
		{
			throw new IllegalStateException("could not read " + resource, e);
		}
	}
}
