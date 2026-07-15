package com.github.diogogdias.bisfinder.engine;

import static org.junit.Assert.assertEquals;

import com.github.diogogdias.bisfinder.calc.CalcData;
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
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Golden-master DPS for the tricky weapons and monster mechanics, frozen from values that were verified exactly
 * against the weirdgloop-port oracle before that oracle was removed. Each maxed-stats, single-weapon setup
 * pins a specific behaviour (fang stab-only accuracy, scythe/two-hit splats, Colossal blade size scaling,
 * Dark bow two arrows, atlatl Strength scaling, Twisted bow scaling, Verzik's defence-level magic defence).
 * A change here means the engine's numbers moved - reconcile against dps.osrs.wiki before re-baselining.
 */
public class GoldenMasterTest
{
	private static final double EPS = 1e-4;
	private static final PlayerSkills MAX = new PlayerSkills(99, 99, 99, 99, 99, 99, 99, 99, 99);
	private static List<EquipmentPiece> equipment;
	private static List<Monster> monsters;

	@BeforeClass
	public static void load()
	{
		equipment = read("/wiki/equipment.json", new TypeToken<List<EquipmentPiece>>()
		{
		}.getType());
		monsters = read("/wiki/monsters.json", new TypeToken<List<Monster>>()
		{
		}.getType());
		CalcData.setAvailableEquipment(equipment);
		CalcData.setSpells(read("/wiki/spells.json", new TypeToken<List<Spell>>()
		{
		}.getType()));
	}

	@Test
	public void maidenMelee()
	{
		Monster maiden = monster("The Maiden of Sugadinti", "Normal");
		assertEquals(6.19050, melee("Osmumten's fang", CombatStyleType.STAB, maiden), EPS);
		assertEquals("fang accuracy is stab-only", 4.62649, melee("Osmumten's fang", CombatStyleType.SLASH, maiden), EPS);
		assertEquals(5.52610, melee("Blade of saeldor (c)", CombatStyleType.SLASH, maiden), EPS);
		assertEquals("scythe three splats", 7.54736, melee("Scythe of vitur", CombatStyleType.SLASH, maiden), EPS);
		assertEquals("two-hit weapon", 4.36045, melee("Sulphur blades", CombatStyleType.SLASH, maiden), EPS);
		assertEquals("colossal blade size scaling", 5.02405, melee("Colossal blade", CombatStyleType.SLASH, maiden), EPS);
	}

	@Test
	public void maidenRanged()
	{
		Monster maiden = monster("The Maiden of Sugadinti", "Normal");
		assertEquals("dark bow two arrows", 4.11533, ranged("Dark bow", "Dragon arrow", CombatStyleStance.RAPID, maiden), EPS);
		assertEquals("atlatl scales off strength", 4.03104, ranged("Eclipse atlatl", "Atlatl dart", CombatStyleStance.RAPID, maiden), EPS);
		assertEquals("twisted bow scaling", 8.76157, ranged("Twisted bow", "Dragon arrow", CombatStyleStance.RAPID, maiden), EPS);
	}

	@Test
	public void verzik()
	{
		Monster p2 = monster("Verzik Vitur", "Normal mode, Phase 2");
		Monster p3 = monster("Verzik Vitur", "Normal mode, Phase 3");
		assertEquals(3.34669, melee("Osmumten's fang", CombatStyleType.STAB, p2), EPS);
		assertEquals("fang slash uses normal accuracy", 2.73035, melee("Osmumten's fang", CombatStyleType.SLASH, p2), EPS);
		assertEquals(2.38569, ranged("Twisted bow", "Dragon arrow", CombatStyleStance.RAPID, p2), EPS);
		assertEquals("magic defends off defence level, not magic level", 2.96508, magic("Tumeken's shadow", p2), EPS);
		assertEquals(5.20251, melee("Osmumten's fang", CombatStyleType.STAB, p3), EPS);
		assertEquals(3.18453, magic("Tumeken's shadow", p3), EPS);
	}

	private double melee(String weapon, CombatStyleType type, Monster monster)
	{
		Player player = Player.builder().skills(MAX).boosts(Potion.SUPER_COMBAT.boost(MAX))
			.prayers(Collections.singletonList(Prayer.PIETY)).buffs(PlayerBuffs.builder().build())
			.style(new PlayerCombatStyle("s", type, CombatStyleStance.AGGRESSIVE))
			.equipment(Player.PlayerEquipment.builder().weapon(named(weapon)).build())
			.build().withGearBonuses(monster);
		return DpsEngine.meleeDps(player, monster);
	}

	private double ranged(String weapon, String ammo, CombatStyleStance stance, Monster monster)
	{
		Player player = Player.builder().skills(MAX).boosts(Potion.RANGING.boost(MAX))
			.prayers(Collections.singletonList(Prayer.RIGOUR)).buffs(PlayerBuffs.builder().build())
			.style(new PlayerCombatStyle("s", CombatStyleType.RANGED, stance))
			.equipment(Player.PlayerEquipment.builder().weapon(named(weapon)).ammo(named(ammo)).build())
			.build().withGearBonuses(monster);
		return DpsEngine.rangedDps(player, monster);
	}

	private double magic(String weapon, Monster monster)
	{
		Player player = Player.builder().skills(MAX).boosts(Potion.SATURATED_HEART.boost(MAX))
			.prayers(Collections.singletonList(Prayer.AUGURY)).buffs(PlayerBuffs.builder().build())
			.style(new PlayerCombatStyle("s", CombatStyleType.MAGIC, CombatStyleStance.ACCURATE))
			.equipment(Player.PlayerEquipment.builder().weapon(named(weapon)).build())
			.build().withGearBonuses(monster);
		return DpsEngine.magicDps(player, monster);
	}

	private static EquipmentPiece named(String name)
	{
		return equipment.stream().filter(p -> p.getName().equals(name)).findFirst()
			.orElseThrow(() -> new IllegalStateException("no item " + name));
	}

	private static Monster monster(String name, String versionContains)
	{
		return monsters.stream().filter(m -> name.equals(m.getName())
			&& m.getVersion() != null && m.getVersion().contains(versionContains)).findFirst()
			.orElseThrow(() -> new IllegalStateException("no monster " + name + " / " + versionContains));
	}

	private static <T> T read(String resource, Type type)
	{
		try (InputStream in = GoldenMasterTest.class.getResourceAsStream(resource))
		{
			return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
}
