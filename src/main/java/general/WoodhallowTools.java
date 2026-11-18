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
	private final JCheckBox checkBoxRatingsExport;
	// Cursed Isles forage timer (Thalassa's), merc plank warning (PP_Timer's), and the rumble minute warning.
	private final JCheckBox checkBoxForageTimer, checkBoxForageSound, checkBoxPlankSound, checkBoxRumbleSound;
	// The row next to the Scan button: the running timer on the left, the shorthand (or wolves left) on the right.
	private final JCheckBox checkBoxScanRow;
	private final JLabel scanTimer = new JLabel(" "), scanShorthand = new JLabel(" ");
	private long dnavEnds;
	static final long FORAGE_WARNING_MS = 30_000;
	static final long PLANK_REPEAT_MS = 6500;   // PP_Timer's gap between "plank swabbie" voice lines
	private long forageWarnedFor, plankPlayedAt, rumbleWarnedMinute = -1;
	private Color shorthandColor;
	private static final Color WARNING_RED = new Color(0xe03030);

	/** Whether timed Woodhallow periods are appended to dr_ratings.jsonl in the data folder. */
	boolean exportRatings() { return scorePerMinute() && checkBoxRatingsExport.isSelected(); }

	/** Master switch for all score-per-minute work (timing, rates in run files, dr_ratings.jsonl, /ve rates). */
	private final JCheckBox checkBoxScorePerMinute;
	boolean scorePerMinute() { return checkBoxScorePerMinute.isSelected(); }
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

		checkBoxScorePerMinute = option(section, prefs, "Score per minute", "scoreperminute", false);
		checkBoxScorePerMinute.setToolTipText("Time Woodhallow duty reports from the chat log and your own game log, and show score per minute");
		JCheckBox sceneTiming = option(section, prefs, "Scene-wide score per minute", "scenescoreperminute", false);
		sceneTiming.setToolTipText("Also time other players' station switches by reading the island scene. Enable after developer approval.");
		Runnable applySceneTiming = () -> {
			sceneTiming.setEnabled(checkBoxScorePerMinute.isSelected());
			tracker.setSceneTrackingEnabled(checkBoxScorePerMinute.isSelected() && sceneTiming.isSelected());
		};
		checkBoxScorePerMinute.addActionListener(ev -> applySceneTiming.run());
		sceneTiming.addActionListener(ev -> applySceneTiming.run());
		applySceneTiming.run();
		chatOnly = option(section, prefs, "Wolf count from chat only", "chatonlywolfcount", false);
		chatOnly.setToolTipText("Use the chat log for the remaining wolf count. Untick to use the selected, visible Ahoy tab.");
		chatOnly.addItemListener(ev -> wolfCount.clearRemaining());
		checkBoxRumbleBoard = option(section, prefs, "Rumble board", "rumbleboard", true);
		checkBoxRumbleShorthand = option(section, prefs, "Rumble shorthand", "rumbleshorthand", true);
		checkBoxClearSound = option(section, prefs, "Last wolf sound", "clearsound", true);
		checkBoxWarningSound = option(section, prefs, "30-second warning", "warningsound", true);
		checkBoxRumbleSound = option(section, prefs, "Rumble: warning 5 s before punch sizes change", "rumblesound", true);
		checkBoxRumbleSound.setToolTipText("Any rumble: a warning sound 5 seconds before each minute, in case you want to save your punch");
		checkBoxForageTimer = option(section, prefs, "Cursed Isles: forage timer", "foragetimer", true);
		checkBoxForageTimer.setToolTipText("Time left to forage, in a Forage tab; red in the last 30 seconds");
		checkBoxForageSound = option(section, prefs, "Cursed Isles: 30 s left to forage sound", "foragesound", true);
		checkBoxForageSound.setToolTipText("Three pulses at 30 seconds left to forage");
		checkBoxPlankSound = option(section, prefs, "Cursed Isles: plank merc voice line", "planksound", true);
		checkBoxPlankSound.setToolTipText("\"Plank swabbie\" every few seconds after the forage while more pirates and mercs are aboard than the next fray takes");
		// Chat log: mercs and pirates leaving the vessel, for the plank check.
		clock.setOnLiveMessage(rumbleClock.plank()::chat);
		checkBoxRatingsExport = option(section, prefs, "Export dr_ratings.jsonl", "drratingsexport", true);
		checkBoxRatingsExport.setToolTipText("Append each timed Woodhallow period's ratings to dr_ratings.jsonl in the data folder");
		checkBoxScanRow = option(section, prefs, "Timers next to the Scan button", "scanrowtimers", true);
		checkBoxScanRow.setToolTipText("Rumble, forage, vampire lair and Dnav station timers beside the Scan button, with the shorthand or wolves left on the right");
		addVolume(section, prefs);
		createDnavTab();
		createRumbleTab();
		Font font = scanTimer.getFont();
		scanTimer.setFont(font.deriveFont(Font.BOLD, font.getSize2D() * 1.3f));
		scanShorthand.setFont(font.deriveFont(Font.BOLD, font.getSize2D() * 1.3f));
	}

	/** Adds the timer and the shorthand beside it ("1:00 (15-16)"), right-aligned in the Scan button's row. */
	void addToScanRow(JPanel row) {
		row.add(Box.createHorizontalGlue());
		row.add(scanTimer);
		row.add(Box.createHorizontalStrut(5));
		row.add(scanShorthand);
		row.add(Box.createHorizontalStrut(6));
	}

	private static String clock(long seconds) { return String.format("%d:%02d", seconds / 60, seconds % 60); }

	/**
	 * The Scan row, every tick: the plank warning, else the rumble timer and its shorthand, else the forage
	 * timer (and a merc to plank), else the vampire lair round, else the Dnav stations and wolves left.
	 */
	private void updateScanRow(long now, long elapsed, long forageLeft, int toPlank, boolean plankAlert) {
		String left = " ", right = " ";
		Color leftColor = labelWolvesLeftColor, rightColor = labelWolvesLeftColor;
		long lairLeft = stationClock.lairTimerEnd() - now;
		if (!checkBoxScanRow.isSelected()) {
			// Nothing.
		} else if (plankAlert) {
			left = plankText(toPlank);
			leftColor = WARNING_RED;
		} else if (elapsed >= 0) {
			long s = elapsed / 1000;
			left = clock(s);
			if (s % 60 >= 55) leftColor = WARNING_RED;
			if (checkBoxRumbleShorthand.isSelected()) {
				boolean maxDamage = stationClock.inDen() && stationClock.elitesLeft() == 0;
				right = "(" + RumbleBoard.shorthand((int) (elapsed / 60_000), maxDamage) + ")";
			}
		} else if (forageLeft >= 0 && checkBoxForageTimer.isSelected()) {
			left = clock((forageLeft + 999) / 1000);
			if (forageLeft <= FORAGE_WARNING_MS) leftColor = WARNING_RED;
			if (toPlank > 0) {
				right = plankText(toPlank);
				rightColor = WARNING_RED;
			}
		} else if (lairLeft > 0 && lairLeft <= StationClock.LAIR_ROUND_AFTER_FRAY_MS) {
			left = clock((lairLeft + 999) / 1000);
			if (lairLeft <= 15_000) leftColor = WARNING_RED;
		} else if (dnavEnds > now) {
			left = clock((dnavEnds - now + 999) / 1000);
			right = labelWolvesLeft.getText();
			rightColor = labelWolvesLeft.getForeground();
		}
		if (!left.equals(scanTimer.getText())) scanTimer.setText(left);
		scanTimer.setForeground(leftColor);
		if (!right.equals(scanShorthand.getText())) scanShorthand.setText(right);
		scanShorthand.setForeground(rightColor);
	}

	private static String plankText(int toPlank) { return "Plank " + toPlank + (toPlank == 1 ? " merc!" : " mercs!"); }

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

	/** When the client showed the latest Cursed Isles forage period's duty report (game log), 0 if none. For auto scan. */
	long lastCiReport() { return rumbleClock.lastCiReport(); }

	/** When the client last showed a duty report no new game followed (game log), 0 if none. For auto scan. */
	long lastFinalReport() { return rumbleClock.lastFinalReport(); }

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
		dnavEnds = inStations ? ends : 0;
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
		shorthandColor = labelRumbleShorthand.getForeground();
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
		long forageLeft = rumbleClock.forageLeft(now);
		playTimerSounds(now, elapsed, forageLeft);
		// Cursed Isles: more pirates + mercs aboard than the next fray takes. Shown during the forage;
		// after it, until the next fray starts, it fills the tab and the voice line repeats.
		MercPlank plank = rumbleClock.plank();
		int toPlank = elapsed < 0 && plank.betweenFrays() ? plank.toPlank() : 0;
		boolean plankAlert = toPlank > 0 && forageLeft < 0;
		if (plankAlert && checkBoxPlankSound.isSelected() && now - plankPlayedAt >= PLANK_REPEAT_MS) {
			plankPlayedAt = now;
			Sounds.play(Sounds.PLANK);
		}
		String plankText = plankText(toPlank);
		updateScanRow(now, elapsed, forageLeft, toPlank, plankAlert);

		boolean board = elapsed >= 0 && checkBoxRumbleBoard.isSelected();
		boolean shorthand = elapsed >= 0 && checkBoxRumbleShorthand.isSelected();
		boolean foraging = elapsed < 0 && forageLeft >= 0 && checkBoxForageTimer.isSelected();
		// The Rumble tab is the board: with the board off, the timer and shorthand are on the Scan row only.
		boolean show = board || foraging || plankAlert;
		String title = plankAlert ? "Plank!" : foraging ? "Forage" : "Rumble";
		int index = tabbedPane.indexOfComponent(panelRumble);
		if (show && index < 0) {
			lastForecastTick = 0;
			tabBeforeRumble = userTab();
			tabbedPane.addTab(title, panelRumble);
			tabbedPane.setSelectedComponent(panelRumble);
		} else if (show && !title.equals(tabbedPane.getTitleAt(index))) {
			tabbedPane.setTitleAt(index, title);
		} else if (!show && index >= 0) {
			removeAutoTab(panelRumble, tabBeforeRumble);
		}
		if (!show) return;
		if (plankAlert || foraging) {
			rumbleSecond = -1;
			rumbleGroups = null;
			rumbleBoard.setVisible(false);
			rumbleForecast.setVisible(false);
			if (plankAlert) {
				labelRumble.setText(plankText);
				labelRumble.setForeground(WARNING_RED);
				labelRumble.setToolTipText(plank.pirates() + " pirates and " + plank.mercs() + " mercs aboard; the next fray takes "
						+ (plank.pirates() + plank.mercs() - toPlank));
				labelRumbleShorthand.setVisible(false);
			} else {
				// Time left to forage, counting down from 2:00; red in the last 30 seconds. A merc that will
				// need planking once it's over shows on the right.
				long s = (forageLeft + 999) / 1000;
				labelRumble.setText(String.format("%d:%02d", s / 60, s % 60));
				labelRumble.setForeground(forageLeft <= FORAGE_WARNING_MS ? WARNING_RED : labelWolvesLeftColor);
				labelRumble.setToolTipText("Time left to forage; red in the last 30 seconds");
				labelRumbleShorthand.setVisible(toPlank > 0);
				labelRumbleShorthand.setText(plankText);
				labelRumbleShorthand.setForeground(WARNING_RED);
			}
			return;
		}
		labelRumble.setToolTipText("Time into the rumble; red in the last 5 seconds before the punch sizes change");
		labelRumbleShorthand.setForeground(shorthandColor);

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
				labelRumbleShorthand.setText(groups);
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

	/**
	 * The rumble's minute warning (5 s before the punch sizes change, once a minute) and the Cursed Isles
	 * forage warning (30 s left, once a period), through the same separate-process player as the rest.
	 */
	private void playTimerSounds(long now, long rumbleElapsed, long forageLeft) {
		if (rumbleElapsed < 0) rumbleWarnedMinute = -1;
		else {
			long sec = rumbleElapsed / 1000, minute = sec / 60;
			if (60 - sec % 60 <= 5 && rumbleWarnedMinute != minute) {
				rumbleWarnedMinute = minute;
				if (checkBoxRumbleSound.isSelected()) Sounds.play(Sounds.RUMBLE_WARNING);
			}
		}
		long forageEnd = rumbleClock.forageEndsAt();
		if (forageLeft >= 0 && forageLeft <= FORAGE_WARNING_MS && forageWarnedFor != forageEnd) {
			forageWarnedFor = forageEnd;
			if (checkBoxForageSound.isSelected()) Sounds.play(Sounds.THIRTY_SECONDS);
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
