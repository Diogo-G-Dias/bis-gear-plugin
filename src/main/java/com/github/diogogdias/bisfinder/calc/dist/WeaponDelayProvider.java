package com.github.diogogdias.bisfinder.calc.dist;

import java.util.List;

/**
 * Port of the WeaponDelayProvider type from osrs-dps-calc (src/lib/HitDist.ts).
 */
@FunctionalInterface
public interface WeaponDelayProvider
{
	List<ProbabilisticDelay> apply(WeightedHit wh);
}
