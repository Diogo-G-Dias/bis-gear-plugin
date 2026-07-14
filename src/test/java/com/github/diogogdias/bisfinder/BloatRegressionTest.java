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
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * A real bank against a real target.
 *
 * <p>The search is a hill climb, so the failure that matters is not "wrong arithmetic" but "never
 * looked": a candidate that only wins once a second slot changes with it. A Blade of saeldor is only
 * worth holding if the shield slot is filled at the same time, and it was being judged against a loadout
 * with an empty shield slot — because the weapon it was replacing was two-handed — so it always lost.
 *
 * <p>This pins the invariant that catches that whole class of bug: whatever the optimizer returns must
 * not be beaten by a setup a player would actually build by hand.
 */
public class BloatRegressionTest
{
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
	public void doesNotLoseToAHandBuiltSaeldorSetup()
	{
		Monster bloat = monster("Pestilent Bloat", "Normal");

		BisOptimizer.Result result = new BisOptimizer().findBest(
			equipment, spells, bank, maxed(), PlayerBuffs.builder().build(), bloat);

		Assert.assertNotNull(result);

		double handBuilt = dps(saeldorSetup(bloat), bloat);

		System.out.println(String.format("Bloat: optimizer picked %s (%s) -> %.3f dps, max %d, acc %.1f%%",
			result.getPlayer().getEquipment().getWeapon().getName(),
			result.getStyle().getName(), result.getDps(), result.getMaxHit(), result.getAccuracy() * 100));
		result.getPlayer().getEquipment().bySlot()
			.forEach((slot, piece) -> System.out.println("    " + slot + ": " + piece.getName()));
		System.out.println(String.format("Bloat: hand-built saeldor + avernic -> %.3f dps", handBuilt));

		Assert.assertTrue(
			String.format("optimizer returned %.3f dps but a hand-built saeldor setup does %.3f",
				result.getDps(), handBuilt),
			result.getDps() >= handBuilt - 0.001);
	}

	/** Blade of saeldor + Ghommal's avernic defender 5, both of which are in the sample bank. */
	private Player saeldorSetup(Monster bloat)
	{
		Player.PlayerEquipment gear = Player.PlayerEquipment.builder()
			.weapon(byId(23995))       // Blade of saeldor
			.shield(byId(27550))       // Ghommal's avernic defender 5
			.head(byId(28254))         // Sanguine torva full helm
			.body(byId(28256))         // Sanguine torva platebody
			.legs(byId(28258))         // Sanguine torva platelegs
			.cape(byId(24855))         // Mythical max cape
			.neck(byId(24780))         // Amulet of blood fury
			.hands(byId(7462))         // Barrows gloves
			.feet(byId(29806))         // Aranea boots
			.ring(byId(22975))         // Brimstone ring
			.build();

		// Same prayer and potion the optimizer assumes, or the comparison is not like for like.
		return Player.builder()
			.skills(maxed())
			.boosts(Potion.SUPER_COMBAT.boost(maxed()))
			.prayers(Collections.singletonList(Prayer.PIETY))
			.buffs(PlayerBuffs.builder().build())
			.style(new PlayerCombatStyle("Slash", CombatStyleType.SLASH, CombatStyleStance.AGGRESSIVE))
			.equipment(gear)
			.build()
			.withGearBonuses(bloat);
	}

	/** Only pieces the sample bank actually contains, so the comparison is one the player could wear. */
	private EquipmentPiece byId(int id)
	{
		Assert.assertTrue("sample bank does not contain item " + id, bank.contains(id));
		return equipment.stream()
			.filter(piece -> piece.getId() == id)
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("no item " + id));
	}

	private double dps(Player player, Monster monster)
	{
		return new PlayerVsNpcCalc(player, monster).getDps();
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
		try (InputStream in = BloatRegressionTest.class.getResourceAsStream(resource))
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
