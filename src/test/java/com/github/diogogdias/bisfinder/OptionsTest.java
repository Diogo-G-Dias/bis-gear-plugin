package com.github.diogogdias.bisfinder;

import com.github.diogogdias.bisfinder.calc.CalcData;
import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.PlayerBuffs;
import com.github.diogogdias.bisfinder.calc.model.PlayerSkills;
import com.github.diogogdias.bisfinder.calc.model.Potion;
import com.github.diogogdias.bisfinder.calc.model.Prayer;
import com.github.diogogdias.bisfinder.calc.model.Spell;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** The advanced options have to actually change the search, not just the words in the panel. */
public class OptionsTest
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
	public void excludedItemIsNeverSuggested()
	{
		Monster bloat = monster("Pestilent Bloat", "Normal");

		BisOptimizer.Result before = find(bloat, BisOptimizer.Options.auto());
		int weapon = before.getPlayer().getEquipment().getWeapon().getId();

		BisOptimizer.Result after = find(bloat, new BisOptimizer.Options(
			null, null, false, Collections.singleton(weapon)));

		Assert.assertNotEquals("the excluded weapon must not come back",
			weapon, after.getPlayer().getEquipment().getWeapon().getId());

		// Excluding the best weapon should cost DPS, not silently return the same setup.
		Assert.assertTrue("excluding the best weapon should lower dps",
			after.getDps() < before.getDps());
	}

	@Test
	public void prayerAndPotionOverridesAreUsed()
	{
		Monster bloat = monster("Pestilent Bloat", "Normal");

		BisOptimizer.Result auto = find(bloat, BisOptimizer.Options.auto());
		Assert.assertEquals(Prayer.PIETY, auto.getPrayer());
		Assert.assertEquals(Potion.SUPER_COMBAT, auto.getPotion());

		BisOptimizer.Result overridden = find(bloat, new BisOptimizer.Options(
			Prayer.CHIVALRY, Potion.NONE, false, Collections.emptySet()));

		Assert.assertEquals(Prayer.CHIVALRY, overridden.getPrayer());
		Assert.assertEquals(Potion.NONE, overridden.getPotion());
		Assert.assertTrue("a weaker prayer and no potion must lower dps",
			overridden.getDps() < auto.getDps());
	}

	@Test
	public void prayingNothingIsHonoured()
	{
		Monster bloat = monster("Pestilent Bloat", "Normal");

		BisOptimizer.Result none = find(bloat, new BisOptimizer.Options(
			null, null, true, Collections.emptySet()));

		Assert.assertNull("no prayer means no prayer", none.getPrayer());
		Assert.assertTrue("praying nothing must lower dps",
			none.getDps() < find(bloat, BisOptimizer.Options.auto()).getDps());
	}

	private BisOptimizer.Result find(Monster target, BisOptimizer.Options options)
	{
		BisOptimizer.Result result = new BisOptimizer().findBest(
			equipment, spells, bank, maxed(), PlayerBuffs.builder().build(), target, options);
		Assert.assertNotNull(result);
		return result;
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
		try (InputStream in = OptionsTest.class.getResourceAsStream(resource))
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
