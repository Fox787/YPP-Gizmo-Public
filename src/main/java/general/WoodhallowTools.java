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
	/** The next pack's werewolves from the island itself, so Ahoy needn't be open; Ahoy is the fallback. */
	private final DenWolves denWolves = new DenWolves();
	private final JCheckBox checkBoxClearSound;
	/** The time-left sound matrix: [activity][0] = 30 s, [activity][1] = 15 s. */
	private final JCheckBox[][] warningSounds = new JCheckBox[TimeLeftWarnings.Activity.values().length][2];
	private final TimeLeftWarnings timeLeftWarnings = new TimeLeftWarnings();
	private final JCheckBox checkBoxRumbleBoard, checkBoxRumbleShorthand;
	// Cursed Isles forage timer (Thalassa's), merc plank warning (PP_Timer's), and the rumble minute warning.
	private final JCheckBox checkBoxPlankSound, checkBoxRumbleSound;
	// The row next to the Scan button: the running timer on the left, the shorthand (or wolves left) on the right.
	private final JCheckBox checkBoxExtendedDnav;
	/** Where the timers go: TIMERS_TOP, TIMERS_BOTTOM or TIMERS_BOTH (pref "timerplace", top by default). */
	private final JComboBox<String> comboTimerPlace;
	static final String TIMERS_TOP = "Top", TIMERS_BOTTOM = "Bottom", TIMERS_BOTH = "Top and bottom";
	private final JLabel scanTimer = fitted(), scanShorthand = fitted();

	/** A label no wider than its text, so the Scan row's glue keeps it on the right (HTML text would stretch). */
	private static JLabel fitted() {
		return new JLabel(" ") {
			@Override public Dimension getMaximumSize() { return getPreferredSize(); }
		};
	}
	private long dnavEnds;
	/** Woodhallow's current station end (any period, the lair one too), 0 when none is on. Set once a second. */
	private long denEnds;
	/** The client's "Next wave in" countdown, for denEnds. */
	private final DenWaveClock waveClock = new DenWaveClock();
	private long wavePeriodStart = Long.MIN_VALUE;
	static final long FORAGE_WARNING_MS = 30_000;
	static final long PLANK_REPEAT_MS = 6500;   // PP_Timer's gap between "plank swabbie" voice lines
	private long plankPlayedAt, rumbleWarnedMinute = -1;
	private Color shorthandColor;
	private static final Color WARNING_RED = new Color(0xe03030);
	/** The forage bar's red (last 30 seconds, plank alert): lighter, so its big text reads on the window's blue. */
	private static final Color FORAGE_RED = new Color(0xff6666);
	/** Above the tabs, whichever is open: the Cursed Isles forage countdown, then the plank alert. */
	private JPanel forageBar;
	private final JPanel timerArea = new JPanel();
	private final HomunculusCounter homunculi = new HomunculusCounter();
	private String selectedActivity = "";
	private long homunculiRound = -1;
	void setActivity(String activity) { selectedActivity = activity; }
	private JLabel labelForage, labelForageDetail;

	/** Master switch for all score-per-minute work (timing, rates in run files, dr_ratings.jsonl, /ve rates). */
	private final JCheckBox checkBoxScorePerMinute;
	boolean scorePerMinute() { return checkBoxScorePerMinute.isSelected(); }
	private JPanel panelDnav, panelRumble, ahoyRow, ahoyButtons;
	private JLabel labelWolvesLeft, labelRumble, labelRumbleShorthand;
	private JTextArea labelStrategy;
	private Color labelWolvesLeftColor;
	private RumbleBoard rumbleBoard;
	private DnavPanel rumbleForecast;
	private Component tabBeforeRumble;
	/** Between Score All and Scan: the Dnav box during stations, the Next Dnav forecast between them. */
	private final JPanel dnavArea = new JPanel() {
		// With nothing to show, a third of the Next Dnav box's height stays as a gap; otherwise what it holds.
		@Override public Dimension getPreferredSize() {
			Dimension d = super.getPreferredSize();
			if (rumbleForecast != null) d.height = Math.max(d.height, rumbleForecast.getPreferredSize().height / 3);
			return d;
		}
	};

	private long clearPlayedFor, dnavPeriodStart = Long.MIN_VALUE;
	private long lastDnavTick;
	private long rumbleSecond = -1;
	private String rumbleGroups;
	private Timer timer;
	private java.util.concurrent.ScheduledExecutorService files;
	private int manualStartingWolves = -1;
	private List<Integer> ahoyChoices = List.of();
	private DRGroupData lastReport;
	private ActivityTracker.Period reportedPeriod;
	/** What the Next Dnav box last showed, for the Dnav report. */
	private DnavForecast lastForecast;
	private Map<String, String> lastForecastLines = Map.of();
	/** One line per second during stations: what each wolf count source said (for the Dnav report). */
	private final java.util.ArrayDeque<String> wolfSamples = new java.util.ArrayDeque<>();
	private String lastDnavShown = "";

	WoodhallowTools(Window game, JTabbedPane tabs, Preferences prefs, StationClock clock, ActivityTracker tracker,
					JPanel woodhallow, JPanel rumble, JPanel sounds) {
		this.game = game;
		tabbedPane = tabs;
		stationClock = clock;
		activityTracker = tracker;

		checkBoxScorePerMinute = option(woodhallow, prefs, "Score/min", "scoreperminute", true);
		checkBoxScorePerMinute.setToolTipText("Time Woodhallow duty reports from the chat log and your own game log."
				+ " Select Score/min in Scan format to add rates to each pirate's row.");
		checkBoxClearSound = option(woodhallow, prefs, "Last wolf sound", "clearsound", true);
		checkBoxExtendedDnav = option(woodhallow, prefs, "Dnav strategies", "extendeddnav", false);
		checkBoxExtendedDnav.setToolTipText("Show the fastest solo lines under Next Dnav using the move times below. Blank times leave a move out.");
		JPanel times = new JPanel();
		times.setLayout(new BoxLayout(times, BoxLayout.Y_AXIS));
		times.setAlignmentX(Component.LEFT_ALIGNMENT);
		times.setBorder(BorderFactory.createEmptyBorder(2, 12, 0, 0));
		woodhallow.add(times);
		addSeconds(times, prefs, "Booch time (s)", "dnavboochsec", "9", v -> boochSeconds = v,
				"<html>Time to Booch before a strategy or a Donkey/Vegas. Default 9 s. Leave blank to exclude Booches.</html>");
		addSeconds(times, prefs, "Single time (s)", "dnavsinglesec", "12", v -> singleSeconds = v,
				"<html>How long a single takes at the very end of a solo line. Leave blank to plan without it.</html>");
		addSeconds(times, prefs, "Double time (s)", "dnavdoublesec", "", v -> doubleSeconds = v,
				"<html>How long you take to build a double (2 x the board). Leave blank to plan without doubles.</html>");
		addSeconds(times, prefs, "Triple time (s)", "dnavtriplesec", "40", v -> tripleSeconds = v,
				"<html>How long you take to build a triple (3 x the board). Leave blank to plan without triples.</html>");
		addSeconds(times, prefs, "Bingo time (s)", "dnavbingosec", "", v -> bingoSeconds = v,
				"<html>How long you take to build a bingo (4 x the board). Leave blank to plan without bingos.</html>");
		addSeconds(times, prefs, "Donkey time (s)", "dnavdonkeysec", "51", v -> donkeySeconds = v,
				"<html>How long the x5 takes after its Booch (Booch time is added separately).<br>"
				+ "A Booch-donkey scores 625 and leaves a 173 board, like a triple. Leave blank to plan without it.</html>");
		addSeconds(times, prefs, "Vegas time (s)", "dnavvegassec", "", v -> vegasSeconds = v,
				"<html>How long the x6 takes after its Booch (Booch time is added separately).<br>"
				+ "A Booch-Vegas scores 750 and leaves a 173 board. Leave blank to plan without it.</html>");
		OptionsPanel.showWhenSelected(checkBoxExtendedDnav, times);

		checkBoxRumbleBoard = option(rumble, prefs, "Rumble board", "rumbleboard", true);
		checkBoxRumbleShorthand = option(rumble, prefs, "Rumble shorthand", "rumbleshorthand", true);
		checkBoxRumbleSound = option(rumble, prefs, "55s warning", "rumblesound", true);
		checkBoxRumbleSound.setToolTipText("Any rumble: a warning sound 5 seconds before each minute, in case you want to save your punch");

		comboTimerPlace = timerPlace(sounds, prefs);
		addWarningMatrix(sounds, prefs);
		checkBoxPlankSound = option(sounds, prefs, "CI: Plank reminder", "planksound", true);
		checkBoxPlankSound.setToolTipText("\"Plank swabbie\" every few seconds after the forage while more pirates and mercs are aboard than the next fray takes");
		// Chat log: mercs and pirates leaving the vessel, for the plank check.
		clock.setOnLiveMessage(message -> {
			rumbleClock.plank().chat(message);
			rumbleClock.ciSwordfight().chat(message);
		});
		addVolume(sounds, prefs);
		createDnavTab();
		createRumbleTab();
		rumbleForecast.setExtended(checkBoxExtendedDnav.isSelected());
		checkBoxExtendedDnav.addActionListener(ev -> rumbleForecast.setExtended(checkBoxExtendedDnav.isSelected()));
		// About the Scan button's height (23 px at the usual 12 pt font).
		Font font = scanTimer.getFont();
		scanTimer.setFont(font.deriveFont(Font.BOLD, font.getSize2D() * 1.45f));
		scanShorthand.setFont(font.deriveFont(Font.BOLD, font.getSize2D() * 1.45f));
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
		if (!timersBottom()) {
			// Nothing.
		} else if (plankAlert) {
			left = plankText(toPlank);
			leftColor = WARNING_RED;
		} else if (elapsed >= 0) {
			long s = elapsed / 1000;
			left = clock(s);
			if (s % 60 >= 55) leftColor = WARNING_RED;
			if (checkBoxRumbleShorthand.isSelected()) {
				int minute = (int) (elapsed / 60_000);
				boolean maxDamage = stationClock.inDen() && stationClock.elitesLeft() == 0;
				// Woodhallow: the groups for an insta on a white name, then the range; elsewhere the range.
				right = stationClock.inDen() ? RumbleBoard.instaShorthand(minute, maxDamage)
						: "(" + RumbleBoard.shorthand(minute, maxDamage) + ")";
			}
		} else if (forageLeft >= 0) {
			// Its own option: the forage timer option is the bar above the tabs.
			left = clock((forageLeft + 999) / 1000);
			if (forageLeft <= FORAGE_WARNING_MS) leftColor = WARNING_RED;
			right = FORAGE_LABEL;
		} else if (lairRoundOn(lairLeft)) {
			left = clock((lairLeft + 999) / 1000);
			if (lairLeft <= LAIR_WARNING_MS) leftColor = WARNING_RED;
			right = LAIR_ROUND_LABEL;
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

	static final String FORAGE_LABEL = "Forage", LAIR_ROUND_LABEL = "Carp/Treasure Haul";
	/** The lair round turns red in its last 15 seconds (the chat's coffin warning). */
	private static final long LAIR_WARNING_MS = 15_000;

	/** The vampire lair's carpentry/treasure haul round after a fray is on. */
	private static boolean lairRoundOn(long lairLeft) {
		return lairLeft > 0 && lairLeft <= StationClock.LAIR_ROUND_AFTER_FRAY_MS;
	}

	private static String plankText(int toPlank) { return "Plank " + toPlank + (toPlank == 1 ? " merc!" : " mercs!"); }

	/**
	 * The timers' place: the bar above the tabs (top), the Scan button's row (bottom), or both. Covers the
	 * rumble, forage, Woodhallow station and vampire lair carpentry/treasure haul timers.
	 */
	private static JComboBox<String> timerPlace(JPanel section, Preferences prefs) {
		JComboBox<String> place = new JComboBox<>(new String[]{TIMERS_TOP, TIMERS_BOTTOM, TIMERS_BOTH});
		String saved = prefs.get("timerplace", TIMERS_TOP);
		place.setSelectedItem(List.of(TIMERS_TOP, TIMERS_BOTTOM, TIMERS_BOTH).contains(saved) ? saved : TIMERS_TOP);
		place.addActionListener(ev -> prefs.put("timerplace", (String) place.getSelectedItem()));
		place.setToolTipText("<html>Rumble, forage, Woodhallow station and vampire lair carpentry/treasure haul timers:<br>"
				+ "top is a bar above the tabs, bottom is beside the Scan button</html>");
		place.setMaximumSize(place.getPreferredSize());
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(new JLabel("Timers: "));
		row.add(place);
		section.add(row);
		return place;
	}

	private boolean timersTop() { return !TIMERS_BOTTOM.equals(comboTimerPlace.getSelectedItem()); }
	private boolean timersBottom() { return !TIMERS_TOP.equals(comboTimerPlace.getSelectedItem()); }

	private static JCheckBox option(JPanel section, Preferences prefs, String label, String key, boolean defaultValue) {
		JCheckBox option = new JCheckBox(label, prefs.getBoolean(key, defaultValue));
		option.addActionListener(ev -> prefs.putBoolean(key, option.isSelected()));
		section.add(option);
		return option;
	}

	/** Seconds for each move in the solo line (DnavPlan); -1 = left blank, so that move isn't used. */
	private volatile int boochSeconds = 9, tripleSeconds = 40, doubleSeconds = -1, singleSeconds = 12, donkeySeconds = 51,
			bingoSeconds = -1, vegasSeconds = -1;

	/**
	 * Time-left warnings: a 30 s and a 15 s box per activity, each its own setting. Ticking both warns
	 * twice. The ticked-by-default cells are the old single options, under their old keys.
	 */
	private void addWarningMatrix(JPanel section, Preferences prefs) {
		JPanel countdowns = OptionsPanel.subsection(section, "Countdown sounds");
		JPanel grid = new JPanel(new GridBagLayout());
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		GridBagConstraints c = new GridBagConstraints();
		c.insets = new Insets(0, 0, 0, 10);
		c.anchor = GridBagConstraints.WEST;
		c.gridy = 0;
		c.gridx = 1;
		grid.add(new JLabel("30 s"), c);
		c.gridx = 2;
		grid.add(new JLabel("15 s"), c);
		String[] when = {
				"Woodhallow: %s s before stations end (from the chat's \"30 seconds!\" line)",
				"Cursed Isles: %s s left to forage",
				"Vampire Lair: %s s left of the round" };
		for (TimeLeftWarnings.Activity activity : TimeLeftWarnings.Activity.values()) {
			int row = activity.ordinal();
			c.gridy = row + 1;
			c.gridx = 0;
			grid.add(new JLabel(activity.label), c);
			for (int col = 0; col < 2; col++) {
				String key = col == 0 ? activity.key30 : activity.key15;
				boolean on = prefs.getBoolean(key, col == 0 ? activity.default30 : activity.default15);
				JCheckBox box = new JCheckBox((String) null, on);
				box.setToolTipText(String.format(when[row], col == 0 ? "30" : "15"));
				box.addActionListener(ev -> prefs.putBoolean(key, box.isSelected()));
				c.gridx = col + 1;
				grid.add(box, c);
				warningSounds[row][col] = box;
			}
		}
		grid.setMaximumSize(grid.getPreferredSize());
		countdowns.add(grid);
	}

	/** A time box: whole seconds 1-300, or blank to leave that move out of the plan. */
	private static void addSeconds(JPanel section, Preferences prefs, String label, String key, String defaultValue,
								   java.util.function.IntConsumer apply, String tip) {
		JTextField field = new JTextField(prefs.get(key, defaultValue).trim(), 4);
		field.setHorizontalAlignment(JTextField.RIGHT);
		field.setToolTipText(tip);
		Runnable read = () -> {
			String text = field.getText().trim();
			int v = -1;
			try { if (!text.isEmpty()) v = Math.max(1, Math.min(300, Integer.parseInt(text))); } catch (NumberFormatException ignored) { }
			apply.accept(v);
			prefs.put(key, v < 0 ? "" : String.valueOf(v));
		};
		read.run();
		field.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			@Override public void insertUpdate(javax.swing.event.DocumentEvent e) { read.run(); }
			@Override public void removeUpdate(javax.swing.event.DocumentEvent e) { read.run(); }
			@Override public void changedUpdate(javax.swing.event.DocumentEvent e) { read.run(); }
		});
		JLabel name = new JLabel(label);
		name.setLabelFor(field);
		name.setToolTipText(tip);
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		row.add(name, BorderLayout.CENTER);
		row.add(field, BorderLayout.EAST);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, field.getPreferredSize().height));
		section.add(row);
	}

	/** The solo lines for the Dnav before {@code fray}, fastest first (moves with a blank time left out). */
	private record PlanSettings(int crew, int fray, int booch, int single, int doubles, int triple, int bingo, int donkey, int vegas) { }
	private PlanSettings lastPlanSettings;
	private List<DnavPlan.Line> lastPlan = List.of();

	private List<DnavPlan.Line> plan(int fray) {
		int crew = stationClock.crew();
		if (crew <= 0 || fray < 2 || fray > 6) return List.of();
		PlanSettings settings = new PlanSettings(crew, fray, boochSeconds, singleSeconds, doubleSeconds, tripleSeconds,
				bingoSeconds, donkeySeconds, vegasSeconds);
		if (!settings.equals(lastPlanSettings)) {
			lastPlan = DnavPlan.options(DnavPlan.needed(crew, fray), tripleSeconds, singleSeconds, donkeySeconds,
					doubleSeconds, bingoSeconds, vegasSeconds, boochSeconds);
			lastPlanSettings = settings;
		}
		return lastPlan;
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
				boolean den = stationClock.inDen() || stationClock.current() != null && now - stationClock.current().start < 900_000;
				if (buttonDnavProblem.isVisible() != den) { buttonDnavProblem.setVisible(den); dnavArea.revalidate(); }
				// Not while stations are open: the Dnav box has the current wave then.
				boolean forecast = dnavEnds <= now && updateForecast();
				if (forecast != rumbleForecast.isVisible()) rumbleForecast.setVisible(forecast);
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

	/**
	 * The activity the logs say a duty report scanned now belongs to (RecordChecker.DEN / CI / LAIR):
	 * the game log's scene, else a Lair arrival in chat. Null when they can't tell.
	 */
	String logActivity(long now) {
		return rumbleClock.scene().activity(now, stationClock.lastArrival("vampireLairs"));
	}

	/** When the client last showed a duty report no new game followed (game log), 0 if none. For auto scan. */
	long lastFinalReport() { return rumbleClock.lastFinalReport(); }

	void scanned(DRGroupData report, ActivityTracker.Period period) {
		lastReport = report;
		reportedPeriod = period;
	}

	private void createDnavTab() {
		panelDnav = new JPanel();
		panelDnav.setLayout(new BoxLayout(panelDnav, BoxLayout.Y_AXIS));
		// Text colours are set here, not left to the game's look and feel: its text areas and border
		// titles default to black on the dark background. Wolves left keeps the label yellow.
		javax.swing.border.TitledBorder dnavTitle = BorderFactory.createTitledBorder("Dnav");
		dnavTitle.setTitleColor(Color.WHITE);
		panelDnav.setBorder(BorderFactory.createCompoundBorder(dnavTitle,
				BorderFactory.createEmptyBorder(0, 3, 2, 3)));
		panelDnav.setVisible(false);
		labelWolvesLeft = new JLabel(" ");
		labelWolvesLeft.setToolTipText("Choose the wolf count source under Options > Woodhallow & Rumble");
		Font dnavFont = labelWolvesLeft.getFont();
		// Sized from the window's font, like the Rumble tab (about 20 and 15 pt at the usual 12-13 pt).
		labelWolvesLeft.setFont(dnavFont.deriveFont(Font.BOLD, dnavFont.getSize2D() * 1.6f));
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
		labelStrategy.setFont(dnavFont.deriveFont(Font.PLAIN, dnavFont.getSize2D() * 1.2f));
		labelStrategy.setForeground(Color.WHITE);
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
		ahoyLabel.setForeground(Color.WHITE);
		ahoyLabel.setToolTipText("Click the starting werewolf count the Ahoy tab shows, for the pace estimate");
		ahoyRow.add(ahoyLabel);
		ahoyButtons = new JPanel();
		ahoyButtons.setLayout(new BoxLayout(ahoyButtons, BoxLayout.X_AXIS));
		ahoyRow.add(ahoyButtons);
		panelDnav.add(Box.createVerticalStrut(6));
		panelDnav.add(ahoyRow);
	}

	/**
	 * When Woodhallow's current station period ends: the client's own "Next wave in" countdown once it has
	 * been read this period (DenWaveClock), else the chat's estimate. 0 when no period is on. Stations are
	 * open while the chat's estimate hasn't passed, or, until the chat marks the period's end, while the
	 * client's countdown runs.
	 */
	private long denStationEnd(long now) {
		StationClock.Period period = stationClock.current();
		if (period == null || !stationClock.inDen() || now - period.start >= 360_000) return 0;
		if (period.start != wavePeriodStart) {
			wavePeriodStart = period.start;
			waveClock.reset();
		}
		long chatEnd = stationClock.expectedEnd(period);
		long wave = waveClock.read(game, now);
		boolean open = chatEnd > now || (!period.closed() && wave > now);
		if (!open) return 0;
		return wave > now ? wave : chatEnd;
	}

	private void updateDnav(long now) {
		StationClock.Period period = stationClock.current();
		long ends = denEnds = denStationEnd(now);
		boolean inStations = ends > now && !period.lair;
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
		int[] seen = {-2, -2};
		WolfCount.Reading reading = wolfCount.read(() -> {
					int fromScene = seen[0] = denWolves.read(game);
					return fromScene >= 0 ? fromScene : (seen[1] = AhoyWolfCount.read(game));
				},
				chat, period.start, now, choices);
		wolfSamples.addLast(String.format(java.util.Locale.ROOT,
				"%s  t+%5.1fs  chat=%d (half %d, changed t+%.1fs)  %s=%s  shown=%d  start=%d%s",
				timeOfDay(now), (now - period.start) / 1000.0, chat.remaining(), chat.atHalf(),
				chat.changedAt() > 0 ? (chat.changedAt() - period.start) / 1000.0 : -1.0,
				"island", seen[0] + (seen[1] == -2 ? "" : " ahoy=" + seen[1]),
				reading.remaining(), reading.starting(), manualStartingWolves >= 0 ? " (clicked " + manualStartingWolves + ")" : ""));
		if (wolfSamples.size() > 1200) wolfSamples.removeFirst();

		ahoyRow.setVisible(reading.starting() < 0);
		if (ahoyRow.isVisible()) updateStartingChoices(choices);
		int startingWolves = reading.starting() >= 0 ? reading.starting() : manualStartingWolves;
		// The work is the wave's exact size from the crew and fray, whatever count it shows (DnavPlan).
		List<DnavPlan.Line> lines = plan(stationClock.frayNumber());
		String strategy = lines.isEmpty() ? "no plan available"
				: lines.stream().map(l -> l.name() + " (" + l.time() + ")").collect(java.util.stream.Collectors.joining("\nor "));
		if (!("Solo: " + strategy).equals(labelStrategy.getText())) {
			labelStrategy.setText("Solo: " + strategy);
			panelDnav.revalidate();
		}
		showPace(reading, startingWolves, half, period.start, ends);
		lastDnavShown = labelWolvesLeft.getText() + " | Solo: " + strategy.replace('\n', ' ');
	}

	private static String timeOfDay(long ms) {
		return java.time.LocalTime.ofInstant(java.time.Instant.ofEpochMilli(ms), java.time.ZoneId.systemDefault())
				.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
	}

	private void updateStartingChoices(List<Integer> choices) {
		if (choices.equals(ahoyChoices)) return;
		ahoyChoices = choices;
		ahoyButtons.removeAll();
		ButtonGroup group = new ButtonGroup();
		for (int count : choices) {
			JToggleButton button = new JToggleButton(String.valueOf(count), count == manualStartingWolves);
			button.setMargin(new Insets(1, 8, 1, 8));
			button.setToolTipText("Confirm the starting count for the pace estimate");
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
			text = "Waiting for wolf count";
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
		labelRumbleShorthand.setToolTipText("<html>Groups to charge this minute: the recommended range,<br>at Woodhallow after the groups for an insta on a white name (in white)</html>");
		shorthandColor = labelRumbleShorthand.getForeground();
		// The Rumble tab is the board; the timer and shorthand go in the timer bar above the tabs.
		rumbleBoard = new RumbleBoard();
		panelRumble = new JPanel(new BorderLayout(0, 2));
		panelRumble.add(rumbleBoard, BorderLayout.CENTER);
		// The timer bar above the tabs, for the rumble and the forages alike: the rumble timer (or the time
		// left) big on the left, the shorthand (or what is timed) on the right. The window's own background
		// and label colours from the game's look and feel, red text as the warning. During a rumble it holds
		// labelRumble and labelRumbleShorthand, otherwise labelForage and labelForageDetail.
		labelForage = new JLabel(" ");
		labelForage.setFont(timerFont);
		labelForageDetail = new JLabel(" ");
		labelForageDetail.setFont(bigFont);
		forageBar = new JPanel(new BorderLayout(8, 0));
		forageBar.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
		forageBar.add(labelForage, BorderLayout.WEST);
		forageBar.add(labelForageDetail, BorderLayout.EAST);
		forageBar.setVisible(false);
		// At Woodhallow: the next Dnav stations' forecast and solo strategy, in the main window (forecastPanel()).
		rumbleForecast = new DnavPanel(baseFont);
		rumbleForecast.setVisible(false);
	}

	/** The timer bar, for the main window to put above the tabs. Hidden unless a timer or the plank alert is on. */
	JComponent forageBar() {
		if (forageBar.getParent() != timerArea) {
			timerArea.setLayout(new BoxLayout(timerArea, BoxLayout.Y_AXIS));
			timerArea.add(forageBar);
			timerArea.add(homunculi);
		}
		return timerArea;
	}

	/**
	 * The main window's Dnav area: the Dnav box (wolves left, solo strategy, starting count) while stations
	 * are open, and the Next Dnav forecast between stations at Woodhallow, from the first fray.
	 */
	JComponent forecastPanel() {
		if (dnavArea.getComponentCount() == 0) {
			dnavArea.setLayout(new BoxLayout(dnavArea, BoxLayout.Y_AXIS));
			panelDnav.setAlignmentX(Component.LEFT_ALIGNMENT);
			rumbleForecast.setAlignmentX(Component.LEFT_ALIGNMENT);
			dnavArea.add(panelDnav);
			dnavArea.add(rumbleForecast);
			buttonDnavProblem.setAlignmentX(Component.LEFT_ALIGNMENT);
			buttonDnavProblem.setMargin(new Insets(1, 6, 1, 6));
			buttonDnavProblem.setToolTipText("Saves everything Gizmo knows about this Dnav (times to the millisecond, wolf counts,"
					+ " chat, plan) to the data folder's dnav_reports, to compare with your video");
			buttonDnavProblem.addActionListener(ev -> reportDnavProblem());
			buttonDnavProblem.setVisible(false);
			dnavArea.add(buttonDnavProblem);
		}
		return dnavArea;
	}

	private final JButton buttonDnavProblem = new JButton("That didn't work");
	private java.util.function.Consumer<String> status = text -> { };
	void setStatus(java.util.function.Consumer<String> status) { this.status = status; }

	/** Writes a plain-text report of the current or last Dnav, to line up with video of it. */
	private void reportDnavProblem() {
		long now = System.currentTimeMillis();
		StringBuilder r = new StringBuilder();
		java.util.function.Consumer<String> line = t -> r.append(t).append(System.lineSeparator());
		line.accept("Gizmo Dnav report, clicked " + java.time.LocalDateTime.now() + " (" + now + " ms)");
		GameLog.Info who = GameLog.read();
		if (who != null) line.accept("Pirate " + who.pirate + ", ocean " + who.ocean);
		int crew = stationClock.crew(), fray = stationClock.frayNumber();
		line.accept("In den " + stationClock.inDen() + ", crew " + crew + ", frays done " + (fray - 1)
				+ ", next fray " + fray + ", deaths this fray " + stationClock.frayDeaths());
		StationClock.Period cur = stationClock.current(), last = stationClock.lastEnded(now);
		for (StationClock.Period p : new StationClock.Period[]{cur, last}) {
			if (p == null) continue;
			line.accept((p == cur ? "Current" : "Last ended") + " stations: opened " + timeOfDay(p.start)
					+ ", expected end " + timeOfDay(stationClock.expectedEnd(p)) + (p.lair ? " (lair)" : ""));
		}
		line.accept("");
		line.accept("Solo settings (-1 = not used): single " + singleSeconds + " s, double " + doubleSeconds
				+ " s, triple " + tripleSeconds + " s, bingo " + bingoSeconds + " s, donkey " + donkeySeconds
				+ " s, Vegas " + vegasSeconds + " s, Booch " + boochSeconds + " s");
		for (int f : new int[]{fray - 1, fray}) {
			if (crew <= 0 || f < 2 || f > 6) continue;
			line.accept(String.format(java.util.Locale.ROOT, "Dnav before fray %d: pack %.3f x share %.1f = %.3f werewolves,"
					+ " total %.1f points, clear at %.1f", f, DnavPlan.pack(crew, f), DnavPlan.share(f),
					DnavPlan.werewolves(crew, f), DnavPlan.total(crew, f), DnavPlan.needed(crew, f)));
			for (DnavPlan.Line l : plan(f)) {
				StringBuilder steps = new StringBuilder();
				double total = DnavPlan.total(crew, f);
				for (DnavPlan.Step st : l.steps())
					steps.append(String.format(java.util.Locale.ROOT, "  %s @%ds %d pts (bar %.0f%%)", st.what(), st.seconds(),
							st.points(), 100 * Math.max(0, total - st.points()) / total));
				line.accept("  " + l.name() + " " + l.time() + " = " + l.points() + " pts:" + steps);
			}
		}
		if (lastForecast != null)
			line.accept(String.format(java.util.Locale.ROOT, "Next Dnav box: fray %d, wolves %d %s, need %.2f/min, team %.2f/min,"
					+ " time %s, clear in %s, lines %s", lastForecast.nextFray, lastForecast.werewolves, lastForecast.odds,
					lastForecast.needRate(), lastForecast.teamRate, DnavForecast.mmss(lastForecast.stationsMs),
					DnavForecast.mmss(lastForecast.clearMs), lastForecastLines));
		line.accept("Dnav box last showed: " + lastDnavShown);
		line.accept("Wolf count source: the island (DenWolves), else Ahoy; chat when newer");
		line.accept("");
		line.accept("== Wolf count, once a second during stations (t = since stations opened)");
		for (String sample : wolfSamples) line.accept(sample);
		line.accept("");
		line.accept("== Your puzzles (game log)");
		for (String p : activityTracker.recentPuzzles()) line.accept(p);
		line.accept("");
		line.accept("== Last duty report scanned");
		if (lastReport != null) line.accept(lastReport.toString());
		if (reportedPeriod != null)
			for (ActivityTracker.Row row : reportedPeriod.rows)
				line.accept(String.format(java.util.Locale.ROOT, "%s %s score %d tokens %s %.1fs%s", row.station, row.pirate,
						row.score, java.util.Arrays.toString(row.tokens), row.ms / 1000.0, row.exact ? " exact" : ""));
		line.accept("");
		line.accept("== Chat (last messages, oldest first)");
		for (String m : stationClock.recentMessages()) line.accept(m);

		String name = "dnav_report_" + java.time.LocalDateTime.now()
				.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")) + ".txt";
		try {
			java.nio.file.Path dir = GameLog.dataFolder().resolve("dnav_reports");
			java.nio.file.Files.createDirectories(dir);
			java.nio.file.Path file = dir.resolve(name);
			java.nio.file.Files.writeString(file, r.toString(), java.nio.charset.StandardCharsets.UTF_8);
			status.accept("Dnav report saved: " + file);
			buttonDnavProblem.setText("Saved");
			new Timer(3000, ev -> buttonDnavProblem.setText("That didn't work")) {{ setRepeats(false); start(); }};
		} catch (java.io.IOException | RuntimeException e) {
			status.accept("Dnav report failed: " + e);
		}
	}

	private void updateRumble(long now) {
		CiSwordfight sword = rumbleClock.ciSwordfight();
		boolean ci = RecordChecker.CI.equals(selectedActivity);
		if (sword.round() != homunculiRound) { homunculiRound = sword.round(); homunculi.reset(); }
		boolean colours = ci && sword.showColours();
		if (homunculi.isVisible() != colours) { homunculi.setVisible(colours); timerArea.revalidate(); timerArea.repaint(); }
		long elapsed = rumbleClock.elapsed(now);
		long forageLeft = rumbleClock.forageLeft(now);
		playTimerSounds(now, elapsed, forageLeft);
		// Cursed Isles: more pirates + mercs aboard than the next fray takes. Not shown during the forage;
		// once it ends, until the next fray starts, it fills the tab and the voice line repeats.
		MercPlank plank = rumbleClock.plank();
		int toPlank = elapsed < 0 && plank.betweenFrays() ? plank.toPlank() : 0;
		boolean plankAlert = toPlank > 0 && forageLeft < 0;
		if (plankAlert && checkBoxPlankSound.isSelected() && now - plankPlayedAt >= PLANK_REPEAT_MS) {
			plankPlayedAt = now;
			Sounds.play(Sounds.PLANK);
		}
		String plankText = plankText(toPlank);
		// The bar: the Cursed Isles forage (game log), else Woodhallow's stations (the client's countdown).
		// The Scan row shows Woodhallow's normal stations in its Dnav branch (with wolves left), so it only
		// takes the Den's final, Dnav-less lair period as a forage. Sounds: TimeLeftWarnings.
		long shownForageLeft = forageLeft >= 0 ? forageLeft : denForageLeft(now);
		long scanForageLeft = forageLeft >= 0 ? forageLeft : dnavEnds > now ? -1 : shownForageLeft;
		updateScanRow(now, elapsed, scanForageLeft, toPlank, plankAlert);

		boolean board = elapsed >= 0 && checkBoxRumbleBoard.isSelected();
		boolean shorthand = elapsed >= 0 && checkBoxRumbleShorthand.isSelected();
		// No forage on: the vampire lair's carpentry/treasure haul round, styled the same.
		long lairLeft = stationClock.lairTimerEnd() - now;
		boolean lairRound = shownForageLeft < 0 && lairRoundOn(lairLeft);
		long barLeft = lairRound ? lairLeft : shownForageLeft;
		boolean rumbleOn = elapsed >= 0 && timersTop();
		boolean foraging = elapsed < 0 && barLeft >= 0 && timersTop();
		updateTimerBar(rumbleOn);
		if (!rumbleOn) updateForageBar(barLeft, lairRound, foraging, plankAlert, plankText, plank, toPlank);
		// The Rumble tab is the board.
		int index = tabbedPane.indexOfComponent(panelRumble);
		if (board && index < 0) {
			tabBeforeRumble = userTab();
			tabbedPane.addTab("Rumble", panelRumble);
			tabbedPane.setSelectedComponent(panelRumble);
		} else if (!board && index >= 0) {
			removeAutoTab(panelRumble, tabBeforeRumble);
		}
		boolean woodhallow = stationClock.inDen();
		boolean maxDamage = woodhallow && stationClock.elitesLeft() == 0;
		int minute = (int) (elapsed / 60_000);
		if (board) {
			rumbleBoard.setVisible(true);
			rumbleBoard.setMinute(minute);
			rumbleBoard.setWerewolfFight(woodhallow, maxDamage);
		}
		if (!rumbleOn) {
			rumbleSecond = -1;
			rumbleGroups = null;
			return;
		}
		long seconds = elapsed / 1000;
		if (seconds != rumbleSecond) {
			rumbleSecond = seconds;
			labelRumble.setText(clock(seconds));
			labelRumble.setForeground(seconds % 60 >= 55 ? FORAGE_RED : labelWolvesLeftColor);
		}
		labelRumbleShorthand.setVisible(shorthand);
		if (shorthand) {
			String groups = woodhallow ? RumbleBoard.instaShorthand(minute, maxDamage) : RumbleBoard.shorthand(minute, maxDamage);
			if (!groups.equals(rumbleGroups)) {
				rumbleGroups = groups;
				labelRumbleShorthand.setText(groups);
			}
		}
	}

	/** Puts the rumble's labels in the timer bar during a rumble (and shows it), the forage ones otherwise. */
	private void updateTimerBar(boolean rumble) {
		boolean holdsRumble = labelRumble.getParent() == forageBar;
		if (rumble != holdsRumble) {
			forageBar.removeAll();
			forageBar.add(rumble ? labelRumble : labelForage, BorderLayout.WEST);
			forageBar.add(rumble ? labelRumbleShorthand : labelForageDetail, BorderLayout.EAST);
			forageBar.setToolTipText(null);
			forageBar.revalidate();
			forageBar.repaint();
		}
		if (rumble && !forageBar.isVisible()) forageBar.setVisible(true);
	}

	/**
	 * The forage bar, above whichever tab is open: the time left to forage (2:00 down), red text in
	 * the last 30 seconds, or the vampire lair's carpentry/treasure haul round (red in the last 15); after the
	 * forage, the plank alert in red while it lasts. Hidden otherwise.
	 */
	private void updateForageBar(long forageLeft, boolean lairRound, boolean foraging, boolean plankAlert, String plankText,
			MercPlank plank, int toPlank) {
		boolean show = foraging || plankAlert;
		if (forageBar.isVisible() != show) forageBar.setVisible(show);
		if (!show) return;
		String time, detail, tip;
		boolean red;
		if (plankAlert) {
			time = plankText;
			detail = plank.pirates() + " pirates, " + plank.mercs() + " mercs";
			tip = plank.pirates() + " pirates and " + plank.mercs() + " mercs aboard; the next fray takes "
					+ (plank.pirates() + plank.mercs() - toPlank);
			red = true;
		} else {
			time = clock((forageLeft + 999) / 1000);
			detail = lairRound ? LAIR_ROUND_LABEL : FORAGE_LABEL;
			tip = lairRound ? "Time left in the carpentry/treasure haul round; red in the last 15 seconds"
					: "Time left to forage; red in the last 30 seconds";
			red = forageLeft <= (lairRound ? LAIR_WARNING_MS : FORAGE_WARNING_MS);
		}
		if (!time.equals(labelForage.getText())) labelForage.setText(time);
		if (!detail.equals(labelForageDetail.getText())) labelForageDetail.setText(detail);
		forageBar.setToolTipText(tip);
		labelForage.setToolTipText(tip);
		labelForage.setForeground(red ? FORAGE_RED : labelWolvesLeftColor);
		labelForageDetail.setForeground(plankAlert ? FORAGE_RED : shorthandColor);
	}

	/**
	 * Woodhallow's stations, every period including the final "Plunder the lair!" one: milliseconds left,
	 * from the client's "Next wave in" countdown (else the chat's estimate), or -1 when none is on.
	 */
	private long denForageLeft(long now) {
		return denEnds > now ? denEnds - now : -1;
	}

	private boolean updateForecast() {
		if (!stationClock.inDen()) return false;
		int fray = stationClock.frayNumber();
		if (fray < 1 || fray > 5) return false;   // the lair (6) has no Dnav after it
		int crew = stationClock.crew();
		if (crew <= 0) return false;              // known from the first "still standin'"
		int deaths = stationClock.frayDeaths();
		// Team pace: each navigator's werewolf points in the last duty report per minute on Nav, as if
		// they navigate the whole of the next stations. A pirate who also foraged is timed by their own
		// time on Nav when it's known; otherwise the whole station period is assumed.
		double team = 0;
		StationClock.Period lp = stationClock.lastEnded(System.currentTimeMillis());
		long len = reportedPeriod == null || lp == null || Math.abs(reportedPeriod.start - lp.start) > 5000
				? 0 : reportedPeriod.end - reportedPeriod.start;
		if (lastReport != null && len > 0 && lastReport.data.containsKey("Navigating"))
			for (DRIndividualData d : lastReport.data.get("Navigating").values())
				team += DnavForecast.navPoints(d.getTokens()) / (navMs(d.getName(), len) / 60000.0);
		DnavForecast f = DnavForecast.during(fray, crew, deaths, team);
		// The work is the wave's exact size (DnavPlan), so the solo lines don't depend on the count shown.
		Map<String, String> lines = new java.util.LinkedHashMap<>();
		for (DnavPlan.Line l : plan(f.nextFray)) lines.put(l.name(), l.time());
		lastForecast = f;
		lastForecastLines = lines;
		rumbleForecast.show(f, lines);
		return true;
	}

	/** How long this pirate was on Nav in the reported period: their measured time if known, else the whole period. */
	private long navMs(String pirate, long periodMs) {
		for (ActivityTracker.Row row : reportedPeriod.rows)
			if ("Navigating".equals(row.station) && row.pirate.equals(pirate) && row.hasRate())
				return Math.min(row.ms, periodMs);
		return periodMs;
	}

	/**
	 * Woodhallow and lair alerts, from the chat log's own messages: a rising chime on "...turned back
	 * the last werewolf!", and Thalassa's warning on "The wolf-kin gather! 30 seconds!" and on the
	 * lair's "Ye hear the sound of rustling in coffins." (15 s left of the round).
	 */
	private void playWerewolfSounds(long now) {
		// Only live messages (the chat log is also read back at startup).
		long cleared = stationClock.lastWolfClearedAt(), thirty = stationClock.thirtySecondsAt();
		if (checkBoxClearSound.isSelected() && cleared > 0 && cleared != clearPlayedFor && now - cleared < 15_000) {
			clearPlayedFor = cleared;
			Sounds.play(Sounds.CLEAR);
		}
		// Woodhallow: the client's countdown (denEnds), else 30 s after the chat's "30 seconds!" line.
		long denEnd = denEnds > now ? denEnds : thirty > 0 ? thirty + TimeLeftWarnings.THIRTY_MS : 0;
		warn(TimeLeftWarnings.Activity.WOODHALLOW, denEnd, denEnd, now);
		// Vampire Lair: 30 s from the round timer (fray end + 2:03); 15 s from the coffin warning, which
		// the chat gives 15 s before the round ends.
		warn(TimeLeftWarnings.Activity.VAMPIRE_LAIR, stationClock.lairTimerEnd(), stationClock.lastLairRoundEnd(), now);
	}

	/** The matrix's two cells for one activity: the 30 s warning against end30, the 15 s one against end15. */
	private void warn(TimeLeftWarnings.Activity activity, long end30, long end15, long now) {
		JCheckBox[] boxes = warningSounds[activity.ordinal()];
		if (timeLeftWarnings.due(activity, TimeLeftWarnings.THIRTY_MS, end30, now) && boxes[0].isSelected())
			Sounds.play(Sounds.THIRTY_SECONDS);
		if (timeLeftWarnings.due(activity, TimeLeftWarnings.FIFTEEN_MS, end15, now) && boxes[1].isSelected())
			Sounds.play(Sounds.THIRTY_SECONDS);
	}

	/**
	 * The rumble's minute warning (5 s before the punch sizes change, once a minute) and the
	 * Cursed Isles time-left warnings (the sound matrix), through the same separate-process player as the rest.
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
		long forageEnd = forageLeft >= 0 ? rumbleClock.forageEndsAt() : 0;
		warn(TimeLeftWarnings.Activity.CURSED_ISLES, forageEnd, forageEnd, now);
	}

	/** Shows the Dnav box (in the main window, where Next Dnav sits) during stations; hides it after. */
	private void setDnavTab(boolean show) {
		if (panelDnav.isVisible() != show) panelDnav.setVisible(show);
	}

	/** The tab the user chose: the selected one, or, if that is the Rumble tab Gizmo opened, the tab from before. */
	private Component userTab() {
		Component c = tabbedPane.getSelectedComponent();
		return c == panelRumble ? tabBeforeRumble : c;
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
