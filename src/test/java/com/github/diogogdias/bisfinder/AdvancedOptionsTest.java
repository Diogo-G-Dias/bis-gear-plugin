package com.github.diogogdias.bisfinder;

import com.github.diogogdias.bisfinder.calc.CalcData;
import com.github.diogogdias.bisfinder.calc.model.CombatStyleStance;
import com.github.diogogdias.bisfinder.calc.model.CombatStyleType;
import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.MonsterInputs;
import com.github.diogogdias.bisfinder.calc.model.Player;
import com.github.diogogdias.bisfinder.calc.model.PlayerBuffs;
import com.github.diogogdias.bisfinder.calc.model.PlayerCombatStyle;
import com.github.diogogdias.bisfinder.calc.model.PlayerSkills;
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
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Defence reductions and soulreaper stacks were ported but never reachable: the plugin left the stacks
 * at zero and the reductions empty, so a hammered-down boss was calculated at full defence and a
 * Soulreaper axe was always scored with no souls on it.
 */
public class AdvancedOptionsTest
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

	@Test
	public void dwhSpecsMakeTheTargetEasierToHit()
	{
		Monster full = monster("Pestilent Bloat", "Normal");
		Monster hammered = withReductions(full, MonsterInputs.DefenceReductions.builder().dwh(3).build());

		double before = com.github.diogogdias.bisfinder.engine.DpsEngine.estimate(whip(full), full).getAccuracy();
		double after = com.github.diogogdias.bisfinder.engine.DpsEngine.estimate(whip(hammered), hammered).getAccuracy();

		System.out.println(String.format("Bloat accuracy: %.1f%% -> %.1f%% after 3 DWH specs",
			before * 100, after * 100));

		Assert.assertTrue("three dwh specs must raise accuracy", after > before);
	}

	@Test
	public void vulnerabilityAndAccursedLowerDefence()
	{
		Monster full = monster("Pestilent Bloat", "Normal");

		// Lower defence shows up as higher accuracy for a fixed attack, so compare hit chance instead.
		double plain = com.github.diogogdias.bisfinder.engine.DpsEngine.estimate(whip(full), full).getAccuracy();

		Monster vulnerable = withReductions(full,
			MonsterInputs.DefenceReductions.builder().vulnerability(true).build());
		Monster cursed = withReductions(full,
			MonsterInputs.DefenceReductions.builder().accursed(true).build());

		double vuln = com.github.diogogdias.bisfinder.engine.DpsEngine.estimate(whip(vulnerable), vulnerable).getAccuracy();
		double curse = com.github.diogogdias.bisfinder.engine.DpsEngine.estimate(whip(cursed), cursed).getAccuracy();

		Assert.assertTrue("vulnerability must raise accuracy", vuln > plain);
		Assert.assertTrue("accursed sceptre must raise accuracy", curse > plain);
		Assert.assertTrue("accursed (-15%) must beat vulnerability (-10%)", curse > vuln);
	}

	@Test
	public void soulStacksRaiseSoulreaperDamage()
	{
		Monster bloat = monster("Pestilent Bloat", "Normal");

		int noSouls = com.github.diogogdias.bisfinder.engine.DpsEngine.estimate(soulreaper(bloat, 0), bloat).getMaxHit();
		int fullSouls = com.github.diogogdias.bisfinder.engine.DpsEngine.estimate(soulreaper(bloat, 5), bloat).getMaxHit();

		System.out.println(String.format("Soulreaper max hit: %d with no souls, %d with 5",
			noSouls, fullSouls));

		Assert.assertTrue("five souls must raise the max hit", fullSouls > noSouls);
	}

	private static Monster withReductions(Monster monster, MonsterInputs.DefenceReductions reductions)
	{
		return monster.toBuilder()
			.inputs(monster.getInputs().toBuilder().defenceReductions(reductions).build())
			.build();
	}

	private Player whip(Monster target)
	{
		return Player.builder()
			.skills(maxed())
			.prayers(Collections.singletonList(Prayer.PIETY))
			.buffs(PlayerBuffs.builder().build())
			.style(new PlayerCombatStyle("Lash", CombatStyleType.SLASH, CombatStyleStance.CONTROLLED))
			.equipment(Player.PlayerEquipment.builder().weapon(byName("Abyssal whip")).build())
			.build()
			.withGearBonuses(target);
	}

	private Player soulreaper(Monster target, int stacks)
	{
		EquipmentPiece axe = byName("Soulreaper axe");
		return Player.builder()
			.skills(maxed())
			.prayers(Collections.singletonList(Prayer.PIETY))
			.buffs(PlayerBuffs.builder().soulreaperStacks(stacks).build())
			.style(PlayerCombatStyle.getCombatStylesForCategory(axe.getCategory()).get(1))
			.equipment(Player.PlayerEquipment.builder().weapon(axe).build())
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
		try (InputStream in = AdvancedOptionsTest.class.getResourceAsStream(resource))
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
