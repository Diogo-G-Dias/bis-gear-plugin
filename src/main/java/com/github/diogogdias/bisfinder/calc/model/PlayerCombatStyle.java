package com.github.diogogdias.bisfinder.calc.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Value;

/** Mirrors a combat style entry from the OSRS Wiki DPS data. */
@Value
public class PlayerCombatStyle
{
	String name;
	CombatStyleType type;
	CombatStyleStance stance;

	/**
	 * The actual player stat is still "ranged", so this is not part of {@link CombatStyleType}.
	 */
	public enum RangedDamageType
	{
		LIGHT,
		STANDARD,
		HEAVY,
		MIXED
	}

	/**
	 * Maps a ranged weapon category to its damage type.
	 */
	public static RangedDamageType getRangedDamageType(EquipmentCategory category)
	{
		switch (category)
		{
			case THROWN:
				return RangedDamageType.LIGHT;

			case BOW:
				return RangedDamageType.STANDARD;

			case CROSSBOW:
			case CHINCHOMPA:
				return RangedDamageType.HEAVY;

			case SALAMANDER:
				return RangedDamageType.MIXED;

			default:
				throw new IllegalArgumentException("Not a ranged weapon category: " + category);
		}
	}

	/**
	 * The combat styles available for a given equipment category. The "Manual Cast" pseudo style is
	 * appended to every category.
	 */
	public static List<PlayerCombatStyle> getCombatStylesForCategory(EquipmentCategory category)
	{
		List<PlayerCombatStyle> ret = new ArrayList<>();
		switch (category)
		{
			case TWO_HANDED_SWORD:
				ret.add(style("Chop", CombatStyleType.SLASH, CombatStyleStance.ACCURATE));
				ret.add(style("Slash", CombatStyleType.SLASH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Smash", CombatStyleType.CRUSH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Block", CombatStyleType.SLASH, CombatStyleStance.DEFENSIVE));
				break;
			case BANNER:
				ret.add(style("Lunge", CombatStyleType.STAB, CombatStyleStance.ACCURATE));
				ret.add(style("Swipe", CombatStyleType.SLASH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Pound", CombatStyleType.CRUSH, CombatStyleStance.CONTROLLED));
				ret.add(style("Block", CombatStyleType.STAB, CombatStyleStance.DEFENSIVE));
				break;
			case BLADED_STAFF:
				ret.add(style("Jab", CombatStyleType.STAB, CombatStyleStance.ACCURATE));
				ret.add(style("Swipe", CombatStyleType.SLASH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Fend", CombatStyleType.CRUSH, CombatStyleStance.DEFENSIVE));
				ret.add(style("Spell", CombatStyleType.MAGIC, CombatStyleStance.DEFENSIVE_AUTOCAST));
				ret.add(style("Spell", CombatStyleType.MAGIC, CombatStyleStance.AUTOCAST));
				break;
			case BLASTER:
				// TODO? (left unimplemented in the source too)
				return Collections.emptyList();
			case BOW:
			case CROSSBOW:
			case THROWN:
				ret.add(style("Accurate", CombatStyleType.RANGED, CombatStyleStance.ACCURATE));
				ret.add(style("Rapid", CombatStyleType.RANGED, CombatStyleStance.RAPID));
				ret.add(style("Longrange", CombatStyleType.RANGED, CombatStyleStance.LONGRANGE));
				break;
			case GUN:
				ret.add(style("Kick", CombatStyleType.CRUSH, CombatStyleStance.AGGRESSIVE));
				break;
			case BULWARK:
				ret.add(style("Pummel", CombatStyleType.CRUSH, CombatStyleStance.ACCURATE));
				ret.add(style("Block", null, null));
				break;
			case MULTI_MELEE:
				ret.add(style("Poke", CombatStyleType.STAB, CombatStyleStance.ACCURATE));
				ret.add(style("Slash", CombatStyleType.SLASH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Pound", CombatStyleType.CRUSH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Block", CombatStyleType.SLASH, CombatStyleStance.DEFENSIVE));
				break;
			case PARTISAN:
				ret.add(style("Stab", CombatStyleType.STAB, CombatStyleStance.ACCURATE));
				ret.add(style("Lunge", CombatStyleType.STAB, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Pound", CombatStyleType.CRUSH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Block", CombatStyleType.STAB, CombatStyleStance.DEFENSIVE));
				break;
			case PICKAXE:
				ret.add(style("Spike", CombatStyleType.STAB, CombatStyleStance.ACCURATE));
				ret.add(style("Impale", CombatStyleType.STAB, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Smash", CombatStyleType.CRUSH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Block", CombatStyleType.STAB, CombatStyleStance.DEFENSIVE));
				break;
			case POLEARM:
				ret.add(style("Jab", CombatStyleType.STAB, CombatStyleStance.CONTROLLED));
				ret.add(style("Swipe", CombatStyleType.SLASH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Fend", CombatStyleType.STAB, CombatStyleStance.DEFENSIVE));
				break;
			case POWERED_STAFF:
			case POWERED_WAND:
				ret.add(style("Accurate", CombatStyleType.MAGIC, CombatStyleStance.ACCURATE));
				ret.add(style("Accurate", CombatStyleType.MAGIC, CombatStyleStance.ACCURATE));
				ret.add(style("Longrange", CombatStyleType.MAGIC, CombatStyleStance.LONGRANGE));
				break;
			case SALAMANDER:
				ret.add(style("Scorch", CombatStyleType.SLASH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Flare", CombatStyleType.RANGED, CombatStyleStance.RAPID));
				ret.add(style("Blaze", CombatStyleType.MAGIC, CombatStyleStance.DEFENSIVE));
				break;
			case CHINCHOMPA:
				ret.add(style("Short fuse", CombatStyleType.RANGED, CombatStyleStance.ACCURATE));
				ret.add(style("Medium fuse", CombatStyleType.RANGED, CombatStyleStance.RAPID));
				ret.add(style("Long fuse", CombatStyleType.RANGED, CombatStyleStance.LONGRANGE));
				break;
			case CLAW:
				ret.add(style("Chop", CombatStyleType.SLASH, CombatStyleStance.ACCURATE));
				ret.add(style("Slash", CombatStyleType.SLASH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Lunge", CombatStyleType.STAB, CombatStyleStance.CONTROLLED));
				ret.add(style("Block", CombatStyleType.SLASH, CombatStyleStance.DEFENSIVE));
				break;
			case BLUDGEON:
				ret.add(style("Pound", CombatStyleType.CRUSH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Pummel", CombatStyleType.CRUSH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Smash", CombatStyleType.CRUSH, CombatStyleStance.AGGRESSIVE));
				break;
			case BLUNT:
				ret.add(style("Pound", CombatStyleType.CRUSH, CombatStyleStance.ACCURATE));
				ret.add(style("Pummel", CombatStyleType.CRUSH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Block", CombatStyleType.CRUSH, CombatStyleStance.DEFENSIVE));
				break;
			case POLESTAFF:
				ret.add(style("Bash", CombatStyleType.CRUSH, CombatStyleStance.ACCURATE));
				ret.add(style("Pound", CombatStyleType.CRUSH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Block", CombatStyleType.CRUSH, CombatStyleStance.DEFENSIVE));
				break;
			case SPIKED:
				ret.add(style("Pound", CombatStyleType.CRUSH, CombatStyleStance.ACCURATE));
				ret.add(style("Pummel", CombatStyleType.CRUSH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Spike", CombatStyleType.STAB, CombatStyleStance.CONTROLLED));
				ret.add(style("Block", CombatStyleType.CRUSH, CombatStyleStance.DEFENSIVE));
				break;
			case STAFF:
				ret.add(style("Bash", CombatStyleType.CRUSH, CombatStyleStance.ACCURATE));
				ret.add(style("Pound", CombatStyleType.CRUSH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Focus", CombatStyleType.CRUSH, CombatStyleStance.DEFENSIVE));
				ret.add(style("Spell", CombatStyleType.MAGIC, CombatStyleStance.DEFENSIVE_AUTOCAST));
				ret.add(style("Spell", CombatStyleType.MAGIC, CombatStyleStance.AUTOCAST));
				break;
			case AXE:
				ret.add(style("Chop", CombatStyleType.SLASH, CombatStyleStance.ACCURATE));
				ret.add(style("Hack", CombatStyleType.SLASH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Smash", CombatStyleType.CRUSH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Block", CombatStyleType.SLASH, CombatStyleStance.DEFENSIVE));
				break;
			case NONE:
			case UNARMED:
				ret.add(style("Punch", CombatStyleType.CRUSH, CombatStyleStance.ACCURATE));
				ret.add(style("Kick", CombatStyleType.CRUSH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Block", CombatStyleType.CRUSH, CombatStyleStance.DEFENSIVE));
				break;
			case SCYTHE:
				ret.add(style("Reap", CombatStyleType.SLASH, CombatStyleStance.ACCURATE));
				ret.add(style("Chop", CombatStyleType.SLASH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Jab", CombatStyleType.CRUSH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Block", CombatStyleType.SLASH, CombatStyleStance.DEFENSIVE));
				break;
			case SLASH_SWORD:
				ret.add(style("Chop", CombatStyleType.SLASH, CombatStyleStance.ACCURATE));
				ret.add(style("Slash", CombatStyleType.SLASH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Lunge", CombatStyleType.STAB, CombatStyleStance.CONTROLLED));
				ret.add(style("Block", CombatStyleType.SLASH, CombatStyleStance.DEFENSIVE));
				break;
			case SPEAR:
				ret.add(style("Lunge", CombatStyleType.STAB, CombatStyleStance.CONTROLLED));
				ret.add(style("Swipe", CombatStyleType.SLASH, CombatStyleStance.CONTROLLED));
				ret.add(style("Pound", CombatStyleType.CRUSH, CombatStyleStance.CONTROLLED));
				ret.add(style("Block", CombatStyleType.STAB, CombatStyleStance.DEFENSIVE));
				break;
			case STAB_SWORD:
				ret.add(style("Stab", CombatStyleType.STAB, CombatStyleStance.ACCURATE));
				ret.add(style("Lunge", CombatStyleType.STAB, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Slash", CombatStyleType.SLASH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Block", CombatStyleType.STAB, CombatStyleStance.DEFENSIVE));
				break;
			case WHIP:
				ret.add(style("Flick", CombatStyleType.SLASH, CombatStyleStance.ACCURATE));
				ret.add(style("Lash", CombatStyleType.SLASH, CombatStyleStance.CONTROLLED));
				ret.add(style("Deflect", CombatStyleType.SLASH, CombatStyleStance.DEFENSIVE));
				break;
			case FLAIL:
				ret.add(style("Chop", CombatStyleType.SLASH, CombatStyleStance.ACCURATE));
				ret.add(style("Slash", CombatStyleType.SLASH, CombatStyleStance.AGGRESSIVE));
				ret.add(style("Block", CombatStyleType.SLASH, CombatStyleStance.DEFENSIVE));
				break;
			default:
				break;
		}

		// Add a pseudo combat style here for manual casting
		ret.add(style("Spell", CombatStyleType.MAGIC, CombatStyleStance.MANUAL_CAST));
		return ret;
	}

	private static PlayerCombatStyle style(String name, CombatStyleType type, CombatStyleStance stance)
	{
		return new PlayerCombatStyle(name, type, stance);
	}
}
