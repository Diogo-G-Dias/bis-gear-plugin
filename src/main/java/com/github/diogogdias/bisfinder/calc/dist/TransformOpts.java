package com.github.diogogdias.bisfinder.calc.dist;

/**
 * Port of the TransformOpts type from osrs-dps-calc (src/lib/HitDist.ts).
 */
public final class TransformOpts
{
	public static final TransformOpts DEFAULT_TRANSFORM_OPTS = new TransformOpts(true);

	private final boolean transformInaccurate;

	public TransformOpts(boolean transformInaccurate)
	{
		this.transformInaccurate = transformInaccurate;
	}

	public boolean isTransformInaccurate()
	{
		return transformInaccurate;
	}
}
