package com.github.diogogdias.bisfinder.calc.model;

/**
 * Combat-boosting potions and the level boosts they grant.
 *
 * <p>Only the potions that raise a combat level are here. Each returns the boost to ADD to the player's
 * base levels, which is exactly what {@link PlayerSkills} boosts are.
 */
public enum Potion
{
	NONE("None")
		{
			@Override
			public PlayerSkills boost(PlayerSkills base)
			{
				return PlayerSkills.none();
			}
		},

	SUPER_COMBAT("Super combat")
		{
			@Override
			public PlayerSkills boost(PlayerSkills base)
			{
				return PlayerSkills.none()
					.withAtk((int) Math.floor(5 + base.getAtk() * 0.15))
					.withStr((int) Math.floor(5 + base.getStr() * 0.15))
					.withDef((int) Math.floor(5 + base.getDef() * 0.15));
			}
		},

	SMELLING_SALTS("Smelling salts")
		{
			@Override
			public PlayerSkills boost(PlayerSkills base)
			{
				return PlayerSkills.none()
					.withAtk((int) Math.floor(11 + base.getAtk() * 0.16))
					.withStr((int) Math.floor(11 + base.getStr() * 0.16))
					.withDef((int) Math.floor(11 + base.getDef() * 0.16))
					.withMagic((int) Math.floor(11 + base.getMagic() * 0.16))
					.withRanged((int) Math.floor(11 + base.getRanged() * 0.16));
			}
		},

	OVERLOAD("Overload")
		{
			@Override
			public PlayerSkills boost(PlayerSkills base)
			{
				return PlayerSkills.none()
					.withAtk((int) Math.floor(5 + base.getAtk() * 0.13))
					.withStr((int) Math.floor(5 + base.getStr() * 0.13))
					.withDef((int) Math.floor(5 + base.getDef() * 0.13))
					.withMagic((int) Math.floor(5 + base.getMagic() * 0.13))
					.withRanged((int) Math.floor(5 + base.getRanged() * 0.13));
			}
		},

	OVERLOAD_PLUS("Overload (+)")
		{
			@Override
			public PlayerSkills boost(PlayerSkills base)
			{
				return PlayerSkills.none()
					.withAtk((int) Math.floor(6 + base.getAtk() * 0.16))
					.withStr((int) Math.floor(6 + base.getStr() * 0.16))
					.withDef((int) Math.floor(6 + base.getDef() * 0.16))
					.withMagic((int) Math.floor(6 + base.getMagic() * 0.16))
					.withRanged((int) Math.floor(6 + base.getRanged() * 0.16));
			}
		},

	RANGING("Ranging potion")
		{
			@Override
			public PlayerSkills boost(PlayerSkills base)
			{
				return PlayerSkills.none()
					.withRanged((int) Math.floor(4 + base.getRanged() * 0.1));
			}
		},

	SUPER_RANGING("Super ranging")
		{
			@Override
			public PlayerSkills boost(PlayerSkills base)
			{
				return PlayerSkills.none()
					.withRanged((int) Math.floor(5 + base.getRanged() * 0.15));
			}
		},

	SATURATED_HEART("Saturated heart")
		{
			@Override
			public PlayerSkills boost(PlayerSkills base)
			{
				return PlayerSkills.none()
					.withMagic((int) Math.floor(4 + base.getMagic() * 0.1));
			}
		},

	SUPER_MAGIC("Super magic")
		{
			@Override
			public PlayerSkills boost(PlayerSkills base)
			{
				return PlayerSkills.none()
					.withMagic((int) Math.floor(5 + base.getMagic() * 0.15));
			}
		};

	private final String potionName;

	Potion(String potionName)
	{
		this.potionName = potionName;
	}

	public String getPotionName()
	{
		return potionName;
	}

	/**
	 * The icon file, which is not always the display name: the wiki calculator's art is named after the
	 * item ("Ranging"), not the potion ("Ranging potion"), and Overload (+) has no art of its own.
	 */
	public String getIconFile()
	{
		switch (this)
		{
			case OVERLOAD_PLUS:
				return "Overload";
			case RANGING:
				return "Ranging";
			default:
				return potionName;
		}
	}

	public abstract PlayerSkills boost(PlayerSkills base);

	/**
	 * The strongest boost for a style that a player can reasonably bring to a fight. Raid-only boosts
	 * (smelling salts, overloads) are deliberately not assumed: they would inflate the DPS of a setup the
	 * player cannot actually use outside a raid.
	 */
	public static Potion best(CombatStyleType type)
	{
		if (type == null)
		{
			return NONE;
		}
		if (type.isMelee())
		{
			return SUPER_COMBAT;
		}
		return type == CombatStyleType.MAGIC ? SATURATED_HEART : RANGING;
	}
}
