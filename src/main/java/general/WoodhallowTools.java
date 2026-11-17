package main.java.general;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import javax.swing.*;

/** Gizmo's temporary gameplay tabs and their settings. Swing state stays on the event thread. */
final class WoodhallowTools {
	private final Window game;
	private final JTabbedPane tabbedPane;
	private final StationClock stationClock;
	private final ActivityTracker activityTracker;
	private final RumbleClock rumbleClock = new RumbleClock();
	private WolfCount wolfCount = new WolfCount();
	private final JCheckBox chatOnly, checkBoxClearSound, checkBoxWarningSound;
	private final JCheckBox checkBoxRumbleBoard, checkBoxRumbleShorthand;
	private JPanel panelDnav, panelRumble, ahoyRow, ahoyButtons;
	private JLabel labelWolvesLeft, labelRumble, labelRumbleShorthand;
	private JTextArea labelStrategy;
	private Color labelWolvesLeftColor;
	private RumbleBoard rumbleBoard;
	private DnavPanel rumbleForecast;
	private Component tabBeforeDnav, tabBeforeRumble;

	private long clearPlayedFor, warningPlayedFor, dnavPeriodStart = Long.MIN_VALUE;
	private long lastDnavTick, lastForecastTick;
	private long rumbleSecond = -1;
	private String rumbleGroups;
	private Timer timer;
	private java.util.concurrent.ScheduledExecutorService files;
	private int manualStartingWolves = -1;
	private List<Integer> ahoyChoices = List.of();
	private DRGroupData lastReport;
	private ActivityTracker.Period reportedPeriod;

	WoodhallowTools(Window game, JTabbedPane tabs, Preferences prefs, StationClock clock, ActivityTracker tracker,
					JPanel section) {
		this.game = game;
		tabbedPane = tabs;
		stationClock = clock;
		activityTracker = tracker;

		JCheckBox sceneTiming = option(section, prefs, "Scene-wide score per minute", "scenescoreperminute", false);
		sceneTiming.setToolTipText("Time other players' station switches at Woodhallow. Enable after developer approval.");
		sceneTiming.addActionListener(ev -> tracker.setSceneTrackingEnabled(sceneTiming.isSelected()));
		tracker.setSceneTrackingEnabled(sceneTiming.isSelected());
		chatOnly = option(section, prefs, "Wolf count from chat only", "chatonlywolfcount", false);
		chatOnly.setToolTipText("Use the chat log for the remaining wolf count. Untick to use the selected, visible Ahoy tab.");
		chatOnly.addItemListener(ev -> wolfCount.clearRemaining());
		checkBoxRumbleBoard = option(section, prefs, "Rumble board", "rumbleboard", true);
		checkBoxRumbleShorthand = option(section, prefs, "Rumble shorthand", "rumbleshorthand", true);
		checkBoxClearSound = option(section, prefs, "Last wolf sound", "clearsound", true);
		checkBoxWarningSound = option(section, prefs, "30-second warning", "warningsound", true);
		addVolume(section, prefs);
		createDnavTab();
		createRumbleTab();
	}

	private static JCheckBox option(JPanel section, Preferences prefs, String label, String key, boolean defaultValue) {
		JCheckBox option = new JCheckBox(label, prefs.getBoolean(key, defaultValue));
		option.addActionListener(ev -> prefs.putBoolean(key, option.isSelected()));
		section.add(option);
		return option;
	}

	private static void addVolume(JPanel section, Preferences prefs) {
		Sounds.setVolume(prefs.getInt("soundvolume", 100));
		JLabel label = new JLabel("Volume " + Sounds.volume() + "%");
		JSlider slider = new JSlider(0, 100, Sounds.volume());
		label.setLabelFor(slider);
		slider.setToolTipText("Gizmo sound volume. Plays a sample after adjustment.");
		slider.addChangeListener(ev -> {
			Sounds.setVolume(slider.getValue());
			label.setText("Volume " + slider.getValue() + "%");
			if (!slider.getValueIsAdjusting()) {
				prefs.putInt("soundvolume", slider.getValue());
				Sounds.play(Sounds.CLEAR);
			}
		});
		JPanel row = new JPanel(new BorderLayout(5, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(label, BorderLayout.WEST);
		row.add(slider, BorderLayout.CENTER);
		section.add(row);
	}

	void start() {
		rumbleClock.setPuzzleListener((object, at) ->
				SwingUtilities.invokeLater(() -> activityTracker.ownPuzzle(object, at)));
		files = Executors.newSingleThreadScheduledExecutor(task -> {
			Thread thread = new Thread(task, "Gizmo puzzle log");
			thread.setDaemon(true);
			return thread;
		});
		files.scheduleWithFixedDelay(() -> rumbleClock.poll(System.currentTimeMillis()), 0, 250, TimeUnit.MILLISECONDS);
		timer = new Timer(250, ev -> {
			long now = System.currentTimeMillis();
			updateRumble(now);
			if (now - lastDnavTick >= 1000) {
				lastDnavTick = now;
				playWerewolfSounds(now);
				updateDnav(now);
			}
		});
		timer.start();
	}

	void stop() {
		if (timer != null) timer.stop();
		if (files != null) files.shutdownNow();
	}

	void scanned(DRGroupData report, ActivityTracker.Period period) {
		lastReport = report;
		reportedPeriod = period;
	}

	private void createDnavTab() {
		panelDnav = new JPanel();
		panelDnav.setLayout(new BoxLayout(panelDnav, BoxLayout.Y_AXIS));
		panelDnav.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
		labelWolvesLeft = new JLabel(" ");
		labelWolvesLeft.setToolTipText("Choose the wolf count source under Options > Woodhallow & Rumble");
		labelWolvesLeft.setFont(labelWolvesLeft.getFont().deriveFont(Font.BOLD, 20f));
		labelWolvesLeft.setAlignmentX(Component.LEFT_ALIGNMENT);
		panelDnav.add(labelWolvesLeft);
		panelDnav.add(Box.createVerticalStrut(6));
		// A read-only text area rather than a label so the strategy wraps in a narrow window.
		labelStrategy = new JTextArea(" ") {
			// Full width, but only as tall as its wrapped lines (BoxLayout would otherwise stretch it).
			@Override public Dimension getMaximumSize() {
				return new Dimension(Integer.MAX_VALUE, getPreferredSize().height);
			}
		};
		labelStrategy.setEditable(false);
		labelStrategy.setFocusable(false);
		labelStrategy.setLineWrap(true);
		labelStrategy.setWrapStyleWord(true);
		labelStrategy.setOpaque(false);
		labelStrategy.setBorder(null);
		labelStrategy.setFont(labelWolvesLeft.getFont().deriveFont(Font.PLAIN, 15f));
		labelStrategy.setAlignmentX(Component.LEFT_ALIGNMENT);
		labelStrategy.setColumns(1);   // never asks for more width than the window has
		// Its wrapped height depends on its width, so lay out again whenever the width changes.
		labelStrategy.addComponentListener(new java.awt.event.ComponentAdapter() {
			private int lastWidth = -1;
			@Override public void componentResized(java.awt.event.ComponentEvent e) {
				if (labelStrategy.getWidth() == lastWidth) return;
				lastWidth = labelStrategy.getWidth();
				SwingUtilities.invokeLater(() -> { labelStrategy.invalidate(); panelDnav.revalidate(); });
			}
		});
		panelDnav.add(labelStrategy);

		labelWolvesLeftColor = labelWolvesLeft.getForeground();
		// The starting count can be confirmed independently of the number remaining.
		ahoyRow = new JPanel();
		ahoyRow.setLayout(new BoxLayout(ahoyRow, BoxLayout.X_AXIS));
		ahoyRow.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel ahoyLabel = new JLabel("Starting wolves: ");
		ahoyLabel.setToolTipText("Click the werewolf count the Ahoy tab shows, for the exact strategy and pace");
		ahoyRow.add(ahoyLabel);
		ahoyButtons = new JPanel();
		ahoyButtons.setLayout(new BoxLayout(ahoyButtons, BoxLayout.X_AXIS));
		ahoyRow.add(ahoyButtons);
		panelDnav.add(Box.createVerticalStrut(6));
		panelDnav.add(ahoyRow);
	}

	private void updateDnav(long now) {
		StationClock.Period period = stationClock.current();
		long ends = stationClock.expectedEnd(period);
		boolean inStations = period != null && stationClock.inDen() && !period.lair && ends > now && now - period.start < 360_000;
		setDnavTab(inStations);
		if (!inStations) return;
		if (dnavPeriodStart != period.start) {
			dnavPeriodStart = period.start;
			manualStartingWolves = -1;
			ahoyChoices = List.of();
			wolfCount = new WolfCount();
		}
		StationClock.WolfProgress chat = stationClock.wolfProgress(period);
		if (chat == null) return;
		int half = chat.atHalf();
		Map<Integer, Double> odds = half < 0
				? DnavForecast.werewolfOdds(Math.max(1, stationClock.crew()), stationClock.frayNumber()) : Map.of();
		List<Integer> choices = half < 0 ? odds.keySet().stream().sorted().toList() : List.of(2 * half, 2 * half + 1);
		WolfCount.Reading reading = wolfCount.read(chatOnly.isSelected(), () -> AhoyWolfCount.read(game),
				chat, period.start, now, choices);

		ahoyRow.setVisible(reading.starting() < 0);
		if (ahoyRow.isVisible()) updateStartingChoices(choices);
		int startingWolves = reading.starting() >= 0 ? reading.starting() : manualStartingWolves;
		String strategy = startingWolves >= 0 ? nz(SoloNavStrategy.forWerewolves(startingWolves))
				: half >= 0 ? twoStrategies(2 * half, 2 * half + 1) : strategiesWithOdds(odds);
		if (!("Solo: " + strategy).equals(labelStrategy.getText())) {
			labelStrategy.setText("Solo: " + strategy);
			panelDnav.revalidate();
		}
		showPace(reading, startingWolves, half, period.start, ends);
	}

	private void updateStartingChoices(List<Integer> choices) {
		if (choices.equals(ahoyChoices)) return;
		ahoyChoices = choices;
		ahoyButtons.removeAll();
		ButtonGroup group = new ButtonGroup();
		for (int count : choices) {
			JToggleButton button = new JToggleButton(String.valueOf(count), count == manualStartingWolves);
			button.setMargin(new Insets(1, 8, 1, 8));
			button.setToolTipText("Confirm the starting count for the solo strategy and pace");
			button.addActionListener(ev -> manualStartingWolves = count);
			group.add(button);
			ahoyButtons.add(button);
			ahoyButtons.add(Box.createHorizontalStrut(4));
		}
		ahoyButtons.revalidate();
		ahoyButtons.repaint();
	}

	private void showPace(WolfCount.Reading reading, int startingWolves, int half, long start, long ends) {
		int left = reading.remaining();
		String text;
		boolean late = false;
		if (left < 0) {
			text = chatOnly.isSelected() ? "Waiting for wolf count in chat" : "Open Ahoy for wolf count";
		} else if (left == 0) {
			text = "Wolves cleared";
		} else {
			text = "Wolves " + left;
			int initial = startingWolves >= 0 ? startingWolves : 2 * half;
			long since = reading.changedAt() - start;
			if (initial > left && since > 0) {
				long clearAt = reading.changedAt() + Math.round(left * (double) since / (initial - left));
				long margin = ends - clearAt;
				long seconds = Math.abs(margin) / 1000;
				late = margin < 0;
				text += String.format(" (%s%d:%02d)%s", late ? "-" : "+", seconds / 60, seconds % 60,
						startingWolves < 0 ? " ~" : "");
			}
		}
		labelWolvesLeft.setText(text);
		labelWolvesLeft.setForeground(late ? Color.RED : labelWolvesLeftColor);
	}

	private static String strategiesWithOdds(Map<Integer, Double> odds) {
		List<String> lines = new ArrayList<>();
		for (var entry : odds.entrySet())
			lines.add(String.format("%d: %s (%.0f%%)", entry.getKey(),
					nz(SoloNavStrategy.forWerewolves(entry.getKey())), entry.getValue() * 100));
		return String.join("\nor ", lines);
	}

	private void createRumbleTab() {
		labelRumble = new JLabel(" ");
		Font baseFont = labelRumble.getFont();
		Font bigFont = baseFont.deriveFont(Font.BOLD, baseFont.getSize2D() * 1.6f);
		Font timerFont = baseFont.deriveFont(Font.BOLD, baseFont.getSize2D() * 2.2f);
		// Timer on the left and the shorthand (recommended groups) on the right, both big, one line.
		labelRumble.setFont(timerFont);
		labelRumble.setToolTipText("Time into the rumble; red in the last 5 seconds before the punch sizes change");
		labelRumbleShorthand = new JLabel(" ");
		labelRumbleShorthand.setFont(bigFont);
		labelRumbleShorthand.setToolTipText("Recommended number of groups to charge this minute");
		JPanel rumbleTop = new JPanel(new BorderLayout(8, 0));
		rumbleTop.add(labelRumble, BorderLayout.WEST);
		rumbleTop.add(labelRumbleShorthand, BorderLayout.EAST);
		rumbleBoard = new RumbleBoard();
		panelRumble = new JPanel(new BorderLayout(0, 2));
		panelRumble.add(rumbleTop, BorderLayout.NORTH);
		panelRumble.add(rumbleBoard, BorderLayout.CENTER);
		// At Woodhallow: the next Dnav stations' forecast and solo strategy, under the board.
		rumbleForecast = new DnavPanel(baseFont);
		rumbleForecast.setVisible(false);
		panelRumble.add(rumbleForecast, BorderLayout.SOUTH);
	}

	private void updateRumble(long now) {
		long elapsed = rumbleClock.elapsed(now);
		boolean board = elapsed >= 0 && checkBoxRumbleBoard.isSelected();
		boolean shorthand = elapsed >= 0 && checkBoxRumbleShorthand.isSelected();
		boolean show = board || shorthand;
		int index = tabbedPane.indexOfComponent(panelRumble);
		if (show && index < 0) {
			lastForecastTick = 0;
			tabBeforeRumble = userTab();
			tabbedPane.addTab("Rumble", panelRumble);
			tabbedPane.setSelectedComponent(panelRumble);
		} else if (!show && index >= 0) {
			removeAutoTab(panelRumble, tabBeforeRumble);
		}
		if (!show) return;

		boolean woodhallow = stationClock.inDen();
		boolean maxDamage = woodhallow && stationClock.elitesLeft() == 0;
		int minute = (int) (elapsed / 60_000);
		rumbleBoard.setVisible(board);
		rumbleBoard.setMinute(minute);
		rumbleBoard.setWerewolfFight(woodhallow, maxDamage);
		long seconds = elapsed / 1000;
		if (seconds != rumbleSecond) {
			rumbleSecond = seconds;
			labelRumble.setText(String.format("%d:%02d", seconds / 60, seconds % 60));
			labelRumble.setForeground(seconds % 60 >= 55 ? new Color(0xe03030) : labelWolvesLeftColor);
		}
		labelRumbleShorthand.setVisible(shorthand);
		if (shorthand) {
			String groups = RumbleBoard.shorthand(minute, maxDamage);
			if (!groups.equals(rumbleGroups)) {
				rumbleGroups = groups;
				labelRumbleShorthand.setText(groups + " groups");
			}
		}
		if (now - lastForecastTick >= 1000) {
			lastForecastTick = now;
			rumbleForecast.setVisible(updateForecast());
		}
	}

	private static String nz(String strategy) { return strategy == null ? "not on the sheet" : strategy; }

	/** "A" if both counts share a strategy, else "A (n) or B (n+1)". */
	private static String twoStrategies(int a, int b) {
		String sa = nz(SoloNavStrategy.forWerewolves(a)), sb = nz(SoloNavStrategy.forWerewolves(b));
		return sa.equals(sb) ? sa : sa + " (" + a + ")\nor " + sb + " (" + b + ")";
	}

	private boolean updateForecast() {
		if (!stationClock.inDen()) return false;
		int fray = stationClock.frayNumber();
		if (fray < 1 || fray > 5) return false;   // the lair (6) has no Dnav after it
		int crew = stationClock.crew();
		if (crew <= 0) return false;              // known from the first "still standin'"
		int deaths = stationClock.frayDeaths();
		// Team pace: the navigators' werewolf points in the last duty report over the last station period
		// (assumes each navigator played for the whole station period).
		double team = 0;
		StationClock.Period lp = stationClock.lastEnded(System.currentTimeMillis());
		long len = reportedPeriod == null || lp == null || Math.abs(reportedPeriod.start - lp.start) > 5000
				? 0 : reportedPeriod.end - reportedPeriod.start;
		if (lastReport != null && len > 0 && lastReport.data.containsKey("Navigating"))
			for (DRIndividualData d : lastReport.data.get("Navigating").values())
				team += DnavForecast.navPoints(d.getTokens()) / (len / 60000.0);
		DnavForecast f = DnavForecast.during(fray, crew, deaths, team);
		// The game rounds the werewolf count at random by its fraction, so give both strategies with odds.
		rumbleForecast.show(f, DnavPanel.strategies(f.odds));
		return true;
	}

	/**
	 * Thalassa's Woodhallow alerts, from the chat log's own messages: a rising chime on "...turned back
	 * the last werewolf!", and three pulses on "The wolf-kin gather! 30 seconds!" (or the lair's).
	 */
	private void playWerewolfSounds(long now) {
		// Only live messages (the chat log is also read back at startup).
		long cleared = stationClock.lastWolfClearedAt(), thirty = stationClock.thirtySecondsAt();
		if (checkBoxClearSound.isSelected() && cleared > 0 && cleared != clearPlayedFor && now - cleared < 15_000) {
			clearPlayedFor = cleared;
			Sounds.play(Sounds.CLEAR);
		}
		if (checkBoxWarningSound.isSelected() && thirty > 0 && thirty != warningPlayedFor && now - thirty < 15_000) {
			warningPlayedFor = thirty;
			Sounds.play(Sounds.THIRTY_SECONDS);
		}
	}

	/** Adds and selects the Dnav tab during stations; removes it after, returning to the previous tab. */
	private void setDnavTab(boolean show) {
		int idx = tabbedPane.indexOfComponent(panelDnav);
		if (show && idx < 0) {
			tabBeforeDnav = userTab();
			tabbedPane.addTab("Dnav", panelDnav);
			tabbedPane.setSelectedComponent(panelDnav);
		} else if (!show && idx >= 0) {
			removeAutoTab(panelDnav, tabBeforeDnav);
		}
	}

	/**
	 * The tab the user chose: the selected one, or, if that is the Rumble or Dnav tab Gizmo
	 * opened, the tab it remembered from before. So when the Dnav tab opens while the Rumble tab is
	 * still up (stations start as the rumble ends), both go back to the user's tab, not to each other.
	 */
	private Component userTab() {
		Component c = tabbedPane.getSelectedComponent();
		if (c == panelRumble) return tabBeforeRumble;
		if (c == panelDnav) return tabBeforeDnav;
		return c;
	}

	/** Removes an automatic tab; if it was showing, goes back to the user's tab (or the first tab). */
	private void removeAutoTab(Component tab, Component back) {
		boolean wasOnIt = tabbedPane.getSelectedComponent() == tab;
		tabbedPane.remove(tab);
		if (!wasOnIt) return;
		if (back != null && tabbedPane.indexOfComponent(back) >= 0) tabbedPane.setSelectedComponent(back);
		else if (tabbedPane.getTabCount() > 0) tabbedPane.setSelectedIndex(0);
	}
}
