package com.github.diogogdias.bisfinder;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class BisFinderPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(BisFinderPlugin.class);
		RuneLite.main(args);
	}
}
