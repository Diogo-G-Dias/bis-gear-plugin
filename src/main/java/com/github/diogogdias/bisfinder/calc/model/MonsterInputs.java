package com.github.diogogdias.bisfinder.calc.model;

import lombok.Builder;
import lombok.Value;

/**
 * Mirrors the per-monster inputs in the OSRS Wiki DPS data (the inputs object of monsters.json).
 *
 * <p>Fields that users have control over in the UI, which may affect buff applicability, monster
 * scaling, etc. Defaults match the OSRS Wiki calculator's neutral starting inputs.
 */
@Value
@Builder(toBuilder = true)
public class MonsterInputs
{
	@Builder.Default
	boolean isFromCoxCm = false;

	@Builder.Default
	int toaInvocationLevel = 0;

	@Builder.Default
	int toaPathLevel = 0;

	@Builder.Default
	int partyMaxCombatLevel = 126;

	/**
	 * Sum total of cox party members' mining level, used to determine guardians' hp.
	 */
	@Builder.Default
	int partySumMiningLevel = 99;

	@Builder.Default
	int partyMaxHpLevel = 99;

	@Builder.Default
	int partySize = 1;

	/**
	 * The monster's current HP, for effects like Ruby bolt (e), or Vardorvis defence.
	 *
	 * <p>Defaults to 150; callers clamp it to the monster's max hp when it exceeds it.
	 */
	@Builder.Default
	int monsterCurrentHp = 150;

	@Builder.Default
	DefenceReductions defenceReductions = DefenceReductions.builder().build();

	/**
	 * Null when unset. BaseCalc.sanitizeInputs populates it for demons.
	 */
	@Builder.Default
	Integer demonbaneVulnerability = null;

	/**
	 * Null when unset.
	 */
	@Builder.Default
	String phase = null;

	public static MonsterInputs defaults()
	{
		return MonsterInputs.builder().build();
	}

	@Value
	@Builder(toBuilder = true)
	public static class DefenceReductions
	{
		@Builder.Default
		boolean vulnerability = false;

		@Builder.Default
		boolean accursed = false;

		@Builder.Default
		int elderMaul = 0;

		@Builder.Default
		int dwh = 0;

		@Builder.Default
		int arclight = 0;

		@Builder.Default
		int emberlight = 0;

		@Builder.Default
		int bgs = 0;

		@Builder.Default
		int tonalztic = 0;

		@Builder.Default
		int seercull = 0;

		@Builder.Default
		int ayak = 0;
	}
}
