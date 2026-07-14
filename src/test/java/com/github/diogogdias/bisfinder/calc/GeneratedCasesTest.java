package com.github.diogogdias.bisfinder.calc;

import com.github.diogogdias.bisfinder.calc.model.CombatStyleStance;
import com.github.diogogdias.bisfinder.calc.model.CombatStyleType;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.Player;
import com.github.diogogdias.bisfinder.calc.model.PlayerBuffs;
import com.github.diogogdias.bisfinder.calc.model.PlayerCombatStyle;
import com.github.diogogdias.bisfinder.calc.model.PlayerSkills;
import com.github.diogogdias.bisfinder.calc.model.Prayer;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;

/**
 * Ground truth for the ported engine.
 *
 * <p>These are the wiki calculator's own generated tests (src/tests/calc/GeneratedTests.test.ts),
 * extracted to JSON: real loadouts by item id, with max hits its maintainers verified in game. If the
 * port is faithful, every one of them passes.
 */
public class GeneratedCasesTest
{
	@Test
	public void matchesWikiCalculatorMaxHits()
	{
		List<Case> cases = load();
		Assert.assertEquals("expected the full set of generated cases", 28, cases.size());

		List<String> failures = new ArrayList<>();
		for (Case c : cases)
		{
			Monster monster = WikiTestData.monsterById(c.monsterId);
			Player player = playerFor(c, monster);

			int actual = new PlayerVsNpcCalc(player, monster).getMax();
			if (actual != c.expectedMaxHit)
			{
				failures.add(String.format("%s: expected max hit %d but was %d",
					c.name, c.expectedMaxHit, actual));
			}
		}

		if (!failures.isEmpty())
		{
			Assert.fail(failures.size() + " of " + cases.size() + " cases disagree with the wiki calculator:\n"
				+ String.join("\n", failures));
		}
	}

	private Player playerFor(Case c, Monster monster)
	{
		Player.PlayerEquipment.PlayerEquipmentBuilder gear = Player.PlayerEquipment.builder();
		for (Map.Entry<String, Integer> slot : c.equipment.entrySet())
		{
			gear = withSlot(gear, slot.getKey(), slot.getValue());
		}

		List<Prayer> prayers = new ArrayList<>();
		for (String name : c.prayers)
		{
			prayers.add(Prayer.valueOf(name));
		}

		Player.PlayerBuilder player = Player.builder()
			.skills(skills(c))
			.prayers(prayers)
			.buffs(buffs(c))
			.equipment(gear.build());

		if (c.style != null)
		{
			player.style(new PlayerCombatStyle(
				c.style.name,
				CombatStyleType.valueOf(c.style.type.toUpperCase(Locale.ROOT)),
				stance(c.style.stance)));
		}

		if (c.spell != null)
		{
			player.spell(WikiTestData.spellByName(c.spell));
		}

		return player.build().withGearBonuses(monster);
	}

	/**
	 * Their harness merges the case's buffs over generateEmptyPlayer's defaults, which already have
	 * onSlayerTask and kandarinDiary set, so only the named buffs are overridden.
	 */
	private PlayerBuffs buffs(Case c)
	{
		PlayerBuffs.PlayerBuffsBuilder buffs = PlayerBuffs.builder()
			.onSlayerTask(true)
			.kandarinDiary(true);

		if (c.buffs == null)
		{
			return buffs.build();
		}

		for (Map.Entry<String, Object> buff : c.buffs.entrySet())
		{
			boolean on = Boolean.TRUE.equals(buff.getValue());
			switch (buff.getKey())
			{
				case "onSlayerTask":
					buffs.onSlayerTask(on);
					break;
				case "inWilderness":
					buffs.inWilderness(on);
					break;
				case "forinthrySurge":
					buffs.forinthrySurge(on);
					break;
				case "kandarinDiary":
					buffs.kandarinDiary(on);
					break;
				case "chargeSpell":
					buffs.chargeSpell(on);
					break;
				case "markOfDarknessSpell":
					buffs.markOfDarknessSpell(on);
					break;
				case "usingSunfireRunes":
					buffs.usingSunfireRunes(on);
					break;
				case "soulreaperStacks":
					buffs.soulreaperStacks(((Number) buff.getValue()).intValue());
					break;
				case "baAttackerLevel":
					buffs.baAttackerLevel(((Number) buff.getValue()).intValue());
					break;
				case "chinchompaDistance":
					buffs.chinchompaDistance(((Number) buff.getValue()).intValue());
					break;
				default:
					throw new IllegalArgumentException("unknown buff " + buff.getKey());
			}
		}
		return buffs.build();
	}

	/** Their harness starts from a level-99 player and overrides only the skills a case names. */
	private PlayerSkills skills(Case c)
	{
		PlayerSkills skills = new PlayerSkills(99, 99, 99, 99, 99, 99, 99, 99, 99);
		if (c.skills == null)
		{
			return skills;
		}

		for (Map.Entry<String, Integer> skill : c.skills.entrySet())
		{
			int level = skill.getValue();
			switch (skill.getKey())
			{
				case "atk":
					skills = skills.withAtk(level);
					break;
				case "str":
					skills = skills.withStr(level);
					break;
				case "def":
					skills = skills.withDef(level);
					break;
				case "hp":
					skills = skills.withHp(level);
					break;
				case "magic":
					skills = skills.withMagic(level);
					break;
				case "ranged":
					skills = skills.withRanged(level);
					break;
				case "prayer":
					skills = skills.withPrayer(level);
					break;
				case "mining":
					skills = skills.withMining(level);
					break;
				case "herblore":
					skills = skills.withHerblore(level);
					break;
				default:
					throw new IllegalArgumentException("unknown skill " + skill.getKey());
			}
		}
		return skills;
	}

	private static CombatStyleStance stance(String name)
	{
		for (CombatStyleStance stance : CombatStyleStance.values())
		{
			if (stance.getStanceName().equals(name))
			{
				return stance;
			}
		}
		throw new IllegalArgumentException("unknown stance " + name);
	}

	private static Player.PlayerEquipment.PlayerEquipmentBuilder withSlot(
		Player.PlayerEquipment.PlayerEquipmentBuilder builder, String slot, int id)
	{
		switch (slot)
		{
			case "head":
				return builder.head(WikiTestData.equipmentById(id));
			case "cape":
				return builder.cape(WikiTestData.equipmentById(id));
			case "neck":
				return builder.neck(WikiTestData.equipmentById(id));
			case "ammo":
				return builder.ammo(WikiTestData.equipmentById(id));
			case "weapon":
				return builder.weapon(WikiTestData.equipmentById(id));
			case "body":
				return builder.body(WikiTestData.equipmentById(id));
			case "shield":
				return builder.shield(WikiTestData.equipmentById(id));
			case "legs":
				return builder.legs(WikiTestData.equipmentById(id));
			case "hands":
				return builder.hands(WikiTestData.equipmentById(id));
			case "feet":
				return builder.feet(WikiTestData.equipmentById(id));
			case "ring":
				return builder.ring(WikiTestData.equipmentById(id));
			default:
				throw new IllegalArgumentException("unknown slot " + slot);
		}
	}

	private static List<Case> load()
	{
		WikiTestData.load();
		try (InputStream in = GeneratedCasesTest.class.getResourceAsStream("/wiki/generated-cases.json"))
		{
			if (in == null)
			{
				throw new IllegalStateException("missing /wiki/generated-cases.json");
			}
			return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8),
				new TypeToken<List<Case>>()
				{
				}.getType());
		}
		catch (Exception e)
		{
			throw new IllegalStateException("could not read generated cases", e);
		}
	}

	private static class Case
	{
		String name;
		int monsterId;
		Map<String, Integer> skills;
		List<String> prayers;
		Style style;
		String spell;
		Map<String, Object> buffs;
		Map<String, Integer> equipment;
		int expectedMaxHit;
	}

	private static class Style
	{
		String name;
		String type;
		String stance;
	}
}
