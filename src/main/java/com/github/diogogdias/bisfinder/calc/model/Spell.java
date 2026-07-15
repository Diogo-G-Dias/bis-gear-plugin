package com.github.diogogdias.bisfinder.calc.model;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Locale;
import lombok.Value;

/**
 * Mirrors the spells.json schema from the OSRS Wiki DPS data. Deserialised from cdn/json/spells.json.
 */
@Value
public class Spell
{
	String name;
	String image;

	@SerializedName("max_hit")
	int maxHit;

	String spellbook;

	/** air, water, earth or fire; null for spells with a fixed max hit. */
	String element;

	public boolean isBindSpell()
	{
		return "Bind".equals(name) || "Snare".equals(name) || "Entangle".equals(name);
	}

	public boolean canUseSunfireRunes()
	{
		return "fire".equals(element);
	}

	/**
	 * Elemental spells share a max hit within their tier: the calculator resolves the strongest
	 * element the player's magic level allows, rather than the element that was picked.
	 */
	public static int maxHit(Spell spell, int magicLevel, List<Spell> allSpells)
	{
		if (spell.getElement() == null)
		{
			return spell.getMaxHit();
		}

		String[] parts = spell.getName().split(" ");
		if (parts.length < 2)
		{
			return spell.getMaxHit();
		}

		String spellClass = parts[1];
		int[] thresholds;
		switch (spellClass.toLowerCase(Locale.ROOT))
		{
			case "strike":
				thresholds = new int[]{13, 9, 5};
				break;
			case "bolt":
				thresholds = new int[]{35, 29, 23};
				break;
			case "blast":
				thresholds = new int[]{59, 53, 47};
				break;
			case "wave":
				thresholds = new int[]{75, 70, 65};
				break;
			case "surge":
				thresholds = new int[]{95, 90, 85};
				break;
			default:
				return spell.getMaxHit();
		}

		String element;
		if (magicLevel >= thresholds[0])
		{
			element = "Fire";
		}
		else if (magicLevel >= thresholds[1])
		{
			element = "Earth";
		}
		else if (magicLevel >= thresholds[2])
		{
			element = "Water";
		}
		else
		{
			element = "Wind";
		}

		Spell resolved = byName(element + " " + spellClass, allSpells);
		return resolved == null ? spell.getMaxHit() : resolved.getMaxHit();
	}

	public static Spell byName(String name, List<Spell> allSpells)
	{
		for (Spell spell : allSpells)
		{
			if (spell.getName().equals(name))
			{
				return spell;
			}
		}
		return null;
	}
}
