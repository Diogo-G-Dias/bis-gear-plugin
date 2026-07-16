package com.github.diogogdias.bisfinder.calc;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Item- and monster-id groupings used to recognise game-specific behaviour, referenced by BaseCalc,
 * PlayerVsNPCCalc, Equipment, MonsterScaling and the bolt distributions.
 *
 * <p>Sets are used purely for lookup speed.
 */
public final class Constants
{
	private Constants()
	{
	}

	public static final Set<Integer> BLOWPIPE_IDS = ids(
		12926, // regular
		28688, // blazing
		31575, // camphor
		31579, // ironwood
		31583 // rosewood
	);

	public static final Set<Integer> AKKHA_IDS = ids(
		11789, 11790, 11791, 11792, 11793, 11794, 11795, 11796
	);

	public static final Set<Integer> AKKHA_SHADOW_IDS = ids(
		11797, 11798, 11799
	);

	public static final Set<Integer> BABA_IDS = ids(
		11778, 11779, 11780
	);

	public static final Set<Integer> KEPHRI_SHIELDED_IDS = ids(11719);

	public static final Set<Integer> KEPHRI_UNSHIELDED_IDS = ids(11721);

	public static final Set<Integer> KEPHRI_OVERLORD_IDS = ids(11724, 11725, 11726);

	public static final Set<Integer> ZEBAK_IDS = ids(11730, 11732, 11733);

	public static final Set<Integer> TOA_OBELISK_IDS = ids(11751, 11750, 11752);

	public static final Set<Integer> P2_WARDEN_IDS = ids(
		11753, 11754, // elidinis
		11756, 11757 // tumeken
	);

	public static final Set<Integer> P3_WARDEN_IDS = ids(
		11761, 11763, // elidinis
		11762, 11764 // tumeken
	);

	public static final Set<Integer> TOA_WARDEN_CORE_EJECTED_IDS = ids(
		11755, // elidinis
		11758 // tumeken
	);

	/**
	 * IDs of monsters that are present in Tombs of Amascut and are affected by path level.
	 */
	public static final Set<Integer> TOMBS_OF_AMASCUT_PATH_MONSTER_IDS = union(
		AKKHA_IDS,
		AKKHA_SHADOW_IDS,
		BABA_IDS,
		KEPHRI_SHIELDED_IDS,
		KEPHRI_UNSHIELDED_IDS,
		KEPHRI_OVERLORD_IDS,
		ZEBAK_IDS
	);

	public static final Set<Integer> TOMBS_OF_AMASCUT_MONSTER_IDS = union(
		TOMBS_OF_AMASCUT_PATH_MONSTER_IDS,
		TOA_OBELISK_IDS,
		P2_WARDEN_IDS,
		TOA_WARDEN_CORE_EJECTED_IDS,
		P3_WARDEN_IDS
	);

	public static final Set<Integer> VERZIK_P1_IDS = ids(
		10830, 10831, 10832, // em
		8369, 8370, 8371, // norm
		10847, 10848, 10849 // hmt
	);

	public static final Set<Integer> VERZIK_IDS = union(
		VERZIK_P1_IDS,
		ids(
			10833, 10834, 10835, // verzik entry mode
			8372, 8373, 8374, // verzik normal mode
			10850, 10851, 10852 // verzik hard mode
		)
	);

	public static final Set<Integer> SOTETSEG_IDS = ids(
		8387, 8388, // normal
		10867, 10868 // hard
	);

	public static final Set<Integer> TOB_MONSTER_IDS = union(
		VERZIK_P1_IDS,
		ids(
			// normal
			8360, 8361, 8362, 8363, 8364, 8365, // maiden
			8366, 8367, // maiden crab + blood spawn
			8359, // bloat
			8342, 8343, 8344, 8345, 8346, 8347, 8348, 8349, 8350, 8351, 8352, 8353, // nylos
			8355, 8356, 8357 // nylo boss
		),
		SOTETSEG_IDS,
		ids(
			8339, 8340, // xarpus
			8372, 8373, 8374, // verzik
			8376, 8381, 8382, 8383, 8384, 8385, // verzik web + nylos

			// hmt
			10822, 10823, 10824, 10825, 10826, 10827, // maiden
			10828, 10829, // maiden crab + blood spawn
			10813, // bloat
			10791, 10792, 10793, 10794, 10795, 10796, 10797, 10798, 10799, 10800, 10801, 10802, // nylos
			10804, 10805, 10806, // nylo demi-boss
			10808, 10809, 10810, // nylo boss
			10770, 10771, 10772, // xarpus
			10850, 10851, 10852, // verzik
			10854, 10858, 10859, 10860, 10861, 10862 // verzik web + nylos
		)
	);

	public static final Set<Integer> TOB_EM_MONSTER_IDS = ids(
		10814, 10815, 10816, 10817, 10818, 10819, // maiden
		10820, 10821, // maiden crab + blood spawn
		10812, // bloat
		10774, 10775, 10776,
		10777, 10778, 10779, 10780, 10781, 10782, 10783, 10784, 10785, // nylos
		10787, 10788, 10789, // nylo boss
		10864, 10865, // sote
		10767, 10768, // xarpus
		10833, 10834, 10835, // verzik
		10837, 10841, 10842, 10843, 10844, 10845 // verzik web + nylos
	);

	/**
	 * IDs of Tekton from the Chambers of Xeric.
	 */
	public static final Set<Integer> TEKTON_IDS = ids(
		7540, 7543, // reg
		7544, 7545 // cm
	);

	/**
	 * IDs of Guardians from the Chambers of Xeric.
	 */
	public static final Set<Integer> GUARDIAN_IDS = ids(
		7569, 7571, // reg
		7570, 7572 // cm
	);

	public static final Set<Integer> OLM_HEAD_IDS = ids(
		7551, // reg
		7554 // cm
	);

	public static final Set<Integer> OLM_MELEE_HAND_IDS = ids(
		7552, // reg
		7555 // cm
	);

	public static final Set<Integer> OLM_MAGE_HAND_IDS = ids(
		7550, // reg
		7553 // cm
	);

	public static final Set<Integer> OLM_IDS = union(OLM_HEAD_IDS, OLM_MELEE_HAND_IDS, OLM_MAGE_HAND_IDS);

	public static final Set<Integer> SCAVENGER_BEAST_IDS = ids(7548, 7549);

	public static final Set<Integer> ABYSSAL_PORTAL_IDS = ids(7533);

	public static final Set<Integer> GLOWING_CRYSTAL_IDS = ids(7568);

	public static final Set<Integer> ICE_DEMON_IDS = ids(
		7584, // reg
		7585 // cm
	);

	public static final Set<Integer> VESPINE_SOLDIER_IDS = ids(7538, 7539);

	public static final Set<Integer> DEATHLY_RANGER_IDS = ids(7559);

	public static final Set<Integer> VESPULA_IDS = ids(7530, 7531, 7532);

	public static final Set<Integer> COX_MAGIC_IS_DEFENSIVE_IDS = union(
		DEATHLY_RANGER_IDS,
		TEKTON_IDS,
		ABYSSAL_PORTAL_IDS,
		VESPULA_IDS,
		VESPINE_SOLDIER_IDS,
		OLM_MELEE_HAND_IDS,
		OLM_MAGE_HAND_IDS
	);

	public static final Set<Integer> COX_USE_SINGLES_SCALING_IDS = union(SCAVENGER_BEAST_IDS, VESPINE_SOLDIER_IDS);

	public static final Set<Integer> FRAGMENT_OF_SEREN_IDS = ids(8917, 8918, 8919, 8920);

	public static final Set<Integer> NIGHTMARE_IDS = ids(
		378, 9425, 9426, 9427, 9428, 9429, 9430, 9431, 9432, 9433, 9460, // nightmare
		377, 9423, 9416, 9417, 9418, 9419, 9420, 9421, 9422, 9424, 11153, 11154, 11155 // phosani's
	);

	/**
	 * IDs of the totems in the Nightmare / Phosani's Nightmare fight. They take double damage from magic.
	 */
	public static final Set<Integer> NIGHTMARE_TOTEM_IDS = ids(
		9434, 9437, 9440, 9443,
		9435, 9438, 9441, 9444
	);

	public static final Set<Integer> NEX_IDS = ids(11278, 11279, 11280, 11281, 11282);

	/**
	 * IDs of monsters that calculate their magical defence using the defence stat.
	 */
	public static final Set<Integer> USES_DEFENCE_LEVEL_FOR_MAGIC_DEFENCE_NPC_IDS = union(
		ICE_DEMON_IDS,
		VERZIK_IDS,
		FRAGMENT_OF_SEREN_IDS,
		ids(
			11709, 11712, // baboon brawler
			9118 // rabbit (prifddinas)
		)
	);

	private static final Set<Integer> DUSK_IDS = ids(
		7851, 7854, 7855, 7882, 7883, 7886, // dusk first form
		7887, 7888, 7889 // dusk second form
	);

	private static final Set<Integer> WARRIORS_GUILD_CYCLOPES = ids(
		2463, 2465, 2467, // L56
		2464, 2466, 2468, // L76
		2137, 2138, 2139, 2140, 2141, 2142 // L106
	);

	public static final Set<Integer> ZULRAH_IDS = ids(2042, 2043, 2044);

	/**
	 * Monsters melee cannot damage at all. Zulrah is deliberately absent: the 7 May 2025 update ("Zulrah is
	 * no longer immune to melee attacks, although only halberds can reach it") made it a matter of reach
	 * rather than immunity, so a halberd hits it for full. See {@code DpsEngine#meleeCanReach}.
	 */
	public static final Set<Integer> IMMUNE_TO_MELEE_DAMAGE_NPC_IDS = union(
		ids(494), // kraken
		ABYSSAL_PORTAL_IDS,
		ids(
			7706, // zuk
			7708, // Jal-MejJak
			12214, 12215, 12219 // leviathan
		)
	);

	public static final Set<Integer> IMMUNE_TO_NON_SALAMANDER_MELEE_DAMAGE_NPC_IDS = ids(
		3169, 3170, 3171, 3172, 3173, 3174, 3175, 3176, 3177, 3178, 3179, 3180, 3181, 3182, 3183, // aviansie
		7037 // reanimated aviansie
	);

	public static final Set<Integer> IMMUNE_TO_RANGED_DAMAGE_NPC_IDS = union(
		TEKTON_IDS,
		DUSK_IDS,
		GLOWING_CRYSTAL_IDS,
		WARRIORS_GUILD_CYCLOPES
	);

	public static final Set<Integer> IMMUNE_TO_BURN_DAMAGE_NPC_IDS = union(
		TEKTON_IDS,
		DUSK_IDS,
		GLOWING_CRYSTAL_IDS,
		WARRIORS_GUILD_CYCLOPES
	);

	public static final Set<Integer> IMMUNE_TO_MAGIC_DAMAGE_NPC_IDS = union(DUSK_IDS, WARRIORS_GUILD_CYCLOPES);

	public static final Set<Integer> BA_ATTACKER_MONSTERS = ids(
		// fighters
		1667, 5739, 5740, 5741, 5742, 5743, 5744, 5745, 5746, 5747,
		// rangers
		1668, 5757, 5758, 5759, 5760, 5761, 5762, 5763, 5764, 5765
	);

	public static final Set<Integer> VARDORVIS_IDS = ids(12223, 12224, 12228, 12425, 12426, 13656);

	public static final Set<Integer> TITAN_BOSS_IDS = ids(
		12596, // Fire elemental (Royal Titans)
		14147 // Ice elemental (Royal Titans)
	);

	public static final Set<Integer> TITAN_ELEMENTAL_IDS = ids(
		14150, // Fire elemental (Royal Titans)
		14151 // Ice elemental (Royal Titans)
	);

	public static final Set<Integer> UNDERWATER_MONSTERS = ids(7796); // lobstrosity

	public static final Set<Integer> YAMA_VOID_FLARE_IDS = ids(14179);

	/**
	 * NPCs that will always die in one hit from a player attack.
	 */
	public static final Set<Integer> ONE_HIT_MONSTERS = ids(
		7223, // Giant rat (Scurrius)
		8584, // Flower
		11193 // Flower (A Night at the Theatre)
	);

	/**
	 * NPCs the player always max hits against with the correct combat style.
	 */
	public static final Set<Integer> ALWAYS_MAX_HIT_MONSTERS_MELEE = union(
		ids(
			11710, 11713, // baboon thrower
			12814 // frem warband archer
		),
		TOA_WARDEN_CORE_EJECTED_IDS,
		YAMA_VOID_FLARE_IDS
	);

	public static final Set<Integer> ALWAYS_MAX_HIT_MONSTERS_RANGED = union(
		ids(
			11711, 11714, // baboon mage
			12815, // frem warband seer
			11717, // cursed baboon
			11715 // baboon shaman
		),
		YAMA_VOID_FLARE_IDS
	);

	public static final Set<Integer> ALWAYS_MAX_HIT_MONSTERS_MAGIC = union(
		ids(
			11709, 11712, // baboon brawler
			12816, // frem warband berserker
			14151, 14150 // Royal titans elementals
		),
		YAMA_VOID_FLARE_IDS
	);

	/**
	 * NPCs that the player has 100% accuracy against.
	 */
	public static final Set<Integer> GUARANTEED_ACCURACY_MONSTERS = ids(5916); // Spawn (abyssal sire)

	public static final int DEFAULT_ATTACK_SPEED = 4;
	public static final double SECONDS_PER_TICK = 0.6;

	public static final Set<Integer> TD_IDS = ids(13599, 13600, 13601, 13602, 13603, 13604, 13605, 13606);

	public static final Set<Integer> ARAXXOR_IDS = ids(13668);

	public static final Set<Integer> HUEYCOATL_HEAD_IDS = ids(14009, 14010, 14013);
	public static final Set<Integer> HUEYCOATL_BODY_IDS = ids(14017);
	public static final Set<Integer> HUEYCOATL_TAIL_IDS = ids(14014);
	public static final Set<Integer> HUEYCOATL_IDS = union(HUEYCOATL_HEAD_IDS, HUEYCOATL_BODY_IDS, HUEYCOATL_TAIL_IDS);
	/**
	 * The body can't receive the pillar buff.
	 */
	public static final Set<Integer> HUEYCOATL_PHASE_IDS = union(HUEYCOATL_HEAD_IDS, HUEYCOATL_TAIL_IDS);

	public static final Set<Integer> DOOM_OF_MOKHAIOTL_IDS = ids(14707);

	public static final Set<Integer> ABYSSAL_SIRE_TRANSITION_IDS = ids(5886, 5889, 5891);

	public static final Set<Integer> YAMA_IDS = ids(14176);

	public static final Set<Integer> MAGGOT_KING_ID = ids(15742);

	public static final Set<Integer> INFINITE_HEALTH_MONSTERS = ids(14779); // gemstone crab

	public static final Set<Integer> ECLIPSE_MOON_IDS = ids(13012);

	private static Set<Integer> ids(int... values)
	{
		Set<Integer> set = new LinkedHashSet<>(values.length);
		for (int value : values)
		{
			set.add(value);
		}
		return Collections.unmodifiableSet(set);
	}

	@SafeVarargs
	private static Set<Integer> union(Set<Integer>... sets)
	{
		Set<Integer> set = new LinkedHashSet<>();
		for (Set<Integer> s : sets)
		{
			set.addAll(s);
		}
		return Collections.unmodifiableSet(set);
	}
}
