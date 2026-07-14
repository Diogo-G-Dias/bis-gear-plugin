package com.github.diogogdias.bisfinder.calc.model;

import com.github.diogogdias.bisfinder.calc.dist.Factor;
import java.util.Arrays;
import java.util.List;

/**
 * Ported from osrs-dps-calc (src/enums/Prayer.ts).
 *
 * <p>Factors are given with a denominator of 100 so that additive prayers work out correctly.
 */
public enum Prayer
{
	BURST_OF_STRENGTH("Burst of Strength", PrayerStyle.MELEE, null, Factor.of(105, 100), 0),
	CLARITY_OF_THOUGHT("Clarity of Thought", PrayerStyle.MELEE, Factor.of(105, 100), null, 0),
	SHARP_EYE("Sharp Eye", PrayerStyle.RANGED, Factor.of(105, 100), Factor.of(105, 100), 0),
	MYSTIC_WILL("Mystic Will", PrayerStyle.MAGIC, Factor.of(105, 100), null, 0),
	SUPERHUMAN_STRENGTH("Superhuman Strength", PrayerStyle.MELEE, null, Factor.of(110, 100), 0),
	IMPROVED_REFLEXES("Improved Reflexes", PrayerStyle.MELEE, Factor.of(110, 100), null, 0),
	HAWK_EYE("Hawk Eye", PrayerStyle.RANGED, Factor.of(110, 100), Factor.of(110, 100), 0),
	MYSTIC_LORE("Mystic Lore", PrayerStyle.MAGIC, Factor.of(110, 100), null, 10),
	ULTIMATE_STRENGTH("Ultimate Strength", PrayerStyle.MELEE, null, Factor.of(115, 100), 0),
	INCREDIBLE_REFLEXES("Incredible Reflexes", PrayerStyle.MELEE, Factor.of(115, 100), null, 0),
	EAGLE_EYE("Eagle Eye", PrayerStyle.RANGED, Factor.of(115, 100), Factor.of(115, 100), 0),
	MYSTIC_MIGHT("Mystic Might", PrayerStyle.MAGIC, Factor.of(115, 100), null, 20),
	CHIVALRY("Chivalry", PrayerStyle.MELEE, Factor.of(115, 100), Factor.of(118, 100), 0),
	PIETY("Piety", PrayerStyle.MELEE, Factor.of(120, 100), Factor.of(123, 100), 0),
	RIGOUR("Rigour", PrayerStyle.RANGED, Factor.of(120, 100), Factor.of(123, 100), 0),
	AUGURY("Augury", PrayerStyle.MAGIC, Factor.of(125, 100), null, 40),
	THICK_SKIN("Thick Skin", null, null, null, 0),
	ROCK_SKIN("Rock Skin", null, null, null, 0),
	STEEL_SKIN("Steel Skin", null, null, null, 0),
	DEADEYE("Deadeye", PrayerStyle.RANGED, Factor.of(118, 100), Factor.of(118, 100), 0),
	MYSTIC_VIGOUR("Mystic Vigour", PrayerStyle.MAGIC, Factor.of(118, 100), null, 30);

	public enum PrayerStyle
	{
		MELEE,
		RANGED,
		MAGIC
	}

	private final String prayerName;
	private final PrayerStyle style;
	private final Factor factorAccuracy;
	private final Factor factorStrength;
	private final int magicDamageBonus;

	Prayer(String prayerName, PrayerStyle style, Factor factorAccuracy, Factor factorStrength, int magicDamageBonus)
	{
		this.prayerName = prayerName;
		this.style = style;
		this.factorAccuracy = factorAccuracy;
		this.factorStrength = factorStrength;
		this.magicDamageBonus = magicDamageBonus;
	}

	public String getPrayerName()
	{
		return prayerName;
	}

	public PrayerStyle getStyle()
	{
		return style;
	}

	public Factor getFactorAccuracy()
	{
		return factorAccuracy;
	}

	public Factor getFactorStrength()
	{
		return factorStrength;
	}

	public int getMagicDamageBonus()
	{
		return magicDamageBonus;
	}

	/** The strongest offensive prayer for each style, used when the panel picks prayers for the player. */
	public static List<Prayer> best(PrayerStyle style)
	{
		switch (style)
		{
			case MELEE:
				return Arrays.asList(PIETY);
			case RANGED:
				return Arrays.asList(RIGOUR);
			case MAGIC:
				return Arrays.asList(AUGURY);
			default:
				throw new IllegalArgumentException("unknown style " + style);
		}
	}
}
