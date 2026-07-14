package com.github.diogogdias.bisfinder.calc.dist;

/**
 * Port of the HitTransformer type from osrs-dps-calc (src/lib/HitDist.ts).
 */
@FunctionalInterface
public interface HitTransformer
{
	HitDistribution apply(Hitsplat hitsplat);
}
