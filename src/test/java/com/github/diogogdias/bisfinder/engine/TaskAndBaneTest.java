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
import com.github.diogogdias.bisfinder.calc.model.Spell;
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
 * The on-task black mask bonus and the demonbane weapons - two of the target-specific effects the clean-room
 * engine was scoring at flat base stats.
 */
public class TaskAndBaneTest
{
	private static List<Monster> monsters;
	private static List<EquipmentPiece> equipment;
	private static List<Spell> spells;

	@BeforeClass
	public static void load()
	{
		monsters = read("/wiki/monsters.json", new TypeToken<List<Monster>>()
		{
		}.getType());
		equipment = read("/wiki/equipment.json", new TypeToken<List<EquipmentPiece>>()
		{
		}.getType());
		spells = read("/wiki/spells.json", new TypeToken<List<Spell>>()
		{
		}.getType());
		CalcData.setAvailableEquipment(equipment);
		CalcData.setSpells(spells);
	}

	// --- on task ------------------------------------------------------------------------------------

	/** The reported bug: only the melee tab moved when "On task" was ticked. */
	@Test
	public void anImbuedMaskLiftsEveryStyleOnTask()
	{
		Monster target = named("Abyssal demon");

		assertTrue("melee gains from an imbued mask on task",
			onTask(item("Slayer helmet (i)"), weapon("Abyssal whip"), CombatStyleType.SLASH, target)
				> offTask(item("Slayer helmet (i)"), weapon("Abyssal whip"), CombatStyleType.SLASH, target));
		assertTrue("ranged gains from an imbued mask on task",
			onTask(item("Slayer helmet (i)"), weapon("Twisted bow"), CombatStyleType.RANGED, target)
				> offTask(item("Slayer helmet (i)"), weapon("Twisted bow"), CombatStyleType.RANGED, target));
		assertTrue("magic gains from an imbued mask on task",
			castOnTask(item("Slayer helmet (i)"), weapon("Kodai wand"), target, true)
				> castOnTask(item("Slayer helmet (i)"), weapon("Kodai wand"), target, false));
	}

	/** A staff scores nothing without a spell, so the magic side has to actually be casting one. */
	private static double castOnTask(EquipmentPiece head, EquipmentPiece weapon, Monster m, boolean onTask)
	{
		Player p = player(head, weapon, CombatStyleType.MAGIC, m, onTask, null).toBuilder()
			.spell(Spell.byName("Fire Surge", spells))
			.build()
			.withGearBonuses(m);
		return DpsEngine.dps(p, m);
	}

	/** Only the imbued variants carry the Ranged and Magic bonus; the plain helm is melee-only. */
	@Test
	public void anUnimbuedMaskIsMeleeOnly()
	{
		Monster target = named("Abyssal demon");

		assertTrue("melee still gains from a plain helm",
			onTask(item("Slayer helmet"), weapon("Abyssal whip"), CombatStyleType.SLASH, target)
				> offTask(item("Slayer helmet"), weapon("Abyssal whip"), CombatStyleType.SLASH, target));
		assertEquals("ranged gains nothing from a plain helm",
			offTask(item("Slayer helmet"), weapon("Twisted bow"), CombatStyleType.RANGED, target),
			onTask(item("Slayer helmet"), weapon("Twisted bow"), CombatStyleType.RANGED, target), 1e-9);
	}

	/**
	 * The helm the game actually hands out is a coloured one, and "Slayer helmet" is only the prefix of the
	 * plain variant - so a prefix match left most players with no on-task bonus at all.
	 */
	@Test
	public void themedSlayerHelmetsCountAsABlackMask()
	{
		Monster target = named("Abyssal demon");

		for (String helm : new String[]{"Purple slayer helmet (i)", "Hydra slayer helmet (i)",
			"Araxyte slayer helmet (i)", "Tzkal slayer helmet (i)", "Oathplate slayer helmet (i)",
			"Black mask (i)"})
		{
			assertEquals(helm + " must match the plain helm's on-task bonus",
				onTask(item("Slayer helmet (i)"), weapon("Abyssal whip"), CombatStyleType.SLASH, target),
				onTask(item(helm), weapon("Abyssal whip"), CombatStyleType.SLASH, target), 1e-9);
		}
	}

	/**
	 * "This stacks multiplicatively with both Void Knight equipment and the Slayer helm (i)" - the lance must
	 * not swallow the mask's bonus, which is what folding both into one either/or chain did.
	 */
	@Test
	public void theDragonHunterLanceStacksWithTheMask()
	{
		Monster dragon = named("Vorkath", "Post-quest");

		double lanceOnly = offTask(null, weapon("Dragon hunter lance"), CombatStyleType.STAB, dragon);
		double lanceAndMask = onTask(item("Slayer helmet (i)"), weapon("Dragon hunter lance"),
			CombatStyleType.STAB, dragon);

		assertTrue("the lance and the mask must stack", lanceAndMask > lanceOnly);
	}

	/** Mod Ash: "Salve effects override the Slayer helm / black mask effects." */
	@Test
	public void theSalveOverridesTheMaskRatherThanStacking()
	{
		Monster undead = named("Vorkath", "Post-quest");

		double salveOnly = onTask(null, weapon("Abyssal whip"), CombatStyleType.SLASH, undead, "Salve amulet(ei)");
		double salveAndMask = onTask(item("Slayer helmet (i)"), weapon("Abyssal whip"), CombatStyleType.SLASH,
			undead, "Salve amulet(ei)");

		assertEquals("the mask must add nothing on top of a salve", salveOnly, salveAndMask, 1e-9);
	}

	/** The wand is a staff, so its bonus used to fire only when bashing a dragon rather than casting at one. */
	@Test
	public void theDragonHunterWandBoostsItsSpells()
	{
		Monster dragon = named("Vorkath", "Post-quest");
		Monster notADragon = named("Abyssal demon");

		// The wand's own stats are weaker than a Kodai's, so against a dragon it can only come out ahead if
		// its dragonbane bonus is actually reaching the spell.
		assertTrue("the wand's spell must beat a Kodai's against a dragon",
			cast(weapon("Dragon hunter wand"), dragon) > cast(weapon("Kodai wand"), dragon));
		assertTrue("and must lose to a Kodai against something that is not a dragon",
			cast(weapon("Dragon hunter wand"), notADragon) < cast(weapon("Kodai wand"), notADragon));
	}

	// --- demonbane ----------------------------------------------------------------------------------

	/**
	 * The same target with and without the demon attribute, so nothing but demonbane can move the number.
	 * Comparing two real monsters would confound it with their different defences, and the smallest bonus
	 * (Burning claws, +5%) is invisible in the max hit alone - 5% of a bare weapon's 16 floors straight back
	 * to 16 - so this scores DPS, which carries the accuracy half too.
	 */
	@Test
	public void demonbaneWeaponsHitDemonsHarder()
	{
		Monster plain = named("Aberrant spectre");
		Monster demon = asDemon(plain);

		for (String name : new String[]{"Arclight", "Emberlight", "Darklight", "Silverlight", "Burning claws"})
		{
			assertTrue(name + " must gain against a demon",
				offTask(null, weapon(name), CombatStyleType.SLASH, demon)
					> offTask(null, weapon(name), CombatStyleType.SLASH, plain));
		}

		assertTrue("the Scorching bow must gain against a demon",
			offTask(null, weapon("Scorching bow"), CombatStyleType.RANGED, demon)
				> offTask(null, weapon("Scorching bow"), CombatStyleType.RANGED, plain));
	}

	private static Monster asDemon(Monster monster)
	{
		return monster.toBuilder()
			.attributes(java.util.Collections.singletonList(
				com.github.diogogdias.bisfinder.calc.model.MonsterAttribute.DEMON))
			.build();
	}

	/** Arclight is +70% damage, so a demon takes 170/100 of what a non-demon does off the same setup. */
	@Test
	public void arclightIsSeventyPercent()
	{
		int off = maxHit(weapon("Arclight"), CombatStyleType.SLASH, named("Aberrant spectre"));
		int on = maxHit(weapon("Arclight"), CombatStyleType.SLASH, named("Abyssal demon"));
		assertEquals("Arclight's demon max hit is +70%", off * 170 / 100, on);
	}

	/** Duke Sucellus resists 30% of demonbane, turning Arclight's 70% into 49%. */
	@Test
	public void dukeSucellusResistsDemonbane()
	{
		int base = maxHit(weapon("Arclight"), CombatStyleType.SLASH, named("Aberrant spectre"));
		int duke = maxHit(weapon("Arclight"), CombatStyleType.SLASH, named("Duke Sucellus", "Post-quest, Awake"));
		assertEquals("Duke takes Arclight at 49%, not 70%", base * 149 / 100, duke);
	}

	/**
	 * Leafy creatures matter far more as an immunity than as a bonus: "they can only be damaged with
	 * leaf-bladed weapons, broad ammunition, or the Magic Dart spell; they are immune to any other types of
	 * attacks." A scythe at a Kurask scored full damage before this.
	 */
	@Test
	public void onlyLeafBladedBroadAndMagicDartHurtALeafyCreature()
	{
		Monster kurask = named("Kurask");

		assertEquals("a scythe cannot hurt a Kurask", 0.0,
			offTask(null, weapon("Scythe of vitur"), CombatStyleType.SLASH, kurask), 0.0);
		assertTrue("a leaf-bladed battleaxe can",
			offTask(null, weapon("Leaf-bladed battleaxe"), CombatStyleType.CRUSH, kurask) > 0);
		assertEquals("a twisted bow with ordinary arrows cannot", 0.0,
			ranged(weapon("Twisted bow"), item("Dragon arrow"), kurask), 0.0);
		assertTrue("a bow with broad arrows can",
			ranged(weapon("Twisted bow"), item("Broad arrows"), kurask) > 0);
		assertEquals("a Kodai casting Fire Surge cannot", 0.0, spellAt("Fire Surge", kurask), 0.0);
		assertTrue("Magic Dart can", spellAt("Magic Dart", kurask) > 0);
	}

	/** Only the battleaxe carries the 17.5%; the sword and spear are merely allowed to hit. */
	@Test
	public void onlyTheLeafBladedBattleaxeGetsTheDamageBonus()
	{
		Monster kurask = named("Kurask");
		Monster plain = kurask.toBuilder().attributes(java.util.Collections.emptyList()).build();

		assertEquals("the battleaxe is +17.5% against a leafy creature",
			maxHit(weapon("Leaf-bladed battleaxe"), CombatStyleType.CRUSH, plain) * 47 / 40,
			maxHit(weapon("Leaf-bladed battleaxe"), CombatStyleType.CRUSH, kurask));
		assertEquals("the sword gets nothing",
			maxHit(weapon("Leaf-bladed sword"), CombatStyleType.SLASH, plain),
			maxHit(weapon("Leaf-bladed sword"), CombatStyleType.SLASH, kurask));
	}

	/** Every keris keeps the original's 33% against kalphites. */
	@Test
	public void aKerisIsThirtyThreePercentAgainstKalphites()
	{
		Monster kalphite = named("Kalphite Worker");
		Monster plain = kalphite.toBuilder().attributes(java.util.Collections.emptyList()).build();

		for (String name : new String[]{"Keris", "Keris partisan", "Keris partisan of breaching",
			"Keris partisan of the sun", "Keris partisan of corruption"})
		{
			assertEquals(name + " is +33% against a kalphite",
				maxHit(weapon(name), CombatStyleType.STAB, plain) * 133 / 100,
				maxHit(weapon(name), CombatStyleType.STAB, kalphite));
		}
	}

	/** "this variant has a damage bonus of 15%, down from the base weapon's 33%" - it trades it for ToA stats. */
	@Test
	public void theKerisPartisanOfAmascutIsOnlyFifteenPercent()
	{
		Monster kalphite = named("Kalphite Worker");
		Monster plain = kalphite.toBuilder().attributes(java.util.Collections.emptyList()).build();

		assertEquals("the amascut variant is +15%, not +33%",
			maxHit(weapon("Keris partisan of amascut"), CombatStyleType.STAB, plain) * 115 / 100,
			maxHit(weapon("Keris partisan of amascut"), CombatStyleType.STAB, kalphite));
	}

	@Test
	public void theBarroniteMaceIsFifteenPercentAgainstGolems()
	{
		Monster golem = named("Black golem");
		Monster plain = golem.toBuilder().attributes(java.util.Collections.emptyList()).build();

		assertEquals("the Barronite mace is +15% against a golem",
			maxHit(weapon("Barronite mace"), CombatStyleType.CRUSH, plain) * 23 / 20,
			maxHit(weapon("Barronite mace"), CombatStyleType.CRUSH, golem));
	}

	/** The rat bone weapons add a flat 10, not a percentage - and the Bone dagger is not one of them. */
	@Test
	public void ratbaneWeaponsAddAFlatTenAgainstRats()
	{
		Monster scurrius = named("Scurrius");
		Monster plain = scurrius.toBuilder().attributes(java.util.Collections.emptyList()).build();

		assertEquals("the Bone mace adds a flat 10 against a rat",
			maxHit(weapon("Bone mace"), CombatStyleType.CRUSH, plain) + 10,
			maxHit(weapon("Bone mace"), CombatStyleType.CRUSH, scurrius));
		assertEquals("the Bone dagger is not a ratbane weapon",
			maxHit(weapon("Bone dagger"), CombatStyleType.STAB, plain),
			maxHit(weapon("Bone dagger"), CombatStyleType.STAB, scurrius));
	}

	@Test
	public void anOrdinarySwordGainsNothingAgainstADemon()
	{
		assertEquals("a whip is no better against a demon",
			maxHit(weapon("Abyssal whip"), CombatStyleType.SLASH, named("Aberrant spectre")),
			maxHit(weapon("Abyssal whip"), CombatStyleType.SLASH, named("Abyssal demon")));
	}

	// --- helpers ------------------------------------------------------------------------------------


	private static double onTask(EquipmentPiece head, EquipmentPiece weapon, CombatStyleType type, Monster m)
	{
		return onTask(head, weapon, type, m, null);
	}

	private static double onTask(EquipmentPiece head, EquipmentPiece weapon, CombatStyleType type, Monster m,
		String neck)
	{
		return DpsEngine.dps(player(head, weapon, type, m, true, neck), m);
	}

	private static double offTask(EquipmentPiece head, EquipmentPiece weapon, CombatStyleType type, Monster m)
	{
		return DpsEngine.dps(player(head, weapon, type, m, false, null), m);
	}

	private static double cast(EquipmentPiece weapon, Monster m)
	{
		Player p = player(null, weapon, CombatStyleType.MAGIC, m, false, null).toBuilder()
			.spell(Spell.byName("Fire Surge", spells))
			.build()
			.withGearBonuses(m);
		return DpsEngine.dps(p, m);
	}

	private static int maxHit(EquipmentPiece weapon, CombatStyleType type, Monster m)
	{
		return DpsEngine.estimate(player(null, weapon, type, m, false, null), m).getMaxHit();
	}

	/** Ranged with a particular ammunition loaded, which is what decides a leafy creature's immunity. */
	private static double ranged(EquipmentPiece weapon, EquipmentPiece ammo, Monster m)
	{
		Player p = player(null, weapon, CombatStyleType.RANGED, m, false, null).toBuilder()
			.equipment(Player.PlayerEquipment.builder().weapon(weapon).ammo(ammo).build())
			.build()
			.withGearBonuses(m);
		return DpsEngine.dps(p, m);
	}

	private static double spellAt(String spellName, Monster m)
	{
		EquipmentPiece staff = weapon("Kodai wand");
		Player p = player(null, staff, CombatStyleType.MAGIC, m, false, null).toBuilder()
			.spell(Spell.byName(spellName, spells))
			.build()
			.withGearBonuses(m);
		return DpsEngine.dps(p, m);
	}

	private static Player player(EquipmentPiece head, EquipmentPiece weapon, CombatStyleType type, Monster m,
		boolean onTask, String neck)
	{
		Player.PlayerEquipment gear = Player.PlayerEquipment.builder().weapon(weapon).head(head).build();
		if (neck != null)
		{
			gear = gear.withSlot("neck", item(neck));
		}
		return Player.builder()
			.style(styleFor(weapon, type))
			.equipment(gear)
			.buffs(PlayerBuffs.builder().onSlayerTask(onTask).build())
			.build()
			.withGearBonuses(m);
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
			.filter(p -> p.getVersion() == null || !"Uncharged".equals(p.getVersion()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no weapon " + name));
	}

	private static Monster named(String name)
	{
		return monsters.stream().filter(m -> name.equals(m.getName())).findFirst()
			.orElseThrow(() -> new IllegalStateException("no monster " + name));
	}

	private static Monster named(String name, String version)
	{
		return monsters.stream()
			.filter(m -> name.equals(m.getName()) && version.equals(m.getVersion()))
			.findFirst()
			.orElseThrow(() -> new IllegalStateException("no monster " + name + " (" + version + ")"));
	}

	private static <T> T read(String resource, Type type)
	{
		try (InputStream in = TaskAndBaneTest.class.getResourceAsStream(resource))
		{
			return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
}
