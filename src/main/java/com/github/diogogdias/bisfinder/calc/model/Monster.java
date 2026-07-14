package com.github.diogogdias.bisfinder.calc.model;

import com.google.gson.annotations.SerializedName;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

/**
 * A monster from the OSRS Wiki DPS calculator's monsters.json. The {@code id} is a real in-game NPC ID,
 * but it is not unique: a handful of monsters appear more than once, distinguished by {@code version}.
 *
 * @see <a href="https://github.com/weirdgloop/osrs-dps-calc/blob/main/cdn/json/monsters.json">monsters.json</a>
 */
@Value
@Builder(toBuilder = true)
public class Monster
{
	int id;
	String name;
	/**
	 * The monster's variant, e.g. "Dragon Slayer II". Empty string when the monster has no variants.
	 */
	String version;
	String image;
	int level;
	/**
	 * Attack speed in ticks.
	 */
	int speed;
	/**
	 * Free-text attack styles from the wiki, e.g. ["Slash", "Magic", "Ranged", "Dragonfire"]. Null in the
	 * JSON for a handful of monsters; {@link #getStyle()} normalises that to an empty list.
	 */
	List<String> style;
	int size;
	/**
	 * Display-only. Usually a plain number but sometimes annotated, e.g. "30 (Magic)". The calculator
	 * computes max hit itself and does not read this.
	 */
	@SerializedName("max_hit")
	String maxHit;
	Skills skills;
	Offensive offensive;
	Defensive defensive;
	List<MonsterAttribute> attributes;
	Immunities immunities;
	@SerializedName("is_slayer_monster")
	boolean slayerMonster;
	/**
	 * Null when the monster has no elemental weakness.
	 */
	Weakness weakness;
	/**
	 * Not present in monsters.json: these are the UI-controlled inputs (party size, invocation level,
	 * current hp, defence reductions, phase, ...) that the calculator scales the monster with. Null on a
	 * freshly deserialised monster; {@link #getInputs()} normalises that to the calculator's defaults.
	 */
	MonsterInputs inputs;

	public List<String> getStyle()
	{
		return style == null ? Collections.emptyList() : style;
	}

	public MonsterInputs getInputs()
	{
		return inputs == null ? MonsterInputs.defaults() : inputs;
	}

	public List<MonsterAttribute> getAttributes()
	{
		return attributes == null ? Collections.emptyList() : attributes;
	}

	@Value
	public static class Skills
	{
		int atk;
		int def;
		int hp;
		int magic;
		int ranged;
		int str;
	}

	@Value
	public static class Offensive
	{
		int atk;
		int magic;
		@SerializedName("magic_str")
		int magicStr;
		int ranged;
		@SerializedName("ranged_str")
		int rangedStr;
		int str;
	}

	@Value
	public static class Defensive
	{
		int stab;
		int slash;
		int crush;
		int magic;
		/**
		 * Ranged defence is split by ranged damage type.
		 */
		int heavy;
		int standard;
		int light;
		@SerializedName("flat_armour")
		int flatArmour;
	}

	@Value
	public static class Immunities
	{
		/**
		 * Null means the monster cannot be burnt at all.
		 */
		BurnImmunity burn;
	}

	@Value
	public static class Weakness
	{
		/**
		 * Spell element: "air", "water", "earth", "fire", or "none".
		 */
		String element;
		int severity;
	}
}
