package com.github.diogogdias.bisfinder.engine;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.github.diogogdias.bisfinder.calc.CalcData;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Chambers of Xeric defence scaling, checked against the public wiki formula computed by hand (there is no
 * longer an oracle engine to diff against). Party member levels default to maxed, so party 1 with no
 * Challenge Mode leaves the base defence untouched, Challenge Mode adds a flat 50% (Tekton is the exception,
 * +20% below 4 players and +35% at 4+), and defence rises monotonically with party size.
 */
public class CoxScalingTest
{
	private static List<Monster> monsters;

	@BeforeClass
	public static void load()
	{
		monsters = read("/wiki/monsters.json", new TypeToken<List<Monster>>()
		{
		}.getType());
		CalcData.setAvailableEquipment(java.util.Collections.emptyList());
	}

	@Test
	public void tektonChallengeModeUsesItsOwnPercentages()
	{
		Monster tekton = named("Tekton");
		assertEquals("solo, no CM leaves base defence", 205, def(tekton, 1, false));
		assertEquals("Tekton CM below 4 players is +20%", 246, def(tekton, 1, true));
		assertEquals("Tekton CM at 4+ players is +35%", 287, def(tekton, 5, true));
	}

	@Test
	public void generalChallengeModeIsFlatFiftyPercent()
	{
		Monster muttadile = named("Muttadile");
		assertEquals(220, def(muttadile, 1, false));
		assertEquals("general CM is +50%", 330, def(muttadile, 1, true));
	}

	@Test
	public void defenceRisesWithPartySize()
	{
		Monster tekton = named("Tekton");
		int p1 = def(tekton, 1, false);
		int p5 = def(tekton, 5, false);
		int p15 = def(tekton, 15, false);
		assertTrue("more players raise defence", p5 > p1 && p15 > p5);
	}

	private static int def(Monster base, int party, boolean cm)
	{
		Monster withInputs = base.toBuilder()
			.inputs(base.getInputs().toBuilder().partySize(party).isFromCoxCm(cm).build())
			.build();
		return RaidScaling.scaleCox(withInputs).getSkills().getDef();
	}

	private static Monster named(String name)
	{
		return monsters.stream().filter(m -> name.equals(m.getName())).findFirst()
			.orElseThrow(() -> new IllegalStateException("no monster " + name));
	}

	private static <T> T read(String resource, Type type)
	{
		try (InputStream in = CoxScalingTest.class.getResourceAsStream(resource))
		{
			return new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), type);
		}
		catch (Exception e)
		{
			throw new RuntimeException(e);
		}
	}
}
