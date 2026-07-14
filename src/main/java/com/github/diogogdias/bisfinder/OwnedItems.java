package com.github.diogogdias.bisfinder;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * The item ids the player owns: everything last seen in the bank, plus what they are currently
 * wearing and carrying. The bank half only refreshes while the bank interface is open, so it is
 * persisted between sessions.
 */
class OwnedItems
{
	private final Set<Integer> bank = new HashSet<>();

	// Inventory and worn gear are tracked apart. They change independently, so storing them together
	// would mean an inventory update wiped the gear the player is wearing.
	private final Set<Integer> inventory = new HashSet<>();
	private final Set<Integer> worn = new HashSet<>();

	/** Epoch millis of the last bank sighting; 0 when the bank has never been seen. */
	@Getter
	private long bankUpdatedAt;

	void setBank(Set<Integer> itemIds, long updatedAt)
	{
		bank.clear();
		bank.addAll(itemIds);
		bankUpdatedAt = updatedAt;
	}

	void setInventory(Set<Integer> itemIds)
	{
		inventory.clear();
		inventory.addAll(itemIds);
	}

	void setWorn(Set<Integer> itemIds)
	{
		worn.clear();
		worn.addAll(itemIds);
	}

	Set<Integer> all()
	{
		Set<Integer> all = new HashSet<>(bank);
		all.addAll(inventory);
		all.addAll(worn);
		return Collections.unmodifiableSet(all);
	}

	boolean hasBank()
	{
		return bankUpdatedAt > 0;
	}

	String serializeBank()
	{
		return bankUpdatedAt + ":" + bank.stream().map(String::valueOf).collect(Collectors.joining(","));
	}

	void deserializeBank(String serialized)
	{
		bank.clear();
		bankUpdatedAt = 0;

		if (serialized == null || serialized.isEmpty())
		{
			return;
		}

		int split = serialized.indexOf(':');
		if (split < 0)
		{
			return;
		}

		try
		{
			bankUpdatedAt = Long.parseLong(serialized.substring(0, split));
		}
		catch (NumberFormatException e)
		{
			return;
		}

		String ids = serialized.substring(split + 1);
		if (ids.isEmpty())
		{
			return;
		}

		for (String id : ids.split(","))
		{
			try
			{
				bank.add(Integer.parseInt(id));
			}
			catch (NumberFormatException ignored)
			{
				// A corrupt entry costs one item, not the whole snapshot.
			}
		}
	}
}
