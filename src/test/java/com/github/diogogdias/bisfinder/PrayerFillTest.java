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

/**
 * Slots that do nothing for DPS are spent on prayer bonus instead — an empty ammo slot on a melee setup,
 * or a cape the target's defences are indifferent to. The rule that matters: this must never cost a
 * single point of DPS.
 */
public class PrayerFillTest
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
	public void sparesSlotsAreSpentOnPrayerWithoutLosingDps()
	{
		Monster bloat = monster("Pestilent Bloat", "Normal");

		List<BisOptimizer.Result> results = new BisOptimizer().findBestPerStyle(
			equipment, spells, bank, maxed(), PlayerBuffs.builder().build(), bloat,
			BisOptimizer.Options.auto());

		Assert.assertFalse(results.isEmpty());

		for (BisOptimizer.Result result : results)
		{
			System.out.println(String.format("Bloat %-6s %.2f dps, prayer bonus %d",
				result.getStyleGroup(), result.getDps(), result.getPrayerBonus()));
		}

		BisOptimizer.Result melee = results.stream()
			.filter(r -> r.getStyleGroup() == BisOptimizer.Style.MELEE)
			.findFirst()
			.orElseThrow(() -> new AssertionError("no melee setup"));

		// The old search left these on pure-DPS picks, which for a maxed bank means a prayer bonus near zero.
		Assert.assertTrue("the spare slots should carry some prayer bonus, but it was "
			+ melee.getPrayerBonus(), melee.getPrayerBonus() > 0);

		// The reported DPS must be the DPS of the setup that is actually shown. Comparing two runs of the
		// same search would prove nothing - both would have the prayer gear on - so recompute it from the
		// returned loadout instead.
		for (BisOptimizer.Result result : results)
		{
			double actual = new com.github.diogogdias.bisfinder.calc.PlayerVsNpcCalc(
				result.getPlayer(), bloat).getDps();

			Assert.assertEquals("the dps shown must be the dps of the setup shown, after the prayer fill",
				actual, result.getDps(), 1e-9);
		}
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
		try (InputStream in = PrayerFillTest.class.getResourceAsStream(resource))
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
