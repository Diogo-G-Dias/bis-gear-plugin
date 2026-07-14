package com.github.diogogdias.bisfinder;

import com.github.diogogdias.bisfinder.calc.CalcData;
import com.github.diogogdias.bisfinder.calc.PlayerVsNpcCalc;
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
 * Souls raise the Soulreaper axe's effective strength, and the player owns the ornamented variant, whose
 * name ("Soulreaper axe (o)") is not the name the calculator checks for. If the canonicalisation of that
 * variant ever breaks, the stacks silently stop counting.
 */
public class SoulreaperTest
{
	private static final int SOULREAPER_AXE_ORNAMENT = 33335;

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
	public void soulsRaiseDamageThroughTheOptimizer()
	{
		Monster bloat = monster("Pestilent Bloat", "Normal");
		Assert.assertTrue("the sample bank has the ornamented axe", bank.contains(SOULREAPER_AXE_ORNAMENT));

		BisOptimizer.Result none = melee(bloat, 0);
		BisOptimizer.Result full = melee(bloat, 5);

		System.out.println(String.format("Bloat melee, 0 souls: %s -> %.2f dps (max %d)",
			none.getPlayer().getEquipment().getWeapon().getName(), none.getDps(), none.getMaxHit()));
		System.out.println(String.format("Bloat melee, 5 souls: %s -> %.2f dps (max %d)",
			full.getPlayer().getEquipment().getWeapon().getName(), full.getDps(), full.getMaxHit()));

		Assert.assertTrue("five souls must not lower the best melee dps", full.getDps() >= none.getDps());
	}

	/** The ornamented axe must be treated as a Soulreaper axe, or the souls do nothing. */
	@Test
	public void ornamentedAxeStillCountsAsASoulreaper()
	{
		Monster bloat = monster("Pestilent Bloat", "Normal");

		int noSouls = new PlayerVsNpcCalc(axe(bloat, 0), bloat).getMax();
		int fullSouls = new PlayerVsNpcCalc(axe(bloat, 5), bloat).getMax();

		System.out.println(String.format("Soulreaper axe (o): max %d with no souls, %d with 5",
			noSouls, fullSouls));

		Assert.assertTrue("souls must raise the ornamented axe's max hit", fullSouls > noSouls);
	}

	private BisOptimizer.Result melee(Monster target, int stacks)
	{
		List<BisOptimizer.Result> results = new BisOptimizer().findBestPerStyle(
			equipment, spells, bank, maxed(),
			PlayerBuffs.builder().soulreaperStacks(stacks).build(), target,
			BisOptimizer.Options.auto());

		return results.stream()
			.filter(r -> r.getStyleGroup() == BisOptimizer.Style.MELEE)
			.findFirst()
			.orElseThrow(() -> new AssertionError("no melee setup"));
	}

	/** Built from the ornamented item the player actually owns, not the base one. */
	private Player axe(Monster target, int stacks)
	{
		EquipmentPiece owned = equipment.stream()
			.filter(piece -> piece.getId() == SOULREAPER_AXE_ORNAMENT)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no ornamented axe"));

		return Player.builder()
			.skills(maxed())
			.boosts(Potion.SUPER_COMBAT.boost(maxed()))
			.prayers(Collections.singletonList(Prayer.PIETY))
			.buffs(PlayerBuffs.builder().soulreaperStacks(stacks).build())
			.style(PlayerCombatStyle.getCombatStylesForCategory(owned.getCategory()).get(1))
			.equipment(Player.PlayerEquipment.builder().weapon(owned).build())
			.build()
			.withGearBonuses(target);
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
		try (InputStream in = SoulreaperTest.class.getResourceAsStream(resource))
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
