package com.github.diogogdias.bisfinder.calc;

import lombok.Builder;
import lombok.Value;

/**
 * Port of CalcOpts / InternalOpts from osrs-dps-calc (src/lib/BaseCalc.ts).
 *
 * <p>detailedOutput is not ported: the CalcDetails instrumentation is replaced by the plain arithmetic
 * it wrapped.
 */
@Value
@Builder(toBuilder = true)
public class CalcOpts
{
	@Builder.Default
	String loadoutName = "unknown";

	@Builder.Default
	boolean disableMonsterScaling = false;

	@Builder.Default
	boolean usingSpecialAttack = false;

	/**
	 * Internal-only flag for when the calc is being spawned as a sub-calc of the current, and as such the
	 * caller guarantees that all required properties are already sanitized and valid.
	 */
	@Builder.Default
	boolean noInit = false;

	@Builder.Default
	Overrides overrides = Overrides.builder().build();

	public static CalcOpts defaults()
	{
		return CalcOpts.builder().build();
	}

	@Value
	@Builder(toBuilder = true)
	public static class Overrides
	{
		/**
		 * Null when not overridden.
		 */
		@Builder.Default
		Double accuracy = null;

		@Builder.Default
		Integer attackRoll = null;

		@Builder.Default
		Integer defenceRoll = null;
	}
}
