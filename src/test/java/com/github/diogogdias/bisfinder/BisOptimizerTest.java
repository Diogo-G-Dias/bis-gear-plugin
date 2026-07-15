package com.github.diogogdias.bisfinder;

import com.github.diogogdias.bisfinder.calc.CalcData;
import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.PlayerBuffs;
import com.github.diogogdias.bisfinder.calc.model.PlayerSkills;
import com.github.diogogdias.bisfinder.calc.model.Potion;
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

public class BisOptimizerTest
{
	private static List<EquipmentPiece> equipment;
	private static List<Monster> monsters;
	private static List<Spell> spells;

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

		CalcData.setAvailableEquipment(equipment);
		CalcData.setSpells(spells);
	}

	/**
	 * The worst case for the search: a player who owns every item in the game. It has to stay fast enough
	 * to run on a sidebar button press, and it has to pick a setup that actually beats the alternatives.
	 */
	@Test
	public void findsBestSetupAgainstVorkathWithEverythingOwned()
	{
		Monster vorkath = monster("Vorkath", "Dragon Slayer II");
		Set<Integer> everything = equipment.stream()
			.map(EquipmentPiece::getId)
			.collect(Collectors.toSet());

		long start = System.currentTimeMillis();
		BisOptimizer.Result result = new BisOptimizer().findBest(
			equipment, spells, everything, maxed(), PlayerBuffs.builder().build(), vorkath);
		long elapsed = System.currentTimeMillis() - start;

		Assert.assertNotNull("a maxed player owning every item must have some setup", result);
		Assert.assertTrue("dps must be positive, was " + result.getDps(), result.getDps() > 0);

		String weapon = result.getPlayer().getEquipment().getWeapon().getName();
		System.out.println(String.format(
			"Vorkath: %s (%s) -> %.2f dps, max %d, acc %.1f%% in %dms",
			weapon, result.getStyle().getName(), result.getDps(), result.getMaxHit(),
			result.getAccuracy() * 100, elapsed));

		// Asserting a specific weapon here would just encode a guess. The invariant that actually matters is
		// that the search never returns something a player could beat by hand: a Dragon hunter lance is the
		// obvious melee answer at a dragon, so whatever wins must at least match it.
		double lance = dpsOf(dragonHunterLanceSetup(vorkath), vorkath);
		System.out.println(String.format("Vorkath: hand-built dragon hunter lance -> %.2f dps", lance));

		Assert.assertTrue(
			String.format("optimizer returned %s at %.2f dps, but a hand-built dragon hunter lance does %.2f",
				weapon, result.getDps(), lance),
			result.getDps() >= lance - 0.001);

		Assert.assertTrue("search took " + elapsed + "ms, too slow for a button press", elapsed < 30_000);
	}

	/** A player who owns a full void set but little else should actually be given the void set. */
	@Test
	public void wearsVoidWhenItIsTheBestSetOwned()
	{
		Monster target = monster("Abyssal demon", "Standard");

		Set<Integer> owned = idsFor(
			"Void melee helm", "Void knight top", "Void knight robe", "Void knight gloves",
			"Abyssal whip", "Amulet of glory", "Fire cape", "Dragon boots", "Berserker ring");

		BisOptimizer.Result result = new BisOptimizer().findBest(
			equipment, spells, owned, maxed(), PlayerBuffs.builder().build(), target);

		Assert.assertNotNull(result);

		List<String> worn = result.getPlayer().getEquipment().all().stream()
			.map(EquipmentPiece::getName)
			.collect(Collectors.toList());

		Assert.assertTrue("void set should be worn, but got " + worn, worn.contains("Void melee helm")
			&& worn.contains("Void knight top") && worn.contains("Void knight robe")
			&& worn.contains("Void knight gloves"));
	}

	/** Max melee with the obvious dragonbane weapon, as a player would build it. */
	private com.github.diogogdias.bisfinder.calc.model.Player dragonHunterLanceSetup(Monster vorkath)
	{
		com.github.diogogdias.bisfinder.calc.model.Player.PlayerEquipment gear =
			com.github.diogogdias.bisfinder.calc.model.Player.PlayerEquipment.builder()
				.weapon(byName("Dragon hunter lance"))
				.shield(byName("Avernic defender"))
				.head(byName("Torva full helm"))
				.body(byName("Torva platebody"))
				.legs(byName("Torva platelegs"))
				.cape(byName("Infernal cape"))
				.neck(byName("Amulet of torture"))
				.hands(byName("Ferocious gloves"))
				.feet(byName("Primordial boots"))
				.ring(byName("Ultor ring"))
				.build();

		// Same prayer and potion the optimizer assumes, or the comparison is not like for like.
		return com.github.diogogdias.bisfinder.calc.model.Player.builder()
			.skills(maxed())
			.boosts(Potion.SUPER_COMBAT.boost(maxed()))
			.prayers(java.util.Collections.singletonList(
				com.github.diogogdias.bisfinder.calc.model.Prayer.PIETY))
			.buffs(PlayerBuffs.builder().build())
			.style(new com.github.diogogdias.bisfinder.calc.model.PlayerCombatStyle(
				"Lunge",
				com.github.diogogdias.bisfinder.calc.model.CombatStyleType.STAB,
				com.github.diogogdias.bisfinder.calc.model.CombatStyleStance.AGGRESSIVE))
			.equipment(gear)
			.build()
			.withGearBonuses(vorkath);
	}

	private double dpsOf(com.github.diogogdias.bisfinder.calc.model.Player player, Monster monster)
	{
		return com.github.diogogdias.bisfinder.engine.DpsEngine.dps(player, monster);
	}

	private static EquipmentPiece byName(String name)
	{
		return equipment.stream()
			.filter(piece -> piece.getName().equals(name))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("no item named " + name));
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
			.orElseThrow(() -> new IllegalArgumentException("no monster " + name + " / " + version));
	}

	private static <T> T read(String resource, Type type)
	{
		try (InputStream in = BisOptimizerTest.class.getResourceAsStream(resource))
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
