//Originally made by Tanonev.
/*Small(ish) edit by Sylcium.
 * 
 * Known bugs
 * -visual glitch when highlighting text in the main scrollpane when there is more than ~100 lines. functionally it works fine.
 * -supposedly saving forage only DR causes a crash. haven't tested this myself.
 * -saying "/e has left the vessel." will get picked up by getStayers. additionally someone saying:
 * 		"Somedick says, "blah blah blah blah
 * 		[12:34:56] Someoneelse has come aboard.
 * 		[98:76:54] Otherdude has left the vessel.
 * 		blah blah blah""
 * 		the only way do deal with this would be to make your own chatlog that logs more data (which chat, player message/game message, etc.) to make it possible to remove false positives.
 * 
 * 
 * 
 */


package main.java.general;

import javax.swing.*;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class DRGizmo {
	public static final String CHATLOG = "chatlog";
	public static final String DRLOG = "drlog";

	Window window;
	JTextArea status;
	boolean debug;
	DRGroupData data = new DRGroupData();

	DRGizmo(Window window, JTextArea status, boolean writeDebug) {
		this.window = window;
		this.status = status;
		debug = writeDebug;
	}

	private void printStatus(String s, boolean isDebug) {
		if (!(isDebug) ||  ((isDebug) && (debug))) {
			status.append(s + System.getProperty("line.separator"));
			status.setCaretPosition(status.getText().length());
		}
	}
	
	
	private void printStatusStackTrace(Exception e) {
		StringWriter sw = new StringWriter();
		e.printStackTrace(new PrintWriter(sw));
		String exceptionAsString = sw.toString();
		
		printStatus(exceptionAsString, true);
	}


	private int getTotalScore(String station, int[] array) {
		if (array == null)
			return 0;
		
		int result = 0;
		if (station.matches("Foraging")) {
			for(int i = 0; i < array.length; i++) {
				result += array[i] * (i + 1);
			}
		} else if (station.matches("Carpentry") && array.length == 3) {
			result += array[0] * -1;
			result += array[1];
			result += array[2] * 2;
		} else if (station.matches("Navigation")) {
			result += array[0];
			result += array[1] * 2;
			result += array[2] * 3;
		} else {
			for(int i = 0; i < array.length; i++) {
				result += array[i];
			}
		}
		return result;
	}

	/** Duty report only, no booty fallback and no console output; null if none is open. */
	public DRGroupData findDutyReportQuietly() {
		try {
			if (findWolves()) return data;
			if (findDutyReport2()) return data;
		} catch (Exception ignored) { }
		return null;
	}

	public DRGroupData scanDutyReport() {
		return scanDutyReport(SpecialDrops.DEFAULTS);
	}

	DRGroupData scanDutyReport(List<SpecialDrops.Rule> specialDrops) {
		booty = null;
		try {
			// findDutyReport2 includes Steam client compatibility fixes and should always be used.
			if (findWolves())
				return data;
			if (findDutyReport2())
				return data;

			// No duty report open: treat the window as a booty division screen instead.
			printLootReport(specialDrops);

			return null;
		} catch (Exception e) {
			return null;
		}

	}

	public void scanAllNodes() {
		try {
			printAllNodes(window, 1, new StringBuilder());
		} catch (Exception e) {
			
		}

	}
	private static final String TWO_COLUMN_PANEL = "com.threerings.piracy.swing.TwoColumnPanel";

	private boolean findWolves() {
		data = new DRGroupData();
		printStatus("Scan Wolves: ", true);

		if (window == null) {
			printStatus("findWolves failed: no window", true);
			return false;
		}

		int wolfStations = 0;
		for (Container station : locateStations()) {
			if (!isWolfStation(station)) continue;
			processWolfStation(station);
			wolfStations++;
		}

		if (wolfStations == 0) {
			printStatus("findWolves failed: no werewolf stations (is the duty report open?)", true);
			return false;
		}
		printStatus("End of FindWolves", true);
		data.setActivity(RecordChecker.DEN);
		return true;
	}

	/** All duty stations under any TwoColumnPanel; falls back to a shape search if the class was renamed. */
	private List<Container> locateStations() {
		List<Container> panels = new ArrayList<>();
		findByClassName(window, TWO_COLUMN_PANEL, panels);

		List<Container> stations = new ArrayList<>();
		for (Container p : panels) collectStations(p, stations, 0, 4);

		if (stations.isEmpty() && window instanceof Container) {
			collectStations((Container) window, stations, 0, 30);
		}
		printStatus("panels=" + panels.size() + " stations=" + stations.size(), true);
		return stations;
	}

	private boolean isWolfStation(Container station) {
		for (int j = 1; j < station.getComponentCount(); j++) {
			Component tp = tokenPanelOf(station.getComponent(j));
			if (tp != null && counterType(tp).startsWith("WEREWOLF_")) return true;
		}
		return false;
	}

	private void processWolfStation(Container station) {
		String stationName = stationName(station);
		printStatus(stationName, true);

		for (int j = 1; j < station.getComponentCount(); j++) {
			try {
				Container row = (Container) station.getComponent(j);
				Container pirateData = (Container) row.getComponent(1);
				String name = ((JLabel) pirateData.getComponent(0)).getText();

				Component drLabel = pirateData.getComponent(1);
				String dr = readStatus(drLabel);


				Component tp = tokenPanelOf(row);
				String type = tp == null ? null : counterType(tp);
				int[] tokens = tp == null ? null : readTokens(tp);

				printStatus(name + " " + type + " " + Arrays.toString(tokens), true);
				data.put(stationName, name, dr, tokens, getWolfScore(type, tokens));
			} catch (Exception e) {
				printStatus("wolf row " + j + " failed in " + stationName + ": " + e, true);
			}
		}
	}

	/** er row -> [1] pirateData -> [2] token panel, or null if the row has none. */
	private Component tokenPanelOf(Component row) {
		if (!(row instanceof Container)) return null;
		Container r = (Container) row;
		if (r.getComponentCount() < 2 || !(r.getComponent(1) instanceof Container)) return null;
		Container pd = (Container) r.getComponent(1);
		return pd.getComponentCount() > 2 ? pd.getComponent(2) : null;
	}

	/** Reads the chest or puzzle counter enum without depending on its obfuscated field name. */
	private String counterType(Component panel) {
		for (Class<?> k = panel.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
			for (java.lang.reflect.Field f : k.getDeclaredFields()) {
				if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !f.getType().isEnum()) continue;
				try {
					f.setAccessible(true);
					Enum<?> counter = (Enum<?>) f.get(panel);
					if (counter == null) continue;
					String name = counter.name();
					if (name.endsWith("_CHESTS") || name.equals("WEREWOLF_MOONS")
							|| name.equals("VAMPIRATE_PATCHES")) return name;
				} catch (Exception ignored) { }
			}
		}
		return "";
	}

	private static final int[] WOLF_MOON_WEIGHTS  = {1, 2, 3};
	private static final int[] WOLF_CHEST_WEIGHTS = {1, 2, 3};

	private int getWolfScore(String type, int[] tokens) {
		if (tokens == null) return 0;
		int[] w = "WEREWOLF_MOONS".equals(type) ? WOLF_MOON_WEIGHTS : WOLF_CHEST_WEIGHTS;
		int score = 0;
		for (int i = 0; i < tokens.length; i++) score += tokens[i] * (i < w.length ? w[i] : 1);
		return score;
	}
	/** Depth-first search for containers whose class name matches exactly. */
	private void findByClassName(Component c, String className, List<Container> out) {
		if (c == null) return;
		if (c instanceof Container && c.getClass().getName().equals(className)) {
			out.add((Container) c);
			return;                                  // don't search inside a match
		}
		if (c instanceof Container) {
			Container cont = (Container) c;
			for (int i = 0; i < cont.getComponentCount(); i++) {
				findByClassName(cont.getComponent(i), className, out);
			}
		}
	}
	/** Status text (Brisk / Steady / Swift ...) from a YoMultiLineLabel. Only "_label" is a hardcoded name. */
	private String readStatus(Component drLabel) {
		Object inner = readFieldInHierarchy(drLabel, "_label");
		if (inner == null) return null;

		String found = null;
		for (Class<?> k = inner.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
			if (k.getName().startsWith("java.")) break;
			for (java.lang.reflect.Field f : k.getDeclaredFields()) {
				if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType() != String.class) continue;
				try {
					f.setAccessible(true);
					String s = (String) f.get(inner);
					if (s == null || s.trim().isEmpty()) continue;
					if (found == null) {
						found = s.trim();
					} else if (!found.equals(s.trim())) {
						printStatus("status ambiguous: '" + found + "' vs '" + s.trim() + "' (" + f.getName() + ")", true);
					}
				} catch (Exception ignored) { }
			}
		}
		return found;
	}

	private Object readFieldInHierarchy(Object o, String name) {
		for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
			try {
				java.lang.reflect.Field f = k.getDeclaredField(name);
				f.setAccessible(true);
				return f.get(o);
			} catch (NoSuchFieldException e) {
				// keep walking up
			} catch (Exception e) {
				return null;
			}
		}
		return null;
	}
	private boolean findDutyReport2() {
		data = new DRGroupData();
		printStatus("Scan Duty Report: ", true);

		if (window == null) {
			printStatus("findDutyReport failed: no window", true);
			return false;
		}

		List<Container> stations = locateStations();   // same helper findWolves uses
		if (stations.isEmpty()) {
			printStatus("findDutyReport failed: no stations (is the duty report open?)", true);
			return false;
		}

		for (Container station : stations) {
			processStation(station);
		}

		data.setActivity(RecordChecker.activityOf(data));
		printStatus("findDutyReport is true (" + data.getActivity() + ")", true);
		return true;
	}

	private final Set<Class<?>> dumped = new HashSet<>();

	/** Station = container whose child0 -> child1 -> child0 is a JLabel with text (the duty title). */
	private String stationName(Container c) {
		if (c.getComponentCount() < 1 || !(c.getComponent(0) instanceof Container)) return null;
		Container header = (Container) c.getComponent(0);
		if (header.getComponentCount() < 2 || !(header.getComponent(1) instanceof Container)) return null;
		Container titlePanel = (Container) header.getComponent(1);
		if (titlePanel.getComponentCount() < 1 || !(titlePanel.getComponent(0) instanceof JLabel)) return null;
		String t = ((JLabel) titlePanel.getComponent(0)).getText();
		return (t == null || t.trim().isEmpty()) ? null : t.trim();
	}

	private void collectStations(Container c, List<Container> out, int depth, int maxDepth) {
		if (c == null || depth > maxDepth) return;
		if (stationName(c) != null) { out.add(c); return; }
		for (int i = 0; i < c.getComponentCount(); i++) {
			Component child = c.getComponent(i);
			if (child instanceof Container) collectStations((Container) child, out, depth + 1, maxDepth);
		}
	}

	private void processStation(Container station) {
		String stationName = stationName(station);
		printStatus(stationName, true);
		for (int j = 1; j < station.getComponentCount(); j++) {
			try {
				processPirateRow(stationName, station.getComponent(j));
			} catch (Exception e) {
				printStatus("row " + j + " failed in " + stationName + ": " + e, true);
			}
		}
	}

	private void processPirateRow(String stationName, Component row) {
		Container pirateData = (Container) ((Container) row).getComponent(1);
		String name = ((JLabel) pirateData.getComponent(0)).getText();
		printStatus(name, true);

		String dr = readStatus(pirateData.getComponent(1));
		printStatus(dr, true);

		int[] tokens = null;
		if (pirateData.getComponentCount() > 2) {
			Component tokenPanel = pirateData.getComponent(2);
			switch (counterType(tokenPanel)) {
				case "CURSED_CHESTS" -> data.setActivity(RecordChecker.CI);
				case "VAMPIRATE_CHESTS", "VAMPIRATE_PATCHES" -> data.setActivity(RecordChecker.LAIR);
				case "WEREWOLF_CHESTS", "WEREWOLF_MOONS" -> data.setActivity(RecordChecker.DEN);
			}
			tokens = readTokens(tokenPanel);
			printStatus(Arrays.toString(tokens), true);
		}
		data.put(stationName, name, dr, tokens, getTotalScore(stationName, tokens));
	}

	/** Finds the first non-static List<int[]> field in the class hierarchy, so no field name is hardcoded. */
	@SuppressWarnings("unchecked")
	private int[] readTokens(Component tokenPanel) {
		List<int[]> rows = null;
		outer:
		for (Class<?> k = tokenPanel.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
			for (java.lang.reflect.Field f : k.getDeclaredFields()) {
				if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !List.class.isAssignableFrom(f.getType())) continue;
				try {
					f.setAccessible(true);
					Object v = f.get(tokenPanel);
					if (!(v instanceof List)) continue;
					boolean ok = true;
					for (Object o : (List<?>) v) if (!(o instanceof int[])) { ok = false; break; }
					if (ok) { rows = (List<int[]>) v; break outer; }
				} catch (Exception ignored) { }
			}
		}
		if (rows == null || rows.isEmpty()) return null;

		int width = 0;
		for (int[] r : rows) if (r != null) width = Math.max(width, r.length);
		int[] tokens = new int[width];
		for (int[] r : rows) if (r != null) for (int k = 0; k < r.length; k++) tokens[k] += r[k];
		return tokens;
	}

	public String scanAhoyTab() {
		try {
			if (findWhoResult())
				return "Howdy";
			return null;
		} catch (Exception e) {
			return null;
		}

	}

	// ---------------------------------------------------------------------------------------------
	// Booty division screen.
	// Matches the 28 Oct 2026 Gizmo.jar build, plus capture into BootyDivision for the export.
	// Layout, as seen through the component tree:
	//   content panel  : has a JLabel "N battles won" as a direct child
	//   money panel    : container holding a GoldBar; its children carry tooltips like "Total value"
	//   pairs panel    : alternating [name, amount] labels (the shares list)
	//   pirate panel   : [header(3 children, [1] is a button with the pirate name), slots]
	//   slot           : container whose last child is a YoMultiLineLabel; tooltip = item name
	// ---------------------------------------------------------------------------------------------

	/** Finds containers that have a "N battles won" label as a direct child (the booty division content). */
	private void collectByChildLabel(Component c, List<Container> out) {
		if (!(c instanceof Container)) return;
		Container cont = (Container) c;
		for (int i = 0; i < cont.getComponentCount(); i++) {
			Component ch = cont.getComponent(i);
			if (ch instanceof JLabel) {
				String t = textOf(ch);
				if (BATTLES_WON.matcher(t).matches()) { out.add(cont); return; }
			}
		}
		for (int i = 0; i < cont.getComponentCount(); i++)
			collectByChildLabel(cont.getComponent(i), out);
	}

	/** First container (depth-first) that directly holds a GoldBar. */
	private Container findMoneyPanel(Container c) {
		for (int i = 0; i < c.getComponentCount(); i++)
			if (c.getComponent(i).getClass().getSimpleName().equals("GoldBar")) return c;
		for (int i = 0; i < c.getComponentCount(); i++) {
			Component ch = c.getComponent(i);
			if (ch instanceof Container) { Container f = findMoneyPanel((Container) ch); if (f != null) return f; }
		}
		return null;
	}

	/** First container (other than exclude) whose children read as [name, amount] pairs. */
	private Container findPairsPanel(Container c, Container exclude) {
		if (c != exclude && looksLikePairs(c)) return c;
		for (int i = 0; i < c.getComponentCount(); i++) {
			Component ch = c.getComponent(i);
			if (ch instanceof Container) { Container f = findPairsPanel((Container) ch, exclude); if (f != null) return f; }
		}
		return null;
	}

	private boolean looksLikePairs(Container c) {
		int n = c.getComponentCount();
		if (n < 2 || n % 2 != 0) return false;
		for (int i = 0; i < n; i += 2) {
			String name = textOf(c.getComponent(i));
			String val = textOf(c.getComponent(i + 1));
			if (name.length() == 0 || parseAmount(name) >= 0 || parseAmount(val) < 0) return false;
		}
		return true;
	}

	/** "1,234,567" -> 1234567; anything that isn't a plain number -> -1. */
	private long parseAmount(String s) {
		if (s == null) return -1;
		String d = s.replace(",", "").trim();
		if (d.length() == 0) return -1;
		for (int i = 0; i < d.length(); i++) if (!Character.isDigit(d.charAt(i))) return -1;
		try { return Long.parseLong(d); } catch (NumberFormatException e) { return -1; }
	}

	/** Tooltip text with HTML stripped and whitespace collapsed, or "". */
	private String tipOf(Component c) {
		if (!(c instanceof JComponent)) return "";
		String t;
		try { t = ((JComponent) c).getToolTipText(); } catch (Exception e) { return ""; }
		if (t == null) return "";
		return t.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ").replaceAll("\\s+", " ").trim();
	}

	/** Text of the first non-empty button directly inside c (the pirate's name on a loot panel). */
	private String buttonNameOf(Component c) {
		if (!(c instanceof Container)) return null;
		Container h = (Container) c;
		for (int i = 0; i < h.getComponentCount(); i++) {
			if (h.getComponent(i) instanceof AbstractButton) {
				String t = ((AbstractButton) h.getComponent(i)).getText();
				if (t != null && t.trim().length() > 0) return t.trim();
			}
		}
		return null;
	}

	/** [header, slots] where header has 3 children with a named button at [1], and slots holds only slots/labels. */
	private boolean isPiratePanel(Container c) {
		if (c.getComponentCount() != 2 || !(c.getComponent(0) instanceof Container)
				|| !(c.getComponent(1) instanceof Container)) return false;
		Container header = (Container) c.getComponent(0);
		if (header.getComponentCount() != 3 || !(header.getComponent(1) instanceof AbstractButton)) return false;
		String n = ((AbstractButton) header.getComponent(1)).getText();
		if (n == null || n.trim().length() == 0) return false;

		Container slots = (Container) c.getComponent(1);
		int real = 0;
		for (int i = 0; i < slots.getComponentCount(); i++) {
			Component k = slots.getComponent(i);
			if (isSlot(k)) real++;
			else if (!(k instanceof JLabel)) return false;
		}
		return real > 0;
	}

	private boolean isSlot(Component c) {
		if (!(c instanceof Container)) return false;
		Container s = (Container) c;
		return s.getComponentCount() >= 2
				&& s.getComponent(s.getComponentCount() - 1).getClass().getSimpleName().equals("YoMultiLineLabel");
	}

	private void collectLootPanels(Component c, List<Container> out) {
		if (!(c instanceof Container)) return;
		Container k = (Container) c;
		if (isPiratePanel(k)) { out.add(k); return; }
		for (int i = 0; i < k.getComponentCount(); i++) collectLootPanels(k.getComponent(i), out);
	}

	private Container findMoneyPanelInWindow() {
		List<Container> contents = new ArrayList<>();
		collectByChildLabel(window, contents);
		return contents.isEmpty() ? null : findMoneyPanel(contents.get(0));
	}

	private static final Pattern BATTLES_WON =
			Pattern.compile("(?i)^(no|\\d+)\\s+battles?\\s+won$");

	private int emitted;
	private static final int MAX_LINES = 5000;

	private void emit(String s) {
		if (emitted++ < MAX_LINES) printStatus(s, true);
		else if (emitted == MAX_LINES + 1) printStatus("... output capped at " + MAX_LINES + " lines", true);
	}

	private static final String POT_LABEL = "Divided treasure";

	private void shout(String pirate, String text, int quantity) {
		booty.specialDrops.computeIfAbsent(pirate, name -> new LinkedHashMap<>()).merge(text, quantity, Integer::sum);
	}

	/** Prints the loot followed by the configured booty alerts. */
	private void printLootReport(List<SpecialDrops.Rule> specialDrops) {
		emitted = 0;
		booty = new BootyDivision();
		printPirateTips(specialDrops);
		printShoutouts(specialDrops);
	}

	/** What the last scan read from the booty screen, or null if no booty screen was found. */
	private BootyDivision booty;

	public BootyDivision getBootyDivision() {
		return booty == null || booty.isEmpty() ? null : booty;
	}

	private void printPirateTips(List<SpecialDrops.Rule> specialDrops) {
		List<Container> panels = new ArrayList<>();
		collectLootPanels(window, panels);

		for (Container p : panels) {
			String name = buttonNameOf(p.getComponent(0));

			Container slots = (Container) p.getComponent(1);
			BootyDivision.PirateLoot loot = new BootyDivision.PirateLoot(name);
			Map<String, Integer> counts = new LinkedHashMap<>();
			int shown = 0;
			for (int i = 0; i < slots.getComponentCount(); i++) {
				Component k = slots.getComponent(i);
				if (!isSlot(k)) continue;
				Container slot = (Container) k;
				shown++;
				String tip = slotTip(slot);
				if (tip.length() == 0) continue;
				int qty = slotQty(slot);
				bump(counts, tip, qty);
				boolean pot = isPot(slot);
				if (pot) bump(loot.potItems, tip, qty);
				else if (SpecialDrops.scopeOf(specialDrops, tip) != null) shout(name, tip, qty);
			}
			int hidden = hiddenCount(slots);
			loot.slots = shown;
			loot.hidden = hidden;
			loot.items.putAll(counts);
			if (booty != null) booty.pirates.add(loot);
			emit("== " + name + " (" + shown + " slots" + (hidden > 0 ? ", " + hidden + " HIDDEN by filter" : "") + ") ==");
			for (Map.Entry<String, Integer> e : counts.entrySet())
				emit((e.getValue() > 1 ? e.getValue() + "x " : "") + e.getKey());
		}
	}

	/** The shared pot slot is labelled "Divided treasure"; it isn't one pirate's drop. */
	private boolean isPot(Container slot) {
		String l = readStatus(slot.getComponent(slot.getComponentCount() - 1));
		return l != null && POT_LABEL.equalsIgnoreCase(l.trim());
	}

	private String slotTip(Component c) {
		return tipDeep(c, 0);
	}

	private String tipDeep(Component c, int depth) {
		String t = tipOf(c);
		if (t.length() > 0) return t;
		if (depth >= 3 || !(c instanceof Container)) return "";
		Container k = (Container) c;
		for (int i = 0; i < k.getComponentCount(); i++) {
			t = tipDeep(k.getComponent(i), depth + 1);
			if (t.length() > 0) return t;
		}
		return "";
	}

	private static final Pattern QTY = Pattern.compile("^×\\s*(\\d+)$");
	private static final Pattern HIDDEN = Pattern.compile("^(\\d+)\\s+hidden$", Pattern.CASE_INSENSITIVE);

	/** Stack size from a "×N" label anywhere in the slot, else 1. */
	private int slotQty(Component c) {
		if (c instanceof JLabel) {
			String t = ((JLabel) c).getText();
			if (t != null) {
				Matcher m = QTY.matcher(t.trim());
				if (m.matches()) return Integer.parseInt(m.group(1));
			}
		}
		if (c instanceof Container) {
			Container k = (Container) c;
			for (int i = 0; i < k.getComponentCount(); i++) {
				int q = slotQty(k.getComponent(i));
				if (q > 1) return q;
			}
		}
		return 1;
	}

	/** "N hidden" label shown when the loot filter hides items. */
	private int hiddenCount(Container slots) {
		for (int i = 0; i < slots.getComponentCount(); i++) {
			Component k = slots.getComponent(i);
			if (k instanceof JLabel) {
				String t = ((JLabel) k).getText();
				if (t != null) {
					Matcher m = HIDDEN.matcher(t.trim());
					if (m.matches()) return Integer.parseInt(m.group(1));
				}
			}
		}
		return 0;
	}

	private static void bump(Map<String, Integer> m, String k, int n) {
		Integer v = m.get(k);
		m.put(k, v == null ? n : v + n);
	}

	private String loggedInPirate() {
		if (window == null) return null;
		String pirate = window instanceof Frame frame ? DRGizmoMain.pirateInTitle(frame.getTitle()) : null;
		if (pirate != null || window.getAccessibleContext() == null) return pirate;
		return DRGizmoMain.pirateInTitle(window.getAccessibleContext().getAccessibleName());
	}

	private void printShoutouts(List<SpecialDrops.Rule> rules) {
		if (booty.specialDrops.isEmpty()) return;
		String ownPirate = rules.stream().anyMatch(rule -> rule.scope() == SpecialDrops.Scope.OWN) ? loggedInPirate() : null;
		boolean headingPrinted = false;
		for (Map.Entry<String, Map<String, Integer>> e : booty.specialDrops.entrySet()) {
			StringJoiner wins = new StringJoiner(", ");
			for (Map.Entry<String, Integer> it : e.getValue().entrySet()) {
				if (SpecialDrops.scopeOf(rules, it.getKey()) == SpecialDrops.Scope.OWN
						&& !e.getKey().equalsIgnoreCase(ownPirate)) continue;
				wins.add((it.getValue() > 1 ? it.getValue() + "x " : "") + it.getKey());
			}
			if (wins.length() == 0) continue;
			if (!headingPrinted) {
				printStatus("== " + SpecialDrops.TITLE + " ==", false);
				headingPrinted = true;
			}
			printStatus(e.getKey() + ": " + wins, false);
		}
	}

	/** getText() via reflection, so it works on JLabel, buttons and the client's own label classes. */
	private String textOf(Component c) {
		try {
			Method m = c.getClass().getMethod("getText");
			m.setAccessible(true);
			Object o = m.invoke(c);
			return o == null ? "" : o.toString().trim();
		} catch (Exception e) {
			return "";
		}
	}
	
	
	
	
	private boolean findWhoResult() {
		
		Component node = window;
		//had to change this to get it to work with steam
		/*
		printStatus("node 1: " + node.getClass().toString(), true);
		if (null == (node = descendNodes(node, new int[] {0, 1, 0, 0}))) {
			printStatus("findDutyReport failed 1", true);
			return false; 
		}
		*/
		
		
		//this gets us 90% of the way there.
		int[] path = scanRecursiveSearcher("class com.threerings.piracy.info.client.WhoInfoView");
		
		if (path == null) {
			printStatus("findWhoResult failed 0", true);
			return false;
		}
		

		
		
		if (null == (node = descendNodes(node, path))) {
			printStatus("findWhoResult failed 1", true);
			return false; 
		}
		
		
		printStatus("node 2: " + node.getClass().toString(), true);
		List<Component> handyTextPanes = findAllComponents(node, "class com.threerings.piracy.swing.HandyTextPane", new ArrayList<Component> ());
		
		
		printStatus("" + handyTextPanes.size(), true);
		for (Component c : handyTextPanes) {
			JEditorPane temp = (JEditorPane) c;
			printStatus(temp.getText(), false);
		}
		
		
		return false;
	}
	
	
	
	
	/**
	 * hopefully scans everything in the fucking game until it finds something with the string you are looking for
	 * @param searchFor
	 * @return some good info
	 */
	public int[] scanRecursiveSearcher(String searchFor) {
		try {
			int[] path = findClassRecursive(window, searchFor, 1, new ArrayList<Integer>());
			if (path != null) {
				return path;
			}
			return null;
		} catch (Exception e) {
			return null;
		}

	}
	
	
	
	
	
	/**
	 * searches every component looking for the specified one. Stops at the first instance of that component found.
	 * @param parent
	 * @param searchFor
	 * @param depth
	 * @param path
	 * @return Returns an int[] that will lead you to the found component from the initial parent
	 */
	private int[] findClassRecursive(Component parent, String searchFor, int depth, List<Integer> path) {
		
		
		Component[] children = ((Container) parent).getComponents();
		
		for (int i = 0; i < children.length; i++)
		{
			StringBuilder whitespace = new StringBuilder();
			for (int j = 0; j < depth; j++) {
				whitespace.append(" ");
			}

			
			if (children[i].getClass().toString().equals(searchFor)) {
				//printStatus("WINNER depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString(), true);
				path.add(i);
				return DRGizmoUtil.ToIntArray(path);
			} else {
				List<Integer> temp = new ArrayList<Integer>(path);
				temp.add(i);
				//printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString(), true);
				int[] result = findClassRecursive(children[i], searchFor, depth + 1, temp);
				if (result != null)
					return result;
			}
			
		}
		
		return null;
	}
	
	
	
	
	
	private List<Component> findAllComponents(Component parent, String searchFor, List<Component> results) {
		
		
		Component[] children = ((Container) parent).getComponents();
		
		for (int i = 0; i < children.length; i++)
		{
			if (children[i].getClass().toString().equals(searchFor))
				results.add(children[i]);
				
			findAllComponents(children[i], searchFor, results);
			
		}
		
		return results;
	}
	
	

	
	
	
	
	private void printAllNodes(Component parent, int depth, StringBuilder path) {
		
		Component[] children = ((Container) parent).getComponents();
		
		for (int i = 0; i < children.length; i++)
		{
			if (children[i] instanceof JLabel) {
				printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + (((JLabel) children[i]).getText()), true);
			} else if (children[i] instanceof JTextPane) {
				printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + (((JTextPane) children[i]).getText()), true);
			} else if (children[i] instanceof JEditorPane) {
				printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + (((JEditorPane) children[i]).getText()), true);
			} else if (children[i] instanceof JButton) {
				printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + (((JButton) children[i]).getText()), true);
			} else {
				printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString(), true);
			}
				
			printAllNodes(children[i], depth + 1, new StringBuilder(path).append(i + ", "));
			
		}
		
	}

	private Component descendNodes(Component parent, int[] path) {
		for(int i=0;i<path.length;i++) {
			printStatus("decendNodes: parent " + i + ": " + parent.getClass().toString(), true);
			for (int j = 0; j < ((Container) parent).getComponentCount(); j++) {
				printStatus("decendNodes: children " + i + ": " + ((Container) parent).getComponent(j).getClass().toString(), true);
			}
			if (null == (parent = descend(parent, path[i]))) return null;
		}
		return parent;
	}

	private Component descend(Component parent, int childNum) {
		if (parent == null) return null;
		if (!(parent instanceof Container)) return null;
		if (childNum >= ((Container) parent).getComponentCount()) return null;
		return ((Container) parent).getComponent(childNum);
	}
}
