package com.github.diogogdias.bisfinder.calc;

import lombok.Value;

/**
 * Port of UserIssue (src/types/State.ts) and the UserIssueType values (src/enums/UserIssueType.ts) that
 * BaseCalc.sanitizeInputs raises.
 */
@Value
public class UserIssue
{
	Type type;
	String message;
	String loadout;

	public enum Type
	{
		EQUIPMENT_WRONG_AMMO,
		EQUIPMENT_MISSING_AMMO,
		EQUIPMENT_SET_EFFECT_UNSUPPORTED,
		EQUIPMENT_SPEC_UNSUPPORTED,
		SPELL_WRONG_WEAPON,
		SPELL_WRONG_MONSTER,
		WEAPON_WRONG_MONSTER,
		RING_RECOIL_UNSUPPORTED,
		FEET_RECOIL_UNSUPPORTED
	}
}
