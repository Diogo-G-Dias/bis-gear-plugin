package com.github.diogogdias.bisfinder.calc;

import com.github.diogogdias.bisfinder.calc.dist.AttackDistribution;
import com.github.diogogdias.bisfinder.calc.dist.HitDistribution;
import com.github.diogogdias.bisfinder.calc.dist.WeightedHit;
import com.github.diogogdias.bisfinder.calc.model.EquipmentCategory;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.Player;
import com.github.diogogdias.bisfinder.calc.model.PlayerCombatStyle;
import com.github.diogogdias.bisfinder.calc.model.PlayerSkills;
import org.junit.Assert;
import org.junit.Test;

/**
 * Special attack coverage for the ported engine.
 */
public class SpecialAttackTest
{
	/** Abyssal demon (Standard). */
	private static final int ABYSSAL_DEMON = 415;

	private static final int EYE_OF_AYAK = 31113; // Charged
	private static final int DRAGON_CLAWS = 13652;
	private static final int DRAGON_WARHAMMER = 13576;
	private static final int BLADE_OF_SAELDOR = 23995; // Charged

	private static Player weaponOnly(int weaponId, Monster monster, EquipmentCategory category)
	{
		return Player.builder()
			.skills(new PlayerSkills(99, 99, 99, 99, 99, 99, 99, 99, 99))
			.style(PlayerCombatStyle.getCombatStylesForCategory(category).get(0))
			.equipment(Player.PlayerEquipment.builder()
				.weapon(WikiTestData.equipmentById(weaponId))
				.build())
			.build()
			.withGearBonuses(monster);
	}

	private static CalcOpts spec()
	{
		return CalcOpts.builder().usingSpecialAttack(true).build();
	}

	@Test
	public void eyeOfAyakSpecIsStrongerAndMoreAccurateThanItsNormalAttack()
	{
		Monster monster = WikiTestData.monsterById(ABYSSAL_DEMON);
		Player player = weaponOnly(EYE_OF_AYAK, monster, EquipmentCategory.POWERED_STAFF);

		PlayerVsNpcCalc normal = new PlayerVsNpcCalc(player, monster);
		PlayerVsNpcCalc special = new PlayerVsNpcCalc(player, monster, spec());

		// normal: floor(99 / 3) - 6 = 27, unchanged by this work
		Assert.assertEquals(27, normal.getMinAndMax().getMax());

		// spec: 27 * 13 / 10 = 35
		Assert.assertEquals(35, special.getMinAndMax().getMax());
		Assert.assertTrue("spec max hit should exceed the normal max hit",
			special.getMinAndMax().getMax() > normal.getMinAndMax().getMax());

		// spec doubles the attack roll, so accuracy strictly improves
		Assert.assertEquals(2 * normal.getMaxAttackRoll(), special.getMaxAttackRoll());
		Assert.assertTrue("spec hit chance should exceed the normal hit chance",
			special.getHitChance() > normal.getHitChance());

		// 5 tick spec, 50 energy
		Assert.assertEquals(5.0, special.getExpectedAttackSpeed(), 1e-9);
		Assert.assertEquals(Integer.valueOf(50), special.getSpecCost());
		Assert.assertEquals(FeatureStatus.IMPLEMENTED, special.isSpecSupported());
	}

	@Test
	public void dragonClawsSpecRollsFourHitsplats()
	{
		Monster monster = WikiTestData.monsterById(ABYSSAL_DEMON);
		Player player = weaponOnly(DRAGON_CLAWS, monster, EquipmentCategory.CLAW);

		PlayerVsNpcCalc special = new PlayerVsNpcCalc(player, monster, spec());

		AttackDistribution dist = special.getDistribution();
		Assert.assertEquals(1, dist.getDists().size());

		for (WeightedHit hit : dist.getDists().get(0).getHits())
		{
			Assert.assertEquals("every dragon claws roll is 4 hitsplats", 4, hit.getHitsplats().size());
		}

		int max = special.getMinAndMax().getMax();

		// the highest-damage branch is the first accuracy roll passing: the four splats sum to
		// (dmg/2) + (dmg/4) + (dmg/8) + (dmg/8 + 1) for dmg up to (2 * max - 1)
		int expectedDistMax = highestClawSum(max);
		Assert.assertEquals(expectedDistMax, dist.getMax());
		Assert.assertEquals(expectedDistMax, special.getMax());

		Assert.assertTrue("the 4-hit spec beats a single max hit", dist.getMax() > max);
		Assert.assertTrue("spec dps must be positive", special.getSpecDps() > 0);
		Assert.assertEquals(Integer.valueOf(50), special.getSpecCost());
	}

	/**
	 * Sum of the four claw hitsplats on the accRoll=0 branch, at its top damage roll.
	 */
	private static int highestClawSum(int max)
	{
		int low = max; // trunc(max * (4 - 0) / 4)
		int dmg = max + low - 1;
		return (dmg / 2) + (dmg / 4) + (dmg / 8) + (dmg / 8) + 1;
	}

	@Test
	public void dragonWarhammerSpecCostIsFifty()
	{
		Monster monster = WikiTestData.monsterById(ABYSSAL_DEMON);
		Player player = weaponOnly(DRAGON_WARHAMMER, monster, EquipmentCategory.BLUNT);

		PlayerVsNpcCalc normal = new PlayerVsNpcCalc(player, monster);
		PlayerVsNpcCalc special = new PlayerVsNpcCalc(player, monster, spec());

		Assert.assertEquals(Integer.valueOf(50), normal.getSpecCost());
		Assert.assertEquals(FeatureStatus.IMPLEMENTED, normal.isSpecSupported());

		// dwh spec is a flat 3/2 max hit, with no accuracy bonus
		int normalMax = normal.getMinAndMax().getMax();
		Assert.assertEquals(normalMax * 3 / 2, special.getMinAndMax().getMax());
		Assert.assertEquals(normal.getMaxAttackRoll(), special.getMaxAttackRoll());
	}

	@Test
	public void bladeOfSaeldorHasNoSpec()
	{
		Monster monster = WikiTestData.monsterById(ABYSSAL_DEMON);
		Player player = weaponOnly(BLADE_OF_SAELDOR, monster, EquipmentCategory.SLASH_SWORD);

		PlayerVsNpcCalc calc = new PlayerVsNpcCalc(player, monster);

		Assert.assertNull(calc.getSpecCost());
		Assert.assertEquals(FeatureStatus.NOT_APPLICABLE, calc.isSpecSupported());
		Assert.assertNull(calc.getSpecCalc());
		Assert.assertTrue("no spec means no user issue is raised", calc.getUserIssues().isEmpty());
	}

	@Test
	public void unsupportedSpecRaisesAUserIssue()
	{
		Monster monster = WikiTestData.monsterById(ABYSSAL_DEMON);
		// Dragon 2h sword is on UNIMPLEMENTED_SPECS
		Player player = Player.builder()
			.skills(new PlayerSkills(99, 99, 99, 99, 99, 99, 99, 99, 99))
			.style(PlayerCombatStyle.getCombatStylesForCategory(EquipmentCategory.TWO_HANDED_SWORD).get(0))
			.equipment(Player.PlayerEquipment.builder()
				.weapon(WikiTestData.equipmentByName("Dragon 2h sword"))
				.build())
			.build()
			.withGearBonuses(monster);

		PlayerVsNpcCalc calc = new PlayerVsNpcCalc(player, monster);

		Assert.assertEquals(FeatureStatus.UNIMPLEMENTED, calc.isSpecSupported());
		Assert.assertEquals(1, calc.getUserIssues().size());
		Assert.assertEquals(UserIssue.Type.EQUIPMENT_SPEC_UNSUPPORTED, calc.getUserIssues().get(0).getType());
	}

	@Test
	public void burningClawsSpecAddsBurnDamageOverTime()
	{
		Monster monster = WikiTestData.monsterById(ABYSSAL_DEMON);
		Player player = weaponOnly(WikiTestData.equipmentByName("Burning claws").getId(), monster,
			EquipmentCategory.CLAW);

		PlayerVsNpcCalc normal = new PlayerVsNpcCalc(player, monster);
		PlayerVsNpcCalc special = new PlayerVsNpcCalc(player, monster, spec());

		Assert.assertEquals(0, normal.getDoTMax());
		Assert.assertEquals(29, special.getDoTMax());
		Assert.assertTrue("burn ticks should contribute expected damage", special.getDoTExpected() > 0);

		HitDistribution dist = special.getDistribution().getDists().get(0);
		for (WeightedHit hit : dist.getHits())
		{
			Assert.assertEquals("every burning claws roll is 3 hitsplats", 3, hit.getHitsplats().size());
		}

		Assert.assertEquals(Integer.valueOf(30), special.getSpecCost());
	}
}
