package com.github.diogogdias.bisfinder;

import com.github.diogogdias.bisfinder.calc.CalcData;
import com.github.diogogdias.bisfinder.calc.PlayerVsNpcCalc;
import com.github.diogogdias.bisfinder.calc.model.CombatStyleStance;
import com.github.diogogdias.bisfinder.calc.model.CombatStyleType;
import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.Player;
import com.github.diogogdias.bisfinder.calc.model.PlayerBuffs;
import com.github.diogogdias.bisfinder.calc.model.PlayerCombatStyle;
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
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/** Which of the four helm/body combinations actually wins at Bloat, and does the search find it? */
public class OathplateDiagTest
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
	 * A one-slot-at-a-time climb cannot find this: from an Oathplate helm with a Torva chest, swapping
	 * either slot alone makes things worse, yet swapping both at once is the best setup there is. The
	 * search has to try pairs of slots together, or it sits in the valley believing it has finished.
	 */
	@Test
	public void findsTheBestHelmAndBodyPairEvenThoughNeitherWinsAlone()
	{
		Monster bloat = monster("Pestilent Bloat", "Normal");

		String[][] combos = {
			{"Torva full helm", "Torva platebody"},
			{"Torva full helm", "Oathplate chest"},
			{"Oathplate helm", "Torva platebody"},
			{"Oathplate helm", "Oathplate chest"},
		};

		double best = 0;
		String bestCombo = null;

		for (String[] combo : combos)
		{
			Player player = setup(bloat, combo[0], combo[1]);
			PlayerVsNpcCalc calc = new PlayerVsNpcCalc(player, bloat);
			double dps = calc.getDps();

			System.out.println(String.format("%-16s + %-16s -> %.4f dps (max %d, acc %.2f%%)",
				combo[0], combo[1], dps, calc.getMax(), calc.getHitChance() * 100));

			if (dps > best)
			{
				best = dps;
				bestCombo = combo[0] + " + " + combo[1];
			}
		}

		System.out.println("best combination: " + bestCombo);
		Assert.assertEquals("Torva full helm + Oathplate chest", bestCombo);

		// And the search, given exactly these four pieces plus the rest of the setup, must land on it.
		Set<Integer> owned = ids("Torva full helm", "Oathplate helm", "Torva platebody", "Oathplate chest",
			"Blade of saeldor (c)", "Avernic defender", "Torva platelegs", "Infernal cape",
			"Salve amulet (e)", "Ferocious gloves", "Primordial boots", "Ultor ring");

		BisOptimizer.Result found = new BisOptimizer().findBest(
			equipment, spells, owned, maxed(), PlayerBuffs.builder().build(), bloat);

		Assert.assertNotNull(found);

		String helm = found.getPlayer().getEquipment().getHead().getName();
		String chest = found.getPlayer().getEquipment().getBody().getName();
		System.out.println("search picked: " + helm + " + " + chest);

		Assert.assertEquals("the search must find the winning pair", "Torva full helm", helm);
		Assert.assertEquals("the search must find the winning pair", "Oathplate chest", chest);
	}

	private static Set<Integer> ids(String... names)
	{
		List<String> wanted = java.util.Arrays.asList(names);
		return equipment.stream()
			.filter(piece -> wanted.contains(piece.getName()))
			.map(EquipmentPiece::getId)
			.collect(java.util.stream.Collectors.toSet());
	}

	private Player setup(Monster target, String helm, String body)
	{
		Player.PlayerEquipment gear = Player.PlayerEquipment.builder()
			.weapon(byName("Blade of saeldor (c)"))
			.shield(byName("Avernic defender"))
			.head(byName(helm))
			.body(byName(body))
			.legs(byName("Torva platelegs"))
			.cape(byName("Infernal cape"))
			.neck(byName("Salve amulet (e)"))
			.hands(byName("Ferocious gloves"))
			.feet(byName("Primordial boots"))
			.ring(byName("Ultor ring"))
			.build();

		return Player.builder()
			.skills(maxed())
			.boosts(Potion.SUPER_COMBAT.boost(maxed()))
			.prayers(Collections.singletonList(Prayer.PIETY))
			.buffs(PlayerBuffs.builder().build())
			.style(new PlayerCombatStyle("Slash", CombatStyleType.SLASH, CombatStyleStance.AGGRESSIVE))
			.equipment(gear)
			.build()
			.withGearBonuses(target);
	}

	private static EquipmentPiece byName(String name)
	{
		return equipment.stream()
			.filter(piece -> piece.getName().equals(name))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("no item named " + name));
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
		try (InputStream in = OathplateDiagTest.class.getResourceAsStream(resource))
		{
			return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
		}
		catch (Exception e)
		{
			throw new IllegalStateException(e);
		}
	}
}
