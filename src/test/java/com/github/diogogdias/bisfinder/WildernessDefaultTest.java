package com.github.diogogdias.bisfinder;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Which targets tick the Wilderness box for themselves. The names are matched against the real monster list,
 * because a typo here would not fail loudly - it would just quietly never match.
 */
public class WildernessDefaultTest
{
	private static List<Monster> monsters;

	@BeforeClass
	public static void load()
	{
		monsters = read("/wiki/monsters.json", new TypeToken<List<Monster>>()
		{
		}.getType());
	}

	@Test
	public void wildernessBossesDefaultOn()
	{
		String[] wilderness = {
			"Callisto", "Artio", "Venenatis", "Spindel", "Vet'ion", "Calvar'ion",
			"Chaos Elemental", "Chaos Fanatic", "Crazy archaeologist",
			"Lava dragon", "Elder Chaos druid", "Mammoth",
			"Scorpia", "Scorpia's guardian", "Revenant dragon", "Revenant maledictus",
		};

		for (String name : wilderness)
		{
			assertTrue(name + " is in the Wilderness", BisFinderPanel.isWildernessOnly(named(name)));
		}
	}

	/**
	 * The King Black Dragon is the trap: it is billed as a Wilderness boss, but its lair is not in the
	 * Wilderness, so a revenant-ether weapon gets nothing there.
	 */
	@Test
	public void kingBlackDragonDefaultsOff()
	{
		assertFalse("the KBD lair is not in the Wilderness",
			BisFinderPanel.isWildernessOnly(named("King Black Dragon")));
	}

	/** A target found both in and out of the Wilderness is the player's call, not ours. */
	@Test
	public void everywhereElseDefaultsOff()
	{
		for (String name : new String[]{"Zulrah", "Vorkath", "Green dragon", "Chaos druid", "Tekton"})
		{
			assertFalse(name + " must not assume the Wilderness", BisFinderPanel.isWildernessOnly(named(name)));
		}
	}

	/** Every listed name must exist in the wiki data, or the default silently never fires. */
	private static Monster named(String name)
	{
		return monsters.stream().filter(m -> name.equals(m.getName())).findFirst()
			.orElseThrow(() -> new IllegalStateException("no monster named " + name));
	}

	private static <T> T read(String resource, java.lang.reflect.Type type)
	{
		try (InputStream in = WildernessDefaultTest.class.getResourceAsStream(resource))
		{
			return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
}
