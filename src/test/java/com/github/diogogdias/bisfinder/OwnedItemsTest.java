package com.github.diogogdias.bisfinder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.junit.Assert;
import org.junit.Test;

public class OwnedItemsTest
{
	private static final int FEROCIOUS_GLOVES = 22981;
	private static final int SHARK = 385;

	/**
	 * Inventory and worn gear arrive as separate container updates. Storing them in one set meant an
	 * inventory change wiped the worn gear, so the gloves on the player's hands vanished the moment they
	 * picked something up — and the search fell back to whatever was in the bank.
	 */
	@Test
	public void inventoryChangeDoesNotForgetWornGear()
	{
		OwnedItems owned = new OwnedItems();
		owned.setWorn(setOf(FEROCIOUS_GLOVES));
		owned.setInventory(setOf(SHARK));

		Assert.assertTrue("worn gear must survive an inventory update",
			owned.all().contains(FEROCIOUS_GLOVES));

		// Player eats the shark: the inventory container changes, the worn one does not.
		owned.setInventory(setOf());

		Assert.assertTrue("worn gear must still be owned after the inventory empties",
			owned.all().contains(FEROCIOUS_GLOVES));
	}

	@Test
	public void ownsBankInventoryAndWornTogether()
	{
		OwnedItems owned = new OwnedItems();
		owned.setBank(setOf(1, 2), 1000L);
		owned.setInventory(setOf(3));
		owned.setWorn(setOf(4));

		Assert.assertEquals(setOf(1, 2, 3, 4), owned.all());
	}

	private static Set<Integer> setOf(Integer... ids)
	{
		return new HashSet<>(Arrays.asList(ids));
	}
}
