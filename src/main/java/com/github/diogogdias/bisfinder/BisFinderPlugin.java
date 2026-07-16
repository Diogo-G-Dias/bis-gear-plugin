package com.github.diogogdias.bisfinder;

import com.github.diogogdias.bisfinder.calc.WikiDataClient;
import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.Player;
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
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.plugins.banktags.TagManager;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.banktags.tabs.LayoutManager;
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
	private static final String SHOW_LOADOUT_KEY = "showLoadoutInBank";

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

	// Injected only when the Bank Tags plugin is enabled; the loadout-in-bank feature no-ops without it.
	@com.google.inject.Inject(optional = true)
	private BankTagsService bankTagsService;

	@com.google.inject.Inject(optional = true)
	private TagManager tagManager;

	@com.google.inject.Inject(optional = true)
	private LayoutManager layoutManager;

	/** The name of the temporary bank tag the current loadout is shown under. */
	private static final String LOADOUT_TAG = "BiS loadout";

	/** The equipment layout positions, mirroring the worn-equipment screen, as 8-wide bank-grid indices. */
	private static final int POS_HEAD = 1;
	private static final int POS_CAPE = 8;
	private static final int POS_NECK = 9;
	private static final int POS_AMMO = 10;
	private static final int POS_WEAPON = 16;
	private static final int POS_BODY = 17;
	private static final int POS_SHIELD = 18;
	private static final int POS_LEGS = 25;
	private static final int POS_HANDS = 32;
	private static final int POS_FEET = 34;
	private static final int POS_RING = 41;

	/** Whether to show the recommended loadout as a temporary bank tag; toggled from the sidebar, persisted. */
	private volatile boolean showLoadoutInBank;

	/** The worn equipment of the last recommended loadout, shown as the temporary bank tag when the bank is open. */
	private volatile Player.PlayerEquipment loadoutGear;

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
		// Score setups with the new BSD-2 engine.
		BisOptimizer.scorer = com.github.diogogdias.bisfinder.engine.DpsEngine::dps;

		showLoadoutInBank = "true".equals(configManager.getConfiguration(CONFIG_GROUP, SHOW_LOADOUT_KEY));

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
		hideLoadoutTag();
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

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			openLoadoutTag();
		}
	}

	boolean isShowLoadoutInBank()
	{
		return showLoadoutInBank;
	}

	/** Sidebar toggle: persist the choice and show or hide the loadout tag immediately. */
	void setShowLoadoutInBank(boolean show)
	{
		showLoadoutInBank = show;
		configManager.setConfiguration(CONFIG_GROUP, SHOW_LOADOUT_KEY, show);
		if (show)
		{
			openLoadoutTag();
		}
		else
		{
			hideLoadoutTag();
		}
	}

	/**
	 * Opens the recommended loadout as a temporary bank tag laid out like the worn-equipment screen. No-op
	 * unless the option is on and the Bank Tags plugin is enabled (which supplies the injected services).
	 */
	private void openLoadoutTag()
	{
		if (!showLoadoutInBank)
		{
			return;
		}
		if (bankTagsService == null || tagManager == null || layoutManager == null)
		{
			log.debug("Loadout-in-bank needs the Bank Tags plugin (bankTags={}, tagManager={}, layoutManager={})",
				bankTagsService != null, tagManager != null, layoutManager != null);
			return;
		}
		final Player.PlayerEquipment gear = loadoutGear;
		if (gear == null)
		{
			return;
		}
		final Set<Integer> ids = loadoutIds(gear);
		clientThread.invoke(() ->
		{
			tagManager.registerTag(LOADOUT_TAG, itemId -> ids.contains(itemId));
			layoutManager.saveLayout(buildLayout(gear));
			bankTagsService.openBankTag(LOADOUT_TAG, BankTagsService.OPTION_HIDE_TAG_NAME);
		});
	}

	/** Closes and unregisters the temporary loadout tag and its layout, if it is showing. */
	private void hideLoadoutTag()
	{
		if (bankTagsService == null || tagManager == null || layoutManager == null)
		{
			return;
		}
		clientThread.invoke(() ->
		{
			if (LOADOUT_TAG.equals(bankTagsService.getActiveTag()))
			{
				bankTagsService.closeBankTag();
			}
			layoutManager.removeLayout(LOADOUT_TAG);
			tagManager.unregisterTag(LOADOUT_TAG);
		});
	}

	/** Re-shows the loadout tag with the latest items, but only if it is the tab the player is looking at. */
	private void refreshLoadoutTagIfActive()
	{
		if (bankTagsService == null || tagManager == null || layoutManager == null)
		{
			return;
		}
		clientThread.invoke(() ->
		{
			if (LOADOUT_TAG.equals(bankTagsService.getActiveTag()))
			{
				openLoadoutTag();
			}
		});
	}

	/** A bank-tag layout placing each worn slot at its equipment-screen position. */
	private static Layout buildLayout(Player.PlayerEquipment gear)
	{
		Layout layout = new Layout(LOADOUT_TAG);
		layout.resize(48);
		place(layout, POS_HEAD, gear.getHead());
		place(layout, POS_CAPE, gear.getCape());
		place(layout, POS_NECK, gear.getNeck());
		place(layout, POS_AMMO, gear.getAmmo());
		place(layout, POS_WEAPON, gear.getWeapon());
		place(layout, POS_BODY, gear.getBody());
		place(layout, POS_SHIELD, gear.getShield());
		place(layout, POS_LEGS, gear.getLegs());
		place(layout, POS_HANDS, gear.getHands());
		place(layout, POS_FEET, gear.getFeet());
		place(layout, POS_RING, gear.getRing());
		return layout;
	}

	private static void place(Layout layout, int pos, EquipmentPiece piece)
	{
		if (piece != null)
		{
			layout.setItemAtPos(pos, piece.getId());
		}
	}

	/** The worn-equipment item ids of a loadout, for the temporary bank tag's membership test. */
	private static Set<Integer> loadoutIds(Player.PlayerEquipment gear)
	{
		Set<Integer> ids = new HashSet<>();
		for (EquipmentPiece piece : new EquipmentPiece[]{gear.getHead(), gear.getCape(), gear.getNeck(),
			gear.getAmmo(), gear.getWeapon(), gear.getBody(), gear.getShield(), gear.getLegs(),
			gear.getHands(), gear.getFeet(), gear.getRing()})
		{
			if (piece != null)
			{
				ids.add(piece.getId());
			}
		}
		return ids;
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

		// Raid inputs (party size, Challenge Mode, ToA invocation) and defence reductions live on the monster.
		// Chambers of Xeric scales the target's defence up-front; ToA scaling is applied at roll time.
		Monster withInputs = target.toBuilder()
			.inputs(target.getInputs().toBuilder()
				.defenceReductions(settings.getDefenceReductions())
				.partySize(settings.getPartySize())
				.isFromCoxCm(settings.isCoxChallengeMode())
				.toaInvocationLevel(settings.getToaInvocationLevel())
				.build())
			.build();
		Monster scaled = com.github.diogogdias.bisfinder.engine.RaidScaling.scaleCox(withInputs);

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

	/** Assumed for a logged-out account, whose real levels the client cannot be asked for. */
	private static final PlayerSkills MAXED = new PlayerSkills(99, 99, 99, 99, 99, 99, 99, 99, 99);

	/**
	 * The player's levels, or a maxed account while logged out. The bank snapshot is kept between sessions,
	 * so a search at the login screen is still worth answering, but with no account loaded the client has no
	 * real levels to give - whatever it returns is not the player's.
	 *
	 * <p>Must be called on the client thread.
	 */
	private PlayerSkills skills()
	{
		if (!profileLoaded())
		{
			return MAXED;
		}

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

	/**
	 * Whether an account is loaded, and so the client's levels are really the player's. Loading, hopping and
	 * a dropped connection all still have the account's levels; only the login screen has none.
	 */
	private boolean profileLoaded()
	{
		GameState state = client.getGameState();
		return state == GameState.LOGGED_IN || state == GameState.LOADING
			|| state == GameState.HOPPING || state == GameState.CONNECTION_LOST;
	}

	private void setResult(List<BisOptimizer.Result> results, String problem)
	{
		loadoutGear = results == null || results.isEmpty() ? null : results.get(0).getPlayer().getEquipment();
		refreshLoadoutTagIfActive();

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
