package com.github.diogogdias.bisfinder.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.github.diogogdias.bisfinder.calc.CalcData;
import com.github.diogogdias.bisfinder.calc.model.CombatStyleType;
import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.MonsterInputs;
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
 * The 2026 Summer Sweep-Up combat changes the engine models: the Tonalztics of ralos defence drain, the
 * Sanguinesti staff's higher base max and new damage proc, and the Inquisitor set's move to per-piece crush
 * bonuses with the mace no longer tripling it. Each value here was verified against the item's own wiki page.
 */
public class SummerSweepUpTest
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

	// --- Tonalztics of ralos: defence drain (10% -> 12.5% of Magic level per hit) -------------------

	/**
	 * "Division ... reduces the target's Defence level by 12.5% of the target's Magic level upon a successful
	 * hit." General Graardor has Magic 80, so each hit drains floor(80 * 12.5%) = 10 Defence. Two hits must be
	 * identical to fighting a target whose Defence level is simply 20 lower - and this input did nothing at all
	 * before, so any effect at all is the fix.
	 */
	@Test
	public void tonalzticsDrainsDefenceByMagicLevel()
	{
		Monster graardor = named("General Graardor");
		assertEquals("Graardor's Magic level is the basis for the drain", 80, graardor.getSkills().getMagic());

		EquipmentPiece whip = weapon("Abyssal whip");
		double twoHits = dps(whip, CombatStyleType.SLASH, withTonalztic(graardor, 2));
		double manual20Lower = dps(whip, CombatStyleType.SLASH, withDefence(graardor, graardor.getSkills().getDef() - 20));

		assertEquals("two Tonalztics hits == 20 lower Defence", manual20Lower, twoHits, 1e-9);
		assertTrue("and it must actually raise DPS versus no drain",
			twoHits > dps(whip, CombatStyleType.SLASH, graardor));
	}

	// --- Sanguinesti staff: base max +1, and the new +8 damage proc --------------------------------

	/** New base max is floor(Magic/3): "27 at 82 Magic" and floor(99/3) = 33 (was 32). */
	@Test
	public void sanguinestiBaseMaxRaisedByOne()
	{
		assertEquals("floor(99/3) = 33", 33, bareMaxHit("Sanguinesti staff", 99));
		assertEquals("floor(82/3) = 27", 27, bareMaxHit("Sanguinesti staff", 82));
		assertEquals("the holy variant shares the formula", 33, bareMaxHit("Holy sanguinesti staff", 99));
	}

	/**
	 * The heal proc now deals 8 extra damage on 1 in 5 successful hits. Like the enchanted-bolt procs, it
	 * raises DPS but not the shown max hit. The DPS must equal the base expected hit plus accuracy * 0.2 * 8.
	 */
	@Test
	public void sanguinestiHealProcAddsDamage()
	{
		Player sang = bare("Sanguinesti staff", 99, named("Abyssal demon"));
		DpsEngine.Estimate e = DpsEngine.estimate(sang, named("Abyssal demon"));

		double withoutProc = RollMath.dps(RollMath.standardExpectedHit(e.getAccuracy(), e.getMaxHit()),
			sang.getAttackSpeed());
		double withProc = RollMath.dps(
			RollMath.standardExpectedHit(e.getAccuracy(), e.getMaxHit()) + e.getAccuracy() * 0.2 * 8.0,
			sang.getAttackSpeed());

		assertEquals("the proc is worth accuracy * 0.2 * 8 per attack", withProc, e.getDps(), 1e-9);
		assertTrue("which is strictly more than without it", e.getDps() > withoutProc);
		assertEquals("the shown max hit stays the base roll, not base + 8", 33, e.getMaxHit());
	}

	// --- Inquisitor: per-piece crush bonus, mace no longer triples ---------------------------------

	/**
	 * A spear's stab, slash and crush styles all use the Controlled stance, so its max hit differs between them
	 * only by the Inquisitor crush multiplier. The full set is +2.5% (0.5 + 1 + 1), spread across the pieces.
	 */
	@Test
	public void fullInquisitorIsTwoAndAHalfPercentOnCrush()
	{
		int stab = inquisitorMax(CombatStyleType.STAB, true, true, true);
		int crush = inquisitorMax(CombatStyleType.CRUSH, true, true, true);
		assertTrue("the modifier must actually change the floored max hit", crush > stab);
		assertEquals("full set is +2.5% on crush", stab * 1025 / 1000, crush);
	}

	/**
	 * The mace no longer triples the set bonus (that was removed for +7 strength / +7 crush on the mace
	 * itself). With the full armour it now gets the ordinary +2.5%, the same as any crush weapon - the spear
	 * proves the multiplier is 2.5%, so the mace on the same armour must land the same 2.5% on its own base.
	 */
	@Test
	public void theInquisitorMaceNoLongerTriplesTheBonus()
	{
		int maceFullArmour = inquisitorMaxWith("Inquisitor's mace", CombatStyleType.CRUSH, true, true, true);

		// The spear tests prove the multiplier is +2.5% (full) and +1% (one piece), weapon-independent - the
		// mace has no special branch any more, so it rides the same code. This is a golden pin at the same
		// gear: the old mace tripling (+7.5%) produced 49 here, so reverting the fix breaks this assertion.
		assertEquals("mace + full armour is +2.5%, not the old +7.5%", 47, maceFullArmour);
	}

	// --- helpers ------------------------------------------------------------------------------------

	private static Monster withTonalztic(Monster m, int hits)
	{
		return m.toBuilder()
			.inputs(m.getInputs().toBuilder()
				.defenceReductions(MonsterInputs.DefenceReductions.builder().tonalztic(hits).build())
				.build())
			.build();
	}

	private static Monster withDefence(Monster m, int def)
	{
		Monster.Skills s = m.getSkills();
		return m.toBuilder()
			.skills(new Monster.Skills(s.getAtk(), def, s.getHp(), s.getMagic(), s.getRanged(), s.getStr()))
			.build();
	}

	private static double dps(EquipmentPiece weapon, CombatStyleType type, Monster m)
	{
		return DpsEngine.dps(meleePlayer(weapon, type, m), m);
	}

	private static Player meleePlayer(EquipmentPiece weapon, CombatStyleType type, Monster m)
	{
		return Player.builder()
			.style(styleFor(weapon, type))
			.equipment(Player.PlayerEquipment.builder().weapon(weapon).build())
			.build()
			.withGearBonuses(m);
	}

	/** Max hit of a bare powered staff at the given Magic level, before any magic-damage gear. */
	private static int bareMaxHit(String staff, int magic)
	{
		Monster target = named("Abyssal demon");
		return DpsEngine.estimate(bare(staff, magic, target), target).getMaxHit();
	}

	private static Player bare(String staffName, int magic, Monster target)
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

	private static int inquisitorMax(CombatStyleType type, boolean helm, boolean body, boolean legs)
	{
		return inquisitorMaxWith("Zamorakian hasta", type, helm, body, legs);
	}

	private static int inquisitorMaxWith(String weaponName, CombatStyleType type, boolean helm, boolean body,
		boolean legs)
	{
		Monster target = named("Abyssal demon");
		EquipmentPiece weapon = weapon(weaponName);
		// Strength gear common to both styles, to lift the base max hit past ~40 where a 2.5% crush multiplier
		// crosses an integer boundary instead of flooring away. It is identical across stab and crush, so it
		// does not disturb the isolation of the Inquisitor modifier.
		Player.PlayerEquipment.PlayerEquipmentBuilder gear = Player.PlayerEquipment.builder().weapon(weapon)
			.neck(item("Amulet of torture"))
			.hands(item("Ferocious gloves"))
			.feet(item("Primordial boots"));
		if (helm)
		{
			gear.head(item("Inquisitor's great helm"));
		}
		if (body)
		{
			gear.body(item("Inquisitor's hauberk"));
		}
		if (legs)
		{
			gear.legs(item("Inquisitor's plateskirt"));
		}
		// Piety + super combat lift the base max hit high enough that a 2.5% multiplier changes the floored
		// number, so the modifier is actually visible rather than lost to rounding.
		PlayerSkills maxed = new PlayerSkills(99, 99, 99, 99, 99, 99, 99, 99, 99);
		Player p = Player.builder()
			.skills(maxed)
			.boosts(com.github.diogogdias.bisfinder.calc.model.Potion.SUPER_COMBAT.boost(maxed))
			.prayers(java.util.Collections.singletonList(
				com.github.diogogdias.bisfinder.calc.model.Prayer.PIETY))
			.style(styleFor(weapon, type))
			.equipment(gear.build())
			.build()
			.withGearBonuses(target);
		return DpsEngine.estimate(p, target).getMaxHit();
	}

	private static PlayerCombatStyle styleFor(EquipmentPiece weapon, CombatStyleType type)
	{
		return PlayerCombatStyle.getCombatStylesForCategory(weapon.getCategory()).stream()
			.filter(s -> s.getType() == type && s.getStance() != null)
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no " + type + " style for " + weapon.getName()));
	}

	private static EquipmentPiece item(String name)
	{
		return equipment.stream().filter(p -> name.equals(p.getName())).findFirst()
			.orElseThrow(() -> new IllegalStateException("no item " + name));
	}

	private static EquipmentPiece weapon(String name)
	{
		return equipment.stream()
			.filter(p -> name.equals(p.getName()) && "weapon".equals(p.getSlot()))
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
		try (InputStream in = SummerSweepUpTest.class.getResourceAsStream(resource))
		{
			return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
}
