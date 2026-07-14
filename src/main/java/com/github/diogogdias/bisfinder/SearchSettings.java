package com.github.diogogdias.bisfinder;

import com.github.diogogdias.bisfinder.calc.model.MonsterInputs;
import com.github.diogogdias.bisfinder.calc.model.Potion;
import com.github.diogogdias.bisfinder.calc.model.Prayer;
import lombok.Builder;
import lombok.Value;

/**
 * Everything the panel lets the player say about the fight, as opposed to what they own.
 *
 * <p>The defence reductions and soulreaper stacks are fed to the ported calculator exactly as the wiki
 * site feeds its own: reductions scale the monster before the rolls are computed, and the stacks raise
 * the Soulreaper axe's effective strength.
 */
@Value
@Builder(toBuilder = true)
public class SearchSettings
{
	@Builder.Default
	boolean onSlayerTask = false;

	@Builder.Default
	boolean inWilderness = false;

	/** Null means "assume the best for the style". */
	@Builder.Default
	Prayer prayer = null;

	/** Null means "assume the best for the style". */
	@Builder.Default
	Potion potion = null;

	/** Prayer has no NONE, so praying nothing is said here instead. */
	@Builder.Default
	boolean noPrayer = false;

	/** Soul stacks on the Soulreaper axe, 0-5. */
	@Builder.Default
	int soulreaperStacks = 0;

	@Builder.Default
	MonsterInputs.DefenceReductions defenceReductions = MonsterInputs.DefenceReductions.builder().build();

	public static SearchSettings defaults()
	{
		return SearchSettings.builder().build();
	}
}
