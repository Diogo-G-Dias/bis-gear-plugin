package com.github.diogogdias.bisfinder.calc.model;

import lombok.Builder;
import lombok.Value;

/**
 * Ported from osrs-dps-calc (src/types/Player.ts).
 *
 * <p>Only the buffs the calculator itself reads are kept; the UI-only fields are dropped.
 */
@Value
@Builder(toBuilder = true)
public class PlayerBuffs
{
	@Builder.Default
	boolean onSlayerTask = false;

	@Builder.Default
	boolean inWilderness = false;

	@Builder.Default
	boolean forinthrySurge = false;

	@Builder.Default
	int soulreaperStacks = 0;

	@Builder.Default
	int baAttackerLevel = 0;

	@Builder.Default
	int chinchompaDistance = 4;

	@Builder.Default
	boolean kandarinDiary = false;

	@Builder.Default
	boolean chargeSpell = false;

	@Builder.Default
	boolean markOfDarknessSpell = false;

	@Builder.Default
	boolean usingSunfireRunes = false;

	public static PlayerBuffs none()
	{
		return PlayerBuffs.builder().build();
	}
}
