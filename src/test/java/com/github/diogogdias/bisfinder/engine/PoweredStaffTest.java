package com.github.diogogdias.bisfinder.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.github.diogogdias.bisfinder.calc.CalcData;
import com.github.diogogdias.bisfinder.calc.model.CombatStyleType;
import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.Player;
import com.github.diogogdias.bisfinder.calc.model.PlayerBuffs;
import com.github.diogogdias.bisfinder.calc.model.PlayerCombatStyle;
import com.github.diogogdias.bisfinder.calc.model.PlayerSkills;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Powered staff base max hits, each pinned to the worked example printed on that staff's own wiki page.
 *
 * <p>The example is the point of these tests. The wiki writes these formulas as stacked fractions, which
 * flatten into ambiguous text when read as prose — the Warped sceptre's reads "⌊8(Magic Level)+9637⌋" — so
 * a formula is only trustworthy once it reproduces a level/max-hit pair the wiki states outright.
 */
public class PoweredStaffTest
{
	private static List<Monster> monsters;
	private static List<EquipmentPiece> equipment;

	@BeforeClass
	public static void load()
	{
		monsters = read("/wiki/monsters.json", new TypeToken<List<Monster>>()
		{
		}.getType());
		equipment = read("/wiki/equipment.json", new TypeToken<List<EquipmentPiece>>()
		{
		}.getType());
		CalcData.setAvailableEquipment(equipment);
	}

	/** Each pair is a level and the max hit the wiki states for it, with no magic-damage gear on. */
	@Test
	public void baseMaxHitsMatchTheWikisWorkedExamples()
	{
		assertBaseMax("Trident of the seas", 75, 20);
		assertBaseMax("Trident of the swamp", 78, 24);
		assertBaseMax("Sanguinesti staff", 82, 26);
		assertBaseMax("Sanguinesti staff", 123, 40);
		assertBaseMax("Accursed sceptre", 70, 17);
		assertBaseMax("Accursed sceptre", 123, 35);
		assertBaseMax("Warped sceptre", 62, 16);
		assertBaseMax("Warped sceptre", 99, 24);
		assertBaseMax("Eye of ayak", 99, 27);
		assertBaseMax("Tumeken's shadow", 60, 21);
	}

	/** The cosmetic and autocast variants share their base staff's formula. */
	@Test
	public void variantsMatchTheirBaseStaff()
	{
		assertEquals("the holy kit is cosmetic",
			baseMax("Sanguinesti staff", 99), baseMax("Holy sanguinesti staff", 99));
		assertEquals("(e) does not change the trident's max hit",
			baseMax("Trident of the seas", 99), baseMax("Trident of the seas (e)", 99));
		assertEquals("(a) does not change the sceptre's max hit",
			baseMax("Accursed sceptre", 99), baseMax("Accursed sceptre (a)", 99));
	}

	/** These were all scoring zero, so magic could never suggest them. */
	@Test
	public void theOnceUnscorablePoweredStaffsNowScore()
	{
		Monster target = named("Abyssal demon");
		for (String staff : new String[]{"Trident of the seas", "Trident of the swamp", "Sanguinesti staff",
			"Thammaron's sceptre", "Accursed sceptre", "Warped sceptre", "Eye of ayak"})
		{
			assertTrue(staff + " must score something", dps(staff, target, 99) > 0);
		}
	}

	/**
	 * The Bone staff is deliberately absent. The wiki's scraped formula ("⌊MagicLevel3⌋+5") contradicts the
	 * same page's own comparison against the trident, so it is left unmodelled rather than guessed at.
	 */
	@Test
	public void theBoneStaffIsStillUnmodelled()
	{
		assertEquals("an unverified staff must score nothing rather than a guess",
			0.0, dps("Bone staff", named("Abyssal demon"), 99), 0.0);
	}

	private static void assertBaseMax(String staff, int magic, int expected)
	{
		assertEquals(staff + " at " + magic + " magic", expected, baseMax(staff, magic));
	}

	/** The max hit with no magic-damage bonuses, which is the staff's raw formula. */
	private static int baseMax(String staff, int magic)
	{
		Monster target = named("Abyssal demon");
		return DpsEngine.estimate(player(staff, target, magic), target).getMaxHit();
	}

	private static double dps(String staff, Monster target, int magic)
	{
		return DpsEngine.dps(player(staff, target, magic), target);
	}

	private static Player player(String staffName, Monster target, int magic)
	{
		EquipmentPiece staff = weapon(staffName);
		PlayerCombatStyle style = PlayerCombatStyle.getCombatStylesForCategory(staff.getCategory()).stream()
			.filter(s -> s.getType() == CombatStyleType.MAGIC && s.getStance() != null)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no magic style for " + staffName));

		return Player.builder()
			.skills(new PlayerSkills(99, 99, 99, magic, 99, 99, 99, 99, 99))
			.style(style)
			.equipment(Player.PlayerEquipment.builder().weapon(staff).build())
			.buffs(PlayerBuffs.builder().build())
			.build()
			.withGearBonuses(target);
	}

	private static EquipmentPiece weapon(String name)
	{
		return equipment.stream()
			.filter(p -> name.equals(p.getName()) && "weapon".equals(p.getSlot()))
			.filter(p -> !"Uncharged".equals(p.getVersion()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no weapon " + name));
	}

	private static Monster named(String name)
	{
		return monsters.stream().filter(m -> name.equals(m.getName())).findFirst()
			.orElseThrow(() -> new IllegalStateException("no monster " + name));
	}

	private static <T> T read(String resource, Type type)
	{
		try (InputStream in = PoweredStaffTest.class.getResourceAsStream(resource))
		{
			return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
}
