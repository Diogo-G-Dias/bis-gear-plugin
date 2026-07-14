package com.github.diogogdias.bisfinder;

import com.github.diogogdias.bisfinder.calc.WikiDataClient;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.PlayerBuffs;
import com.github.diogogdias.bisfinder.calc.model.PlayerSkills;
import com.github.diogogdias.bisfinder.calc.model.Potion;
import com.github.diogogdias.bisfinder.calc.model.Prayer;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import javax.inject.Inject;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;

@Slf4j
@PluginDescriptor(
	name = "BiS Gear",
	description = "Pick a boss and find the best-DPS gear setup you can build from the items in your bank",
	tags = {"dps", "gear", "bis", "boss", "bank", "setup"}
)
public class BisFinderPlugin extends Plugin
{
	private static final String CONFIG_GROUP = "bisfinder";
	private static final String BANK_KEY = "bankSnapshot";
	private static final String EXCLUDED_KEY = "excludedItems";

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private ClientToolbar clientToolbar;

	@Inject
	private ConfigManager configManager;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private WikiDataClient wikiData;

	@Inject
	private ItemManager itemManager;

	@Inject
	private Icons icons;

	private final OwnedItems owned = new OwnedItems();
	private final BisOptimizer optimizer = new BisOptimizer();

	/**
	 * Pictures are fetched here rather than on RuneLite's shared executor, which also runs the DPS search:
	 * a backlog of images must never hold up a search the player is waiting on.
	 */
	private ExecutorService imageExecutor;
	private final AtomicInteger thumbnailGeneration = new AtomicInteger();

	private BisFinderPanel panel;
	private NavigationButton navButton;
	private volatile boolean dataLoaded;

	@Override
	protected void startUp()
	{
		imageExecutor = Executors.newSingleThreadExecutor();
		panel = new BisFinderPanel(this, itemManager);
		navButton = NavigationButton.builder()
			.tooltip("BiS Gear")
			.icon(ImageUtil.loadImageResource(BisFinderPlugin.class, "/icon.png"))
			.priority(7)
			.panel(panel)
			.build();
		clientToolbar.addNavigation(navButton);

		owned.deserializeBank(configManager.getConfiguration(CONFIG_GROUP, BANK_KEY));
		readCurrentContainers();
		updateOwnedSummary();
		panel.setExcluded(excludedCount());

		executor.execute(this::loadData);
	}

	@Override
	protected void shutDown()
	{
		imageExecutor.shutdownNow();
		imageExecutor = null;
		clientToolbar.removeNavigation(navButton);
		panel = null;
		navButton = null;
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int id = event.getContainerId();
		if (id == InventoryID.BANK)
		{
			owned.setBank(itemIds(event.getItemContainer()), System.currentTimeMillis());
			configManager.setConfiguration(CONFIG_GROUP, BANK_KEY, owned.serializeBank());
			updateOwnedSummary();
		}
		else if (id == InventoryID.INV)
		{
			owned.setInventory(itemIds(event.getItemContainer()));
			updateOwnedSummary();
		}
		else if (id == InventoryID.WORN)
		{
			owned.setWorn(itemIds(event.getItemContainer()));
			updateOwnedSummary();
		}
	}

	/**
	 * Reads what the player is carrying right now. ItemContainerChanged only fires when a container
	 * changes, so without this the gear they are already wearing when the plugin starts is invisible —
	 * which is how a pair of worn Ferocious gloves loses to the Barrows gloves sitting in the bank.
	 */
	private void readCurrentContainers()
	{
		clientThread.invoke(() ->
		{
			owned.setInventory(itemIds(client.getItemContainer(InventoryID.INV)));
			owned.setWorn(itemIds(client.getItemContainer(InventoryID.WORN)));
			updateOwnedSummary();
		});
	}

	/** Fetches a monster's picture off the UI thread; the first sighting of one hits the network. */
	void loadMonsterImage(Monster monster)
	{
		executor.execute(() ->
		{
			BufferedImage image = icons.monster(monster.getImage());
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setMonsterImage(image);
				}
			});
		});
	}

	/**
	 * The picture for a row in the monster list, if it has already been fetched. Returns null rather than
	 * blocking: the list is drawn on the EDT and must never wait on the network.
	 */
	BufferedImage thumbnail(Monster monster)
	{
		return icons.cachedThumbnail(monster.getImage());
	}

	/**
	 * Fetches the pictures for the monsters currently listed and repaints the list as they arrive.
	 *
	 * <p>Each call supersedes the last: typing another letter abandons the pictures for the previous list
	 * rather than queueing another forty downloads behind it. They run on their own thread so a backlog of
	 * images can never delay a DPS search, and the results are cached, so a list that has been seen before
	 * costs nothing.
	 */
	void loadThumbnails(List<Monster> shown)
	{
		List<Monster> missing = shown.stream()
			.filter(monster -> icons.cachedThumbnail(monster.getImage()) == null)
			.collect(java.util.stream.Collectors.toList());

		if (missing.isEmpty())
		{
			return;
		}

		final int generation = thumbnailGeneration.incrementAndGet();

		imageExecutor.execute(() ->
		{
			for (Monster monster : missing)
			{
				if (generation != thumbnailGeneration.get())
				{
					// The list has moved on; nobody is waiting for these any more.
					return;
				}

				if (icons.thumbnail(monster.getImage()) != null)
				{
					SwingUtilities.invokeLater(() ->
					{
						if (panel != null && generation == thumbnailGeneration.get())
						{
							panel.thumbnailsArrived();
						}
					});
				}
			}
		});
	}

	BufferedImage prayerIcon(Prayer prayer)
	{
		return icons.prayer(prayer);
	}

	BufferedImage potionIcon(Potion potion)
	{
		return icons.potion(potion);
	}

	/** Called from the panel's Find button. */
	void findBestSetup(Monster target, SearchSettings settings)
	{
		if (!dataLoaded)
		{
			setResult(null, "Gear data has not loaded yet.");
			return;
		}

		Set<Integer> ownedIds = owned.all();
		if (ownedIds.isEmpty())
		{
			setResult(null, "Open your bank once so BiS Gear can see what you own.");
			return;
		}

		BisOptimizer.Options options = new BisOptimizer.Options(
			settings.getPrayer(), settings.getPotion(), settings.isNoPrayer(), excludedItems());

		// The defence reductions are an input to the monster, which MonsterScaling then applies.
		Monster scaled = target.toBuilder()
			.inputs(target.getInputs().toBuilder()
				.defenceReductions(settings.getDefenceReductions())
				.build())
			.build();

		// Skill levels may only be read on the client thread.
		clientThread.invoke(() ->
		{
			PlayerSkills skills = skills();
			executor.execute(() -> search(scaled, ownedIds, skills, settings, options));
		});
	}

	/** Items the player has told us never to suggest again. Kept between sessions. */
	private Set<Integer> excludedItems()
	{
		String saved = configManager.getConfiguration(CONFIG_GROUP, EXCLUDED_KEY);
		Set<Integer> ids = new HashSet<>();
		if (saved == null || saved.isEmpty())
		{
			return ids;
		}

		for (String id : saved.split(","))
		{
			try
			{
				ids.add(Integer.parseInt(id.trim()));
			}
			catch (NumberFormatException ignored)
			{
				// A corrupt entry costs one exclusion, not the whole list.
			}
		}
		return ids;
	}

	void excludeItem(int itemId)
	{
		Set<Integer> ids = excludedItems();
		ids.add(itemId);
		saveExclusions(ids);
	}

	void clearExclusions()
	{
		saveExclusions(new HashSet<>());
	}

	int excludedCount()
	{
		return excludedItems().size();
	}

	private void saveExclusions(Set<Integer> ids)
	{
		configManager.setConfiguration(CONFIG_GROUP, EXCLUDED_KEY,
			ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));

		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setExcluded(ids.size());
			}
		});
	}

	private void search(Monster target, Set<Integer> ownedIds, PlayerSkills skills, SearchSettings settings,
		BisOptimizer.Options options)
	{
		try
		{
			PlayerBuffs buffs = PlayerBuffs.builder()
				.onSlayerTask(settings.isOnSlayerTask())
				.inWilderness(settings.isInWilderness())
				.soulreaperStacks(settings.getSoulreaperStacks())
				.build();

			List<BisOptimizer.Result> results = optimizer.findBestPerStyle(
				wikiData.equipment(), wikiData.spells(), ownedIds, skills, buffs, target, options);

			if (results.isEmpty())
			{
				setResult(null, "Nothing you own can damage this target.");
			}
			else
			{
				setResult(results, null);
			}
		}
		catch (IOException e)
		{
			log.warn("Could not search for {}", target.getName(), e);
			setResult(null, "Could not load gear data.");
		}
	}

	private void loadData()
	{
		try
		{
			wikiData.loadAll();
			dataLoaded = true;

			List<Monster> monsters = wikiData.monsters();
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setMonsters(monsters);
				}
			});
		}
		catch (IOException e)
		{
			log.warn("Could not load wiki calculator data", e);
			SwingUtilities.invokeLater(() ->
			{
				if (panel != null)
				{
					panel.setStatus("Could not load gear data");
				}
			});
		}
	}

	/** Must be called on the client thread. */
	private PlayerSkills skills()
	{
		return new PlayerSkills(
			client.getRealSkillLevel(Skill.ATTACK),
			client.getRealSkillLevel(Skill.DEFENCE),
			client.getRealSkillLevel(Skill.HITPOINTS),
			client.getRealSkillLevel(Skill.MAGIC),
			client.getRealSkillLevel(Skill.PRAYER),
			client.getRealSkillLevel(Skill.RANGED),
			client.getRealSkillLevel(Skill.STRENGTH),
			client.getRealSkillLevel(Skill.MINING),
			client.getRealSkillLevel(Skill.HERBLORE));
	}

	private void setResult(List<BisOptimizer.Result> results, String problem)
	{
		SwingUtilities.invokeLater(() ->
		{
			if (panel == null)
			{
				return;
			}
			if (results == null || results.isEmpty())
			{
				panel.setNoResult(problem);
			}
			else
			{
				panel.setResults(results);
			}
		});
	}

	private void updateOwnedSummary()
	{
		int items = owned.all().size();
		long updatedAt = owned.getBankUpdatedAt();

		SwingUtilities.invokeLater(() ->
		{
			if (panel != null)
			{
				panel.setOwned(items, updatedAt);
			}
		});
	}

	private static Set<Integer> itemIds(ItemContainer container)
	{
		Set<Integer> ids = new HashSet<>();
		if (container == null)
		{
			return ids;
		}

		for (Item item : container.getItems())
		{
			if (item.getId() > 0)
			{
				ids.add(item.getId());
			}
		}
		return ids;
	}
}
