package com.github.diogogdias.bisfinder.calc.model;

import lombok.Value;
import lombok.With;

/**
 * Ported from osrs-dps-calc (src/types/Player.ts).
 *
 * <p>Used both for the player's base levels and for the boosts added on top of them, which is why
 * the values may be negative.
 */
@Value
@With
public class PlayerSkills
{
	int atk;
	int def;
	int hp;
	int magic;
	int prayer;
	int ranged;
	int str;
	int mining;
	int herblore;

	public static PlayerSkills none()
	{
		return new PlayerSkills(0, 0, 0, 0, 0, 0, 0, 0, 0);
	}
}
