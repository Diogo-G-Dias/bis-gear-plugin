package com.github.diogogdias.bisfinder;

import com.github.diogogdias.bisfinder.calc.model.EquipmentPiece;
import com.github.diogogdias.bisfinder.calc.model.Monster;
import com.github.diogogdias.bisfinder.calc.model.MonsterInputs;
import com.github.diogogdias.bisfinder.calc.model.Player;
import com.github.diogogdias.bisfinder.calc.model.Potion;
import com.github.diogogdias.bisfinder.calc.model.Prayer;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.ImageIcon;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JFormattedTextField;
import javax.swing.JTabbedPane;
import javax.swing.SpinnerNumberModel;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.DefaultFormatter;
import net.runelite.client.game.ItemManager;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.AsyncBufferedImage;

class BisFinderPanel extends PluginPanel
{
	/** Keeps the result list short enough to stay usable in the sidebar. */
	private static final int MAX_RESULTS = 40;

	/** An equipment square, big enough for a 32px item sprite with a little room around it. */
	private static final int SLOT_SIZE = 38;

	// The weapons whose specials drain a target's defence, so each input can be shown as its own sprite.
	private static final int DRAGON_WARHAMMER = 13576;
	private static final int ELDER_MAUL = 21003;
	private static final int ARCLIGHT = 19675;
	private static final int EMBERLIGHT = 29589;
	private static final int BANDOS_GODSWORD = 11804;
	private static final int TONALZTICS = 28922;
	private static final int SEERCULL = 6724;
	private static final int EYE_OF_AYAK = 31113;
	private static final int SOULREAPER_AXE = 28338;

	private final BisFinderPlugin plugin;
	private final ItemManager itemManager;

	private final JLabel monsterImage = new JLabel();
	private final JLabel status = new JLabel("Loading gear data...");
	private final IconTextField search = new IconTextField();
	private final DefaultListModel<Monster> resultModel = new DefaultListModel<>();
	private final JList<Monster> results = new JList<>(resultModel);
	private final JScrollPane resultScroll = new JScrollPane(results);
	private final JLabel selectedName = new JLabel();
	private final JCheckBox onTask = new JCheckBox("On slayer task");
	private final JButton findButton = new JButton("Find best setup");

	private final JComboBox<Object> prayerChoice = new JComboBox<>();
	private final JComboBox<Object> potionChoice = new JComboBox<>();
	private final JLabel excluded = new JLabel();

	private final JButton advancedToggle = new JButton("Advanced options");
	private final JPanel advanced = new JPanel();
	private final JCheckBox inWilderness = new JCheckBox("In wilderness");
	private final JCheckBox vulnerability = new JCheckBox("Vulnerability");
	private final JCheckBox accursed = new JCheckBox("Accursed sceptre");
	private final JSpinner dwh = counter(0, 10);
	private final JSpinner elderMaul = counter(0, 10);
	private final JSpinner arclight = counter(0, 10);
	private final JSpinner emberlight = counter(0, 10);
	private final JSpinner bgs = counter(0, 500);
	private final JSpinner tonalztic = counter(0, 10);
	private final JSpinner seercull = counter(0, 50);
	private final JSpinner ayak = counter(0, 200);
	private final JSpinner soulreaper = counter(0, 5);

	/** Shown as the first entry in each dropdown; means "work it out for the style". */
	private static final String AUTO = "Auto (best)";
	private static final String NO_PRAYER = "None";

	private final JLabel headline = new JLabel();
	private final JPanel numbers = new JPanel(new GridLayout(0, 2, 4, 2));
	private final JPanel setup = new JPanel();
	private final JLabel bankSummary = new JLabel();
	private final JLabel version = new JLabel();

	private List<Monster> monsters = new ArrayList<>();
	private Monster selected;

	/** Pictures are only fetched once typing pauses; this holds the list they are wanted for. */
	private List<Monster> pending = new ArrayList<>();
	private final Timer thumbnailDebounce = new Timer(250, null);

	/** Re-runs the search shortly after an option changes. */
	private final Timer searchDebounce = new Timer(350, null);

	BisFinderPanel(BisFinderPlugin plugin, ItemManager itemManager)
	{
		this.plugin = plugin;
		this.itemManager = itemManager;

		setLayout(new BorderLayout(0, 8));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));

		status.setFont(FontManager.getRunescapeSmallFont());
		status.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		status.setAlignmentX(Component.LEFT_ALIGNMENT);

		search.setIcon(IconTextField.Icon.SEARCH);
		search.setToolTipText("Search any monster by name");
		search.setEnabled(false);
		search.setPreferredSize(new Dimension(100, 26));
		search.setMaximumSize(new Dimension(Integer.MAX_VALUE, 26));
		search.setAlignmentX(Component.LEFT_ALIGNMENT);
		search.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		search.getDocument().addDocumentListener(new DocumentListener()
		{
			@Override
			public void insertUpdate(DocumentEvent e)
			{
				refreshResults();
			}

			@Override
			public void removeUpdate(DocumentEvent e)
			{
				refreshResults();
			}

			@Override
			public void changedUpdate(DocumentEvent e)
			{
				refreshResults();
			}
		});

		// The monster list only appears while the search field (or the list itself) is focused, so it does not
		// sit open taking up the sidebar the rest of the time. IconTextField wraps a private inner text field
		// and does not forward focus listeners, so watch the global focus owner instead.
		java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager()
			.addPropertyChangeListener("focusOwner", evt ->
			{
				if (!(evt.getNewValue() instanceof Component))
				{
					return;
				}
				Component focused = (Component) evt.getNewValue();
				boolean inSearchOrList = javax.swing.SwingUtilities.isDescendingFrom(focused, search)
					|| javax.swing.SwingUtilities.isDescendingFrom(focused, resultScroll);
				showResults(inSearchOrList);
			});

		results.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		results.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		results.setFixedCellHeight(28);
		results.setCellRenderer((list, value, index, isSelected, focused) ->
		{
			JLabel label = new JLabel("<html>" + label(value) + "<br><font color='#909090'>level "
				+ value.getLevel() + " &middot; " + value.getSkills().getHp() + " hp</font></html>");
			label.setBorder(BorderFactory.createEmptyBorder(2, 5, 2, 5));
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setOpaque(true);
			label.setIconTextGap(6);
			label.setBackground(isSelected ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

			// Already-fetched pictures are drawn straight away; the rest arrive from thumbnails() below and
			// the list repaints. Fetching them inside the renderer would hit the network on the EDT.
			BufferedImage thumbnail = plugin.thumbnail(value);
			if (thumbnail != null)
			{
				label.setIcon(new ImageIcon(thumbnail));
			}
			return label;
		});
		results.addListSelectionListener(e ->
		{
			if (e.getValueIsAdjusting())
			{
				return;
			}

			Monster picked = results.getSelectedValue();
			if (picked != null)
			{
				selected = picked;
				selectedName.setText(label(picked));
				findButton.setEnabled(true);
				monsterImage.setIcon(null);
				clearResult();
				showResults(false);
				plugin.loadMonsterImage(picked);
			}
		});

		onTask.setFont(FontManager.getRunescapeSmallFont());
		onTask.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		onTask.setBackground(ColorScheme.DARK_GRAY_COLOR);
		onTask.setAlignmentX(Component.LEFT_ALIGNMENT);
		onTask.setFocusable(false);

		prayerChoice.addItem(AUTO);
		prayerChoice.addItem(NO_PRAYER);
		for (Prayer prayer : Prayer.values())
		{
			// Defensive-only prayers change nothing about damage, so they are not offered.
			if (prayer.getStyle() != null)
			{
				prayerChoice.addItem(prayer);
			}
		}
		style(prayerChoice, "Prayer the DPS assumes");

		potionChoice.addItem(AUTO);
		for (Potion potion : Potion.values())
		{
			potionChoice.addItem(potion);
		}
		style(potionChoice, "Boost the DPS assumes");

		excluded.setFont(FontManager.getRunescapeSmallFont());
		excluded.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		excluded.setAlignmentX(Component.LEFT_ALIGNMENT);
		excluded.setCursor(new java.awt.Cursor(java.awt.Cursor.HAND_CURSOR));
		excluded.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mouseClicked(java.awt.event.MouseEvent e)
			{
				if (plugin.excludedCount() > 0)
				{
					plugin.clearExclusions();
					search();
				}
			}
		});

		thumbnailDebounce.setRepeats(false);
		thumbnailDebounce.addActionListener(e -> plugin.loadThumbnails(pending));

		buildAdvanced();

		// Changing an option re-runs the search. Without this the result on screen silently belonged to the
		// options as they were when Find was last pressed, so adding soul stacks looked like it did nothing.
		// Unlike a fresh search, this keeps the current result on screen until the new one is ready, so the
		// panel does not collapse to "Searching..." and jump while the numbers are recomputed.
		searchDebounce.setRepeats(false);
		searchDebounce.addActionListener(e ->
		{
			if (selected != null)
			{
				plugin.findBestSetup(selected, settings());
			}
		});

		for (JCheckBox box : new JCheckBox[]{onTask, inWilderness, vulnerability, accursed})
		{
			box.addActionListener(e -> rerun());
		}
		for (JSpinner spinner : new JSpinner[]{dwh, elderMaul, arclight, emberlight, bgs, tonalztic,
			seercull, ayak, soulreaper})
		{
			spinner.addChangeListener(e -> rerun());
		}
		prayerChoice.addActionListener(e -> rerun());
		potionChoice.addActionListener(e -> rerun());

		findButton.setEnabled(false);
		findButton.setFocusable(false);
		findButton.setAlignmentX(Component.LEFT_ALIGNMENT);
		findButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		findButton.addActionListener(e -> search());

		resultScroll.setPreferredSize(new Dimension(100, 150));
		resultScroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 150));
		resultScroll.setAlignmentX(Component.LEFT_ALIGNMENT);
		resultScroll.setBorder(BorderFactory.createEmptyBorder());
		resultScroll.setVisible(false);

		selectedName.setFont(FontManager.getRunescapeBoldFont());
		selectedName.setForeground(ColorScheme.BRAND_ORANGE);
		selectedName.setAlignmentX(Component.LEFT_ALIGNMENT);
		selectedName.setBorder(BorderFactory.createEmptyBorder(6, 0, 2, 0));

		headline.setFont(FontManager.getRunescapeBoldFont());
		headline.setForeground(ColorScheme.PROGRESS_COMPLETE_COLOR);
		headline.setAlignmentX(Component.LEFT_ALIGNMENT);

		numbers.setAlignmentX(Component.LEFT_ALIGNMENT);
		numbers.setBackground(ColorScheme.DARK_GRAY_COLOR);

		setup.setLayout(new BoxLayout(setup, BoxLayout.Y_AXIS));
		setup.setAlignmentX(Component.LEFT_ALIGNMENT);

		bankSummary.setFont(FontManager.getRunescapeSmallFont());
		bankSummary.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		bankSummary.setAlignmentX(Component.LEFT_ALIGNMENT);
		bankSummary.setVerticalAlignment(SwingConstants.TOP);

		content.add(status);
		content.add(Box.createVerticalStrut(8));
		content.add(search);
		content.add(Box.createVerticalStrut(4));
		monsterImage.setAlignmentX(Component.LEFT_ALIGNMENT);
		monsterImage.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

		content.add(resultScroll);
		content.add(selectedName);
		content.add(monsterImage);
		content.add(Box.createVerticalStrut(6));
		content.add(onTask);
		content.add(Box.createVerticalStrut(4));
		content.add(labelled("Prayer", prayerChoice));
		content.add(Box.createVerticalStrut(2));
		content.add(labelled("Boost", potionChoice));
		content.add(Box.createVerticalStrut(4));
		content.add(advancedToggle);
		content.add(advanced);
		content.add(Box.createVerticalStrut(6));
		content.add(findButton);
		content.add(Box.createVerticalStrut(10));
		content.add(headline);
		content.add(numbers);
		content.add(Box.createVerticalStrut(6));
		content.add(setup);
		content.add(Box.createVerticalStrut(10));
		content.add(header("Your items"));
		content.add(bankSummary);
		content.add(excluded);

		version.setFont(FontManager.getRunescapeSmallFont());
		version.setForeground(ColorScheme.MEDIUM_GRAY_COLOR);
		version.setAlignmentX(Component.LEFT_ALIGNMENT);
		version.setBorder(BorderFactory.createEmptyBorder(10, 0, 2, 0));
		version.setText(buildLabel());
		content.add(Box.createVerticalStrut(6));
		content.add(version);

		add(content, BorderLayout.NORTH);
	}

	/** The dev build number the client was launched with, read from the resource gradle stamps on each run. */
	private static String buildLabel()
	{
		try (java.io.InputStream in = BisFinderPanel.class.getResourceAsStream("/bisfinder-build.txt"))
		{
			if (in != null)
			{
				String number = new java.io.BufferedReader(
					new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8)).readLine();
				if (number != null && !number.trim().isEmpty())
				{
					return "BiS Gear — dev build #" + number.trim();
				}
			}
		}
		catch (java.io.IOException ignored)
		{
			// Fall through to the unversioned label.
		}
		return "BiS Gear — dev";
	}

	/**
	 * The fight, rather than the gear: how far the target's defence has already been hammered down, and
	 * how many souls are stacked on a Soulreaper axe. Hidden by default, because most searches want none
	 * of it.
	 */
	private void buildAdvanced()
	{
		advanced.setLayout(new BoxLayout(advanced, BoxLayout.Y_AXIS));
		advanced.setAlignmentX(Component.LEFT_ALIGNMENT);
		advanced.setBackground(ColorScheme.DARK_GRAY_COLOR);
		advanced.setVisible(false);

		for (JCheckBox box : new JCheckBox[]{inWilderness, vulnerability, accursed})
		{
			box.setFont(FontManager.getRunescapeSmallFont());
			box.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			box.setBackground(ColorScheme.DARK_GRAY_COLOR);
			box.setAlignmentX(Component.LEFT_ALIGNMENT);
			box.setFocusable(false);
		}

		inWilderness.setToolTipText("Powers the wilderness weapons (Craw's bow, Viggora's chainmace...)");
		vulnerability.setToolTipText("Vulnerability cast on the target: -10% defence");
		accursed.setToolTipText("Accursed sceptre special: -15% defence");
		advanced.add(inWilderness);
		advanced.add(vulnerability);
		advanced.add(accursed);
		advanced.add(Box.createVerticalStrut(6));

		// The reductions are all "how many of this weapon's specials have landed", so the weapon's own sprite
		// says what each box is far better than an abbreviation does.
		advanced.add(sectionLabel("Defence reduction"));
		advanced.add(reductions());

		advanced.add(Box.createVerticalStrut(6));
		advanced.add(sectionLabel("Soul stacks"));
		advanced.add(reductionRow(SOULREAPER_AXE, soulreaper,
			"Souls stacked on the Soulreaper axe (each is +6% strength)"));

		advancedToggle.setFocusable(false);
		advancedToggle.setAlignmentX(Component.LEFT_ALIGNMENT);
		advancedToggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
		advancedToggle.setFont(FontManager.getRunescapeSmallFont());
		advancedToggle.addActionListener(e ->
		{
			advanced.setVisible(!advanced.isVisible());
			revalidate();
			repaint();
		});
	}

	/**
	 * The defence reductions, two to a row: each is the weapon's own sprite next to a small box for how
	 * many of its specials have landed.
	 */
	private JPanel reductions()
	{
		JPanel grid = new JPanel(new GridLayout(0, 2, 4, 2));
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.setBackground(ColorScheme.DARK_GRAY_COLOR);

		grid.add(reductionRow(DRAGON_WARHAMMER, dwh, "Dragon warhammer specials landed: -30% defence each"));
		grid.add(reductionRow(ELDER_MAUL, elderMaul, "Elder maul specials landed: -35% defence each"));
		grid.add(reductionRow(ARCLIGHT, arclight, "Arclight specials landed (demons only)"));
		grid.add(reductionRow(EMBERLIGHT, emberlight, "Emberlight specials landed (demons only)"));
		grid.add(reductionRow(BANDOS_GODSWORD, bgs, "Defence already drained by a Bandos godsword, in points"));
		grid.add(reductionRow(TONALZTICS, tonalztic, "Tonalztics of ralos specials landed"));
		grid.add(reductionRow(SEERCULL, seercull, "Magic level drained by a Seercull, in points"));
		grid.add(reductionRow(EYE_OF_AYAK, ayak, "Magic defence drained by Eye of ayak specials, in points"));

		return grid;
	}

	/** One reduction: the weapon's sprite, and a box small enough that two fit across the sidebar. */
	private JPanel reductionRow(int itemId, JSpinner spinner, String tooltip)
	{
		JPanel row = new JPanel(new BorderLayout(3, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setToolTipText(tooltip);

		JLabel icon = new JLabel();
		icon.setPreferredSize(new Dimension(26, 26));
		icon.setHorizontalAlignment(SwingConstants.CENTER);
		icon.setToolTipText(tooltip);
		itemManager.getImage(itemId).addTo(icon);

		spinner.setToolTipText(tooltip);
		spinner.setPreferredSize(new Dimension(46, 22));
		spinner.setMaximumSize(new Dimension(46, 22));

		row.add(icon, BorderLayout.WEST);
		row.add(spinner, BorderLayout.CENTER);
		return row;
	}

	private static JLabel sectionLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private SearchSettings settings()
	{
		Object prayer = prayerChoice.getSelectedItem();
		Object potion = potionChoice.getSelectedItem();

		MonsterInputs.DefenceReductions reductions = MonsterInputs.DefenceReductions.builder()
			.vulnerability(vulnerability.isSelected())
			.accursed(accursed.isSelected())
			.dwh(value(dwh))
			.elderMaul(value(elderMaul))
			.arclight(value(arclight))
			.emberlight(value(emberlight))
			.bgs(value(bgs))
			.tonalztic(value(tonalztic))
			.seercull(value(seercull))
			.ayak(value(ayak))
			.build();

		return SearchSettings.builder()
			.onSlayerTask(onTask.isSelected())
			.inWilderness(inWilderness.isSelected())
			.prayer(prayer instanceof Prayer ? (Prayer) prayer : null)
			.potion(potion instanceof Potion ? (Potion) potion : null)
			.noPrayer(NO_PRAYER.equals(prayer))
			.soulreaperStacks(value(soulreaper))
			.defenceReductions(reductions)
			.build();
	}

	/**
	 * Re-runs the search after an option changes, but only once the player has stopped fiddling — holding
	 * the arrow on a spinner would otherwise fire a search per click.
	 */
	private void rerun()
	{
		if (selected != null)
		{
			searchDebounce.restart();
		}
	}

	private void search()
	{
		if (selected == null)
		{
			return;
		}

		clearResult();
		headline.setText("Searching...");
		plugin.findBestSetup(selected, settings());
	}

	private static int value(JSpinner spinner)
	{
		return ((Number) spinner.getValue()).intValue();
	}

	private static JSpinner counter(int min, int max)
	{
		JSpinner spinner = new JSpinner(new SpinnerNumberModel(min, min, max, 1));
		spinner.setFont(FontManager.getRunescapeSmallFont());

		// A typed number is committed as it is typed, rather than only when the field loses focus. Without
		// this, typing "3" and pressing enter left the spinner still holding its old value.
		JSpinner.NumberEditor editor = new JSpinner.NumberEditor(spinner, "0");
		JFormattedTextField field = editor.getTextField();
		field.setFocusLostBehavior(JFormattedTextField.COMMIT);
		((DefaultFormatter) field.getFormatter()).setCommitsOnValidEdit(true);
		spinner.setEditor(editor);

		return spinner;
	}

	/** Right-clicking a suggested item stops it being suggested again. */
	private void addExcludeMenu(JLabel label, EquipmentPiece piece)
	{
		JPopupMenu menu = new JPopupMenu();
		JMenuItem exclude = new JMenuItem("Don't suggest " + piece.getName());
		exclude.addActionListener(e ->
		{
			plugin.excludeItem(piece.getId());
			search();
		});
		menu.add(exclude);
		label.setComponentPopupMenu(menu);
	}

	void setExcluded(int count)
	{
		if (count == 0)
		{
			excluded.setText("");
			return;
		}

		excluded.setText(count + " item" + (count == 1 ? "" : "s") + " excluded (click to clear)");
	}

	private JPanel labelled(String name, JComponent field)
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(ColorScheme.DARK_GRAY_COLOR);
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));

		JLabel key = new JLabel(name);
		key.setFont(FontManager.getRunescapeSmallFont());
		key.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		key.setPreferredSize(new Dimension(48, 22));

		row.add(key, BorderLayout.WEST);
		row.add(field, BorderLayout.CENTER);
		return row;
	}

	private static void style(JComboBox<Object> box, String tooltip)
	{
		box.setFont(FontManager.getRunescapeSmallFont());
		box.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		box.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		box.setToolTipText(tooltip);
		box.setFocusable(false);
		box.setRenderer((list, value, index, isSelected, focused) ->
		{
			String text;
			if (value instanceof Prayer)
			{
				text = ((Prayer) value).getPrayerName();
			}
			else if (value instanceof Potion)
			{
				text = ((Potion) value).getPotionName();
			}
			else
			{
				text = String.valueOf(value);
			}

			JLabel label = new JLabel(text);
			label.setFont(FontManager.getRunescapeSmallFont());
			label.setOpaque(true);
			label.setBorder(BorderFactory.createEmptyBorder(2, 4, 2, 4));
			label.setBackground(isSelected ? ColorScheme.DARKER_GRAY_HOVER_COLOR : ColorScheme.DARKER_GRAY_COLOR);
			label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
			return label;
		});
	}

	void setMonsters(List<Monster> all)
	{
		monsters = all;
		search.setEnabled(true);
		status.setText(all.size() + " monsters loaded");
		refreshResults();
	}

	void setStatus(String text)
	{
		status.setText(text);
	}

	/**
	 * One block per style, best first — the way gearscape lists them, so the cost of using the style you
	 * actually want is visible. The winner is expanded; the others collapse to a headline you can click.
	 */
	void setResults(List<BisOptimizer.Result> results)
	{
		clearResult();

		BisOptimizer.Result winner = results.get(0);
		headline.setText(String.format("%.2f DPS  %s", winner.getDps(), styleName(winner)));

		// One tab per style, best first, so only one setup is on screen at a time and the sidebar stays
		// readable. The tab title carries the DPS, so the comparison needs no clicking.
		JTabbedPane tabs = new JTabbedPane();
		tabs.setFont(FontManager.getRunescapeSmallFont());
		tabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
		tabs.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		tabs.setAlignmentX(Component.LEFT_ALIGNMENT);
		tabs.setFocusable(false);

		// The sidebar is narrow: with the DPS in the titles the three tabs did not fit on one row and wrapped
		// onto separate rows, which is what made them look detached. Keep them on one row, and let the DPS
		// live in the tab's first stat instead.
		tabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);

		// The melee attack styles (stab/slash/crush) share one top-level "Melee" tab and split into sub-tabs
		// inside it, so which weapon wins each style - e.g. a stab fang vs a slash blade - is visible at a glance.
		List<BisOptimizer.Result> meleeResults = new ArrayList<>();
		for (BisOptimizer.Result result : results)
		{
			if (result.getStyleGroup() == BisOptimizer.Style.MELEE)
			{
				meleeResults.add(result);
			}
		}

		boolean meleeAdded = false;
		for (BisOptimizer.Result result : results)
		{
			if (result.getStyleGroup() == BisOptimizer.Style.MELEE && meleeResults.size() > 1)
			{
				if (meleeAdded)
				{
					continue;
				}
				meleeAdded = true;

				JTabbedPane meleeTabs = new JTabbedPane();
				meleeTabs.setFont(FontManager.getRunescapeSmallFont());
				meleeTabs.setBackground(ColorScheme.DARK_GRAY_COLOR);
				meleeTabs.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
				meleeTabs.setFocusable(false);
				meleeTabs.setTabLayoutPolicy(JTabbedPane.SCROLL_TAB_LAYOUT);
				for (BisOptimizer.Result meleeResult : meleeResults)
				{
					meleeTabs.addTab(meleeStyleName(meleeResult), bodyFor(meleeResult));
					double behind = winner.getDps() - meleeResult.getDps();
					meleeTabs.setToolTipTextAt(meleeTabs.getTabCount() - 1, meleeResult == winner
						? String.format("%.2f dps - the best you can do here", meleeResult.getDps())
						: String.format("%.2f dps - %.2f behind the best", meleeResult.getDps(), behind));
				}

				JPanel wrapper = new JPanel(new BorderLayout());
				wrapper.setBackground(ColorScheme.DARK_GRAY_COLOR);
				wrapper.add(meleeTabs, BorderLayout.CENTER);
				tabs.addTab("Melee", wrapper);
				tabs.setToolTipTextAt(tabs.getTabCount() - 1,
					String.format("Best melee: %.2f dps", meleeResults.get(0).getDps()));
			}
			else
			{
				tabs.addTab(styleName(result), bodyFor(result));
				double behind = winner.getDps() - result.getDps();
				tabs.setToolTipTextAt(tabs.getTabCount() - 1, result == winner
					? String.format("%.2f dps - the best you can do here", result.getDps())
					: String.format("%.2f dps - %.2f behind the best", result.getDps(), behind));
			}
		}

		setup.add(tabs);

		revalidate();
		repaint();
	}

	/**
	 * The defence reductions and soul stacks the DPS was calculated with, in words. Empty when the target
	 * is being fought at full defence with no souls, which is the usual case.
	 */
	private String describeReductions()
	{
		List<String> applied = new ArrayList<>();

		addIfSet(applied, value(dwh), "DWH");
		addIfSet(applied, value(elderMaul), "Elder maul");
		addIfSet(applied, value(arclight), "Arclight");
		addIfSet(applied, value(emberlight), "Emberlight");
		addIfSet(applied, value(tonalztic), "Ralos");
		addIfSet(applied, value(seercull), "Seercull");
		addIfSet(applied, value(ayak), "Ayak");

		if (value(bgs) > 0)
		{
			applied.add(value(bgs) + " BGS damage");
		}
		if (vulnerability.isSelected())
		{
			applied.add("Vulnerability");
		}
		if (accursed.isSelected())
		{
			applied.add("Accursed");
		}
		if (value(soulreaper) > 0)
		{
			applied.add(value(soulreaper) + " soul" + (value(soulreaper) == 1 ? "" : "s"));
		}

		return applied.isEmpty() ? "" : "<html>Assumes " + String.join(", ", applied) + "</html>";
	}

	private static void addIfSet(List<String> applied, int count, String name)
	{
		if (count > 0)
		{
			applied.add(count + "x " + name);
		}
	}

	private static String styleName(BisOptimizer.Result result)
	{
		switch (result.getStyleGroup())
		{
			case MELEE:
				return "Melee";
			case RANGED:
				return "Ranged";
			default:
				return "Magic";
		}
	}

	/** "Stab", "Slash" or "Crush" for a melee result's attack style. */
	private static String meleeStyleName(BisOptimizer.Result result)
	{
		String type = result.getStyle().getType().name();
		return type.charAt(0) + type.substring(1).toLowerCase();
	}

	/** A scrollable body panel holding one setup's numbers and gear. */
	private JPanel bodyFor(BisOptimizer.Result result)
	{
		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBackground(ColorScheme.DARK_GRAY_COLOR);
		body.setBorder(BorderFactory.createEmptyBorder(6, 2, 2, 2));
		fill(body, result);
		return body;
	}

	/** The numbers and the gear for one style's setup. */
	private void fill(JPanel body, BisOptimizer.Result result)
	{
		JPanel stats = new JPanel(new GridLayout(0, 2, 4, 2));
		stats.setAlignmentX(Component.LEFT_ALIGNMENT);
		stats.setBackground(ColorScheme.DARK_GRAY_COLOR);

		addNumber(stats, "DPS", String.format("%.2f", result.getDps()));
		addNumber(stats, "Max hit", Integer.toString(result.getMaxHit()));
		addNumber(stats, "Accuracy", String.format("%.1f%%", result.getAccuracy() * 100));
		addNumber(stats, "Style", result.getStyle().getName());
		addNumber(stats, "Prayer bonus", Integer.toString(result.getPrayerBonus()));

		Player player = result.getPlayer();
		if (player.getSpell() != null)
		{
			addNumber(stats, "Spell", player.getSpell().getName());
		}

		body.add(stats);
		body.add(Box.createVerticalStrut(4));
		body.add(equipmentGrid(player));

		// The grid is sprites only, so the weapon - the one slot worth reading without hovering - is named.
		EquipmentPiece weapon = player.getEquipment().getWeapon();
		if (weapon != null)
		{
			body.add(row(weapon.getName()));
		}

		body.add(Box.createVerticalStrut(4));
		body.add(assumedRow(result.getPrayer() == null ? "No prayer" : result.getPrayer().getPrayerName(),
			result.getPrayer() == null ? null : plugin.prayerIcon(result.getPrayer())));
		body.add(assumedRow(result.getPotion().getPotionName(), plugin.potionIcon(result.getPotion())));

		// The DPS is calculated against a target whose defence has already been hammered down by whatever is
		// set in the advanced options. Say so, or the number reads as if the boss were at full defence.
		String reductions = describeReductions();
		if (!reductions.isEmpty())
		{
			body.add(assumedRow(reductions, null));
		}

		// The special attack is extra damage on top of the DPS above, not instead of it: the calculator
		// prices it by how fast the energy comes back.
		BisOptimizer.Spec spec = result.getSpec();
		if (spec != null)
		{
			JPanel specStats = new JPanel(new GridLayout(0, 2, 4, 2));
			specStats.setAlignmentX(Component.LEFT_ALIGNMENT);
			specStats.setBackground(ColorScheme.DARK_GRAY_COLOR);

			addNumber(specStats, "Spec max", Integer.toString(spec.getMaxHit()));
			addNumber(specStats, "Spec acc", String.format("%.1f%%", spec.getAccuracy() * 100));
			addNumber(specStats, "Spec dps", String.format("+%.2f", spec.getDps()));
			addNumber(specStats, "Spec cost", spec.getCost() + "%");

			body.add(Box.createVerticalStrut(4));
			body.add(specStats);
		}
	}

	void setNoResult(String reason)
	{
		clearResult();
		headline.setText("");
		addNote(reason);
		revalidate();
		repaint();
	}

	void setOwned(int ownedItems, long bankUpdatedAt)
	{
		if (bankUpdatedAt == 0)
		{
			bankSummary.setText("<html>Open your bank once so BiS Gear<br>can see what you own.</html>");
			return;
		}

		bankSummary.setText("<html>" + ownedItems + " items owned"
			+ "<br>Bank seen " + ago(bankUpdatedAt) + "</html>");
	}

	private void clearResult()
	{
		headline.setText("");
		numbers.removeAll();
		setup.removeAll();
		numbers.revalidate();
		numbers.repaint();
		setup.revalidate();
		setup.repaint();
	}

	/** Shows or hides the monster list. When shown it is refreshed to match the current query first. */
	private void showResults(boolean show)
	{
		if (show)
		{
			refreshResults();
		}
		resultScroll.setVisible(show);
		resultScroll.revalidate();
		resultScroll.repaint();
	}

	/** With no query the list shows every monster; names that start with the query rank first. */
	private void refreshResults()
	{
		String query = search.getText().trim().toLowerCase(Locale.ROOT);

		List<Monster> matches = new ArrayList<>();
		for (Monster monster : monsters)
		{
			if (monster.getName() == null)
			{
				continue;
			}
			if (query.isEmpty() || monster.getName().toLowerCase(Locale.ROOT).contains(query))
			{
				matches.add(monster);
			}
		}

		matches.sort(Comparator
			.comparing((Monster m) -> !m.getName().toLowerCase(Locale.ROOT).startsWith(query))
			.thenComparing(Monster::getName));

		resultModel.clear();
		List<Monster> shown = matches.stream().limit(MAX_RESULTS).collect(java.util.stream.Collectors.toList());
		shown.forEach(resultModel::addElement);

		// The names appear instantly; the pictures are fetched only once the player stops typing, so a burst
		// of keystrokes does not queue a download for every list it passed through on the way.
		pending = shown;
		thumbnailDebounce.restart();
	}

	void thumbnailsArrived()
	{
		results.repaint();
	}

	private void addNumber(JPanel stats, String name, String value)
	{
		JLabel key = new JLabel(name);
		key.setFont(FontManager.getRunescapeSmallFont());
		key.setForeground(ColorScheme.LIGHT_GRAY_COLOR);

		JLabel val = new JLabel(value);
		val.setFont(FontManager.getRunescapeSmallFont());
		val.setForeground(ColorScheme.BRAND_ORANGE);
		val.setHorizontalAlignment(SwingConstants.RIGHT);

		stats.add(key);
		stats.add(val);
	}

	/**
	 * The setup drawn the way the worn-equipment tab draws it, rather than as eleven stacked rows: the
	 * sidebar is narrow, and the shape of the grid is what a player already reads at a glance.
	 *
	 * <pre>
	 *        head
	 *  cape  neck  ammo
	 * weapon body  shield
	 *        legs
	 * hands  feet  ring
	 * </pre>
	 *
	 * Hover a square for the item's name; right-click it to stop it being suggested.
	 */
	private JPanel equipmentGrid(Player player)
	{
		String[][] layout = {
			{null, "head", null},
			{"cape", "neck", "ammo"},
			{"weapon", "body", "shield"},
			{null, "legs", null},
			{"hands", "feet", "ring"},
		};

		Map<String, EquipmentPiece> worn = player.getEquipment().bySlot();

		JPanel grid = new JPanel(new GridLayout(layout.length, 3, 2, 2));
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		grid.setBackground(ColorScheme.DARK_GRAY_COLOR);
		grid.setMaximumSize(new Dimension(Integer.MAX_VALUE, layout.length * (SLOT_SIZE + 2)));

		for (String[] row : layout)
		{
			for (String slot : row)
			{
				grid.add(slot == null ? blank() : square(slot, worn.get(slot)));
			}
		}

		return grid;
	}

	private JLabel blank()
	{
		JLabel label = new JLabel();
		label.setOpaque(false);
		return label;
	}

	/** One equipment square: the item's in-game sprite, or an empty slot. */
	private JLabel square(String slot, EquipmentPiece piece)
	{
		JLabel label = new JLabel();
		label.setOpaque(true);
		label.setHorizontalAlignment(SwingConstants.CENTER);
		label.setVerticalAlignment(SwingConstants.CENTER);
		label.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		label.setBorder(BorderFactory.createLineBorder(ColorScheme.DARK_GRAY_COLOR));
		label.setPreferredSize(new Dimension(SLOT_SIZE, SLOT_SIZE));

		if (piece == null)
		{
			label.setToolTipText("No " + slot);
			return label;
		}

		// A blowpipe's darts are loaded into the weapon rather than worn in the ammo slot, so the tooltip is
		// the only place they can be seen.
		String name = piece.getName();
		if (piece.getItemVars() != null && piece.getItemVars().getBlowpipeDartName() != null)
		{
			name += " (" + piece.getItemVars().getBlowpipeDartName() + "s)";
		}

		label.setToolTipText(name);

		AsyncBufferedImage sprite = itemManager.getImage(piece.getId());
		sprite.addTo(label);

		addExcludeMenu(label, piece);
		return label;
	}

	private JLabel assumedRow(String name, BufferedImage icon)
	{
		JLabel label = row(name);
		label.setToolTipText("Assumed by the DPS figure");
		if (icon != null)
		{
			label.setIcon(new ImageIcon(icon));
		}
		return label;
	}

	private void addNote(String text)
	{
		JLabel label = row("<html>" + text + "</html>");
		setup.add(label);
	}

	private static JLabel row(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		label.setBorder(BorderFactory.createEmptyBorder(1, 0, 1, 0));
		label.setIconTextGap(6);
		return label;
	}

	void setMonsterImage(BufferedImage image)
	{
		monsterImage.setIcon(image == null ? null : new ImageIcon(image));
	}

	/** The wiki data has one entry per version, so the version disambiguates same-named monsters. */
	private static String label(Monster monster)
	{
		String version = monster.getVersion();
		return version == null || version.isEmpty()
			? monster.getName()
			: monster.getName() + " (" + version + ")";
	}

	private static JLabel header(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(ColorScheme.BRAND_ORANGE);
		label.setAlignmentX(Component.LEFT_ALIGNMENT);
		return label;
	}

	private static String titleCase(String name)
	{
		if (name == null || name.isEmpty())
		{
			return "";
		}
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	private static String ago(long epochMillis)
	{
		Duration age = Duration.between(Instant.ofEpochMilli(epochMillis), Instant.now());
		if (age.toHours() < 1)
		{
			return "just now";
		}
		if (age.toDays() < 1)
		{
			return age.toHours() + "h ago";
		}
		return age.toDays() + "d ago";
	}
}
