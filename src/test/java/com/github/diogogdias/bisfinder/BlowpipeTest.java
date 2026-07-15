package com.github.diogogdias.bisfinder;

import com.github.diogogdias.bisfinder.calc.CalcData;
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
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A blowpipe gets all of its ranged strength from the darts loaded into it, and the calculator reads
 * those from the weapon's itemVars. Nothing filled that in, so every blowpipe was being scored as if it
 * were firing nothing.
 */
public class BlowpipeTest
{
	private static final int BLAZING_BLOWPIPE = 28688;
	private static final int DRAGON_DART = 11230;

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

	@Test
	public void loadedBlowpipeOutDamagesAnEmptyOne()
	{
		Monster target = monster("Abyssal demon", "Standard");

		double empty = dps(blowpipe(target, null));
		double loaded = dps(blowpipe(target, DRAGON_DART));

		System.out.println(String.format("Blowpipe: empty %.2f dps, dragon darts %.2f dps", empty, loaded));

		Assert.assertTrue("darts must add damage, but empty was " + empty + " and loaded " + loaded,
			loaded > empty);
	}

	/** The optimizer must load the best dart the player owns, without being told to. */
	@Test
	public void optimizerLoadsTheBestOwnedDart()
	{
		Monster target = monster("Abyssal demon", "Standard");

		Set<Integer> owned = ids("Blazing blowpipe", "Dragon dart", "Rune dart", "Armadyl chestplate",
			"Armadyl chainskirt", "Armadyl helmet", "Necklace of anguish", "Ava's assembler",
			"Barrows gloves", "Pegasian boots", "Archers ring");

		BisOptimizer.Result result = new BisOptimizer().findBest(
			equipment, spells, owned, maxed(), PlayerBuffs.builder().build(), target);

		Assert.assertNotNull(result);

		EquipmentPiece weapon = result.getPlayer().getEquipment().getWeapon();

		// The name is "Toxic blowpipe", not "Blazing": cosmetic variants canonicalise onto the base item.
		Assert.assertTrue("the blowpipe should win here, but got " + weapon.getName(),
			weapon.getName().endsWith("blowpipe"));
		Assert.assertNotNull("the blowpipe must be loaded with darts", weapon.getItemVars());
		Assert.assertEquals("it must load the strongest dart owned",
			"Dragon dart", weapon.getItemVars().getBlowpipeDartName());
	}

	private Player blowpipe(Monster target, Integer dartId)
	{
		EquipmentPiece pipe = byId(BLAZING_BLOWPIPE);
		if (dartId != null)
		{
			EquipmentPiece dart = byId(dartId);
			pipe = pipe.withItemVars(new EquipmentPiece.ItemVars(dart.getId(), dart.getName()));
		}

		return Player.builder()
			.skills(maxed())
			.boosts(Potion.RANGING.boost(maxed()))
			.prayers(Collections.singletonList(Prayer.RIGOUR))
			.buffs(PlayerBuffs.builder().build())
			.style(PlayerCombatStyle.getCombatStylesForCategory(pipe.getCategory()).get(1))
			.equipment(Player.PlayerEquipment.builder().weapon(pipe).build())
			.build()
			.withGearBonuses(target);
	}

	private double dps(Player player)
	{
		return com.github.diogogdias.bisfinder.engine.DpsEngine.dps(player, monster("Abyssal demon", "Standard"));
	}

	private static Set<Integer> ids(String... names)
	{
		List<String> wanted = java.util.Arrays.asList(names);
		return equipment.stream()
			.filter(piece -> wanted.contains(piece.getName()))
			.map(EquipmentPiece::getId)
			.collect(Collectors.toSet());
	}

	private static EquipmentPiece byId(int id)
	{
		return equipment.stream()
			.filter(piece -> piece.getId() == id)
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("no item " + id));
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
		try (InputStream in = BlowpipeTest.class.getResourceAsStream(resource))
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
