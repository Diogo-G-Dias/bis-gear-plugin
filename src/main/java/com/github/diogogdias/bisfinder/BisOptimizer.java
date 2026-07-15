package com.github.diogogdias.bisfinder;

import com.github.diogogdias.bisfinder.calc.Constants;
import com.github.diogogdias.bisfinder.calc.Equipment;
import com.github.diogogdias.bisfinder.engine.DpsEngine;
import com.github.diogogdias.bisfinder.calc.model.CombatStyleType;
import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.Player;
import com.github.diogogdias.bisfinder.calc.model.PlayerBuffs;
import com.github.diogogdias.bisfinder.calc.model.PlayerCombatStyle;
import com.github.diogogdias.bisfinder.calc.model.PlayerSkills;
import com.github.diogogdias.bisfinder.calc.model.Potion;
import com.github.diogogdias.bisfinder.calc.model.Prayer;
import com.github.diogogdias.bisfinder.calc.model.Spell;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Value;

/**
 * Finds the highest-DPS setup a player can build from the items they own, by asking the ported wiki
 * calculator for the DPS of real loadouts.
 *
 * <p>The search is a hill climb, which is what gearscape.net's own best-in-slot worker does: hold a
 * loadout, try every candidate item for one slot at a time, keep whatever improves DPS, and sweep until
 * a pass changes nothing. The weapon is just another slot, so a better weapon is found the same way as
 * a better ring. Scoring every candidate with the real calculator means set effects, bolt procs and
 * target-specific bonuses are all priced correctly rather than guessed at.
 *
 * <p>Two things stop the climb from getting stuck. Candidates are pruned by dominance first, so the
 * search only ever considers items that could win. And because a hill climb cannot cross a valley — a
 * void piece is a downgrade until all four are worn — the climb is repeated from several seeds: the
 * strongest weapons of each attack type, and every complete set the player owns, with the set's slots
 * pinned. The answer is the best result over all seeds.
 */
public class BisOptimizer
{
	/**
	 * Sets worth wearing only as a set. Each becomes a seed with its slots pinned, so the climb can reach
	 * a setup that only pays off once every piece is worn.
	 */
	private static final List<List<String>> SETS = Arrays.asList(
		Arrays.asList("Void melee helm", "Void knight top", "Void knight robe", "Void knight gloves"),
		Arrays.asList("Void ranger helm", "Void knight top", "Void knight robe", "Void knight gloves"),
		Arrays.asList("Void mage helm", "Void knight top", "Void knight robe", "Void knight gloves"),
		Arrays.asList("Void melee helm", "Elite void top", "Elite void robe", "Void knight gloves"),
		Arrays.asList("Void ranger helm", "Elite void top", "Elite void robe", "Void knight gloves"),
		Arrays.asList("Void mage helm", "Elite void top", "Elite void robe", "Void knight gloves"),
		Arrays.asList("Obsidian helmet", "Obsidian platebody", "Obsidian platelegs"),
		Arrays.asList("Crystal helm", "Crystal body", "Crystal legs"),
		Arrays.asList("Inquisitor's great helm", "Inquisitor's hauberk", "Inquisitor's plateskirt"),
		Arrays.asList("Dharok's helm", "Dharok's platebody", "Dharok's platelegs"),
		Arrays.asList("Virtus mask", "Virtus robe top", "Virtus robe bottom")
	);

	private static final Set<String> SET_PIECES = SETS.stream()
		.flatMap(List::stream)
		.collect(Collectors.toSet());

	private static final List<String> ARMOUR_SLOTS =
		Arrays.asList("head", "cape", "neck", "ammo", "body", "shield", "legs", "hands", "feet", "ring");

	/**
	 * Gauntlet and Corrupted Gauntlet gear, which is destroyed on leaving the Gauntlet and so can never be
	 * in a bank. These item ids are excluded from the search.
	 */
	private static final Set<Integer> UNBANKABLE = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		23861, 23862, 23863, 23864, 23886, 23887, 23888, 23889, 23890, 23891, 23892, 23893, 23894, 23895,
		23896, 23897, 23898, 23899, 23900, 23901, 23902, 23903,
		23820, 23821, 23822, 23823, 23840, 23841, 23842, 23843, 23844, 23845, 23846, 23847, 23848, 23849,
		23850, 23851, 23852, 23853, 23854, 23855, 23856, 23857
	)));

	/** How many of the strongest weapons per attack type are used as starting points for the climb. */
	private static final int WEAPON_SEEDS_PER_TYPE = 3;

	/** How many items per slot are tried in the two-slot pass; the rest lost on their own merits. */
	private static final int PAIR_CANDIDATES = 6;

	/** The climb settles in two or three sweeps; the cap only bounds a pathological case. */
	private static final int MAX_SWEEPS = 5;

	private Options options = Options.auto();

	@Value
	public static class Result
	{
		Player player;
		double dps;
		int maxHit;
		double accuracy;
		PlayerCombatStyle style;

		/** The prayer and potion the DPS assumes, so the number is never read as unboosted. */
		Prayer prayer;
		Potion potion;

		/** Melee, ranged or magic - which of the three this is the best setup for. */
		Style styleGroup;

		/** The weapon's special attack, or null when it has none the calculator supports. */
		Spec spec;

		/** Total prayer bonus of the setup, after the spare slots are spent on prayer gear. */
		int prayerBonus;
	}

	/**
	 * What the weapon's special attack does with this setup. The DPS figure is the calculator's own: the
	 * damage the spec adds per second, given how fast the energy comes back (halved by a Lightbearer).
	 */
	@Value
	public static class Spec
	{
		int maxHit;
		double accuracy;
		double dps;
		int cost;
	}

	/** A weapon paired with one of its combat styles, and the spell it would cast. */
	@Value
	private static class Armament
	{
		EquipmentPiece weapon;
		PlayerCombatStyle style;
		Spell spell;
	}

	/**
	 * What the player wants assumed, rather than what the search would assume for them. A null prayer or
	 * potion means "pick the best for the style"; {@link Prayer} has no NONE, so praying nothing is an
	 * empty prayer list, which is what {@code noPrayer} is for.
	 */
	@Value
	public static class Options
	{
		Prayer prayer;
		Potion potion;
		boolean noPrayer;
		Set<Integer> excludedItems;

		public static Options auto()
		{
			return new Options(null, null, false, Collections.emptySet());
		}
	}

	public Result findBest(List<EquipmentPiece> allEquipment, List<Spell> spells, Set<Integer> ownedIds,
		PlayerSkills skills, PlayerBuffs buffs, Monster monster)
	{
		return findBest(allEquipment, spells, ownedIds, skills, buffs, monster, Options.auto());
	}

	public Result findBest(List<EquipmentPiece> allEquipment, List<Spell> spells, Set<Integer> ownedIds,
		PlayerSkills skills, PlayerBuffs buffs, Monster monster, Options options)
	{
		List<Result> byStyle = findBestPerStyle(allEquipment, spells, ownedIds, skills, buffs, monster, options);
		return byStyle.isEmpty() ? null : byStyle.get(0);
	}

	/**
	 * The best setup for each of melee, ranged and magic, strongest first — the way gearscape shows them,
	 * so the cost of picking a style you would rather use is visible instead of hidden.
	 *
	 * <p>A style is only listed if the player owns something that can use it against this target.
	 */
	public List<Result> findBestPerStyle(List<EquipmentPiece> allEquipment, List<Spell> spells,
		Set<Integer> ownedIds, PlayerSkills skills, PlayerBuffs buffs, Monster monster, Options options)
	{
		this.options = options;

		List<EquipmentPiece> owned = ownedEquipment(allEquipment, ownedIds);

		List<Armament> armaments = armaments(owned, spells, skills);
		if (armaments.isEmpty())
		{
			return Collections.emptyList();
		}

		Map<String, List<EquipmentPiece>> bySlot = owned.stream()
			.filter(piece -> !"weapon".equals(piece.getSlot()))
			.collect(Collectors.groupingBy(EquipmentPiece::getSlot));

		// Keyed by attack type (stab/slash/crush/ranged/magic) so the melee styles are reported separately -
		// the sidebar shows the best stab, slash and crush setup, since which one wins shifts by target.
		Map<CombatStyleType, Player> best = new EnumMap<>(CombatStyleType.class);
		Map<CombatStyleType, Double> bestDps = new EnumMap<>(CombatStyleType.class);

		for (Seed seed : seeds(armaments, bySlot))
		{
			Player player = climb(seed, armaments, bySlot, skills, buffs, monster);
			if (player == null)
			{
				continue;
			}

			double dps = dps(player, monster);
			CombatStyleType key = player.getStyle().getType();

			if (dps > 0 && dps > bestDps.getOrDefault(key, 0.0))
			{
				bestDps.put(key, dps);
				best.put(key, player);
			}
		}

		List<Result> results = new ArrayList<>();
		for (Map.Entry<CombatStyleType, Player> entry : best.entrySet())
		{
			Player climbed = improveInPairs(entry.getValue(), bySlot, monster);
			Player player = fillWithPrayerGear(climbed, bySlot, monster);
			CombatStyleType type = player.getStyle().getType();

			// Recompute on the final gear: improveInPairs/fillWithPrayerGear change the setup after the
			// climb, so the climb's stored numbers are stale for the loadout actually shown.
			DpsEngine.Estimate estimate = DpsEngine.estimate(player, monster);
			results.add(new Result(player, estimate.getDps(), estimate.getMaxHit(), estimate.getAccuracy(),
				player.getStyle(), prayers(type).isEmpty() ? null : prayers(type).get(0), potion(type),
				Style.of(type), null, player.getBonuses().getPrayer()));
		}

		results.sort(Comparator.comparingDouble(Result::getDps).reversed());
		return results;
	}

	/**
	 * Changes two slots at once, which a one-slot-at-a-time climb cannot.
	 *
	 * <p>Some pairs of items only win together. At Bloat a Torva helm with an Oathplate chest beats an
	 * Oathplate helm with a Torva chest — but from the second, swapping either slot on its own makes things
	 * worse, so the climb sits there believing it is finished. This tries every pair of slots against every
	 * pair of their candidates, and keeps going while it finds something.
	 *
	 * <p>Only the leading candidates for a slot are paired up: pairing everything against everything would
	 * be a much bigger search for gear that was already beaten on its own merits.
	 */
	private Player improveInPairs(Player player, Map<String, List<EquipmentPiece>> bySlot, Monster monster)
	{
		Player current = player;

		for (int pass = 0; pass < MAX_SWEEPS; pass++)
		{
			double currentDps = dps(current, monster);
			boolean improved = false;

			for (int i = 0; i < ARMOUR_SLOTS.size() && !improved; i++)
			{
				for (int j = i + 1; j < ARMOUR_SLOTS.size() && !improved; j++)
				{
					String slotA = ARMOUR_SLOTS.get(i);
					String slotB = ARMOUR_SLOTS.get(j);

					for (EquipmentPiece a : pairCandidates(slotA, current, bySlot))
					{
						for (EquipmentPiece b : pairCandidates(slotB, current, bySlot))
						{
							Player trial = current.toBuilder()
								.equipment(current.getEquipment()
									.withSlot(slotA, a)
									.withSlot(slotB, b))
								.build()
								.withGearBonuses(monster);

							double dps = dps(trial, monster);
							if (dps > currentDps + 1e-9)
							{
								currentDps = dps;
								current = trial;
								improved = true;
							}
						}
					}
				}
			}

			if (!improved)
			{
				break;
			}
		}

		return current;
	}

	/** The few items in a slot worth pairing with another slot's, plus whatever is worn there now. */
	private List<EquipmentPiece> pairCandidates(String slot, Player player,
		Map<String, List<EquipmentPiece>> bySlot)
	{
		if ("shield".equals(slot) && player.getEquipment().getWeapon().isTwoHanded())
		{
			return Collections.emptyList();
		}

		CombatStyleType type = player.getStyle().getType();

		List<EquipmentPiece> items = bySlot.getOrDefault(slot, Collections.emptyList()).stream()
			.filter(item -> !"ammo".equals(slot) || Equipment.ammoApplicability(
				player.getEquipment().getWeapon().getId(), item.getId())
				== Equipment.AmmoApplicability.INCLUDED)
			.sorted(Comparator
				.comparingInt((EquipmentPiece item) -> strength(item, type) + accuracy(item, type))
				.reversed())
			.limit(PAIR_CANDIDATES)
			.collect(Collectors.toList());

		return items;
	}

	/**
	 * Once the DPS is settled, spends the slots that are not paying for themselves on prayer bonus.
	 *
	 * <p>A slot only changes if the swap costs no DPS at all: an empty ammo slot on a melee setup, or a
	 * cape whose offensive bonuses the target does not care about, becomes the highest-prayer item the
	 * player owns for that slot. Nothing here can make a setup weaker — a candidate that loses even a
	 * fraction of a point of DPS is rejected.
	 */
	private Player fillWithPrayerGear(Player player, Map<String, List<EquipmentPiece>> bySlot, Monster monster)
	{
		Player current = player;
		double target = dps(current, monster);

		for (String slot : ARMOUR_SLOTS)
		{
			EquipmentPiece worn = current.getEquipment().bySlot().get(slot);

			if ("shield".equals(slot) && current.getEquipment().getWeapon().isTwoHanded())
			{
				continue;
			}

			int bestPrayer = worn == null ? 0 : worn.getBonuses().getPrayer();
			Player best = current;

			int wornOffence = offensiveStrength(worn);
			for (EquipmentPiece candidate : bySlot.getOrDefault(slot, Collections.emptyList()))
			{
				if (candidate.getBonuses().getPrayer() <= bestPrayer)
				{
					continue;
				}

				// A prayer upgrade must not sacrifice offensive strength - keep Torva over a faceguard even
				// when the max hit ties, rather than trading damage headroom for prayer.
				if (offensiveStrength(candidate) < wornOffence)
				{
					continue;
				}

				// Ammo the weapon cannot hold is not wearable, but ammo it merely tolerates - a blessing on a
				// melee setup - is.
				if ("ammo".equals(slot) && Equipment.ammoApplicability(
					current.getEquipment().getWeapon().getId(), candidate.getId())
					== Equipment.AmmoApplicability.INVALID)
				{
					continue;
				}

				Player trial = current.toBuilder()
					.equipment(current.getEquipment().withSlot(slot, candidate))
					.build()
					.withGearBonuses(monster);

				if (dps(trial, monster) >= target - 1e-9)
				{
					bestPrayer = candidate.getBonuses().getPrayer();
					best = trial;
				}
			}

			current = best;
		}

		return current;
	}

	/** A piece's total offensive strength (melee/ranged/magic), used to break DPS ties toward damage gear. */
	private static int offensiveStrength(EquipmentPiece piece)
	{
		if (piece == null)
		{
			return 0;
		}
		return piece.getBonuses().getStr() + piece.getBonuses().getRangedStr() + piece.getBonuses().getMagicStr();
	}

	/** The three styles a player chooses between; the melee attack types are not separate choices. */
	public enum Style
	{
		MELEE,
		RANGED,
		MAGIC;

		static Style of(CombatStyleType type)
		{
			if (type.isMelee())
			{
				return MELEE;
			}
			return type == CombatStyleType.MAGIC ? MAGIC : RANGED;
		}
	}

	/**
	 * Sweeps the slots, swapping one item at a time for the best candidate, until nothing improves. The
	 * weapon is swapped the same way, which is also where a change of attack style or spell happens.
	 */
	private Player climb(Seed seed, List<Armament> armaments, Map<String, List<EquipmentPiece>> bySlot,
		PlayerSkills skills, PlayerBuffs buffs, Monster monster)
	{
		Armament armament = seed.armament;

		// Start dressed. An empty loadout would leave a bow without ammo, scoring zero damage, and the
		// first weapon swap would abandon ranged before it ever had a chance.
		Map<String, EquipmentPiece> worn = startingKit(armament, bySlot);
		worn.putAll(seed.pinned);

		double currentDps = dps(build(armament, worn, skills, buffs, monster), monster);

		for (int sweep = 0; sweep < MAX_SWEEPS; sweep++)
		{
			boolean improved = false;

			// Only weapons of the same attack type are considered: swapping style mid-climb would need the
			// whole armour set to change with it, which is what the per-type seeds are for.
			for (Armament candidate : armaments)
			{
				if (candidate == armament || candidate.style.getType() != armament.style.getType())
				{
					continue;
				}

				Map<String, EquipmentPiece> trial = compatible(worn, candidate, bySlot);
				double dps = dps(build(candidate, trial, skills, buffs, monster), monster);
				if (dps > currentDps)
				{
					currentDps = dps;
					armament = candidate;
					worn = trial;
					improved = true;
				}
			}

			for (String slot : ARMOUR_SLOTS)
			{
				if (seed.pinned.containsKey(slot))
				{
					continue;
				}
				if ("shield".equals(slot) && armament.weapon.isTwoHanded())
				{
					continue;
				}

				for (EquipmentPiece candidate : candidates(slot, armament, bySlot))
				{
					EquipmentPiece previous = worn.get(slot);
					worn.put(slot, candidate);

					double dps = dps(build(armament, worn, skills, buffs, monster), monster);
					if (dps > currentDps)
					{
						currentDps = dps;
						improved = true;
					}
					else if (previous == null)
					{
						worn.remove(slot);
					}
					else
					{
						worn.put(slot, previous);
					}
				}
			}

			if (!improved)
			{
				break;
			}
		}

		return build(armament, worn, skills, buffs, monster);
	}

	/**
	 * A plausible loadout to start the climb from: the best item in each slot by strength, then accuracy.
	 * It only has to be good enough that the weapon is not wasted — the climb corrects it from there.
	 */
	private Map<String, EquipmentPiece> startingKit(Armament armament, Map<String, List<EquipmentPiece>> bySlot)
	{
		CombatStyleType type = armament.style.getType();
		Map<String, EquipmentPiece> worn = new HashMap<>();

		for (String slot : ARMOUR_SLOTS)
		{
			if ("shield".equals(slot) && armament.weapon.isTwoHanded())
			{
				continue;
			}

			candidates(slot, armament, bySlot).stream()
				.max(Comparator
					.comparingInt((EquipmentPiece item) -> strength(item, type))
					.thenComparingInt(item -> accuracy(item, type)))
				.ifPresent(item -> worn.put(slot, item));
		}
		return worn;
	}

	/**
	 * Fits the worn gear to a weapon the climb is about to try.
	 *
	 * <p>A two-hander drops the shield, and ammo the weapon cannot use is dropped. Crucially, the slots a
	 * weapon *unlocks* are filled straight away: a one-hander gets the best shield, a bow gets its best
	 * ammo. Judging a one-hander while the shield slot is still empty — because the weapon it is replacing
	 * was two-handed — would reject it on a loadout nobody would ever wear, which is how a Blade of
	 * saeldor loses to a scythe it should beat.
	 */
	private Map<String, EquipmentPiece> compatible(Map<String, EquipmentPiece> worn, Armament armament,
		Map<String, List<EquipmentPiece>> bySlot)
	{
		Map<String, EquipmentPiece> trial = new HashMap<>(worn);

		if (armament.weapon.isTwoHanded())
		{
			trial.remove("shield");
		}
		else if (!trial.containsKey("shield"))
		{
			best("shield", armament, bySlot).ifPresent(shield -> trial.put("shield", shield));
		}

		EquipmentPiece ammo = trial.get("ammo");
		if (ammo != null && Equipment.ammoApplicability(armament.weapon.getId(), ammo.getId())
			== Equipment.AmmoApplicability.INVALID)
		{
			trial.remove("ammo");
		}

		if (!trial.containsKey("ammo"))
		{
			best("ammo", armament, bySlot).ifPresent(fresh -> trial.put("ammo", fresh));
		}

		return trial;
	}

	private java.util.Optional<EquipmentPiece> best(String slot, Armament armament,
		Map<String, List<EquipmentPiece>> bySlot)
	{
		CombatStyleType type = armament.style.getType();
		return candidates(slot, armament, bySlot).stream()
			.max(Comparator
				.comparingInt((EquipmentPiece item) -> strength(item, type))
				.thenComparingInt(item -> accuracy(item, type)));
	}

	/**
	 * Per-slot candidates for this style. An item is dropped only when another owned item in the slot is
	 * at least as good on both the accuracy bonus and the strength bonus, which cannot change the answer
	 * for a plain item — but an item the calculator special-cases is never dropped, because its value does
	 * not show up in those two numbers.
	 */
	private List<EquipmentPiece> candidates(String slot, Armament armament,
		Map<String, List<EquipmentPiece>> bySlot)
	{
		List<EquipmentPiece> items = new ArrayList<>(bySlot.getOrDefault(slot, Collections.emptyList()));

		if ("ammo".equals(slot))
		{
			// Only ammo the weapon actually fires. Ammo a melee weapon merely tolerates contributes nothing,
			// and would otherwise be picked arbitrarily among ties and shown as part of the setup.
			items = items.stream()
				.filter(ammo -> Equipment.ammoApplicability(armament.weapon.getId(), ammo.getId())
					== Equipment.AmmoApplicability.INCLUDED)
				.collect(Collectors.toList());
		}

		CombatStyleType type = armament.style.getType();
		List<EquipmentPiece> front = new ArrayList<>();
		for (EquipmentPiece item : items)
		{
			if (isSpecialCased(item) || !dominated(item, items, type))
			{
				front.add(item);
			}
		}
		return front;
	}

	/**
	 * Every weapon the player owns, paired with each of its attack styles, minus the weapons that another
	 * owned weapon beats outright: same handedness, no slower, and at least as good on both the accuracy
	 * and the strength bonus for that style. A weapon with an effect is always kept.
	 */
	private List<Armament> armaments(List<EquipmentPiece> owned, List<Spell> spells, PlayerSkills skills)
	{
		// A non-positive attack speed means the item is not really a weapon (greegrees, holiday items). It
		// would otherwise divide DPS by a negative interval and win outright.
		List<EquipmentPiece> weapons = owned.stream()
			.filter(piece -> "weapon".equals(piece.getSlot()))
			.filter(piece -> piece.getSpeed() > 0)
			.map(piece -> loadBlowpipe(piece, owned))
			.collect(Collectors.toList());

		List<Armament> armaments = new ArrayList<>();
		for (EquipmentPiece weapon : weapons)
		{
			for (PlayerCombatStyle style : PlayerCombatStyle.getCombatStylesForCategory(weapon.getCategory()))
			{
				if (style.getType() == null)
				{
					continue;
				}

				if (EffectItems.matters(weapon.getName())
					|| !dominatedWeapon(weapon, weapons, style.getType()))
				{
					armaments.add(new Armament(weapon, style, spellFor(style, spells, skills)));
				}
			}
		}
		return armaments;
	}

	/**
	 * A blowpipe is loaded with darts rather than using the ammo slot, and all of its ranged strength
	 * comes from them — an unloaded one is a blowgun with nothing in it. The calculator reads the dart
	 * from the weapon's itemVars, which nothing else fills in, so the best dart the player owns is loaded
	 * here. Darts sit in the "weapon" slot in the wiki data, not "ammo".
	 */
	private EquipmentPiece loadBlowpipe(EquipmentPiece weapon, List<EquipmentPiece> owned)
	{
		if (!Constants.BLOWPIPE_IDS.contains(weapon.getId()))
		{
			return weapon;
		}

		EquipmentPiece dart = owned.stream()
			.filter(piece -> piece.getName().endsWith(" dart"))
			.filter(piece -> piece.getBonuses().getRangedStr() > 0)
			.max(Comparator.comparingInt(piece -> piece.getBonuses().getRangedStr()))
			.orElse(null);

		if (dart == null)
		{
			return weapon;
		}

		return weapon.withItemVars(new EquipmentPiece.ItemVars(dart.getId(), dart.getName()));
	}

	private boolean dominatedWeapon(EquipmentPiece weapon, List<EquipmentPiece> weapons, CombatStyleType type)
	{
		for (EquipmentPiece other : weapons)
		{
			if (other == weapon || other.isTwoHanded() != weapon.isTwoHanded()
				|| other.getSpeed() > weapon.getSpeed())
			{
				continue;
			}

			boolean atLeastAsGood = accuracy(other, type) >= accuracy(weapon, type)
				&& strength(other, type) >= strength(weapon, type);
			boolean strictlyBetter = accuracy(other, type) > accuracy(weapon, type)
				|| strength(other, type) > strength(weapon, type)
				|| other.getSpeed() < weapon.getSpeed();

			if (atLeastAsGood && strictlyBetter)
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Starting points: the strongest few weapons of each attack type, and every complete set the player
	 * owns, pinned so the climb cannot take it apart one piece at a time.
	 */
	private List<Seed> seeds(List<Armament> armaments, Map<String, List<EquipmentPiece>> bySlot)
	{
		List<Seed> seeds = new ArrayList<>();

		for (CombatStyleType type : CombatStyleType.values())
		{
			armaments.stream()
				.filter(a -> a.style.getType() == type)
				.sorted(Comparator
					.comparingInt((Armament a) -> strength(a.weapon, type))
					.thenComparingInt(a -> accuracy(a.weapon, type))
					.reversed())
				.limit(WEAPON_SEEDS_PER_TYPE)
				.forEach(a -> seeds.add(new Seed(a, Collections.emptyMap())));
		}

		List<Map<String, EquipmentPiece>> sets = ownedSets(bySlot);
		if (!sets.isEmpty())
		{
			List<Seed> withSets = new ArrayList<>();
			for (Seed seed : seeds)
			{
				for (Map<String, EquipmentPiece> set : sets)
				{
					withSets.add(new Seed(seed.armament, set));
				}
			}
			seeds.addAll(withSets);
		}

		return seeds;
	}

	private List<Map<String, EquipmentPiece>> ownedSets(Map<String, List<EquipmentPiece>> bySlot)
	{
		List<Map<String, EquipmentPiece>> sets = new ArrayList<>();

		for (List<String> set : SETS)
		{
			Map<String, EquipmentPiece> pieces = new HashMap<>();
			for (String name : set)
			{
				EquipmentPiece piece = findOwned(bySlot, name);
				if (piece == null)
				{
					pieces = null;
					break;
				}
				pieces.put(piece.getSlot(), piece);
			}

			if (pieces != null && pieces.size() == set.size())
			{
				sets.add(pieces);
			}
		}
		return sets;
	}

	private EquipmentPiece findOwned(Map<String, List<EquipmentPiece>> bySlot, String name)
	{
		for (List<EquipmentPiece> slot : bySlot.values())
		{
			for (EquipmentPiece piece : slot)
			{
				if (piece.getName().equals(name))
				{
					return piece;
				}
			}
		}
		return null;
	}

	private boolean isSpecialCased(EquipmentPiece item)
	{
		return SET_PIECES.contains(item.getName()) || EffectItems.matters(item.getName());
	}

	private boolean dominated(EquipmentPiece item, List<EquipmentPiece> items, CombatStyleType type)
	{
		for (EquipmentPiece other : items)
		{
			if (other == item)
			{
				continue;
			}

			boolean atLeastAsGood = accuracy(other, type) >= accuracy(item, type)
				&& strength(other, type) >= strength(item, type);
			boolean strictlyBetter = accuracy(other, type) > accuracy(item, type)
				|| strength(other, type) > strength(item, type);

			if (atLeastAsGood && strictlyBetter)
			{
				return true;
			}
		}
		return false;
	}

	private int accuracy(EquipmentPiece item, CombatStyleType type)
	{
		EquipmentPiece.Offensive offensive = item.getOffensive();
		switch (type)
		{
			case STAB:
				return offensive.getStab();
			case SLASH:
				return offensive.getSlash();
			case CRUSH:
				return offensive.getCrush();
			case MAGIC:
				return offensive.getMagic();
			default:
				return offensive.getRanged();
		}
	}

	private int strength(EquipmentPiece item, CombatStyleType type)
	{
		EquipmentPiece.Bonuses bonuses = item.getBonuses();
		if (type.isMelee())
		{
			return bonuses.getStr();
		}
		return type == CombatStyleType.MAGIC ? bonuses.getMagicStr() : bonuses.getRangedStr();
	}

	/**
	 * The strongest spell the player can cast, for a style that needs one. A weaker spell of the same
	 * element is never better, so only the best is tried.
	 */
	private Spell spellFor(PlayerCombatStyle style, List<Spell> spells, PlayerSkills skills)
	{
		if (style.getType() != CombatStyleType.MAGIC || style.getStance() == null
			|| !style.getStance().isCastStance())
		{
			return null;
		}

		Spell best = null;
		int bestMax = -1;
		for (Spell spell : spells)
		{
			if (spell.isBindSpell())
			{
				continue;
			}

			int max = Spell.maxHit(spell, skills.getMagic(), spells);
			if (max > bestMax)
			{
				bestMax = max;
				best = spell;
			}
		}
		return best;
	}

	private Player build(Armament armament, Map<String, EquipmentPiece> worn, PlayerSkills skills,
		PlayerBuffs buffs, Monster monster)
	{
		Player.PlayerEquipment gear = Player.PlayerEquipment.builder().weapon(armament.weapon).build();
		for (Map.Entry<String, EquipmentPiece> entry : worn.entrySet())
		{
			gear = gear.withSlot(entry.getKey(), entry.getValue());
		}

		CombatStyleType type = armament.style.getType();
		return Player.builder()
			.skills(skills)
			.boosts(potion(type).boost(skills))
			.prayers(prayers(type))
			.buffs(buffs)
			.style(armament.style)
			.spell(armament.spell)
			.equipment(gear)
			.build()
			.withGearBonuses(monster);
	}

	/** The player's chosen prayer, or the best one for the style if they have not chosen. */
	private List<Prayer> prayers(CombatStyleType type)
	{
		if (options.isNoPrayer())
		{
			return Collections.emptyList();
		}
		if (options.getPrayer() != null)
		{
			return Collections.singletonList(options.getPrayer());
		}

		if (type.isMelee())
		{
			return Prayer.best(Prayer.PrayerStyle.MELEE);
		}
		return type == CombatStyleType.MAGIC
			? Prayer.best(Prayer.PrayerStyle.MAGIC)
			: Prayer.best(Prayer.PrayerStyle.RANGED);
	}

	/** The player's chosen boost, or the best sensible one for the style if they have not chosen. */
	private Potion potion(CombatStyleType type)
	{
		return options.getPotion() != null ? options.getPotion() : Potion.best(type);
	}

	/** How a candidate setup is scored: the clean-room DPS engine. */
	public static java.util.function.ToDoubleBiFunction<Player, Monster> scorer =
		com.github.diogogdias.bisfinder.engine.DpsEngine::dps;

	private double dps(Player player, Monster monster)
	{
		if (player == null)
		{
			return 0;
		}

		double dps = scorer.applyAsDouble(player, monster);
		return Double.isFinite(dps) ? dps : 0;
	}

	private List<EquipmentPiece> ownedEquipment(List<EquipmentPiece> allEquipment, Set<Integer> ownedIds)
	{
		Set<Integer> canonical = new HashSet<>();
		for (int id : ownedIds)
		{
			canonical.add(Equipment.getCanonicalItemId(id));
		}

		return allEquipment.stream()
			.filter(piece -> canonical.contains(piece.getId()))
			.filter(piece -> !UNBANKABLE.contains(piece.getId()))
			.filter(piece -> !options.getExcludedItems().contains(piece.getId()))
			.collect(Collectors.toList());
	}

	@Value
	private static class Seed
	{
		Armament armament;
		Map<String, EquipmentPiece> pinned;
	}
}
