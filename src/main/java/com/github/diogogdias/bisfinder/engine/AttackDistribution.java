package com.github.diogogdias.bisfinder.engine;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * An attack's hitsplats. Most weapons are a single standard hitsplat; multi-hit weapons (Scythe of vitur,
 * dual-hit crossbows, ...) and proc effects contribute additional hitsplats. DPS is the summed expected
 * damage over the weapon's attack speed.
 */
public final class AttackDistribution
{
	private final List<Hitsplat> hitsplats;

	public AttackDistribution(List<Hitsplat> hitsplats)
	{
		this.hitsplats = Collections.unmodifiableList(hitsplats);
	}

	public static AttackDistribution single(Hitsplat hitsplat)
	{
		return new AttackDistribution(Collections.singletonList(hitsplat));
	}

	public static AttackDistribution of(Hitsplat... hitsplats)
	{
		return new AttackDistribution(Arrays.asList(hitsplats));
	}

	public double expectedDamage()
	{
		double total = 0.0;
		for (Hitsplat hitsplat : hitsplats)
		{
			total += hitsplat.expectedDamage();
		}
		return total;
	}

	public double dps(int attackSpeedTicks)
	{
		return expectedDamage() / (attackSpeedTicks * RollMath.SECONDS_PER_TICK);
	}

	public List<Hitsplat> getHitsplats()
	{
		return hitsplats;
	}
}
