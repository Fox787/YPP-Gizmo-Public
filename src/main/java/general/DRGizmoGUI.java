package main.java.general;

import main.java.prefobj.PrefObj;
import main.java.prefobj.RadioButtonProfileSelector;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import javax.swing.event.CaretEvent;
import javax.swing.event.CaretListener;
import javax.swing.text.NumberFormatter;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import static main.java.general.DRGizmoMain.prefs;

public class DRGizmoGUI extends JFrame implements ActionListener{

	private Window window;

	public static final String[] OCEANS = new String[] {"Cerulean", "Emerald", "Meridian", "Obsidian", "No Ocean"};

	public static final String AUTOCOPY = "autocopy", AUTOREAD = "autoread", BROKENRECORDALERT = "brokenrecordalert",
			DEBUG = "debug", IGNOREAI = "ignoreai",

			SCANWRITEDATE = "scanwritedate", SCANUSETABS = "scanusetabs", SCANOCEAN = "scanocean",
			SCANSTATIONHEADERS = "scanstationheaders", SCANSTATION = "scanstation", SCANPIRATE = "scanpirate",
			SCANRATING = "scanrating", SCANTOKENSCORE = "scantokenscore", SCANTOKENARRAY = "scantokenarray", SCANSHEETCOMBO = "scansheetcombo",

			SCOREWRITEDATE = "scorewritedate", SCOREUSETABS = "scoreusetabs", SCOREOCEAN = "scoreocean",
			SCORESTATIONHEADERS = "scorestationheaders", SCORESTATION = "scorestation", SCOREPIRATE = "scorepirate",
			SCORERATING = "scorerating", SCORETOKENSCORE = "scoretokenscore", SCORETOKENARRAY = "scoretokenarray", SCORESHEETCOMBO = "scoresheetcombo",

			TOKENCIRCLE = "tokencircle", TOKENDIAMOND = "tokendiamond", TOKENPLUS = "tokenplus",
			TOKENCROSS = "tokencross", TOKENTHRALL = "tokenthrall",
			TOKENCANNONBALL = "tokencannonball",
			CHESTSMALL = "chestsmall", CHESTMEDIUM = "chestmedium", CHESTLARGE = "chestlarge",

			CCENTRYINDIVIDUAL = "ccentryindividual",
			CCENTRY7 = "ccentry7", CCENTRY6 = "ccentry6", CCENTRY5 = "ccentry5", CCENTRY4 = "ccentry4",
			CCENTRY3 = "ccentry3", CCENTRY2 = "ccentry2", CCENTRY1 = "ccentry1",

			CCFORAGEINDIVIDUAL = "ccforageindividual",
			CCFORAGE7 = "ccforage7", CCFORAGE6 = "ccforage6", CCFORAGE5 = "ccforage5", CCFORAGE4 = "ccforage4",
			CCFORAGE3 = "ccforage3", CCFORAGE2 = "ccforage2", CCFORAGE1 = "ccforage1",

			FULLENTRYINDIVIDUAL = "fullentryindividual",
			FULLENTRY7 = "fullentry7", FULLENTRY6 = "fullentry6", FULLENTRY5 = "fullentry5", FULLENTRY4 = "fullentry4",
			FULLENTRY3 = "fullentry3", FULLENTRY2 = "fullentry2", FULLENTRY1 = "fullentry1",

			SINGLEDRINDIVIDUAL = "singledrindividual",
			SINGLEDR7 = "singledr7", SINGLEDR6 = "singledr6", SINGLEDR5 = "singledr5", SINGLEDR4 = "singledr4",
			SINGLEDR3 = "singledr3", SINGLEDR2 = "singledr2", SINGLEDR1 = "singledr1",

			// Lair records. Crew keys end in the crew size (12, 7, 6, 5, 4).
			LAIRICS = "lairics", LAIRCHESTS = "lairchests",
			LAIRTOTALCHESTS = "lairtotalchests", LAIRFRAY = "lairfray", LAIRIC = "lairic",
			LAIRCARPSCORE = "laircarpscore", LAIRCARPHOLES = "laircarpholes", LAIRVAMPIREPROOF = "lairvampireproof",

			// Wolves records (placeholder).
			WOLVESFURRBALLS4 = "wolvesfurrballs4",
			TREEDATA = "treedata";

			private static final String[] GLOBAL_SETTINGS = {
				// CC Records - these are records, so they should be global
				CCENTRYINDIVIDUAL, CCENTRY7, CCENTRY6, CCENTRY5, CCENTRY4, CCENTRY3, CCENTRY2, CCENTRY1,
				CCFORAGEINDIVIDUAL, CCFORAGE7, CCFORAGE6, CCFORAGE5, CCFORAGE4, CCFORAGE3, CCFORAGE2, CCFORAGE1,

				// Score Records - also records, so global
				SINGLEDRINDIVIDUAL, SINGLEDR7, SINGLEDR6, SINGLEDR5, SINGLEDR4, SINGLEDR3, SINGLEDR2, SINGLEDR1,
				FULLENTRYINDIVIDUAL, FULLENTRY7, FULLENTRY6, FULLENTRY5, FULLENTRY4, FULLENTRY3, FULLENTRY2, FULLENTRY1,

				// Lair and Wolves records
				LAIRICS + "12", LAIRICS + "7", LAIRICS + "6", LAIRICS + "5", LAIRICS + "4",
				LAIRCHESTS + "12", LAIRCHESTS + "7", LAIRCHESTS + "6", LAIRCHESTS + "5", LAIRCHESTS + "4",
				LAIRTOTALCHESTS, LAIRFRAY, LAIRIC, LAIRCARPSCORE, LAIRCARPHOLES, LAIRVAMPIREPROOF,
				WOLVESFURRBALLS4,

				// Window size - global
				"width", "height",

				// Tree data - global (all profiles see same DR tree)
				TREEDATA
			};


	private static JTextArea textAreaOutput, textAreaTests;
	private JButton buttonOutputClear;
	public JButton buttonDRTreeRemove;
	private JButton buttonDRTreeClear;
	private JButton buttonSavePrefs;
	private JButton buttonScan;
	private JButton buttonScore;
	private JButton buttonTestsWho;
	private JButton buttonTestsBootyDiv;
	private JButton buttonOutputNew;
	private JButton buttonOutputAvg;
	private JButton buttonTestsClear;
	private JButton buttonOutputBox;
	private JButton buttonOutputJar;
	private JButton buttonOutputChest;
	private JButton buttonOutputCombined;
	private JButton buttonOutputCarpAvg;
	private JCheckBox checkBoxAutoCopy, checkBoxBrokenRecordAlert;
	private JCheckBox checkBoxDebug, checkBoxIgnoreAI;
	private JLabel labelDRCount;

	public static final String SCANSCOREPERMINUTE = "scanscoreperminute", SHOWEXTRABUTTONS = "showextrabuttons";
	/** Box, Jar, Chest, Averages, Avg/CC's and Carp Avg under the Output tab. */
	private JPanel panelOutputButtonPanel;
	private JCheckBox checkBoxShowExtraButtons;
	private JCheckBox checkBoxScan2ScorePerMinute;
	private JCheckBox checkBoxScanWriteDate, checkBoxScanUseTabs, checkBoxScanStationHeaders, checkBoxScan2Station, checkBoxScan2Pirate, checkBoxScan2Rating, checkBoxScan2TokenScore, checkBoxScan2TokenArray, checkBoxScan2SheetCombo;
	private JComboBox comboBoxScanOcean;

	private JCheckBox checkBoxScoreWriteDate, checkBoxScoreUseTabs, checkBoxScoreStationHeaders, checkBoxScore2Station, checkBoxScore2Pirate, checkBoxScore2Rating, checkBoxScore2TokenScore, checkBoxScore2TokenArray, checkBoxScore2SheetCombo;
	private JComboBox comboBoxScoreOcean;

	public JTree drTree;
	public DefaultMutableTreeNode drTreeRootNode;
	public DefaultTreeModel drTreeModel;

	private JFormattedTextField formattedTextFieldTokenCircle, formattedTextFieldTokenDiamond, formattedTextFieldTokenPlus, formattedTextFieldTokenCross, formattedTextFieldTokenThrall;
	private JFormattedTextField formattedTextFieldCannonball;
	private JFormattedTextField formattedTextFieldSmallChest, formattedTextFieldMediumChest, formattedTextFieldLargeChest;
	private JFormattedTextField formattedTextFieldEntryIndividual, formattedTextFieldEntry7, formattedTextFieldEntry6,
		formattedTextFieldEntry5, formattedTextFieldEntry4, formattedTextFieldEntry3, formattedTextFieldEntry2, formattedTextFieldEntry1;
	private JFormattedTextField formattedTextFieldForageIndividual, formattedTextFieldForage7, formattedTextFieldForage6,
	formattedTextFieldForage5, formattedTextFieldForage4, formattedTextFieldForage3, formattedTextFieldForage2, formattedTextFieldForage1;

	private JFormattedTextField formattedTextFieldSingleDRIndividual, formattedTextFieldSingleDR7, formattedTextFieldSingleDR6,
			formattedTextFieldSingleDR5, formattedTextFieldSingleDR4, formattedTextFieldSingleDR3, formattedTextFieldSingleDR2, formattedTextFieldSingleDR1;
	private JFormattedTextField formattedTextFieldFullEntryIndividual, formattedTextFieldFullEntry7, formattedTextFieldFullEntry6,
			formattedTextFieldFullEntry5, formattedTextFieldFullEntry4, formattedTextFieldFullEntry3, formattedTextFieldFullEntry2, formattedTextFieldFullEntry1;

	/** Records tab: Lair crew records follow RecordChecker.LAIR_CREW_SIZES. */
	private final JFormattedTextField[] formattedTextFieldLairICs = new JFormattedTextField[RecordChecker.LAIR_CREW_SIZES.length];
	private final JFormattedTextField[] formattedTextFieldLairChests = new JFormattedTextField[RecordChecker.LAIR_CREW_SIZES.length];
	private JFormattedTextField formattedTextFieldLairTotalChests, formattedTextFieldLairFray, formattedTextFieldLairIC,
			formattedTextFieldLairCarpScore, formattedTextFieldLairCarpHoles, formattedTextFieldLairVampireProof;
	private JFormattedTextField formattedTextFieldWolvesFurrballs4;

	private RadioButtonProfileSelector profileSelector;
	private Preferences currentPrefs;
	private Preferences globalPrefs;

	private String currentProfile = "Default";
	private final String PROFILE_ROOT = "profiles";
	private final String GLOBAL_ROOT = "global";

	private JCheckBox checkBoxBootyExport;
	private JTextField textFieldChatLogFolder, textFieldBootyTextFolder;
	private JButton buttonChatLogBrowse, buttonBootyTextBrowse;

	public static final String BOOTYEXPORT = "bootyexport", CHATLOGFOLDER = "chatlogfolder", BOOTYTEXTFOLDER = "bootytextfolder";

	private JLabel labelWolvesLeft;
	private ActivityTracker activityTracker;
	private JLabel labelSymbols;
	private RunLog runLog;
	private StationClock stationClock;
	private long lastChatLookup;
	/** Auto scan for every activity (Den, CI, Lair) or none; off means scanning by hand. */
	private JCheckBox checkBoxAutoScan;
	public static final String AUTOSCAN = "autoscan";
	/** Station timer already auto-scanned for, and the last duty report scanned (by hand or auto). */
	private long autoScannedDeadline;
	private String lastScannedReport;
	/** Auto scan was looking for a report on the previous tick. */
	private boolean autoScanArmed;
	/** How long after the last station symbol auto scan keeps looking for the duty report. */
	private static final long AUTOSCAN_SYMBOL_WINDOW_MS = 3 * 60_000L;

	/** Score-per-minute lists still to copy, next first. */
	private final java.util.ArrayDeque<String> pendingCopies = new java.util.ArrayDeque<>();
	private boolean pasteWatchInstalled;

	/** Puzzle Pirates chat takes at most this many characters per message. */
	static final int CHAT_LIMIT = 240;

	/** The text as one piece if it fits in a chat message, else whole lines packed into pieces that each fit. */
	static java.util.List<String> splitForChat(String text) {
		java.util.List<String> out = new java.util.ArrayList<>();
		if (text.length() <= CHAT_LIMIT) { out.add(text); return out; }
		String nl = System.getProperty("line.separator");
		StringBuilder cur = new StringBuilder();
		for (String line : text.split("\\r?\\n")) {
			if (cur.length() > 0 && cur.length() + nl.length() + line.length() > CHAT_LIMIT) {
				out.add(cur.toString());
				cur.setLength(0);
			}
			if (cur.length() > 0) cur.append(nl);
			cur.append(line);
		}
		if (cur.length() > 0) out.add(cur.toString());
		return out;
	}

	private static void copyToClipboard(String s) {
		StringSelection selection = new StringSelection(s);
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
	}

	/** Copies the next queued list; if more remain, the next Ctrl+V in this process moves on again. */
	private void copyNext() {
		String next = pendingCopies.poll();
		if (next == null) return;
		copyToClipboard(next);
		printStatus(pendingCopies.isEmpty() ? "Copied the last list" : "Copied; paste with Ctrl+V and the next list is copied", true);
		if (!pasteWatchInstalled) {
			pasteWatchInstalled = true;
			// Only watches key presses in this Java process (Gizmo and the game's window); changes nothing.
			Toolkit.getDefaultToolkit().addAWTEventListener(ev -> {
				java.awt.event.KeyEvent k = (java.awt.event.KeyEvent) ev;
				if (k.getID() == java.awt.event.KeyEvent.KEY_PRESSED && k.getKeyCode() == java.awt.event.KeyEvent.VK_V
						&& k.isControlDown() && !pendingCopies.isEmpty()) {
					// Let the paste read the clipboard first.
					javax.swing.Timer t = new javax.swing.Timer(300, e2 -> copyNext());
					t.setRepeats(false);
					t.start();
				}
			}, AWTEvent.KEY_EVENT_MASK);
		}
	}

	/** Auto scan is on and this is an activity it covers (RecordChecker.DEN / CI / LAIR). */
	private boolean autoScanWanted(String activity) {
		if (!checkBoxAutoScan.isSelected()) return false;
		return RecordChecker.DEN.equals(activity) || RecordChecker.CI.equals(activity) || RecordChecker.LAIR.equals(activity);
	}

	/**
	 * Auto scan looks for a new duty report while either
	 *  - a Den station period ended in the last 2 minutes (chat log), or
	 *  - a station symbol was over someone's head in a Den, Cursed Isles or Vampire Lair scene in the
	 *    last few minutes (foraging, carpentry, treasure haul...),
	 * and auto scan is ticked. A report that is new is scanned once, like the Scan button.
	 */
	private void autoScanTick() {
		long now = System.currentTimeMillis();
		long d = checkBoxAutoScan.isSelected() ? activityTracker.lastStationEnd(now) : 0;
		boolean byClock = d != 0 && d != autoScannedDeadline && now - d <= 120_000;
		long seen = activityTracker.lastSymbolAt();
		boolean bySymbol = seen != 0 && now - seen <= AUTOSCAN_SYMBOL_WINDOW_MS && autoScanWanted(activityTracker.lastSymbolKind());
		boolean wasArmed = autoScanArmed;
		autoScanArmed = byClock || bySymbol;
		if (!autoScanArmed) return;
		try {
			// Look quietly first, so an empty attempt doesn't fill the Output tab.
			DRGroupData found = new DRGizmo(window, new JTextArea(), false).findDutyReportQuietly();
			if (found == null || found.isEmpty()) return;
			String key = found.toString();
			if (!wasArmed && !byClock && lastScannedReport == null) {
				// Just started watching and a report is already open: it is from before, leave it.
				lastScannedReport = key;
				return;
			}
			if (key.equals(lastScannedReport)) return;   // still the previous report
			String activity = RecordChecker.activityOf(found);
			if (!autoScanWanted(activity)) return;        // not an activity the player opted into
			if (byClock) autoScannedDeadline = d;
			printStatus("Auto scan: reading the " + (activity == null ? "" : activity + " ") + "duty report", false);
			buttonScan.doClick();
		} catch (RuntimeException ignored) {
			// Try again on the next tick.
		}
	}

	/**
	 * The output boxes are created under a temporary Metal look and feel, and drew their text blurry
	 * until clicked (the click repaint used the desktop's font smoothing). Always paint with the
	 * desktop's text antialiasing so the first paint matches.
	 */
	static final class SharpTextArea extends JTextArea {
		private boolean redrawQueued;

		@Override
		protected void paintComponent(Graphics g) {
			Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
			if (hints instanceof java.util.Map && g instanceof Graphics2D)
				((Graphics2D) g).addRenderingHints((java.util.Map<?, ?>) hints);
			super.paintComponent(g);
			// With the game's 2x mode (sun.java2d.uiScale=2) the game's repaint manager hands Swing an
			// unscaled buffer, so text is drawn at 1x and stretched. Once painting has been quiet for a
			// moment (so scrolling isn't interrupted), redraw the visible text directly onto the window,
			// which is at full resolution - what a click did.
			if (isShowing()) sharpenLater.restart();
		}

		private final javax.swing.Timer sharpenLater = new javax.swing.Timer(400, e -> redrawDirect());
		{ sharpenLater.setRepeats(false); }

		private void redrawDirect() {
			if (!isShowing()) return;
			Graphics wg = getGraphics();   // the window's own surface, already at screen scale
			if (wg == null) return;
			try {
				Rectangle vis = getVisibleRect();
				wg.setClip(vis);
				Object hints = Toolkit.getDefaultToolkit().getDesktopProperty("awt.font.desktophints");
				if (hints instanceof java.util.Map && wg instanceof Graphics2D)
					((Graphics2D) wg).addRenderingHints((java.util.Map<?, ?>) hints);
				super.paintComponent(wg);   // not this.paintComponent: no further redraw is queued
			} finally {
				wg.dispose();
			}
		}
	}

	/** Number in a formatted field, or def when it's empty or not a number. */
	private static int intOf(JTextField f, int def) {
		try { return Integer.parseInt(f.getText().replaceAll(",", "").trim()); } catch (NumberFormatException e) { return def; }
	}
	private static double doubleOf(JTextField f, double def) {
		try { return Double.parseDouble(f.getText().replaceAll(",", "").trim()); } catch (NumberFormatException e) { return def; }
	}

	/** Titled box for a column of records on the Records tab. */
	private static JPanel recordBox(String title) {
		JPanel p = new JPanel();
		p.setAlignmentX(Component.LEFT_ALIGNMENT);
		p.setAlignmentY(Component.TOP_ALIGNMENT);
		p.setBorder(new TitledBorder(null, title, TitledBorder.LEADING, TitledBorder.TOP, null, null));
		p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
		return p;
	}

	/** Label and whole-number field on one line, added to parent. Each field gets its own formatter. */
	private static JFormattedTextField recordField(JPanel parent, String label, String tip) {
		return recordField(parent, label, tip, 92);
	}

	private static JFormattedTextField recordField(JPanel parent, String label, String tip, int labelWidth) {
		NumberFormatter f = new NumberFormatter(NumberFormat.getIntegerInstance());
		f.setValueClass(Integer.class);
		f.setMinimum(0);
		f.setAllowsInvalid(true);

		Box row = Box.createHorizontalBox();
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel l = new JLabel(label);
		l.setPreferredSize(new Dimension(labelWidth, 20));
		l.setMinimumSize(new Dimension(labelWidth, 20));
		l.setMaximumSize(new Dimension(labelWidth, 20));
		row.add(l);

		JFormattedTextField field = new JFormattedTextField(f);
		field.setPreferredSize(new Dimension(40, 20));
		field.setMinimumSize(new Dimension(40, 20));
		field.setMaximumSize(new Dimension(40, 20));
		if (tip != null) {
			l.setToolTipText(tip);
			field.setToolTipText(tip);
		}
		row.add(field);
		parent.add(row);
		return field;
	}

	/** The Lair records as currently typed on the Records tab (blank counts as 0, i.e. not set). */
	private RecordChecker.LairRecords lairRecords() {
		RecordChecker.LairRecords r = new RecordChecker.LairRecords();
		for (int i = 0; i < RecordChecker.LAIR_CREW_SIZES.length; i++) {
			r.crewICs[i] = intOf(formattedTextFieldLairICs[i], 0);
			r.crewChests[i] = intOf(formattedTextFieldLairChests[i], 0);
		}
		r.totalChests = intOf(formattedTextFieldLairTotalChests, 0);
		r.fray = intOf(formattedTextFieldLairFray, 0);
		r.ic = intOf(formattedTextFieldLairIC, 0);
		r.carpScore = intOf(formattedTextFieldLairCarpScore, 0);
		r.carpHoles = intOf(formattedTextFieldLairCarpHoles, 0);
		r.vampireProof = intOf(formattedTextFieldLairVampireProof, 0);
		return r;
	}

	/** Prints broken or tied records under a heading naming the activity; nothing when there are none. */
	private void announceRecords(String activity, String... found) {
		StringBuilder sb = new StringBuilder();
		for (String f : found) if (f != null && !f.trim().isEmpty()) sb.append(f.trim()).append(System.getProperty("line.separator"));
		if (sb.length() == 0) {
			printStatus("Broken Record Alert (" + activity + "): no records broken", true);
			return;
		}
		printStatus("*** " + activity + " record! ***", false);
		printStatus(sb.toString().trim(), false);
	}

	/** Scan: checks one duty report against the records for the activity it came from. */
	private void checkScanRecords(DRGroupData data) {
		String activity = RecordChecker.activityOf(data);
		if (RecordChecker.CI.equals(activity)) {
			announceRecords("CI",
				data.getBrokenRecords(intOf(formattedTextFieldForageIndividual, 0),
					intOf(formattedTextFieldForage1, 0), intOf(formattedTextFieldForage2, 0),
					intOf(formattedTextFieldForage3, 0), intOf(formattedTextFieldForage4, 0),
					intOf(formattedTextFieldForage5, 0), intOf(formattedTextFieldForage6, 0),
					intOf(formattedTextFieldForage7, 0)),
				data.getBrokenRecords(doubleOf(formattedTextFieldSingleDRIndividual, 0),
					doubleOf(formattedTextFieldSingleDR1, 0), doubleOf(formattedTextFieldSingleDR2, 0),
					doubleOf(formattedTextFieldSingleDR3, 0), doubleOf(formattedTextFieldSingleDR4, 0),
					doubleOf(formattedTextFieldSingleDR5, 0), doubleOf(formattedTextFieldSingleDR6, 0),
					doubleOf(formattedTextFieldSingleDR7, 0)));
		} else if (RecordChecker.LAIR.equals(activity)) {
			announceRecords("Lair", RecordChecker.lairFray(data, lairRecords()));
		} else if (RecordChecker.DEN.equals(activity)) {
			printStatus("Broken Record Alert: Wolves records aren't checked yet", true);
		} else {
			printStatus("Broken Record Alert: couldn't tell if this report is CI, Lair or Den", true);
		}
	}

	/** Score All: checks the entry (every DR in the tree), each activity against its own records. */
	private void checkEntryRecords(DRPersistantData all) {
		DRPersistantData ci = RecordChecker.only(all, RecordChecker.CI);
		DRPersistantData lair = RecordChecker.only(all, RecordChecker.LAIR);
		DRPersistantData den = RecordChecker.only(all, RecordChecker.DEN);
		int kinds = (ci.isEmpty() ? 0 : 1) + (lair.isEmpty() ? 0 : 1) + (den.isEmpty() ? 0 : 1);
		if (kinds > 1)
			printStatus("Broken Record Alert: the DR Tree mixes CI, Lair and Den reports; each is checked on its own", false);

		if (!ci.isEmpty()) {
			// getBrokenRecords works on copies, so the scores printed above aren't changed.
						announceRecords("CI",
				ci.getBrokenRecords(intOf(formattedTextFieldEntryIndividual, 0),
					intOf(formattedTextFieldEntry1, 0), intOf(formattedTextFieldEntry2, 0),
					intOf(formattedTextFieldEntry3, 0), intOf(formattedTextFieldEntry4, 0),
					intOf(formattedTextFieldEntry5, 0), intOf(formattedTextFieldEntry6, 0),
					intOf(formattedTextFieldEntry7, 0)),
				ci.getBrokenRecords(doubleOf(formattedTextFieldFullEntryIndividual, 0),
					doubleOf(formattedTextFieldFullEntry1, 0), doubleOf(formattedTextFieldFullEntry2, 0),
					doubleOf(formattedTextFieldFullEntry3, 0), doubleOf(formattedTextFieldFullEntry4, 0),
					doubleOf(formattedTextFieldFullEntry5, 0), doubleOf(formattedTextFieldFullEntry6, 0),
					doubleOf(formattedTextFieldFullEntry7, 0)));
		}
		if (!lair.isEmpty()) announceRecords("Lair", RecordChecker.lairEntry(lair, lairRecords()));
		if (!den.isEmpty()) printStatus("Broken Record Alert: Wolves records aren't checked yet", true);
	}

	/** Score records hold decimals; every other record is a whole number. */
	private static boolean isDecimalRecord(String key) {
		return key.startsWith("singledr") || key.startsWith("fullentry");
	}

	/** Every record on the Records tab by its settings key, in the order shown. */
	private java.util.LinkedHashMap<String, JFormattedTextField> recordFields() {
		java.util.LinkedHashMap<String, JFormattedTextField> m = new java.util.LinkedHashMap<>();
		m.put(CCENTRYINDIVIDUAL, formattedTextFieldEntryIndividual);
		m.put(CCENTRY7, formattedTextFieldEntry7); m.put(CCENTRY6, formattedTextFieldEntry6);
		m.put(CCENTRY5, formattedTextFieldEntry5); m.put(CCENTRY4, formattedTextFieldEntry4);
		m.put(CCENTRY3, formattedTextFieldEntry3); m.put(CCENTRY2, formattedTextFieldEntry2);
		m.put(CCENTRY1, formattedTextFieldEntry1);
		m.put(CCFORAGEINDIVIDUAL, formattedTextFieldForageIndividual);
		m.put(CCFORAGE7, formattedTextFieldForage7); m.put(CCFORAGE6, formattedTextFieldForage6);
		m.put(CCFORAGE5, formattedTextFieldForage5); m.put(CCFORAGE4, formattedTextFieldForage4);
		m.put(CCFORAGE3, formattedTextFieldForage3); m.put(CCFORAGE2, formattedTextFieldForage2);
		m.put(CCFORAGE1, formattedTextFieldForage1);
		m.put(SINGLEDRINDIVIDUAL, formattedTextFieldSingleDRIndividual);
		m.put(SINGLEDR7, formattedTextFieldSingleDR7); m.put(SINGLEDR6, formattedTextFieldSingleDR6);
		m.put(SINGLEDR5, formattedTextFieldSingleDR5); m.put(SINGLEDR4, formattedTextFieldSingleDR4);
		m.put(SINGLEDR3, formattedTextFieldSingleDR3); m.put(SINGLEDR2, formattedTextFieldSingleDR2);
		m.put(SINGLEDR1, formattedTextFieldSingleDR1);
		m.put(FULLENTRYINDIVIDUAL, formattedTextFieldFullEntryIndividual);
		m.put(FULLENTRY7, formattedTextFieldFullEntry7); m.put(FULLENTRY6, formattedTextFieldFullEntry6);
		m.put(FULLENTRY5, formattedTextFieldFullEntry5); m.put(FULLENTRY4, formattedTextFieldFullEntry4);
		m.put(FULLENTRY3, formattedTextFieldFullEntry3); m.put(FULLENTRY2, formattedTextFieldFullEntry2);
		m.put(FULLENTRY1, formattedTextFieldFullEntry1);
		for (int i = 0; i < RecordChecker.LAIR_CREW_SIZES.length; i++)
			m.put(LAIRICS + RecordChecker.LAIR_CREW_SIZES[i], formattedTextFieldLairICs[i]);
		for (int i = 0; i < RecordChecker.LAIR_CREW_SIZES.length; i++)
			m.put(LAIRCHESTS + RecordChecker.LAIR_CREW_SIZES[i], formattedTextFieldLairChests[i]);
		m.put(LAIRTOTALCHESTS, formattedTextFieldLairTotalChests);
		m.put(LAIRFRAY, formattedTextFieldLairFray);
		m.put(LAIRIC, formattedTextFieldLairIC);
		m.put(LAIRCARPSCORE, formattedTextFieldLairCarpScore);
		m.put(LAIRCARPHOLES, formattedTextFieldLairCarpHoles);
		m.put(LAIRVAMPIREPROOF, formattedTextFieldLairVampireProof);
		m.put(WOLVESFURRBALLS4, formattedTextFieldWolvesFurrballs4);
		return m;
	}

	/** Saves only the records (they're global, so every profile sees them). */
	private void saveRecords() {
		for (java.util.Map.Entry<String, JFormattedTextField> e : recordFields().entrySet()) {
			if (isDecimalRecord(e.getKey())) globalPrefs.putDouble(e.getKey(), doubleOf(e.getValue(), 0));
			else globalPrefs.putInt(e.getKey(), intOf(e.getValue(), 0));
		}
		try {
			globalPrefs.flush();
		} catch (BackingStoreException ex) {
			printStatus("Error saving records: " + ex.getMessage(), false);
		}
	}

	/** Reads a shared records file into the Records tab and saves it. Records not in the file are left as they are. */
	private void importRecords() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Import Records");
		chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Records file (.json, .txt)", "json", "txt"));
		if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
		java.io.File file = chooser.getSelectedFile();

		java.util.Map<String, String> read;
		try {
			read = RecordsFile.parse(new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8));
		} catch (IOException | IllegalArgumentException ex) {
			JOptionPane.showMessageDialog(this, "Couldn't read " + file.getName() + ":\n" + ex.getMessage(),
					"Import Records", JOptionPane.ERROR_MESSAGE);
			return;
		}

		java.util.LinkedHashMap<String, JFormattedTextField> fields = recordFields();
		java.util.List<String> unknown = new ArrayList<>();
		int known = 0;
		for (String key : read.keySet()) {
			if (fields.containsKey(key)) known++;
			else unknown.add(key);
		}
		if (known == 0) {
			JOptionPane.showMessageDialog(this, file.getName() + " has no records Gizmo knows.",
					"Import Records", JOptionPane.WARNING_MESSAGE);
			return;
		}
		int ok = JOptionPane.showConfirmDialog(this, "Import " + known + " records from " + file.getName()
				+ "?\nThis replaces those records on the Records tab.", "Import Records", JOptionPane.OK_CANCEL_OPTION);
		if (ok != JOptionPane.OK_OPTION) return;

		for (java.util.Map.Entry<String, String> e : read.entrySet()) {
			JFormattedTextField f = fields.get(e.getKey());
			if (f == null) continue;
			double v = Double.parseDouble(e.getValue());
			if (isDecimalRecord(e.getKey())) f.setValue(v);
			else f.setValue((int) Math.round(v));
		}
		saveRecords();
		printStatus("Imported " + known + " records from " + file.getName(), false);
		if (!unknown.isEmpty()) printStatus("Not recognised, skipped: " + String.join(", ", unknown), false);
	}

	/** Writes every record on the Records tab to a file others can import. */
	private void exportRecords() {
		JFileChooser chooser = new JFileChooser();
		chooser.setDialogTitle("Export Records");
		chooser.setSelectedFile(new java.io.File("gizmo-records.json"));
		if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
		java.io.File file = chooser.getSelectedFile();
		if (file.exists() && JOptionPane.showConfirmDialog(this, file.getName() + " already exists. Replace it?",
				"Export Records", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) return;

		java.util.LinkedHashMap<String, String> out = new java.util.LinkedHashMap<>();
		for (java.util.Map.Entry<String, JFormattedTextField> e : recordFields().entrySet()) {
			if (isDecimalRecord(e.getKey()))
				out.put(e.getKey(), new java.math.BigDecimal(doubleOf(e.getValue(), 0)).setScale(2, RoundingMode.HALF_UP).toPlainString());
			else
				out.put(e.getKey(), String.valueOf(intOf(e.getValue(), 0)));
		}
		try {
			java.nio.file.Files.write(file.toPath(), RecordsFile.write(out).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			printStatus("Records exported to " + file.getAbsolutePath(), false);
		} catch (IOException ex) {
			printStatus("Export failed: " + ex.getMessage(), false);
		}
	}

	private WolfWatch wolfWatch;

	/** Label, text field and browse button on one line. */
	private JComponent folderRow(String label, JTextField field, JButton browse) {
		Box row = Box.createHorizontalBox();
		row.setAlignmentX(Component.LEFT_ALIGNMENT);
		JLabel l = new JLabel(label + " ");
		l.setPreferredSize(new Dimension(70, 20));
		row.add(l);
		field.setMaximumSize(new Dimension(Integer.MAX_VALUE, 22));
		field.addActionListener(ev -> saveLogSettings());
		row.add(field);
		browse.setMargin(new Insets(0, 4, 0, 4));
		browse.addActionListener(this);
		row.add(browse);
		return row;
	}

	public void printStatus(String text, boolean isDebug) {
		if (textAreaOutput != null && checkBoxDebug != null) {
			if (!(isDebug) ||  ((isDebug) && (checkBoxDebug.isSelected()))) {
				textAreaOutput.append(text + System.getProperty("line.separator"));
				textAreaOutput.setCaretPosition(textAreaOutput.getText().length());
			}
		}
	}

	public void printTestStatus(String text, boolean isDebug) {
		if (textAreaTests != null && checkBoxDebug != null) {
			if (!(isDebug) ||  ((isDebug) && (checkBoxDebug.isSelected()))) {
				textAreaTests.append(text + System.getProperty("line.separator"));
				textAreaTests.setCaretPosition(textAreaTests.getText().length());
			}
		}
	}


	public DRGizmoGUI(Window window) {

		this.window = window;
		this.globalPrefs = DRGizmoMain.prefs; // Global prefs at root level

		setTitle("Gizmo the Magic Counter v" + DRGizmoMain.VERSION + "");

		getContentPane().setBounds(100, 100, 250, 628);
		getContentPane().setPreferredSize(new Dimension(250, 628));
		getContentPane().setLayout(new BorderLayout(0, 0));


		NumberFormat format = NumberFormat.getIntegerInstance();
		//format.setGroupingUsed(false);
	    NumberFormatter integerFormatter = new NumberFormatter(format);
	    integerFormatter.setValueClass(Integer.class);
	    integerFormatter.setMinimum(0);
	    integerFormatter.setMaximum(Integer.MAX_VALUE);
	    integerFormatter.setAllowsInvalid(true);

		NumberFormat formatDouble = DecimalFormat.getInstance();
		formatDouble.setMinimumFractionDigits(2);
		formatDouble.setMaximumFractionDigits(2);
		formatDouble.setRoundingMode(RoundingMode.HALF_UP);

		NumberFormatter doubleFormatter = new NumberFormatter(formatDouble);
		doubleFormatter.setValueClass(Double.class);
		doubleFormatter.setMinimum(0.00);
		doubleFormatter.setMaximum(Double.MAX_VALUE);
		doubleFormatter.setAllowsInvalid(true);

		JTabbedPane tabbedPane = new JTabbedPane(JTabbedPane.TOP);
		getContentPane().add(tabbedPane, BorderLayout.CENTER);

		JPanel panelOutput = new JPanel();
		panelOutput.setDoubleBuffered(false);
		tabbedPane.addTab("Output", null, panelOutput, null);
		panelOutput.setLayout(new BorderLayout(0, 0));

			JScrollPane scrollPaneOutput = new JScrollPane();
			panelOutput.add(scrollPaneOutput, BorderLayout.CENTER);

			LookAndFeel yoLookAndFeel = UIManager.getLookAndFeel();
			try {
				UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
			} catch (ClassNotFoundException e) {
			} catch (InstantiationException e) {
			} catch (IllegalAccessException e) {
			} catch (UnsupportedLookAndFeelException e) {}

			textAreaOutput = new SharpTextArea();
			textAreaOutput.setBackground(new Color(169, 185, 193)); //highlighted dr color
			//textAreaOutput.setBackground(new Color(140, 162, 172)); //non-highlighted dr color
			textAreaOutput.addCaretListener(new CaretListener() {
				public void caretUpdate(CaretEvent arg0) {
					repaint();
				}
			});
			scrollPaneOutput.setViewportView(textAreaOutput);

			try {
				UIManager.setLookAndFeel(yoLookAndFeel);
			} catch (UnsupportedLookAndFeelException e) {}

			JPanel paneButtons = new JPanel();
			paneButtons.setLayout(new BorderLayout(0, 0));
			panelOutput.add(paneButtons, BorderLayout.SOUTH);

			panelOutputButtonPanel = new JPanel();
			paneButtons.add(panelOutputButtonPanel);
			panelOutputButtonPanel.setLayout(new BoxLayout(panelOutputButtonPanel, BoxLayout.Y_AXIS));

			JPanel panelOutputButtonGeneral = new JPanel();
			panelOutputButtonGeneral.setAlignmentX(Component.RIGHT_ALIGNMENT);
			panelOutputButtonPanel.add(panelOutputButtonGeneral);
			panelOutputButtonGeneral.setLayout(new BoxLayout(panelOutputButtonGeneral, BoxLayout.Y_AXIS));

			Box horizontalOutput1 = Box.createHorizontalBox();
			horizontalOutput1.setAlignmentX(Component.RIGHT_ALIGNMENT);
			panelOutputButtonGeneral.add(horizontalOutput1);

			buttonOutputBox = new JButton("Box");
			buttonOutputBox.addActionListener(this);
			buttonOutputBox.setPreferredSize(new Dimension(80, 17));
			buttonOutputBox.setMinimumSize(new Dimension(80, 17));
			buttonOutputBox.setMaximumSize(new Dimension(80, 17));
			horizontalOutput1.add(buttonOutputBox);

			buttonOutputJar = new JButton("Jar");
			buttonOutputJar.addActionListener(this);
			buttonOutputJar.setPreferredSize(new Dimension(80, 17));
			buttonOutputJar.setMinimumSize(new Dimension(80, 17));
			buttonOutputJar.setMaximumSize(new Dimension(80, 17));
			horizontalOutput1.add(buttonOutputJar);

			buttonOutputChest = new JButton("Chest");
			buttonOutputChest.addActionListener(this);
			buttonOutputChest.setPreferredSize(new Dimension(80, 17));
			buttonOutputChest.setMinimumSize(new Dimension(80, 17));
			buttonOutputChest.setMaximumSize(new Dimension(80, 17));
			horizontalOutput1.add(buttonOutputChest);


			Box horizontalOutput2 = Box.createHorizontalBox();
			horizontalOutput2.setAlignmentX(Component.RIGHT_ALIGNMENT);
			panelOutputButtonGeneral.add(horizontalOutput2);

			buttonOutputAvg = new JButton("Averages");
			buttonOutputAvg.addActionListener(this);
			buttonOutputAvg.setPreferredSize(new Dimension(80, 17));
			buttonOutputAvg.setMinimumSize(new Dimension(80, 17));
			buttonOutputAvg.setMaximumSize(new Dimension(80, 17));
			horizontalOutput2.add(buttonOutputAvg);


			buttonOutputCombined = new JButton("Avg/CC's");
			buttonOutputCombined.addActionListener(this);
			buttonOutputCombined.setPreferredSize(new Dimension(80, 17));
			buttonOutputCombined.setMinimumSize(new Dimension(80, 17));
			buttonOutputCombined.setMaximumSize(new Dimension(80, 17));
			horizontalOutput2.add(buttonOutputCombined);


			buttonOutputCarpAvg = new JButton( "Carp Avg");
			buttonOutputCarpAvg.addActionListener(this);
			buttonOutputCarpAvg.setPreferredSize(new Dimension(80, 17));
			buttonOutputCarpAvg.setMinimumSize(new Dimension(80, 17));
			buttonOutputCarpAvg.setMaximumSize(new Dimension(80, 17));
			horizontalOutput2.add(buttonOutputCarpAvg);



			JPanel panelDRTree = new JPanel();
			tabbedPane.addTab("DR Tree", null, panelDRTree, null);
			panelDRTree.setLayout(new BorderLayout(0, 0));

			JScrollPane scrollPaneDRTree = new JScrollPane();
			panelDRTree.add(scrollPaneDRTree, BorderLayout.CENTER);




			drTreeRootNode = new DefaultMutableTreeNode("Root Node");
			drTreeModel = new DefaultTreeModel(drTreeRootNode);

			drTree = new JTree();
			//drTree.setBackground(new Color(197, 199, 174)); //same color as tabs
			drTree.setBackground(new Color(169, 185, 193)); //highlighted dr color
			//drTree.setBackground(new Color(140, 162, 172)); //non-highlighted dr color

			//set getBackgroundNonSelectionColor for drTree... wow
			drTree.setCellRenderer(new DefaultTreeCellRenderer() {

	        	@Override
	            public Color getBackgroundNonSelectionColor() {
	                return (this.getBackground());
	            }
				@Override
				public Component getTreeCellRendererComponent(final JTree tree, final Object value, final boolean sel, final boolean expanded, final boolean leaf, final int row, final boolean hasFocus) {
			        final Component ret = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

			        this.setText(value.toString());
			        return ret;
			    }
	        });

			drTree.setShowsRootHandles(true);
			drTree.setRootVisible(false);
			drTree.getSelectionModel().setSelectionMode(TreeSelectionModel.DISCONTIGUOUS_TREE_SELECTION);
			drTree.setModel(drTreeModel);
			//populate dr tree
			try {
				//TODO make sure this works
				DRPersistantData tempDRPersistantData = (DRPersistantData) PrefObj.getObject(prefs, TREEDATA);
				for (DRGroupData group : tempDRPersistantData.getAll()) {
					addDRToTree(group);
				}
				printStatus("Tree Loaded", true);
			} catch (IOException e) {
				printStatus("Loading Tree Failed: IO", true);
			} catch (BackingStoreException e) {
				printStatus("Loading Tree Failed: BSE", true);
			} catch (ClassNotFoundException e) {
				printStatus("Loading Tree Failed: CNFE", true);
			} catch (Exception e) {
				printStatus("Loading Tree Failed: any Exception", true);
			}
			scrollPaneDRTree.setViewportView(drTree);

			JPanel panelDRTreeButtonPanel = new JPanel();
			panelDRTree.add(panelDRTreeButtonPanel, BorderLayout.SOUTH);
			panelDRTreeButtonPanel.setLayout(new BoxLayout(panelDRTreeButtonPanel, BoxLayout.X_AXIS));

			Component hGlue_2 = Box.createHorizontalGlue();
			panelDRTreeButtonPanel.add(hGlue_2);

			buttonDRTreeRemove = new JButton("Remove");
			buttonDRTreeRemove.addActionListener(this);
			buttonDRTreeRemove.setPreferredSize(new Dimension(80, 23));
			panelDRTreeButtonPanel.add(buttonDRTreeRemove);

			buttonDRTreeClear = new JButton("Clear");
			buttonDRTreeClear.addActionListener(this);
			buttonDRTreeClear.setPreferredSize(new Dimension(80, 23));
			panelDRTreeButtonPanel.add(buttonDRTreeClear);



			JPanel panelOptions = new JPanel();
			tabbedPane.addTab("Options", null, panelOptions, null);
			panelOptions.setLayout(new BorderLayout(0, 0));

				JScrollPane scrollPaneOptions = new JScrollPane();
				panelOptions.add(scrollPaneOptions, BorderLayout.CENTER);

				JPanel panelOptions_2 = new JPanel();
				scrollPaneOptions.setViewportView(panelOptions_2);
				panelOptions_2.setLayout(new BoxLayout(panelOptions_2, BoxLayout.Y_AXIS));

			JPanel panelOptionsGeneral = new JPanel();
			panelOptionsGeneral.setAlignmentX(Component.LEFT_ALIGNMENT);
			panelOptions_2.add(panelOptionsGeneral);
			panelOptionsGeneral.setLayout(new BorderLayout());

			// LEFT SIDE - Save button and checkboxes
			JPanel leftPanel = new JPanel();
			leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));

			buttonSavePrefs = new JButton("Save Prefs");
			buttonSavePrefs.setToolTipText("Also saves window height and width");
			buttonSavePrefs.addActionListener(this);
			buttonSavePrefs.setAlignmentX(Component.LEFT_ALIGNMENT); // Align left
			leftPanel.add(buttonSavePrefs);

			checkBoxDebug = new JCheckBox("Debug");
			checkBoxDebug.setAlignmentX(Component.LEFT_ALIGNMENT); // Align left
			leftPanel.add(checkBoxDebug);

			checkBoxIgnoreAI = new JCheckBox("Ignore AI");
			checkBoxIgnoreAI.setAlignmentX(Component.LEFT_ALIGNMENT); // Align left
			leftPanel.add(checkBoxIgnoreAI);

			checkBoxShowExtraButtons = new JCheckBox("Show extra buttons");
			checkBoxShowExtraButtons.setToolTipText("Box, Jar, Chest, Averages, Avg/CC's and Carp Avg on the Output tab");
			checkBoxShowExtraButtons.setAlignmentX(Component.LEFT_ALIGNMENT);
			checkBoxShowExtraButtons.addActionListener(ev -> {
				globalPrefs.putBoolean(SHOWEXTRABUTTONS, checkBoxShowExtraButtons.isSelected());
				applyExtraButtons();
			});
			leftPanel.add(checkBoxShowExtraButtons);

			profileSelector = new RadioButtonProfileSelector(DRGizmoMain.prefs);
			profileSelector.setProfileChangeListener(new RadioButtonProfileSelector.ProfileChangeListener() {
				@Override
				public void onProfileChanged(String profileName) {
					switchToProfile(profileName);
				}

				@Override
				public void onProfileDeleted(String profileName) {
					printStatus("Profile '" + profileName + "' deleted", false);
				}
			});
			profileSelector.setAlignmentX(Component.CENTER_ALIGNMENT);

			panelOptionsGeneral.add(leftPanel, BorderLayout.WEST);
			panelOptionsGeneral.add(profileSelector, BorderLayout.CENTER);

			currentPrefs = profileSelector.getCurrentProfilePrefs();
			// A fresh install has no selected profile yet; without this every field stays empty and Scan
			// fails parsing the token values.
			if (currentPrefs == null) currentPrefs = globalPrefs.node(PROFILE_ROOT).node(currentProfile);

			// Booty export: where to find chat logs, and where readable copies go.
			JPanel panelBootyExport = new JPanel();
			panelBootyExport.setBorder(new TitledBorder(null, "Booty Export", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelBootyExport.setAlignmentX(Component.LEFT_ALIGNMENT);
			panelBootyExport.setLayout(new BoxLayout(panelBootyExport, BoxLayout.Y_AXIS));
			panelOptions_2.add(panelBootyExport);

				checkBoxBootyExport = new JCheckBox("Export booty division when scanned");
				checkBoxBootyExport.setToolTipText("Writes exported_data/<ocean>/booty_divisions_<pirate>.jsonl beside the client, plus N.txt in the text folder");
				checkBoxBootyExport.setAlignmentX(Component.LEFT_ALIGNMENT);
				checkBoxBootyExport.addActionListener(ev -> saveLogSettings());
				panelBootyExport.add(checkBoxBootyExport);

				textFieldChatLogFolder = new JTextField();
				buttonChatLogBrowse = new JButton("...");
				panelBootyExport.add(folderRow("Chat logs", textFieldChatLogFolder, buttonChatLogBrowse));

				textFieldBootyTextFolder = new JTextField();
				textFieldBootyTextFolder.setToolTipText("Leave empty to only write the .jsonl");
				buttonBootyTextBrowse = new JButton("...");
				panelBootyExport.add(folderRow("Text copies", textFieldBootyTextFolder, buttonBootyTextBrowse));

			JPanel panelOptionsPrintOptions = new JPanel();
			panelOptionsPrintOptions.setAlignmentX(Component.LEFT_ALIGNMENT);
			panelOptions_2.add(panelOptionsPrintOptions);
			panelOptionsPrintOptions.setLayout(new BoxLayout(panelOptionsPrintOptions, BoxLayout.X_AXIS));

				JPanel panelPrintOptionsScan = new JPanel();
				panelPrintOptionsScan.setBorder(new TitledBorder(null, "Scanning", TitledBorder.LEADING, TitledBorder.TOP, null, null));
				panelOptionsPrintOptions.add(panelPrintOptionsScan);
				panelPrintOptionsScan.setLayout(new BoxLayout(panelPrintOptionsScan, BoxLayout.Y_AXIS));

					checkBoxScanWriteDate = new JCheckBox("Write Date");
					panelPrintOptionsScan.add(checkBoxScanWriteDate);

					checkBoxScanUseTabs = new JCheckBox("Use Tabs");
					panelPrintOptionsScan.add(checkBoxScanUseTabs);

					comboBoxScanOcean = new JComboBox();
					comboBoxScanOcean.setAlignmentX(Component.LEFT_ALIGNMENT);
					panelPrintOptionsScan.add(comboBoxScanOcean);
					comboBoxScanOcean.setModel(new DefaultComboBoxModel(OCEANS));
					comboBoxScanOcean.setMaximumSize(new Dimension(120, 23));

					checkBoxScanStationHeaders = new JCheckBox("Station Headers");
					panelPrintOptionsScan.add(checkBoxScanStationHeaders);

					JPanel panelPrintOptionsScan_2 = new JPanel();
					panelPrintOptionsScan_2.setBorder(new TitledBorder(null, "IDK What to", TitledBorder.LEADING, TitledBorder.TOP, null, null));
					panelPrintOptionsScan.add(panelPrintOptionsScan_2);
					panelPrintOptionsScan_2.setLayout(new BoxLayout(panelPrintOptionsScan_2, BoxLayout.Y_AXIS));

					checkBoxScan2Station = new JCheckBox("Station");
					panelPrintOptionsScan_2.add(checkBoxScan2Station);

					checkBoxScan2Pirate = new JCheckBox("Pirate");
					panelPrintOptionsScan_2.add(checkBoxScan2Pirate);

					checkBoxScan2Rating = new JCheckBox("Rating");
					panelPrintOptionsScan_2.add(checkBoxScan2Rating);

					checkBoxScan2TokenScore = new JCheckBox("Token Score");
					panelPrintOptionsScan_2.add(checkBoxScan2TokenScore);

					checkBoxScan2TokenArray = new JCheckBox("Token Array");
					panelPrintOptionsScan_2.add(checkBoxScan2TokenArray);

					checkBoxScan2SheetCombo = new JCheckBox("Sheet Combo");
					checkBoxScan2SheetCombo.setToolTipText(" Creates a Score + Array Combo in Single Cell.");
					panelPrintOptionsScan_2.add(checkBoxScan2SheetCombo);

					checkBoxScan2ScorePerMinute = new JCheckBox("Score/min");
					checkBoxScan2ScorePerMinute.setToolTipText("Wolves Den only: adds (score per minute) to each pirate's line instead of a separate list.");
					panelPrintOptionsScan_2.add(checkBoxScan2ScorePerMinute);

				JPanel panelPrintOptionsScore = new JPanel();
				panelPrintOptionsScore.setBorder(new TitledBorder(null, "Scoring", TitledBorder.LEADING, TitledBorder.TOP, null, null));
				panelOptionsPrintOptions.add(panelPrintOptionsScore);
				panelPrintOptionsScore.setLayout(new BoxLayout(panelPrintOptionsScore, BoxLayout.Y_AXIS));

					checkBoxScoreWriteDate = new JCheckBox("Write Date");
					panelPrintOptionsScore.add(checkBoxScoreWriteDate);

					checkBoxScoreUseTabs = new JCheckBox("Use Tabs");
					panelPrintOptionsScore.add(checkBoxScoreUseTabs);

					comboBoxScoreOcean = new JComboBox();
					comboBoxScoreOcean.setAlignmentX(Component.LEFT_ALIGNMENT);
					comboBoxScoreOcean.setModel(new DefaultComboBoxModel(OCEANS));
					comboBoxScoreOcean.setMaximumSize(new Dimension(120, 23));
					panelPrintOptionsScore.add(comboBoxScoreOcean);

					checkBoxScoreStationHeaders = new JCheckBox("Station Headers");
					panelPrintOptionsScore.add(checkBoxScoreStationHeaders);

					JPanel panePrintOptionsScore_2 = new JPanel();
					panePrintOptionsScore_2.setBorder(new TitledBorder(null, "Name These", TitledBorder.LEADING, TitledBorder.TOP, null, null));
					panelPrintOptionsScore.add(panePrintOptionsScore_2);
					panePrintOptionsScore_2.setLayout(new BoxLayout(panePrintOptionsScore_2, BoxLayout.Y_AXIS));

					checkBoxScore2Station = new JCheckBox("Station");
					panePrintOptionsScore_2.add(checkBoxScore2Station);

					checkBoxScore2Pirate = new JCheckBox("Pirate");
					panePrintOptionsScore_2.add(checkBoxScore2Pirate);

					checkBoxScore2Rating = new JCheckBox("Rating");
					panePrintOptionsScore_2.add(checkBoxScore2Rating);

					checkBoxScore2TokenScore = new JCheckBox("Token Score");
					panePrintOptionsScore_2.add(checkBoxScore2TokenScore);

					checkBoxScore2TokenArray = new JCheckBox("Token Array");
					panePrintOptionsScore_2.add(checkBoxScore2TokenArray);

					checkBoxScore2SheetCombo = new JCheckBox("Sheet Combo");
					checkBoxScore2SheetCombo.setToolTipText(" Creates a Score + Array Combo in Single Cell.");
					panePrintOptionsScore_2.add(checkBoxScore2SheetCombo);



			JPanel panelOptionsTokenAndChestValues = new JPanel();
			panelOptionsTokenAndChestValues.setAlignmentX(Component.LEFT_ALIGNMENT);
			panelOptionsTokenAndChestValues.setBorder(new TitledBorder(null, "Token/Chest Values", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelOptions_2.add(panelOptionsTokenAndChestValues);
			panelOptionsTokenAndChestValues.setLayout(new BoxLayout(panelOptionsTokenAndChestValues, BoxLayout.X_AXIS));

			JPanel panelTokenValues = new JPanel();
			panelTokenValues.setBorder(new TitledBorder(null, "Tokens", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelOptionsTokenAndChestValues.add(panelTokenValues);
			panelTokenValues.setLayout(new BoxLayout(panelTokenValues, BoxLayout.Y_AXIS));

				Box horizontalBoxTokenCircle = Box.createHorizontalBox();
				horizontalBoxTokenCircle.setAlignmentX(Component.LEFT_ALIGNMENT);
				panelTokenValues.add(horizontalBoxTokenCircle);

				JLabel labelTokenCircle = new JLabel("Circle");
				labelTokenCircle.setMinimumSize(new Dimension(60, 23));
				labelTokenCircle.setMaximumSize(new Dimension(60, 23));
				horizontalBoxTokenCircle.add(labelTokenCircle);

				formattedTextFieldTokenCircle = new JFormattedTextField(integerFormatter);
				formattedTextFieldTokenCircle.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldTokenCircle.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldTokenCircle.setMaximumSize(new Dimension(30, 20));
				horizontalBoxTokenCircle.add(formattedTextFieldTokenCircle);

				Box horizontalBoxTokenDiamond = Box.createHorizontalBox();
				horizontalBoxTokenDiamond.setAlignmentX(Component.LEFT_ALIGNMENT);
				panelTokenValues.add(horizontalBoxTokenDiamond);

				JLabel labelTokenDiamond = new JLabel("Diamond");
				labelTokenDiamond.setMinimumSize(new Dimension(60, 23));
				labelTokenDiamond.setMaximumSize(new Dimension(60, 23));
				horizontalBoxTokenDiamond.add(labelTokenDiamond);

				formattedTextFieldTokenDiamond = new JFormattedTextField(integerFormatter);
				formattedTextFieldTokenDiamond.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldTokenDiamond.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldTokenDiamond.setMaximumSize(new Dimension(30, 20));
				horizontalBoxTokenDiamond.add(formattedTextFieldTokenDiamond);

				Box horizontalBoxTokenPlus = Box.createHorizontalBox();
				horizontalBoxTokenPlus.setAlignmentX(Component.LEFT_ALIGNMENT);
				panelTokenValues.add(horizontalBoxTokenPlus);

				JLabel labelTokenPlus = new JLabel("Plus");
				labelTokenPlus.setMinimumSize(new Dimension(60, 23));
				labelTokenPlus.setMaximumSize(new Dimension(60, 23));
				horizontalBoxTokenPlus.add(labelTokenPlus);

				formattedTextFieldTokenPlus = new JFormattedTextField(integerFormatter);
				formattedTextFieldTokenPlus.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldTokenPlus.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldTokenPlus.setMaximumSize(new Dimension(30, 20));
				horizontalBoxTokenPlus.add(formattedTextFieldTokenPlus);

				Box horizontalBoxTokenCross = Box.createHorizontalBox();
				horizontalBoxTokenCross.setAlignmentX(Component.LEFT_ALIGNMENT);
				panelTokenValues.add(horizontalBoxTokenCross);

				JLabel labelTokenCross = new JLabel("Cross");
				labelTokenCross.setMinimumSize(new Dimension(60, 23));
				labelTokenCross.setMaximumSize(new Dimension(60, 23));
				horizontalBoxTokenCross.add(labelTokenCross);

				formattedTextFieldTokenCross = new JFormattedTextField(integerFormatter);
				formattedTextFieldTokenCross.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldTokenCross.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldTokenCross.setMaximumSize(new Dimension(30, 20));
				horizontalBoxTokenCross.add(formattedTextFieldTokenCross);

				Box horizontalBoxTokenThrall = Box.createHorizontalBox();
				horizontalBoxTokenThrall.setAlignmentX(Component.LEFT_ALIGNMENT);
				panelTokenValues.add(horizontalBoxTokenThrall);

				JLabel labelTokenThrall = new JLabel("Thrall");
				labelTokenThrall.setMinimumSize(new Dimension(60, 23));
				labelTokenThrall.setMaximumSize(new Dimension(60, 23));
				horizontalBoxTokenThrall.add(labelTokenThrall);

				formattedTextFieldTokenThrall = new JFormattedTextField(integerFormatter);
				formattedTextFieldTokenThrall.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldTokenThrall.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldTokenThrall.setMaximumSize(new Dimension(30, 20));
				horizontalBoxTokenThrall.add(formattedTextFieldTokenThrall);

			JPanel panelCannonballAndChests = new JPanel();
			panelOptionsTokenAndChestValues.add(panelCannonballAndChests);
			panelCannonballAndChests.setLayout(new BoxLayout(panelCannonballAndChests, BoxLayout.Y_AXIS));

				Box horizontalBoxCannonball = Box.createHorizontalBox();
				panelCannonballAndChests.add(horizontalBoxCannonball);

				JLabel labelCannonball = new JLabel("Cannonball");
				horizontalBoxCannonball.add(labelCannonball);

				formattedTextFieldCannonball = new JFormattedTextField(integerFormatter);
				formattedTextFieldCannonball.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldCannonball.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldCannonball.setMaximumSize(new Dimension(30, 20));
				horizontalBoxCannonball.add(formattedTextFieldCannonball);

			JPanel panelChests = new JPanel();
			panelChests.setBorder(new TitledBorder(null, "Chests", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelCannonballAndChests.add(panelChests);
			panelChests.setLayout(new BoxLayout(panelChests, BoxLayout.Y_AXIS));

				Box horizontalBoxSmallChest = Box.createHorizontalBox();
				panelChests.add(horizontalBoxSmallChest);

				JLabel labelSmallChest = new JLabel("Small");
				labelSmallChest.setMinimumSize(new Dimension(60, 23));
				labelSmallChest.setMaximumSize(new Dimension(60, 23));
				horizontalBoxSmallChest.add(labelSmallChest);

				formattedTextFieldSmallChest = new JFormattedTextField(integerFormatter);
				formattedTextFieldSmallChest.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldSmallChest.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldSmallChest.setMaximumSize(new Dimension(30, 23));
				horizontalBoxSmallChest.add(formattedTextFieldSmallChest);

				Box horizontalBoxMediumChest = Box.createHorizontalBox();
				panelChests.add(horizontalBoxMediumChest);

				JLabel labelMediumChest = new JLabel("Medium");
				labelMediumChest.setMinimumSize(new Dimension(60, 23));
				labelMediumChest.setMaximumSize(new Dimension(60, 23));
				horizontalBoxMediumChest.add(labelMediumChest);

				formattedTextFieldMediumChest = new JFormattedTextField(integerFormatter);
				formattedTextFieldMediumChest.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldMediumChest.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldMediumChest.setMaximumSize(new Dimension(30, 23));
				horizontalBoxMediumChest.add(formattedTextFieldMediumChest);

				Box horizontalBoxLargeChest = Box.createHorizontalBox();
				panelChests.add(horizontalBoxLargeChest);

				JLabel labelLargeChest = new JLabel("Large");
				labelLargeChest.setMinimumSize(new Dimension(60, 23));
				labelLargeChest.setMaximumSize(new Dimension(60, 23));
				horizontalBoxLargeChest.add(labelLargeChest);

				formattedTextFieldLargeChest = new JFormattedTextField(integerFormatter);
				formattedTextFieldLargeChest.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldLargeChest.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldLargeChest.setMaximumSize(new Dimension(30, 23));
				horizontalBoxLargeChest.add(formattedTextFieldLargeChest);

			// Records tab: one box per activity. Broken Record Alert checks the box for the activity scanned.
			JPanel panelRecords = new JPanel();
			tabbedPane.addTab("Records", null, panelRecords, null);
			panelRecords.setLayout(new BorderLayout(0, 0));

				JScrollPane scrollPaneRecords = new JScrollPane();
				panelRecords.add(scrollPaneRecords, BorderLayout.CENTER);

				JPanel panelRecords_2 = new JPanel();
				scrollPaneRecords.setViewportView(panelRecords_2);
				panelRecords_2.setLayout(new BoxLayout(panelRecords_2, BoxLayout.Y_AXIS));

				JButton buttonImportRecords = new JButton("Import");
				buttonImportRecords.setToolTipText("Fill in the records from a shared records file");
				buttonImportRecords.addActionListener(ev -> importRecords());
				JButton buttonExportRecords = new JButton("Export");
				buttonExportRecords.setToolTipText("Write these records to a file others can import");
				buttonExportRecords.addActionListener(ev -> exportRecords());
				JButton buttonSaveRecords = new JButton("Save");
				buttonSaveRecords.setToolTipText("Save the records (Save Prefs on the Options tab saves them too)");
				buttonSaveRecords.addActionListener(ev -> {
					saveRecords();
					printStatus("Records saved", false);
				});
				JPanel panelRecordsButtons = new JPanel();
				panelRecordsButtons.setLayout(new BoxLayout(panelRecordsButtons, BoxLayout.X_AXIS));
				panelRecordsButtons.add(buttonImportRecords);
				panelRecordsButtons.add(buttonExportRecords);
				panelRecordsButtons.add(Box.createHorizontalGlue());
				panelRecordsButtons.add(buttonSaveRecords);
				panelRecords.add(panelRecordsButtons, BorderLayout.SOUTH);

			JPanel panelRecordsCI = new JPanel();
			panelRecordsCI.setAlignmentX(Component.LEFT_ALIGNMENT);
			panelRecordsCI.setBorder(new TitledBorder(null, "CI", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelRecordsCI.setLayout(new BoxLayout(panelRecordsCI, BoxLayout.Y_AXIS));
			panelRecords_2.add(panelRecordsCI);

			JPanel panelCCRecords = new JPanel();
			panelCCRecords.setAlignmentX(Component.LEFT_ALIGNMENT);
			panelCCRecords.setBorder(new TitledBorder(null, "CC Records", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelRecordsCI.add(panelCCRecords);
			panelCCRecords.setLayout(new BoxLayout(panelCCRecords, BoxLayout.X_AXIS));

				JPanel panelForageEntry = new JPanel();
				panelForageEntry.setBorder(new TitledBorder(null, "Full Entry", TitledBorder.LEADING, TitledBorder.TOP, null, null));
				panelCCRecords.add(panelForageEntry);
				panelForageEntry.setLayout(new BoxLayout(panelForageEntry, BoxLayout.Y_AXIS));

				Box horizontalBoxEntryIndividual = Box.createHorizontalBox();
				panelForageEntry.add(horizontalBoxEntryIndividual);

				JLabel lblNewLabelEntryIndividual = new JLabel("Individual");
				lblNewLabelEntryIndividual.setPreferredSize(new Dimension(60, 20));
				lblNewLabelEntryIndividual.setMinimumSize(new Dimension(60, 20));
				lblNewLabelEntryIndividual.setMaximumSize(new Dimension(60, 20));
				horizontalBoxEntryIndividual.add(lblNewLabelEntryIndividual);

				formattedTextFieldEntryIndividual = new JFormattedTextField(integerFormatter);
				formattedTextFieldEntryIndividual.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldEntryIndividual.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldEntryIndividual.setMaximumSize(new Dimension(30, 20));
				horizontalBoxEntryIndividual.add(formattedTextFieldEntryIndividual);

				Box horizontalBoxEntry7 = Box.createHorizontalBox();
				panelForageEntry.add(horizontalBoxEntry7);

				JLabel labelEntry7 = new JLabel("7 Man");
				labelEntry7.setPreferredSize(new Dimension(60, 20));
				labelEntry7.setMinimumSize(new Dimension(60, 20));
				labelEntry7.setMaximumSize(new Dimension(60, 20));
				horizontalBoxEntry7.add(labelEntry7);

				formattedTextFieldEntry7 = new JFormattedTextField(integerFormatter);
				formattedTextFieldEntry7.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldEntry7.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldEntry7.setMaximumSize(new Dimension(30, 20));
				horizontalBoxEntry7.add(formattedTextFieldEntry7);

				Box horizontalBoxEntry6 = Box.createHorizontalBox();
				panelForageEntry.add(horizontalBoxEntry6);

				JLabel labelEntry6 = new JLabel("6 Man");
				labelEntry6.setPreferredSize(new Dimension(60, 20));
				labelEntry6.setMinimumSize(new Dimension(60, 20));
				labelEntry6.setMaximumSize(new Dimension(60, 20));
				horizontalBoxEntry6.add(labelEntry6);

				formattedTextFieldEntry6 = new JFormattedTextField(integerFormatter);
				formattedTextFieldEntry6.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldEntry6.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldEntry6.setMaximumSize(new Dimension(30, 20));
				horizontalBoxEntry6.add(formattedTextFieldEntry6);

				Box horizontalBoxEntry5 = Box.createHorizontalBox();
				panelForageEntry.add(horizontalBoxEntry5);

				JLabel labelEntry5 = new JLabel("5 Man");
				labelEntry5.setPreferredSize(new Dimension(60, 20));
				labelEntry5.setMinimumSize(new Dimension(60, 20));
				labelEntry5.setMaximumSize(new Dimension(60, 20));
				horizontalBoxEntry5.add(labelEntry5);

				formattedTextFieldEntry5 = new JFormattedTextField(integerFormatter);
				formattedTextFieldEntry5.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldEntry5.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldEntry5.setMaximumSize(new Dimension(30, 20));
				horizontalBoxEntry5.add(formattedTextFieldEntry5);

				Box horizontalBoxEntry4 = Box.createHorizontalBox();
				panelForageEntry.add(horizontalBoxEntry4);

				JLabel labelEntry4 = new JLabel("4 Man");
				labelEntry4.setPreferredSize(new Dimension(60, 20));
				labelEntry4.setMinimumSize(new Dimension(60, 20));
				labelEntry4.setMaximumSize(new Dimension(60, 20));
				horizontalBoxEntry4.add(labelEntry4);

				formattedTextFieldEntry4 = new JFormattedTextField(integerFormatter);
				formattedTextFieldEntry4.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldEntry4.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldEntry4.setMaximumSize(new Dimension(30, 20));
				horizontalBoxEntry4.add(formattedTextFieldEntry4);

				Box horizontalBoxEntry3 = Box.createHorizontalBox();
				panelForageEntry.add(horizontalBoxEntry3);

				JLabel labelEntry3 = new JLabel("3 Man");
				labelEntry3.setPreferredSize(new Dimension(60, 20));
				labelEntry3.setMinimumSize(new Dimension(60, 20));
				labelEntry3.setMaximumSize(new Dimension(60, 20));
				horizontalBoxEntry3.add(labelEntry3);

				formattedTextFieldEntry3 = new JFormattedTextField(integerFormatter);
				formattedTextFieldEntry3.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldEntry3.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldEntry3.setMaximumSize(new Dimension(30, 20));
				horizontalBoxEntry3.add(formattedTextFieldEntry3);

				Box horizontalBoxEntry2 = Box.createHorizontalBox();
				panelForageEntry.add(horizontalBoxEntry2);

				JLabel labelEntry2 = new JLabel("2 Man");
				labelEntry2.setPreferredSize(new Dimension(60, 20));
				labelEntry2.setMinimumSize(new Dimension(60, 20));
				labelEntry2.setMaximumSize(new Dimension(60, 20));
				horizontalBoxEntry2.add(labelEntry2);

				formattedTextFieldEntry2 = new JFormattedTextField(integerFormatter);
				formattedTextFieldEntry2.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldEntry2.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldEntry2.setMaximumSize(new Dimension(30, 20));
				horizontalBoxEntry2.add(formattedTextFieldEntry2);

				Box horizontalBoxEntry1 = Box.createHorizontalBox();
				panelForageEntry.add(horizontalBoxEntry1);

				JLabel labelEntry1 = new JLabel("1 Man");
				labelEntry1.setPreferredSize(new Dimension(60, 20));
				labelEntry1.setMinimumSize(new Dimension(60, 20));
				labelEntry1.setMaximumSize(new Dimension(60, 20));
				horizontalBoxEntry1.add(labelEntry1);

				formattedTextFieldEntry1 = new JFormattedTextField(integerFormatter);
				formattedTextFieldEntry1.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldEntry1.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldEntry1.setMaximumSize(new Dimension(30, 20));
				horizontalBoxEntry1.add(formattedTextFieldEntry1);

			JPanel panelSingleForage = new JPanel();
			panelSingleForage.setBorder(new TitledBorder(null, "Single DR", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelCCRecords.add(panelSingleForage, 0);	// left, matching Score Records (Single DR | Full Entry)
			panelSingleForage.setLayout(new BoxLayout(panelSingleForage, BoxLayout.Y_AXIS));

				Box horizontalBoxForageIndividual = Box.createHorizontalBox();
				panelSingleForage.add(horizontalBoxForageIndividual);

				JLabel lblNewLabelForageIndividual = new JLabel("Individual");
				lblNewLabelForageIndividual.setPreferredSize(new Dimension(60, 20));
				lblNewLabelForageIndividual.setMinimumSize(new Dimension(60, 20));
				lblNewLabelForageIndividual.setMaximumSize(new Dimension(60, 20));
				horizontalBoxForageIndividual.add(lblNewLabelForageIndividual);

				formattedTextFieldForageIndividual = new JFormattedTextField(integerFormatter);
				formattedTextFieldForageIndividual.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldForageIndividual.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldForageIndividual.setMaximumSize(new Dimension(30, 20));
				horizontalBoxForageIndividual.add(formattedTextFieldForageIndividual);

				Box horizontalBoxForage7 = Box.createHorizontalBox();
				panelSingleForage.add(horizontalBoxForage7);

				JLabel labelForage7 = new JLabel("7 Man");
				labelForage7.setPreferredSize(new Dimension(60, 20));
				labelForage7.setMinimumSize(new Dimension(60, 20));
				labelForage7.setMaximumSize(new Dimension(60, 20));
				horizontalBoxForage7.add(labelForage7);

				formattedTextFieldForage7 = new JFormattedTextField(integerFormatter);
				formattedTextFieldForage7.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldForage7.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldForage7.setMaximumSize(new Dimension(30, 20));
				horizontalBoxForage7.add(formattedTextFieldForage7);

				Box horizontalBoxForage6 = Box.createHorizontalBox();
				panelSingleForage.add(horizontalBoxForage6);

				JLabel labelForage6 = new JLabel("6 Man");
				labelForage6.setPreferredSize(new Dimension(60, 20));
				labelForage6.setMinimumSize(new Dimension(60, 20));
				labelForage6.setMaximumSize(new Dimension(60, 20));
				horizontalBoxForage6.add(labelForage6);

				formattedTextFieldForage6 = new JFormattedTextField(integerFormatter);
				formattedTextFieldForage6.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldForage6.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldForage6.setMaximumSize(new Dimension(30, 20));
				horizontalBoxForage6.add(formattedTextFieldForage6);

				Box horizontalBoxForage5 = Box.createHorizontalBox();
				panelSingleForage.add(horizontalBoxForage5);

				JLabel labelForage5 = new JLabel("5 Man");
				labelForage5.setPreferredSize(new Dimension(60, 20));
				labelForage5.setMinimumSize(new Dimension(60, 20));
				labelForage5.setMaximumSize(new Dimension(60, 20));
				horizontalBoxForage5.add(labelForage5);

				formattedTextFieldForage5 = new JFormattedTextField(integerFormatter);
				formattedTextFieldForage5.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldForage5.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldForage5.setMaximumSize(new Dimension(30, 20));
				horizontalBoxForage5.add(formattedTextFieldForage5);

				Box horizontalBoxForage4 = Box.createHorizontalBox();
				panelSingleForage.add(horizontalBoxForage4);

				JLabel labelForage4 = new JLabel("4 Man");
				labelForage4.setPreferredSize(new Dimension(60, 20));
				labelForage4.setMinimumSize(new Dimension(60, 20));
				labelForage4.setMaximumSize(new Dimension(60, 20));
				horizontalBoxForage4.add(labelForage4);

				formattedTextFieldForage4 = new JFormattedTextField(integerFormatter);
				formattedTextFieldForage4.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldForage4.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldForage4.setMaximumSize(new Dimension(30, 20));
				horizontalBoxForage4.add(formattedTextFieldForage4);

				Box horizontalBoxForage3 = Box.createHorizontalBox();
				panelSingleForage.add(horizontalBoxForage3);

				JLabel labelForage3 = new JLabel("3 Man");
				labelForage3.setPreferredSize(new Dimension(60, 20));
				labelForage3.setMinimumSize(new Dimension(60, 20));
				labelForage3.setMaximumSize(new Dimension(60, 20));
				horizontalBoxForage3.add(labelForage3);

				formattedTextFieldForage3 = new JFormattedTextField(integerFormatter);
				formattedTextFieldForage3.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldForage3.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldForage3.setMaximumSize(new Dimension(30, 20));
				horizontalBoxForage3.add(formattedTextFieldForage3);

				Box horizontalBoxForage2 = Box.createHorizontalBox();
				panelSingleForage.add(horizontalBoxForage2);

				JLabel labelForage2 = new JLabel("2 Man");
				labelForage2.setPreferredSize(new Dimension(60, 20));
				labelForage2.setMinimumSize(new Dimension(60, 20));
				labelForage2.setMaximumSize(new Dimension(60, 20));
				horizontalBoxForage2.add(labelForage2);

				formattedTextFieldForage2 = new JFormattedTextField(integerFormatter);
				formattedTextFieldForage2.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldForage2.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldForage2.setMaximumSize(new Dimension(30, 20));
				horizontalBoxForage2.add(formattedTextFieldForage2);

				Box horizontalBoxForage1 = Box.createHorizontalBox();
				panelSingleForage.add(horizontalBoxForage1);

				JLabel labelForage1 = new JLabel("1 Man");
				labelForage1.setPreferredSize(new Dimension(60, 20));
				labelForage1.setMinimumSize(new Dimension(60, 20));
				labelForage1.setMaximumSize(new Dimension(60, 20));
				horizontalBoxForage1.add(labelForage1);

				formattedTextFieldForage1 = new JFormattedTextField(integerFormatter);
				formattedTextFieldForage1.setPreferredSize(new Dimension(30, 20));
				formattedTextFieldForage1.setMinimumSize(new Dimension(30, 20));
				formattedTextFieldForage1.setMaximumSize(new Dimension(30, 20));
				horizontalBoxForage1.add(formattedTextFieldForage1);

			JPanel panelScoreRecords = new JPanel();
			panelScoreRecords.setAlignmentX(Component.LEFT_ALIGNMENT);
			panelScoreRecords.setBorder(new TitledBorder(null, "Score Records", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelRecordsCI.add(panelScoreRecords);
			panelScoreRecords.setLayout(new BoxLayout(panelScoreRecords, BoxLayout.X_AXIS));

			JPanel panelSingleDR = new JPanel();
			panelSingleDR.setBorder(new TitledBorder(null, "Single DR", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelScoreRecords.add(panelSingleDR);
			panelSingleDR.setLayout(new BoxLayout(panelSingleDR, BoxLayout.Y_AXIS));

				Box horizontalBoxEntrySingle = Box.createHorizontalBox();
				panelSingleDR.add(horizontalBoxEntrySingle);

				JLabel lblNewLabelSingleDRIndividual = new JLabel("Individ");
				lblNewLabelSingleDRIndividual.setPreferredSize(new Dimension(40, 20));
				lblNewLabelSingleDRIndividual.setMinimumSize(new Dimension(40, 20));
				lblNewLabelSingleDRIndividual.setMaximumSize(new Dimension(40, 20));
				horizontalBoxEntrySingle.add(lblNewLabelSingleDRIndividual);

				formattedTextFieldSingleDRIndividual = new JFormattedTextField(doubleFormatter);
				formattedTextFieldSingleDRIndividual.setPreferredSize(new Dimension(50, 20));
				formattedTextFieldSingleDRIndividual.setMinimumSize(new Dimension(50, 20));
				formattedTextFieldSingleDRIndividual.setMaximumSize(new Dimension(50, 20));
				horizontalBoxEntrySingle.add(formattedTextFieldSingleDRIndividual);

				Box horizontalBoxDR7 = Box.createHorizontalBox();
				panelSingleDR.add(horizontalBoxDR7);

				JLabel labelDR7 = new JLabel("7 Man");
				labelDR7.setPreferredSize(new Dimension(40, 20));
				labelDR7.setMinimumSize(new Dimension(40, 20));
				labelDR7.setMaximumSize(new Dimension(40, 20));
				horizontalBoxDR7.add(labelDR7);

				formattedTextFieldSingleDR7 = new JFormattedTextField(doubleFormatter);
				formattedTextFieldSingleDR7.setPreferredSize(new Dimension(50, 20));
				formattedTextFieldSingleDR7.setMinimumSize(new Dimension(50, 20));
				formattedTextFieldSingleDR7.setMaximumSize(new Dimension(50, 20));
				horizontalBoxDR7.add(formattedTextFieldSingleDR7);

				Box horizontalBoxDR6 = Box.createHorizontalBox();
				panelSingleDR.add(horizontalBoxDR6);

				JLabel labelDR6 = new JLabel("6 Man");
				labelDR6.setPreferredSize(new Dimension(40, 20));
				labelDR6.setMinimumSize(new Dimension(40, 20));
				labelDR6.setMaximumSize(new Dimension(40, 20));
				horizontalBoxDR6.add(labelDR6);

				formattedTextFieldSingleDR6 = new JFormattedTextField(doubleFormatter);
				formattedTextFieldSingleDR6.setPreferredSize(new Dimension(50, 20));
				formattedTextFieldSingleDR6.setMinimumSize(new Dimension(50, 20));
				formattedTextFieldSingleDR6.setMaximumSize(new Dimension(50, 20));
				horizontalBoxDR6.add(formattedTextFieldSingleDR6);

				Box horizontalBoxDR5 = Box.createHorizontalBox();
				panelSingleDR.add(horizontalBoxDR5);

				JLabel labelDR5 = new JLabel("5 Man");
				labelDR5.setPreferredSize(new Dimension(40, 20));
				labelDR5.setMinimumSize(new Dimension(40, 20));
				labelDR5.setMaximumSize(new Dimension(40, 20));
				horizontalBoxDR5.add(labelDR5);

				formattedTextFieldSingleDR5 = new JFormattedTextField(doubleFormatter);
				formattedTextFieldSingleDR5.setPreferredSize(new Dimension(50, 20));
				formattedTextFieldSingleDR5.setMinimumSize(new Dimension(50, 20));
				formattedTextFieldSingleDR5.setMaximumSize(new Dimension(50, 20));
				horizontalBoxDR5.add(formattedTextFieldSingleDR5);

				Box horizontalBoxDR4 = Box.createHorizontalBox();
				panelSingleDR.add(horizontalBoxDR4);

				JLabel labelDR4 = new JLabel("4 Man");
				labelDR4.setPreferredSize(new Dimension(40, 20));
				labelDR4.setMinimumSize(new Dimension(40, 20));
				labelDR4.setMaximumSize(new Dimension(40, 20));
				horizontalBoxDR4.add(labelDR4);

				formattedTextFieldSingleDR4 = new JFormattedTextField(doubleFormatter);
				formattedTextFieldSingleDR4.setPreferredSize(new Dimension(50, 20));
				formattedTextFieldSingleDR4.setMinimumSize(new Dimension(50, 20));
				formattedTextFieldSingleDR4.setMaximumSize(new Dimension(50, 20));
				horizontalBoxDR4.add(formattedTextFieldSingleDR4);

				Box horizontalBoxDR3 = Box.createHorizontalBox();
				panelSingleDR.add(horizontalBoxDR3);

				JLabel labelDR3 = new JLabel("3 Man");
				labelDR3.setPreferredSize(new Dimension(40, 20));
				labelDR3.setMinimumSize(new Dimension(40, 20));
				labelDR3.setMaximumSize(new Dimension(40, 20));
				horizontalBoxDR3.add(labelDR3);

				formattedTextFieldSingleDR3 = new JFormattedTextField(doubleFormatter);
				formattedTextFieldSingleDR3.setPreferredSize(new Dimension(50, 20));
				formattedTextFieldSingleDR3.setMinimumSize(new Dimension(50, 20));
				formattedTextFieldSingleDR3.setMaximumSize(new Dimension(50, 20));
				horizontalBoxDR3.add(formattedTextFieldSingleDR3);

				Box horizontalBoxDR2 = Box.createHorizontalBox();
				panelSingleDR.add(horizontalBoxDR2);

				JLabel labelDR2 = new JLabel("2 Man");
				labelDR2.setPreferredSize(new Dimension(40, 20));
				labelDR2.setMinimumSize(new Dimension(40, 20));
				labelDR2.setMaximumSize(new Dimension(40, 20));
				horizontalBoxDR2.add(labelDR2);

				formattedTextFieldSingleDR2 = new JFormattedTextField(doubleFormatter);
				formattedTextFieldSingleDR2.setPreferredSize(new Dimension(50, 20));
				formattedTextFieldSingleDR2.setMinimumSize(new Dimension(50, 20));
				formattedTextFieldSingleDR2.setMaximumSize(new Dimension(50, 20));
				horizontalBoxDR2.add(formattedTextFieldSingleDR2);

				Box horizontalBoxDR1 = Box.createHorizontalBox();
				panelSingleDR.add(horizontalBoxDR1);

				JLabel labelDR1 = new JLabel("1 Man");
				labelDR1.setPreferredSize(new Dimension(40, 20));
				labelDR1.setMinimumSize(new Dimension(40, 20));
				labelDR1.setMaximumSize(new Dimension(40, 20));
				horizontalBoxDR1.add(labelDR1);

				formattedTextFieldSingleDR1 = new JFormattedTextField(doubleFormatter);
				formattedTextFieldSingleDR1.setPreferredSize(new Dimension(50, 20));
				formattedTextFieldSingleDR1.setMinimumSize(new Dimension(50, 20));
				formattedTextFieldSingleDR1.setMaximumSize(new Dimension(50, 20));
				horizontalBoxDR1.add(formattedTextFieldSingleDR1);

			JPanel panelFullEntry = new JPanel();
			panelFullEntry.setBorder(new TitledBorder(null, "Full Entry", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelScoreRecords.add(panelFullEntry);
			panelFullEntry.setLayout(new BoxLayout(panelFullEntry, BoxLayout.Y_AXIS));

				Box horizontalBoxFullEntryIndividual = Box.createHorizontalBox();
				panelFullEntry.add(horizontalBoxFullEntryIndividual);

				JLabel lblNewLabelFullEntryIndividual = new JLabel("Individ");
				lblNewLabelFullEntryIndividual.setPreferredSize(new Dimension(40, 20));
				lblNewLabelFullEntryIndividual.setMinimumSize(new Dimension(40, 20));
				lblNewLabelFullEntryIndividual.setMaximumSize(new Dimension(40, 20));
				horizontalBoxFullEntryIndividual.add(lblNewLabelFullEntryIndividual);

				formattedTextFieldFullEntryIndividual = new JFormattedTextField(doubleFormatter);
				formattedTextFieldFullEntryIndividual.setPreferredSize(new Dimension(50, 20));
				formattedTextFieldFullEntryIndividual.setMinimumSize(new Dimension(50, 20));
				formattedTextFieldFullEntryIndividual.setMaximumSize(new Dimension(50, 20));
				horizontalBoxFullEntryIndividual.add(formattedTextFieldFullEntryIndividual);

				Box horizontalBoxFullEntry7 = Box.createHorizontalBox();
				panelFullEntry.add(horizontalBoxFullEntry7);

				JLabel labelFullEntry7 = new JLabel("7 Man");
				labelFullEntry7.setPreferredSize(new Dimension(40, 20));
				labelFullEntry7.setMinimumSize(new Dimension(40, 20));
				labelFullEntry7.setMaximumSize(new Dimension(40, 20));
				horizontalBoxFullEntry7.add(labelFullEntry7);

				formattedTextFieldFullEntry7 = new JFormattedTextField(doubleFormatter);
				formattedTextFieldFullEntry7.setPreferredSize(new Dimension(50, 20));
				formattedTextFieldFullEntry7.setMinimumSize(new Dimension(50, 20));
				formattedTextFieldFullEntry7.setMaximumSize(new Dimension(50, 20));
				horizontalBoxFullEntry7.add(formattedTextFieldFullEntry7);

				Box horizontalBoxFullEntry6 = Box.createHorizontalBox();
				panelFullEntry.add(horizontalBoxFullEntry6);

				JLabel labelFullEntry6 = new JLabel("6 Man");
				labelFullEntry6.setPreferredSize(new Dimension(40, 20));
				labelFullEntry6.setMinimumSize(new Dimension(40, 20));
				labelFullEntry6.setMaximumSize(new Dimension(40, 20));
				horizontalBoxFullEntry6.add(labelFullEntry6);

				formattedTextFieldFullEntry6 = new JFormattedTextField(doubleFormatter);
				formattedTextFieldFullEntry6.setPreferredSize(new Dimension(50, 20));
				formattedTextFieldFullEntry6.setMinimumSize(new Dimension(50, 20));
				formattedTextFieldFullEntry6.setMaximumSize(new Dimension(50, 20));
				horizontalBoxFullEntry6.add(formattedTextFieldFullEntry6);

				Box horizontalBoxFullEntry5 = Box.createHorizontalBox();
				panelFullEntry.add(horizontalBoxFullEntry5);

				JLabel labelFullEntry5 = new JLabel("5 Man");
				labelFullEntry5.setPreferredSize(new Dimension(40, 20));
				labelFullEntry5.setMinimumSize(new Dimension(40, 20));
				labelFullEntry5.setMaximumSize(new Dimension(40, 20));
				horizontalBoxFullEntry5.add(labelFullEntry5);

				formattedTextFieldFullEntry5 = new JFormattedTextField(doubleFormatter);
				formattedTextFieldFullEntry5.setPreferredSize(new Dimension(50, 20));
				formattedTextFieldFullEntry5.setMinimumSize(new Dimension(50, 20));
				formattedTextFieldFullEntry5.setMaximumSize(new Dimension(50, 20));
				horizontalBoxFullEntry5.add(formattedTextFieldFullEntry5);

				Box horizontalBoxFullEntry4 = Box.createHorizontalBox();
				panelFullEntry.add(horizontalBoxFullEntry4);

				JLabel labelFullEntry4 = new JLabel("4 Man");
				labelFullEntry4.setPreferredSize(new Dimension(40, 20));
				labelFullEntry4.setMinimumSize(new Dimension(40, 20));
				labelFullEntry4.setMaximumSize(new Dimension(40, 20));
				horizontalBoxFullEntry4.add(labelFullEntry4);

				formattedTextFieldFullEntry4 = new JFormattedTextField(doubleFormatter);
				formattedTextFieldFullEntry4.setPreferredSize(new Dimension(50, 20));
				formattedTextFieldFullEntry4.setMinimumSize(new Dimension(50, 20));
				formattedTextFieldFullEntry4.setMaximumSize(new Dimension(50, 20));
				horizontalBoxFullEntry4.add(formattedTextFieldFullEntry4);

				Box horizontalBoxFullEntry3 = Box.createHorizontalBox();
				panelFullEntry.add(horizontalBoxFullEntry3);

				JLabel labelFullEntry3 = new JLabel("3 Man");
				labelFullEntry3.setPreferredSize(new Dimension(40, 20));
				labelFullEntry3.setMinimumSize(new Dimension(40, 20));
				labelFullEntry3.setMaximumSize(new Dimension(40, 20));
				horizontalBoxFullEntry3.add(labelFullEntry3);

				formattedTextFieldFullEntry3 = new JFormattedTextField(doubleFormatter);
				formattedTextFieldFullEntry3.setPreferredSize(new Dimension(50, 20));
				formattedTextFieldFullEntry3.setMinimumSize(new Dimension(50, 20));
				formattedTextFieldFullEntry3.setMaximumSize(new Dimension(50, 20));
				horizontalBoxFullEntry3.add(formattedTextFieldFullEntry3);

				Box horizontalBoxFullEntry2 = Box.createHorizontalBox();
				panelFullEntry.add(horizontalBoxFullEntry2);

				JLabel labelFullEntry2 = new JLabel("2 Man");
				labelFullEntry2.setPreferredSize(new Dimension(40, 20));
				labelFullEntry2.setMinimumSize(new Dimension(40, 20));
				labelFullEntry2.setMaximumSize(new Dimension(40, 20));
				horizontalBoxFullEntry2.add(labelFullEntry2);

				formattedTextFieldFullEntry2 = new JFormattedTextField(doubleFormatter);
				formattedTextFieldFullEntry2.setPreferredSize(new Dimension(50, 20));
				formattedTextFieldFullEntry2.setMinimumSize(new Dimension(50, 20));
				formattedTextFieldFullEntry2.setMaximumSize(new Dimension(50, 20));
				horizontalBoxFullEntry2.add(formattedTextFieldFullEntry2);

				Box horizontalBoxFullEntry1 = Box.createHorizontalBox();
				panelFullEntry.add(horizontalBoxFullEntry1);

				JLabel labelFullEntry1 = new JLabel("1 Man");
				labelFullEntry1.setPreferredSize(new Dimension(40, 20));
				labelFullEntry1.setMinimumSize(new Dimension(40, 20));
				labelFullEntry1.setMaximumSize(new Dimension(40, 20));
				horizontalBoxFullEntry1.add(labelFullEntry1);

				formattedTextFieldFullEntry1 = new JFormattedTextField(doubleFormatter);
				formattedTextFieldFullEntry1.setPreferredSize(new Dimension(50, 20));
				formattedTextFieldFullEntry1.setMinimumSize(new Dimension(50, 20));
				formattedTextFieldFullEntry1.setMaximumSize(new Dimension(50, 20));
				horizontalBoxFullEntry1.add(formattedTextFieldFullEntry1);


			JPanel panelRecordsLair = new JPanel();
			panelRecordsLair.setAlignmentX(Component.LEFT_ALIGNMENT);
			panelRecordsLair.setBorder(new TitledBorder(null, "Lair", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelRecordsLair.setLayout(new BoxLayout(panelRecordsLair, BoxLayout.Y_AXIS));
			panelRecords_2.add(panelRecordsLair);

				JPanel panelLairCrew = new JPanel();
				panelLairCrew.setAlignmentX(Component.LEFT_ALIGNMENT);
				panelLairCrew.setLayout(new BoxLayout(panelLairCrew, BoxLayout.X_AXIS));
				panelRecordsLair.add(panelLairCrew);

				// Crew records for one entry (Score All), by how many pirates were in it.
				JPanel panelLairICs = recordBox("Most ICs");
				panelLairICs.setToolTipText("Most ICs by the crew in one entry - Score All");
				panelLairCrew.add(panelLairICs);
				JPanel panelLairChests = recordBox("Most Chests");
				panelLairChests.setToolTipText("Most chests (all types) by the crew in one entry - Score All");
				panelLairCrew.add(panelLairChests);
				for (int i = 0; i < RecordChecker.LAIR_CREW_SIZES.length; i++) {
					String crew = RecordChecker.LAIR_CREW_SIZES[i] + " Pirates";
					formattedTextFieldLairICs[i] = recordField(panelLairICs, crew, "ICs in one entry with " + crew, 62);
					formattedTextFieldLairChests[i] = recordField(panelLairChests, crew, "Chests in one entry with " + crew, 62);
				}

				JPanel panelLairIndividual = recordBox("Individual");
				panelRecordsLair.add(panelLairIndividual);
				formattedTextFieldLairTotalChests = recordField(panelLairIndividual, "Total Chests", "Most chests (all types) by one pirate in one entry - Score All");
				formattedTextFieldLairFray = recordField(panelLairIndividual, "Fray Record", "Most chests (all types) by one pirate in one fray - Scan");
				formattedTextFieldLairIC = recordField(panelLairIndividual, "IC Record", "Most ICs by one pirate in one entry - Score All");
				formattedTextFieldLairCarpScore = recordField(panelLairIndividual, "Carp Score", "Highest carp score in one fray - Scan");
				formattedTextFieldLairCarpHoles = recordField(panelLairIndividual, "Carp Holes", "Most carp holes (all three numbers) in one fray - Scan");
				formattedTextFieldLairVampireProof = recordField(panelLairIndividual, "Vampire Proof", "Most vampire proof in one fray - Scan");

			JPanel panelRecordsWolves = new JPanel();
			panelRecordsWolves.setAlignmentX(Component.LEFT_ALIGNMENT);
			panelRecordsWolves.setBorder(new TitledBorder(null, "Wolves", TitledBorder.LEADING, TitledBorder.TOP, null, null));
			panelRecordsWolves.setLayout(new BoxLayout(panelRecordsWolves, BoxLayout.Y_AXIS));
			panelRecords_2.add(panelRecordsWolves);

				formattedTextFieldWolvesFurrballs4 = recordField(panelRecordsWolves, "4 Furrballs", "Placeholder: not checked yet");

				JPanel panelTests = new JPanel();
				if (DRGizmoMain.DEVELOPERS) {
					tabbedPane.addTab("Tests", null, panelTests, null);
				}
				panelTests.setLayout(new BorderLayout(0, 0));

				JScrollPane scrollPaneTests = new JScrollPane();
				panelTests.add(scrollPaneTests, BorderLayout.CENTER);


				textAreaTests = new SharpTextArea();
				textAreaTests.setBackground(new Color(169, 185, 193)); //highlighted dr color

				textAreaTests.addCaretListener(new CaretListener() {
					public void caretUpdate(CaretEvent arg0) {
						repaint();
					}
				});
				scrollPaneTests.setViewportView(textAreaTests);

				JPanel panelTestsButtonsPanel = new JPanel();
				panelTests.add(panelTestsButtonsPanel, BorderLayout.SOUTH);
				panelTestsButtonsPanel.setLayout(new BoxLayout(panelTestsButtonsPanel, BoxLayout.X_AXIS));

				buttonTestsWho = new JButton("Scan /w");
				buttonTestsWho.addActionListener(this);
				buttonTestsWho.setPreferredSize(new Dimension(80, 23));
				panelTestsButtonsPanel.add(buttonTestsWho);

				buttonTestsBootyDiv = new JButton("Scan Divvy");
				buttonTestsBootyDiv.addActionListener(this);
				buttonTestsBootyDiv.setPreferredSize(new Dimension(80, 23));
				panelTestsButtonsPanel.add(buttonTestsBootyDiv);

				Component hGlue_1_1 = Box.createHorizontalGlue();
				panelTestsButtonsPanel.add(hGlue_1_1);

				buttonTestsClear = new JButton("Clear");
				buttonTestsClear.addActionListener(this);
				buttonTestsClear.setPreferredSize(new Dimension(80, 23));
				panelTestsButtonsPanel.add(buttonTestsClear);


		JPanel panelGeneral = new JPanel();
		getContentPane().add(panelGeneral, BorderLayout.SOUTH);
		panelGeneral.setLayout(new BorderLayout(0, 0));

		JPanel panelScan = new JPanel();
		panelGeneral.add(panelScan, BorderLayout.SOUTH);
		panelScan.setLayout(new BoxLayout(panelScan, BoxLayout.X_AXIS));

			buttonScan = new JButton("Scan");
			buttonScan.addActionListener(this);
			buttonScan.setMaximumSize(new Dimension(80, 23));
			buttonScan.setPreferredSize(new Dimension(80, 23));
			panelScan.add(buttonScan);

		JPanel panelQuickOptions = new JPanel();
		panelQuickOptions.setBorder(new TitledBorder(null, "Quick Options", TitledBorder.LEADING, TitledBorder.TOP, null, null));
		panelGeneral.add(panelQuickOptions, BorderLayout.CENTER);
		panelQuickOptions.setLayout(new BoxLayout(panelQuickOptions, BoxLayout.Y_AXIS));

			checkBoxAutoCopy = new JCheckBox("Auto Copy");
			panelQuickOptions.add(checkBoxAutoCopy);

			checkBoxBrokenRecordAlert = new JCheckBox("Broken Record Alert");
			panelQuickOptions.add(checkBoxBrokenRecordAlert);

			// Read-only: shown here in Gizmo's window, never added to the client.
			labelWolvesLeft = new JLabel(" ");
			panelQuickOptions.add(labelWolvesLeft);

			wolfWatch = new WolfWatch(window, r -> labelWolvesLeft.setText(r == null ? " " : r.text()));
			stationClock = new StationClock();
			activityTracker = new ActivityTracker(window, globalPrefs, stationClock);
			// Follow the logged-in pirate's chat log for station start/end messages.
			new javax.swing.Timer(1000, ev -> {
				long now = System.currentTimeMillis();
				if (now - lastChatLookup > 60_000) {
					lastChatLookup = now;
					GameLog.Info g = GameLog.read();
					Path folder = textFieldChatLogFolder.getText().trim().isEmpty() ? null : Paths.get(textFieldChatLogFolder.getText().trim());
					if (g != null) {
						stationClock.follow(ChatLog.findLog(folder, g.pirate, g.ocean));
						activityTracker.setSelf(g.pirate);
					}
				}
				stationClock.poll();
			}).start();
			runLog = new RunLog(globalPrefs);
			activityTracker.setOnArrival(firstSinceStart -> {
				if (firstSinceStart) runLog.startNewRunUnlessRecent();
				else runLog.startNewRun();
			});

			// Auto scan: while stations are running (or just after), look for a new duty report every
			// 2 seconds and scan it the same way the Scan button does. One setting for Den, CI and Lair;
			// untick to scan by hand.
			checkBoxAutoScan = new JCheckBox("Auto Scan DR");
			checkBoxAutoScan.setToolTipText("Scan the duty report on its own after each Wolves Den, Cursed Isles or Vampire Lair round. Untick to scan by hand.");
			checkBoxAutoScan.setSelected(globalPrefs.getBoolean(AUTOSCAN, true));
			checkBoxAutoScan.addActionListener(ev -> globalPrefs.putBoolean(AUTOSCAN, checkBoxAutoScan.isSelected()));
			panelQuickOptions.add(checkBoxAutoScan);
			new javax.swing.Timer(2000, ev -> autoScanTick()).start();

			// Live check that the symbols over heads are being read: count, and the list in the tooltip.
			labelSymbols = new JLabel("Symbols: not found yet");
			panelQuickOptions.add(labelSymbols);
			new javax.swing.Timer(2000, ev -> {
				java.util.List<String> seen = activityTracker.snapshot();
				int withSymbol = 0;
				for (String s : seen) if (!s.contains("[0]")) withSymbol++;
				labelSymbols.setText(seen.isEmpty() ? "Symbols: not found yet"
						: "Symbols: " + seen.size() + " pirates, " + withSymbol + " with a symbol");
				labelSymbols.setToolTipText(seen.isEmpty() ? null : "<html>" + String.join("<br>", seen) + "</html>");
			}).start();

		JPanel panelScore = new JPanel();
		panelGeneral.add(panelScore, BorderLayout.NORTH);
		panelScore.setLayout(new BoxLayout(panelScore, BoxLayout.X_AXIS));

			buttonScore = new JButton("Score All");
			buttonScore.addActionListener(this);
			buttonScore.setPreferredSize(new Dimension(80, 23));
			panelScore.add(buttonScore);

			buttonOutputClear = new JButton("Clear");
			buttonOutputClear.addActionListener(this);
			buttonOutputClear.setPreferredSize(new Dimension(80, 17));
			panelScore.add(buttonOutputClear);

			buttonOutputNew = new JButton("???");
			buttonOutputNew.addActionListener(this);
			buttonOutputNew.setPreferredSize(new Dimension(80, 23));
			//panelScore.add(buttonOutputNew);

			Component hGlue_3 = Box.createHorizontalGlue();
			panelScore.add(hGlue_3);

			labelDRCount = new JLabel("DR Count : " + (drTreeRootNode.getChildCount()));
			panelScore.add(labelDRCount);

			Component rigidArea = Box.createRigidArea(new Dimension(20, 20));
			panelScore.add(rigidArea);

		loadSettings();

		// Diagnosis for blurry text until clicked: is the client's repaint manager (and its 2x scaling)
		// drawing Gizmo's window too? Printed once, after the window is up.
		SwingUtilities.invokeLater(() -> {
			GraphicsConfiguration gc = getGraphicsConfiguration();
			java.awt.geom.AffineTransform tx = gc == null ? null : gc.getDefaultTransform();
			printStatus("Display check: repaint manager " + RepaintManager.currentManager(this).getClass().getName()
					+ ", screen scale " + (tx == null ? "?" : tx.getScaleX() + "x")
					+ ", uiScale " + System.getProperty("sun.java2d.uiScale", "unset"), false);
		});
	}

	@Override
	public void actionPerformed(ActionEvent e) {

		if (e.getSource().equals(buttonOutputClear)) {
			textAreaOutput.setText("");
			return;
		}

		if (e.getSource().equals(buttonDRTreeRemove)) {
			TreePath[] selections = drTree.getSelectionPaths();
			if (selections != null && selections.length > 0) {
				// Collect nodes to remove
				ArrayList<DefaultMutableTreeNode> nodesToRemove = new ArrayList<>();
				for (TreePath selection : selections) {
					DefaultMutableTreeNode node = (DefaultMutableTreeNode) selection.getLastPathComponent();
					MutableTreeNode parent = (MutableTreeNode) node.getParent();
					if (parent != null) {
						nodesToRemove.add(node);
					}
				}
				// Remove all selected nodes
				for (DefaultMutableTreeNode node : nodesToRemove) {
					drTreeModel.removeNodeFromParent(node);
				}
				// Update count/labels
				labelDRCount.setText("DR Count : " + (drTreeRootNode.getChildCount()));
				try {
					PrefObj.putObject(prefs, TREEDATA, drTreeToPersistantData());
					printStatus("Tree Saved", true);
				} catch (IOException | BackingStoreException | ClassNotFoundException ex) {
					printStatus("Saving Tree Failed: " + ex.getMessage(), true);
				}
				return;
			}
			Toolkit.getDefaultToolkit().beep();
			return;
		}

		if (e.getSource().equals(buttonDRTreeClear)) {
	        drTreeRootNode.removeAllChildren();
	        drTreeModel.reload();
	        //update DRCount label
			labelDRCount.setText("DR Count : " + (drTreeRootNode.getChildCount()));

			try {
				PrefObj.putObject(prefs, TREEDATA, drTreeToPersistantData());
				printStatus("Tree Saved", true);
			} catch (IOException e1) {
				printStatus("Saving Tree Failed: IO", true);
			} catch (BackingStoreException e1) {
				printStatus("Saving Tree Failed: BSE", true);
			} catch (ClassNotFoundException e1) {
				printStatus("Saving Tree Failed: CNFE", true);
			}

	        printStatus("DR Tree Cleared", false);
	        printStatus("", false);
			return;
		}

		if (e.getSource().equals(buttonSavePrefs)) {
			saveCurrentSettings();
			saveLogSettings();
			printStatus("Preferences Saved to profile: " + profileSelector.getSelectedProfile(), false);
			printStatus("", false);
			return;
		}

		if (e.getSource().equals(buttonScan)) {
			DRGizmo gizmo = new DRGizmo(window, textAreaOutput, checkBoxDebug.isSelected());
			DRGroupData data = gizmo.scanDutyReport();
			if (data != null && !data.isEmpty()) {
				lastScannedReport = data.toString();

				//add to drTree with the default tokenScore
				addDRToTree(data);
				//save new true to prefs
				try {
    				PrefObj.putObject(prefs, TREEDATA, drTreeToPersistantData());
    				printStatus("Tree Saved", true);
    			} catch (IOException e1) {
    				printStatus("Saving Tree Failed: IO", true);
    			} catch (BackingStoreException e1) {
    				printStatus("Saving Tree Failed: BSE", true);
    			} catch (ClassNotFoundException e1) {
    				printStatus("Saving Tree Failed: CNFE", true);
    			}

				//change tokenScore based on the settings
				data.calculateCustomTokenScore(intOf(formattedTextFieldTokenCircle, 1),
						intOf(formattedTextFieldTokenDiamond, 1),
						intOf(formattedTextFieldTokenPlus, 1),
						intOf(formattedTextFieldTokenCross, 1),
						intOf(formattedTextFieldTokenThrall, 1),
						intOf(formattedTextFieldCannonball, 1),
						intOf(formattedTextFieldSmallChest, 1),
						intOf(formattedTextFieldMediumChest, 2),
						intOf(formattedTextFieldLargeChest, 3));
				// Score per minute first, so it can go on the same lines as the report. Only Wolves Den
				// reports are timed; CI and Lair reports print just the options the player ticked.
				boolean isDen = RecordChecker.DEN.equals(RecordChecker.activityOf(data));
				String perMinute = null;
				java.util.Map<String, java.util.Map<String, Double>> rates = null;
				ActivityTracker.Period timed = null;
				if (isDen) {
					try {
						perMinute = activityTracker.report(data);
						ActivityTracker.Period fresh = activityTracker.takeNewPeriod();
						if (fresh != null) printStatus("Saved to " + runLog.record(fresh), true);
						timed = activityTracker.lastPeriod();
						rates = ActivityTracker.rates(timed);
					} catch (IOException | RuntimeException ex) {
						printStatus("Score per minute failed: " + ex, false);
					}
				}
				boolean inline = isDen && checkBoxScan2ScorePerMinute.isSelected();
				//print dr to output with print settings
				String dataString = data.toString(checkBoxIgnoreAI.isSelected(), checkBoxScanWriteDate.isSelected(),
						checkBoxScanUseTabs.isSelected(), comboBoxScanOcean.getSelectedItem().toString(),
						checkBoxScanStationHeaders.isSelected(), checkBoxScan2Station.isSelected(), checkBoxScan2Pirate.isSelected(),
						checkBoxScan2Rating.isSelected(), checkBoxScan2TokenScore.isSelected(),
						checkBoxScan2TokenArray.isSelected(), checkBoxScan2SheetCombo.isSelected(),
						inline ? rates : null);
				printStatus(dataString, false);
				// Without the Score/min option, the per-minute lists still print separately.
				if (!inline && perMinute != null && !perMinute.isEmpty()) printStatus(perMinute, false);
				//check for broken records, against the records for the activity this report is from
				if (checkBoxBrokenRecordAlert.isSelected()) {
					checkScanRecords(data);
				}

				//copy
				if (checkBoxAutoCopy.isSelected()) {
					// First the report exactly as printed, split by station when it is over the chat limit;
					// then (Den only, when Score/min isn't already on the lines) the score-per-minute lists.
					// The first piece is copied now, the rest after each Ctrl+V.
					java.util.List<String> pieces = new java.util.ArrayList<>();
					if (dataString.length() <= CHAT_LIMIT) {
						pieces.add(dataString);
					} else {
						for (String station : data.data.keySet()) {
							String part = data.get(station).toString(checkBoxIgnoreAI.isSelected(), checkBoxScanWriteDate.isSelected(),
									checkBoxScanUseTabs.isSelected(), comboBoxScanOcean.getSelectedItem().toString(),
									checkBoxScanStationHeaders.isSelected(), checkBoxScan2Station.isSelected(), checkBoxScan2Pirate.isSelected(),
									checkBoxScan2Rating.isSelected(), checkBoxScan2TokenScore.isSelected(),
									checkBoxScan2TokenArray.isSelected(), checkBoxScan2SheetCombo.isSelected(),
									inline ? rates : null);
							pieces.addAll(splitForChat(part));
						}
					}
					if (timed != null && !inline)
						for (String block : ActivityTracker.blocks(timed, checkBoxScanUseTabs.isSelected() ? "\t" : " ", false))
							pieces.addAll(splitForChat(block));
					pieces.removeIf(String::isEmpty);
					pendingCopies.clear();
					if (pieces.size() > 1) {
						pendingCopies.addAll(pieces);
						copyNext();
					} else {
						copyToClipboard(pieces.isEmpty() ? dataString : pieces.get(0));
					}
				}
			} else if (gizmo.getBootyDivision() != null) {
				exportBooty(gizmo.getBootyDivision());
			} else {
				printStatus("Scan Failed", false);
			}
			printStatus("", false);
			return;
		}

		if (e.getSource().equals(buttonChatLogBrowse) || e.getSource().equals(buttonBootyTextBrowse)) {
			JTextField field = e.getSource().equals(buttonChatLogBrowse) ? textFieldChatLogFolder : textFieldBootyTextFolder;
			JFileChooser chooser = new JFileChooser(field.getText().isEmpty() ? null : field.getText());
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
				field.setText(chooser.getSelectedFile().getAbsolutePath());
				saveLogSettings();
			}
			return;
		}

		if (e.getSource().equals(buttonScore)) {
			DRPersistantData data = drTreeToPersistantData();
			if (data != null && !data.isEmpty()) {
				//change tokenScore based on the settings
				data.calculateCustomTokenScore(intOf(formattedTextFieldTokenCircle, 1),
						intOf(formattedTextFieldTokenDiamond, 1),
						intOf(formattedTextFieldTokenPlus, 1),
						intOf(formattedTextFieldTokenCross, 1),
						intOf(formattedTextFieldTokenThrall, 1),
						intOf(formattedTextFieldCannonball, 1),
						intOf(formattedTextFieldSmallChest, 1),
						intOf(formattedTextFieldMediumChest, 2),
						intOf(formattedTextFieldLargeChest, 3));
				//print dr to output with print settings
				String dataString = data.toString(checkBoxIgnoreAI.isSelected(), checkBoxScoreWriteDate.isSelected(),
						checkBoxScoreUseTabs.isSelected(), comboBoxScoreOcean.getSelectedItem().toString(),
						checkBoxScoreStationHeaders.isSelected(), checkBoxScore2Station.isSelected(), checkBoxScore2Pirate.isSelected(),
						checkBoxScore2Rating.isSelected(), checkBoxScore2TokenScore.isSelected(),
						checkBoxScore2TokenArray.isSelected(), checkBoxScore2SheetCombo.isSelected());
				printStatus(dataString, false);

				//check for broken records: each activity in the tree against its own records
				if (checkBoxBrokenRecordAlert.isSelected()) {
					checkEntryRecords(data);
				}

				//copy
				if (checkBoxAutoCopy.isSelected()) {
					StringSelection selection = new StringSelection(dataString);
				    Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
				    clipboard.setContents(selection, selection);
				}
			} else {
				printStatus("Scoring Failed", false);
			}
			printStatus("", false);
			return;
		}

		if (e.getSource().equals(buttonOutputAvg)){
			DRPersistantData data = drTreeToPersistantData();
			if (data != null && !data.isEmpty()) {
				//change tokenScore based on the settings
				data.calculateCustomTokenScore(intOf(formattedTextFieldTokenCircle, 1),
						intOf(formattedTextFieldTokenDiamond, 1),
						intOf(formattedTextFieldTokenPlus, 1),
						intOf(formattedTextFieldTokenCross, 1),
						intOf(formattedTextFieldTokenThrall, 1),
						intOf(formattedTextFieldCannonball, 1),
						intOf(formattedTextFieldSmallChest, 1),
						intOf(formattedTextFieldMediumChest, 2),
						intOf(formattedTextFieldLargeChest, 3));
				//print dr to output with print settings
				String dataString = data.toAvg(drTreeRootNode.getChildCount());
				printStatus(dataString, false);

				//copy
				if (checkBoxAutoCopy.isSelected()) {
					StringSelection selection = new StringSelection(dataString);
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					clipboard.setContents(selection, selection);
				}
			} else {
				printStatus("Averages Failed", false);
			}
			printStatus("", false);
			return;
		}

		if (e.getSource().equals(buttonOutputBox)){
			DRPersistantData data = drTreeToPersistantData();
			if (data != null && !data.isEmpty()) {
				//change tokenScore based on the settings
				data.calculateCustomTokenScore(intOf(formattedTextFieldTokenCircle, 1),
						intOf(formattedTextFieldTokenDiamond, 1),
						intOf(formattedTextFieldTokenPlus, 1),
						intOf(formattedTextFieldTokenCross, 1),
						intOf(formattedTextFieldTokenThrall, 1),
						intOf(formattedTextFieldCannonball, 1),
						intOf(formattedTextFieldSmallChest, 1),
						intOf(formattedTextFieldMediumChest, 2),
						intOf(formattedTextFieldLargeChest, 3));
				//print dr to output with print settings
				String dataString = data.toAny(0,drTreeRootNode.getChildCount());
				printStatus(dataString, false);

				//copy
				if (checkBoxAutoCopy.isSelected()) {
					StringSelection selection = new StringSelection(dataString);
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					clipboard.setContents(selection, selection);
				}
			} else {
				printStatus("BBs Failed", false);
			}
			printStatus("", false);
			return;
		}
		if (e.getSource().equals(buttonOutputJar)){
			DRPersistantData data = drTreeToPersistantData();
			if (data != null && !data.isEmpty()) {
				//change tokenScore based on the settings
				data.calculateCustomTokenScore(intOf(formattedTextFieldTokenCircle, 1),
						intOf(formattedTextFieldTokenDiamond, 1),
						intOf(formattedTextFieldTokenPlus, 1),
						intOf(formattedTextFieldTokenCross, 1),
						intOf(formattedTextFieldTokenThrall, 1),
						intOf(formattedTextFieldCannonball, 1),
						intOf(formattedTextFieldSmallChest, 1),
						intOf(formattedTextFieldMediumChest, 2),
						intOf(formattedTextFieldLargeChest, 3));
				//print dr to output with print settings
				String dataString = data.toAny(1, drTreeRootNode.getChildCount());
				printStatus(dataString, false);

				//copy
				if (checkBoxAutoCopy.isSelected()) {
					StringSelection selection = new StringSelection(dataString);
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					clipboard.setContents(selection, selection);
				}
			} else {
				printStatus("FJs Failed", false);
			}
			printStatus("", false);
			return;
		}
		if (e.getSource().equals(buttonOutputChest)){
			DRPersistantData data = drTreeToPersistantData();
			if (data != null && !data.isEmpty()) {
				//change tokenScore based on the settings
				data.calculateCustomTokenScore(intOf(formattedTextFieldTokenCircle, 1),
						intOf(formattedTextFieldTokenDiamond, 1),
						intOf(formattedTextFieldTokenPlus, 1),
						intOf(formattedTextFieldTokenCross, 1),
						intOf(formattedTextFieldTokenThrall, 1),
						intOf(formattedTextFieldCannonball, 1),
						intOf(formattedTextFieldSmallChest, 1),
						intOf(formattedTextFieldMediumChest, 2),
						intOf(formattedTextFieldLargeChest, 3));
				//print dr to output with print settings
				String dataString = data.toAny(2, drTreeRootNode.getChildCount());
				printStatus(dataString, false);

				//copy
				if (checkBoxAutoCopy.isSelected()) {
					StringSelection selection = new StringSelection(dataString);
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					clipboard.setContents(selection, selection);
				}
			} else {
				printStatus("CCs Failed", false);
			}
			printStatus("", false);
			return;
		}

		if (e.getSource().equals(buttonOutputCombined)){
			DRPersistantData data = drTreeToPersistantData();
			if (data != null && !data.isEmpty()) {
				//change tokenScore based on the settings
				data.calculateCustomTokenScore(intOf(formattedTextFieldTokenCircle, 1),
						intOf(formattedTextFieldTokenDiamond, 1),
						intOf(formattedTextFieldTokenPlus, 1),
						intOf(formattedTextFieldTokenCross, 1),
						intOf(formattedTextFieldTokenThrall, 1),
						intOf(formattedTextFieldCannonball, 1),
						intOf(formattedTextFieldSmallChest, 1),
						intOf(formattedTextFieldMediumChest, 2),
						intOf(formattedTextFieldLargeChest, 3));
				//print dr to output with print settings
				String dataString = data.toCombined(drTreeRootNode.getChildCount());
				printStatus(dataString, false);

				//copy
				if (checkBoxAutoCopy.isSelected()) {
					StringSelection selection = new StringSelection(dataString);
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					clipboard.setContents(selection, selection);
				}
			} else {
				printStatus("Combined Failed", false);
			}
			printStatus("", false);
			return;
		}

		if (e.getSource().equals(buttonOutputCarpAvg)){
			DRPersistantData data = drTreeToPersistantData();
			if (data != null && !data.isEmpty()) {
				//change tokenScore based on the settings
				data.calculateCustomTokenScore(intOf(formattedTextFieldTokenCircle, 1),
						intOf(formattedTextFieldTokenDiamond, 1),
						intOf(formattedTextFieldTokenPlus, 1),
						intOf(formattedTextFieldTokenCross, 1),
						intOf(formattedTextFieldTokenThrall, 1),
						intOf(formattedTextFieldCannonball, 1),
						intOf(formattedTextFieldSmallChest, 1),
						intOf(formattedTextFieldMediumChest, 2),
						intOf(formattedTextFieldLargeChest, 3));
				//print dr to output with print settings
				String dataString = data.toCarpAvg(drTreeRootNode.getChildCount());
				printStatus(dataString, false);

				//copy
				if (checkBoxAutoCopy.isSelected()) {
					StringSelection selection = new StringSelection(dataString);
					Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
					clipboard.setContents(selection, selection);
				}
			} else {
				printStatus("Carp Avg Failed", false);
			}
			printStatus("", false);
			return;
		}

		if (e.getSource().equals(buttonOutputNew)){
			return;
		}

		if (e.getSource().equals(buttonTestsClear)) {
			textAreaTests.setText("3");
			return;
		}


		if (e.getSource().equals(buttonTestsWho)) {

			String s = new DRGizmo(window, textAreaTests, checkBoxDebug.isSelected()).scanAhoyTab();
			printTestStatus(s, false);

			return;
		}


		if (e.getSource().equals(buttonTestsBootyDiv)) {
			printTestStatus("you tried to scan for booty division but i haven't made that yet", false);
			printStatus("backup: you tried to scan for booty division but i haven't made that yet", false);

			//String s = new DRGizmo(window, textAreaTests, checkBoxDebug.isSelected()).scanRecursiveSearcher("class com.threerings.piracy.info.client.WhoInfoView");
			//printTestStatus(s, false);
			new DRGizmo(window, textAreaTests, checkBoxDebug.isSelected()).scanAllNodes();

			return;
		}





		textAreaOutput.append("unknown event: " + e.toString() + "\n" + e.getSource().toString() + "\n" + e.getSource().getClass().toString() + "\n");

	}



	/** A DR's node in the tree (shown as its number); remembers whether the DR was CI, Lair or Den. */
	static final class DRGroupNode extends DefaultMutableTreeNode {
		final String activity;

		DRGroupNode(String activity) {
			this.activity = activity;
		}
	}

	public void addDRToTree(DRGroupData dr) {
		DefaultMutableTreeNode nodeNumber = new DRGroupNode(RecordChecker.activityOf(dr));
		//DefaultMutableTreeNode parent = new DefaultMutableTreeNode(userObject)

		//make a node (1, 2, 3, ...) for this dr and decend into it
		nodeNumber.setUserObject(drTreeRootNode.getChildCount()+1);

		for (String station : dr.data.keySet()) {
			DefaultMutableTreeNode nodeStation = new DefaultMutableTreeNode();
			nodeStation.setUserObject(station);
			//make a node for this station and decend into it
			for (String pirate : dr.data.get(station).keySet()) {
				//add dr.get(station, pirate).toString() as a leaf to this node
				DefaultMutableTreeNode nodeIndividualDR = new DefaultMutableTreeNode();
				nodeIndividualDR.setUserObject(new DRIndividualData(dr.get(station, pirate)));
				nodeStation.add(nodeIndividualDR);
			}
			//add station do nodeNumber
			nodeNumber.add(nodeStation);
		}
		drTreeModel.insertNodeInto(nodeNumber, drTreeRootNode, drTreeRootNode.getChildCount());

		//if we previously had no drs then we have to open up the root because we won't beable to click it
		if (drTreeRootNode.getChildCount() == 1) {
			drTree.expandPath(new TreePath(drTreeRootNode.getPath()));
		}
		//update DRCount label
		if (labelDRCount != null)
			labelDRCount.setText("DR Count : " + (drTreeRootNode.getChildCount()));
	}

	private DRPersistantData drTreeToPersistantData() {
		DRPersistantData result = new DRPersistantData();
		for (int groupIndex = 0; groupIndex < drTreeRootNode.getChildCount(); groupIndex++) {
			TreeNode groupNodes = drTreeRootNode.getChildAt(groupIndex);
			DRGroupData groupData = new DRGroupData();
			if (groupNodes instanceof DRGroupNode) groupData.setActivity(((DRGroupNode) groupNodes).activity);

			for (int stationIndex = 0; stationIndex < groupNodes.getChildCount(); stationIndex++) {
				TreeNode stationNodes = groupNodes.getChildAt(stationIndex);
				for (int pirateIndex = 0; pirateIndex < stationNodes.getChildCount(); pirateIndex++) {
					DefaultMutableTreeNode pirateNodes = (DefaultMutableTreeNode) stationNodes.getChildAt(pirateIndex);
					groupData.put((DRIndividualData) pirateNodes.getUserObject());
				}
			}
			result.add(groupData);
		}
		return result;
	}


	/**
	 * Switches to a different profile by loading its preferences
	 */
	private void switchToProfile(String profileName) {
		currentPrefs = profileSelector.getCurrentProfilePrefs();
		loadSettings();
		printStatus("Switched to profile: " + profileName, false);
	}

	/** Adds chat log and game log context to a scanned booty division and writes it out. */
	private void exportBooty(BootyDivision booty) {
		if (!checkBoxBootyExport.isSelected()) {
			printStatus("Booty export is off (Options > Booty Export)", false);
			return;
		}
		StringBuilder notes = new StringBuilder();
		Path chatFolder = textFieldChatLogFolder.getText().trim().isEmpty() ? null : Paths.get(textFieldChatLogFolder.getText().trim());
		BootyExporter.attachLogs(booty, chatFolder, notes);
		printStatus(notes.toString().trim(), true);

		if (booty.chat != null && booty.chat.openedAt != null) {
			ChatLog.Segment c = booty.chat;
			printStatus("Voyage: " + c.battlesWon + " won, " + c.battlesLost + " lost, "
					+ (c.chestsOpened == null ? "?" : c.chestsOpened) + " chests opened"
					+ (c.myCut == null ? "" : ", your cut " + c.myCut + " PoE"), false);
		}

		booty.runFiles.addAll(runLog.pending());

		Path textFolder = textFieldBootyTextFolder.getText().trim().isEmpty() ? null : Paths.get(textFieldBootyTextFolder.getText().trim());
		try {
			BootyExporter.Result r = BootyExporter.export(booty, textFolder);
			if (!r.duplicate) {   // the next voyage starts afresh
				activityTracker.clearPeriods();
				runLog.clearPending();
				runLog.startNewRun();
			}
			if (r.duplicate) {
				printStatus("Booty division already exported to " + r.jsonl.getFileName(), false);
			} else {
				printStatus("Booty exported: " + r.jsonl + (r.text == null ? "" : " and " + r.text.getFileName()), false);
			}
			if (checkBoxAutoCopy.isSelected()) {
				StringSelection selection = new StringSelection(booty.toText());
				Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
			}
		} catch (IOException | RuntimeException ex) {
			printStatus("Booty export failed: " + ex, false);
		}
	}

	/** Log folders are machine-wide, so they live in the global prefs rather than a profile. */
	private void saveLogSettings() {
		globalPrefs.putBoolean(BOOTYEXPORT, checkBoxBootyExport.isSelected());
		globalPrefs.put(CHATLOGFOLDER, textFieldChatLogFolder.getText().trim());
		globalPrefs.put(BOOTYTEXTFOLDER, textFieldBootyTextFolder.getText().trim());
	}

	private void loadLogSettings() {
		checkBoxBootyExport.setSelected(globalPrefs.getBoolean(BOOTYEXPORT, true));
		String chat = globalPrefs.get(CHATLOGFOLDER, "");
		if (chat.isEmpty()) {
			Path guess = ChatLog.guessFolder();
			chat = guess == null ? "" : guess.toString();
		}
		textFieldChatLogFolder.setText(chat);
		String text = globalPrefs.get(BOOTYTEXTFOLDER, "");
		if (text.isEmpty() && !chat.isEmpty()) text = Paths.get(chat, "booty_division").toString();
		textFieldBootyTextFolder.setText(text);

		wolfWatch.start();
		activityTracker.start();

		checkBoxShowExtraButtons.setSelected(globalPrefs.getBoolean(SHOWEXTRABUTTONS, true));
		applyExtraButtons();
	}

	private void applyExtraButtons() {
		if (panelOutputButtonPanel == null) return;
		panelOutputButtonPanel.setVisible(checkBoxShowExtraButtons.isSelected());
		panelOutputButtonPanel.getParent().revalidate();
	}

	/**
	 * Loads all settings from the current profile
	 */
	private void loadSettings() {
		loadLogSettings();
		if (currentPrefs == null) {
			return;
		}

		// Profile-specific settings
		checkBoxDebug.setSelected(currentPrefs.getBoolean(DEBUG, false));
		checkBoxIgnoreAI.setSelected(currentPrefs.getBoolean(IGNOREAI, false));
		checkBoxAutoCopy.setSelected(currentPrefs.getBoolean(AUTOCOPY, false));
		checkBoxBrokenRecordAlert.setSelected(currentPrefs.getBoolean(BROKENRECORDALERT, false));

		// Scan options
		checkBoxScanWriteDate.setSelected(currentPrefs.getBoolean(SCANWRITEDATE, false));
		checkBoxScanUseTabs.setSelected(currentPrefs.getBoolean(SCANUSETABS, false));
		comboBoxScanOcean.setSelectedIndex(currentPrefs.getInt(SCANOCEAN, 3));
		checkBoxScanStationHeaders.setSelected(currentPrefs.getBoolean(SCANSTATIONHEADERS, false));
		checkBoxScan2Station.setSelected(currentPrefs.getBoolean(SCANSTATION, false));
		checkBoxScan2Pirate.setSelected(currentPrefs.getBoolean(SCANPIRATE, false));
		checkBoxScan2Rating.setSelected(currentPrefs.getBoolean(SCANRATING, false));
		checkBoxScan2TokenScore.setSelected(currentPrefs.getBoolean(SCANTOKENSCORE, false));
		checkBoxScan2TokenArray.setSelected(currentPrefs.getBoolean(SCANTOKENARRAY, false));
		checkBoxScan2SheetCombo.setSelected(currentPrefs.getBoolean(SCANSHEETCOMBO, false));
		checkBoxScan2ScorePerMinute.setSelected(currentPrefs.getBoolean(SCANSCOREPERMINUTE, false));

		// Scoring options
		checkBoxScoreWriteDate.setSelected(currentPrefs.getBoolean(SCOREWRITEDATE, false));
		checkBoxScoreUseTabs.setSelected(currentPrefs.getBoolean(SCOREUSETABS, false));
		comboBoxScoreOcean.setSelectedIndex(currentPrefs.getInt(SCOREOCEAN, 3));
		checkBoxScoreStationHeaders.setSelected(currentPrefs.getBoolean(SCORESTATIONHEADERS, false));
		checkBoxScore2Station.setSelected(currentPrefs.getBoolean(SCORESTATION, false));
		checkBoxScore2Pirate.setSelected(currentPrefs.getBoolean(SCOREPIRATE, false));
		checkBoxScore2Rating.setSelected(currentPrefs.getBoolean(SCORERATING, false));
		checkBoxScore2TokenScore.setSelected(currentPrefs.getBoolean(SCORETOKENSCORE, false));
		checkBoxScore2TokenArray.setSelected(currentPrefs.getBoolean(SCORETOKENARRAY, false));
		checkBoxScore2SheetCombo.setSelected(currentPrefs.getBoolean(SCORESHEETCOMBO,false));

		// Token values (profile-specific)
		formattedTextFieldTokenCircle.setValue(currentPrefs.getInt(TOKENCIRCLE, 1));
		formattedTextFieldTokenDiamond.setValue(currentPrefs.getInt(TOKENDIAMOND, 1));
		formattedTextFieldTokenPlus.setValue(currentPrefs.getInt(TOKENPLUS, 1));
		formattedTextFieldTokenCross.setValue(currentPrefs.getInt(TOKENCROSS, 1));
		formattedTextFieldTokenThrall.setValue(currentPrefs.getInt(TOKENTHRALL, 1));
		formattedTextFieldCannonball.setValue(currentPrefs.getInt(TOKENCANNONBALL, 1));

		// Chest values (profile-specific)
		formattedTextFieldSmallChest.setValue(currentPrefs.getInt(CHESTSMALL, 1));
		formattedTextFieldMediumChest.setValue(currentPrefs.getInt(CHESTMEDIUM, 2));
		formattedTextFieldLargeChest.setValue(currentPrefs.getInt(CHESTLARGE, 3));

		// GLOBAL SETTINGS - CC Records (Entry)
		formattedTextFieldEntryIndividual.setValue(globalPrefs.getInt(CCENTRYINDIVIDUAL, 0));
		formattedTextFieldEntry7.setValue(globalPrefs.getInt(CCENTRY7, 0));
		formattedTextFieldEntry6.setValue(globalPrefs.getInt(CCENTRY6, 0));
		formattedTextFieldEntry5.setValue(globalPrefs.getInt(CCENTRY5, 0));
		formattedTextFieldEntry4.setValue(globalPrefs.getInt(CCENTRY4, 0));
		formattedTextFieldEntry3.setValue(globalPrefs.getInt(CCENTRY3, 0));
		formattedTextFieldEntry2.setValue(globalPrefs.getInt(CCENTRY2, 0));
		formattedTextFieldEntry1.setValue(globalPrefs.getInt(CCENTRY1, 0));

		// GLOBAL SETTINGS - CC Records (Forage)
		formattedTextFieldForageIndividual.setValue(globalPrefs.getInt(CCFORAGEINDIVIDUAL, 0));
		formattedTextFieldForage7.setValue(globalPrefs.getInt(CCFORAGE7, 0));
		formattedTextFieldForage6.setValue(globalPrefs.getInt(CCFORAGE6, 0));
		formattedTextFieldForage5.setValue(globalPrefs.getInt(CCFORAGE5, 0));
		formattedTextFieldForage4.setValue(globalPrefs.getInt(CCFORAGE4, 0));
		formattedTextFieldForage3.setValue(globalPrefs.getInt(CCFORAGE3, 0));
		formattedTextFieldForage2.setValue(globalPrefs.getInt(CCFORAGE2, 0));
		formattedTextFieldForage1.setValue(globalPrefs.getInt(CCFORAGE1, 0));

		// GLOBAL SETTINGS - Score Records (Single DR)
		formattedTextFieldSingleDRIndividual.setValue(globalPrefs.getDouble(SINGLEDRINDIVIDUAL, 0));
		formattedTextFieldSingleDR7.setValue(globalPrefs.getDouble(SINGLEDR7, 0));
		formattedTextFieldSingleDR6.setValue(globalPrefs.getDouble(SINGLEDR6, 0));
		formattedTextFieldSingleDR5.setValue(globalPrefs.getDouble(SINGLEDR5, 0));
		formattedTextFieldSingleDR4.setValue(globalPrefs.getDouble(SINGLEDR4, 0));
		formattedTextFieldSingleDR3.setValue(globalPrefs.getDouble(SINGLEDR3, 0));
		formattedTextFieldSingleDR2.setValue(globalPrefs.getDouble(SINGLEDR2, 0));
		formattedTextFieldSingleDR1.setValue(globalPrefs.getDouble(SINGLEDR1, 0));

		// GLOBAL SETTINGS - Score Records (Full Entry)
		formattedTextFieldFullEntryIndividual.setValue(globalPrefs.getDouble(FULLENTRYINDIVIDUAL, 0));
		formattedTextFieldFullEntry7.setValue(globalPrefs.getDouble(FULLENTRY7, 0));
		formattedTextFieldFullEntry6.setValue(globalPrefs.getDouble(FULLENTRY6, 0));
		formattedTextFieldFullEntry5.setValue(globalPrefs.getDouble(FULLENTRY5, 0));
		formattedTextFieldFullEntry4.setValue(globalPrefs.getDouble(FULLENTRY4, 0));
		formattedTextFieldFullEntry3.setValue(globalPrefs.getDouble(FULLENTRY3, 0));
		formattedTextFieldFullEntry2.setValue(globalPrefs.getDouble(FULLENTRY2, 0));
		formattedTextFieldFullEntry1.setValue(globalPrefs.getDouble(FULLENTRY1, 0));

		// GLOBAL SETTINGS - Lair and Wolves records (0 = not set)
		for (int i = 0; i < RecordChecker.LAIR_CREW_SIZES.length; i++) {
			formattedTextFieldLairICs[i].setValue(globalPrefs.getInt(LAIRICS + RecordChecker.LAIR_CREW_SIZES[i], 0));
			formattedTextFieldLairChests[i].setValue(globalPrefs.getInt(LAIRCHESTS + RecordChecker.LAIR_CREW_SIZES[i], 0));
		}
		formattedTextFieldLairTotalChests.setValue(globalPrefs.getInt(LAIRTOTALCHESTS, 0));
		formattedTextFieldLairFray.setValue(globalPrefs.getInt(LAIRFRAY, 0));
		formattedTextFieldLairIC.setValue(globalPrefs.getInt(LAIRIC, 0));
		formattedTextFieldLairCarpScore.setValue(globalPrefs.getInt(LAIRCARPSCORE, 0));
		formattedTextFieldLairCarpHoles.setValue(globalPrefs.getInt(LAIRCARPHOLES, 0));
		formattedTextFieldLairVampireProof.setValue(globalPrefs.getInt(LAIRVAMPIREPROOF, 0));
		formattedTextFieldWolvesFurrballs4.setValue(globalPrefs.getInt(WOLVESFURRBALLS4, 0));
	}

	/**
	 * Saves all current settings to the current profile
	 */
	private void saveCurrentSettings() {
		if (currentPrefs == null) {
			return;
		}

		try {
			// Save all settings (same as buttonSavePrefs but to currentPrefs)
			currentPrefs.putBoolean(AUTOCOPY, checkBoxAutoCopy.isSelected());
			currentPrefs.putBoolean(BROKENRECORDALERT, checkBoxBrokenRecordAlert.isSelected());
			currentPrefs.putBoolean(DEBUG, checkBoxDebug.isSelected());
			currentPrefs.putBoolean(IGNOREAI, checkBoxIgnoreAI.isSelected());

			// ... (copy all the put statements from buttonSavePrefs) ...

			currentPrefs.putBoolean(SCANWRITEDATE, checkBoxScanWriteDate.isSelected());
			currentPrefs.putBoolean(SCANUSETABS, checkBoxScanUseTabs.isSelected());
			currentPrefs.putInt(SCANOCEAN, comboBoxScanOcean.getSelectedIndex());
			currentPrefs.putBoolean(SCANSTATIONHEADERS, checkBoxScanStationHeaders.isSelected());
			currentPrefs.putBoolean(SCANSTATION, checkBoxScan2Station.isSelected());
			currentPrefs.putBoolean(SCANPIRATE, checkBoxScan2Pirate.isSelected());
			currentPrefs.putBoolean(SCANRATING, checkBoxScan2Rating.isSelected());
			currentPrefs.putBoolean(SCANTOKENSCORE, checkBoxScan2TokenScore.isSelected());
			currentPrefs.putBoolean(SCANTOKENARRAY, checkBoxScan2TokenArray.isSelected());
			currentPrefs.putBoolean(SCANSHEETCOMBO, checkBoxScan2SheetCombo.isSelected());
			currentPrefs.putBoolean(SCANSCOREPERMINUTE, checkBoxScan2ScorePerMinute.isSelected());

			currentPrefs.putBoolean(SCOREWRITEDATE, checkBoxScoreWriteDate.isSelected());
			currentPrefs.putBoolean(SCOREUSETABS, checkBoxScoreUseTabs.isSelected());
			currentPrefs.putInt(SCOREOCEAN, comboBoxScoreOcean.getSelectedIndex());
			currentPrefs.putBoolean(SCORESTATIONHEADERS, checkBoxScoreStationHeaders.isSelected());
			currentPrefs.putBoolean(SCORESTATION, checkBoxScore2Station.isSelected());
			currentPrefs.putBoolean(SCOREPIRATE, checkBoxScore2Pirate.isSelected());
			currentPrefs.putBoolean(SCORERATING, checkBoxScore2Rating.isSelected());
			currentPrefs.putBoolean(SCORETOKENSCORE, checkBoxScore2TokenScore.isSelected());
			currentPrefs.putBoolean(SCORETOKENARRAY, checkBoxScore2TokenArray.isSelected());
			currentPrefs.putBoolean(SCORESHEETCOMBO, checkBoxScore2SheetCombo.isSelected());

			currentPrefs.putInt(TOKENCIRCLE, Integer.parseInt(formattedTextFieldTokenCircle.getText().replaceAll(",", "")));
			currentPrefs.putInt(TOKENDIAMOND, Integer.parseInt(formattedTextFieldTokenDiamond.getText().replaceAll(",", "")));
			currentPrefs.putInt(TOKENPLUS, Integer.parseInt(formattedTextFieldTokenPlus.getText().replaceAll(",", "")));
			currentPrefs.putInt(TOKENCROSS, Integer.parseInt(formattedTextFieldTokenCross.getText().replaceAll(",", "")));
			currentPrefs.putInt(TOKENTHRALL, Integer.parseInt(formattedTextFieldTokenThrall.getText().replaceAll(",", "")));

			currentPrefs.putInt(TOKENCANNONBALL, Integer.parseInt(formattedTextFieldCannonball.getText().replaceAll(",", "")));

			currentPrefs.putInt(CHESTSMALL, Integer.parseInt(formattedTextFieldSmallChest.getText().replaceAll(",", "")));
			currentPrefs.putInt(CHESTMEDIUM, Integer.parseInt(formattedTextFieldMediumChest.getText().replaceAll(",", "")));
			currentPrefs.putInt(CHESTLARGE, Integer.parseInt(formattedTextFieldLargeChest.getText().replaceAll(",", "")));

			// GLOBAL SETTINGS - CC Records (Entry)
			globalPrefs.putInt(CCENTRYINDIVIDUAL, Integer.parseInt(formattedTextFieldEntryIndividual.getText().replaceAll(",", "")));
			globalPrefs.putInt(CCENTRY7, Integer.parseInt(formattedTextFieldEntry7.getText().replaceAll(",", "")));
			globalPrefs.putInt(CCENTRY6, Integer.parseInt(formattedTextFieldEntry6.getText().replaceAll(",", "")));
			globalPrefs.putInt(CCENTRY5, Integer.parseInt(formattedTextFieldEntry5.getText().replaceAll(",", "")));
			globalPrefs.putInt(CCENTRY4, Integer.parseInt(formattedTextFieldEntry4.getText().replaceAll(",", "")));
			globalPrefs.putInt(CCENTRY3, Integer.parseInt(formattedTextFieldEntry3.getText().replaceAll(",", "")));
			globalPrefs.putInt(CCENTRY2, Integer.parseInt(formattedTextFieldEntry2.getText().replaceAll(",", "")));
			globalPrefs.putInt(CCENTRY1, Integer.parseInt(formattedTextFieldEntry1.getText().replaceAll(",", "")));

			// GLOBAL SETTINGS - CC Records (Forage)
			globalPrefs.putInt(CCFORAGEINDIVIDUAL, Integer.parseInt(formattedTextFieldForageIndividual.getText().replaceAll(",", "")));
			globalPrefs.putInt(CCFORAGE7, Integer.parseInt(formattedTextFieldForage7.getText().replaceAll(",", "")));
			globalPrefs.putInt(CCFORAGE6, Integer.parseInt(formattedTextFieldForage6.getText().replaceAll(",", "")));
			globalPrefs.putInt(CCFORAGE5, Integer.parseInt(formattedTextFieldForage5.getText().replaceAll(",", "")));
			globalPrefs.putInt(CCFORAGE4, Integer.parseInt(formattedTextFieldForage4.getText().replaceAll(",", "")));
			globalPrefs.putInt(CCFORAGE3, Integer.parseInt(formattedTextFieldForage3.getText().replaceAll(",", "")));
			globalPrefs.putInt(CCFORAGE2, Integer.parseInt(formattedTextFieldForage2.getText().replaceAll(",", "")));
			globalPrefs.putInt(CCFORAGE1, Integer.parseInt(formattedTextFieldForage1.getText().replaceAll(",", "")));

			// GLOBAL SETTINGS - Score Records (Single DR)
			globalPrefs.putDouble(SINGLEDRINDIVIDUAL, Double.parseDouble(formattedTextFieldSingleDRIndividual.getText().replaceAll(",", "")));
			globalPrefs.putDouble(SINGLEDR7, Double.parseDouble(formattedTextFieldSingleDR7.getText().replaceAll(",", "")));
			globalPrefs.putDouble(SINGLEDR6, Double.parseDouble(formattedTextFieldSingleDR6.getText().replaceAll(",", "")));
			globalPrefs.putDouble(SINGLEDR5, Double.parseDouble(formattedTextFieldSingleDR5.getText().replaceAll(",", "")));
			globalPrefs.putDouble(SINGLEDR4, Double.parseDouble(formattedTextFieldSingleDR4.getText().replaceAll(",", "")));
			globalPrefs.putDouble(SINGLEDR3, Double.parseDouble(formattedTextFieldSingleDR3.getText().replaceAll(",", "")));
			globalPrefs.putDouble(SINGLEDR2, Double.parseDouble(formattedTextFieldSingleDR2.getText().replaceAll(",", "")));
			globalPrefs.putDouble(SINGLEDR1, Double.parseDouble(formattedTextFieldSingleDR1.getText().replaceAll(",", "")));

			// GLOBAL SETTINGS - Score Records (Full Entry)
			globalPrefs.putDouble(FULLENTRYINDIVIDUAL, Double.parseDouble(formattedTextFieldFullEntryIndividual.getText().replaceAll(",", "")));
			globalPrefs.putDouble(FULLENTRY7, Double.parseDouble(formattedTextFieldFullEntry7.getText().replaceAll(",", "")));
			globalPrefs.putDouble(FULLENTRY6, Double.parseDouble(formattedTextFieldFullEntry6.getText().replaceAll(",", "")));
			globalPrefs.putDouble(FULLENTRY5, Double.parseDouble(formattedTextFieldFullEntry5.getText().replaceAll(",", "")));
			globalPrefs.putDouble(FULLENTRY4, Double.parseDouble(formattedTextFieldFullEntry4.getText().replaceAll(",", "")));
			globalPrefs.putDouble(FULLENTRY3, Double.parseDouble(formattedTextFieldFullEntry3.getText().replaceAll(",", "")));
			globalPrefs.putDouble(FULLENTRY2, Double.parseDouble(formattedTextFieldFullEntry2.getText().replaceAll(",", "")));
			globalPrefs.putDouble(FULLENTRY1, Double.parseDouble(formattedTextFieldFullEntry1.getText().replaceAll(",", "")));

			// GLOBAL SETTINGS - Lair and Wolves records
			for (int i = 0; i < RecordChecker.LAIR_CREW_SIZES.length; i++) {
				globalPrefs.putInt(LAIRICS + RecordChecker.LAIR_CREW_SIZES[i], intOf(formattedTextFieldLairICs[i], 0));
				globalPrefs.putInt(LAIRCHESTS + RecordChecker.LAIR_CREW_SIZES[i], intOf(formattedTextFieldLairChests[i], 0));
			}
			globalPrefs.putInt(LAIRTOTALCHESTS, intOf(formattedTextFieldLairTotalChests, 0));
			globalPrefs.putInt(LAIRFRAY, intOf(formattedTextFieldLairFray, 0));
			globalPrefs.putInt(LAIRIC, intOf(formattedTextFieldLairIC, 0));
			globalPrefs.putInt(LAIRCARPSCORE, intOf(formattedTextFieldLairCarpScore, 0));
			globalPrefs.putInt(LAIRCARPHOLES, intOf(formattedTextFieldLairCarpHoles, 0));
			globalPrefs.putInt(LAIRVAMPIREPROOF, intOf(formattedTextFieldLairVampireProof, 0));
			globalPrefs.putInt(WOLVESFURRBALLS4, intOf(formattedTextFieldWolvesFurrballs4, 0));

			// GLOBAL SETTINGS - Window size
			globalPrefs.putInt("width", getWidth());
			globalPrefs.putInt("height", getHeight());

			currentPrefs.flush();
			globalPrefs.flush();
		} catch (BackingStoreException e) {
			printStatus("Error saving settings: " + e.getMessage(), false);
		}
	}

	/**
	 * Checks if a preference key should be stored globally (not per-profile)
	 */
	private boolean isGlobalSetting(String key) {
		for (String globalKey : GLOBAL_SETTINGS) {
			if (globalKey.equals(key)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Gets the appropriate preferences node for a given key
	 */
	private Preferences getPrefsForKey(String key) {
		return isGlobalSetting(key) ? globalPrefs : currentPrefs;
	}

}
