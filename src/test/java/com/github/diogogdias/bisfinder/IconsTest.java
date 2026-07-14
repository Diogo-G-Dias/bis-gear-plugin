package com.github.diogogdias.bisfinder;

import com.github.diogogdias.bisfinder.calc.model.Potion;
import com.github.diogogdias.bisfinder.calc.model.Prayer;
import java.io.InputStream;
import org.junit.Assert;
import org.junit.Test;

/**
 * The icons are looked up by name, so a rename or a missing file makes them silently disappear rather
 * than fail. These assert every name the panel can ask for actually resolves to a bundled file.
 */
public class IconsTest
{
	@Test
	public void everyPrayerHasAnIcon()
	{
		for (Prayer prayer : Prayer.values())
		{
			String path = "/prayers/" + prayer.getPrayerName().replace(' ', '_') + ".png";
			assertBundled(path, prayer.name());
		}
	}

	@Test
	public void everyPotionHasAnIcon()
	{
		for (Potion potion : Potion.values())
		{
			if (potion == Potion.NONE)
			{
				continue;
			}

			String path = "/potions/" + potion.getIconFile() + ".png";
			assertBundled(path, potion.name());
		}
	}

	private void assertBundled(String path, String what)
	{
		try (InputStream in = IconsTest.class.getResourceAsStream(path))
		{
			Assert.assertNotNull("no icon bundled at " + path + " for " + what, in);
		}
		catch (Exception e)
		{
			throw new AssertionError("could not read " + path, e);
		}
	}
}
