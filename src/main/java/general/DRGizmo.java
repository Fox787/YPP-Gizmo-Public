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
import java.lang.reflect.InvocationTargetException;
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
		try {
			// findDutyReport2 includes Steam client compatibility fixes and should always be used.
			if (findWolves())
				return data;
			if (findDutyReport2())
				return data;

			// No duty report open: treat the window as a booty division screen instead.
			System.out.println("\n\n");
			printLootReport(null);

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
			if (tp != null && wolfCounterType(tp) != null) return true;
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
				String type = tp == null ? null : wolfCounterType(tp);
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

	/** Returns e.g. "WEREWOLF_MOONS" / "WEREWOLF_CHESTS" if the panel carries a werewolf counter enum, else null. */
	private String wolfCounterType(Component panel) {
		for (Class<?> k = panel.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
			for (java.lang.reflect.Field f : k.getDeclaredFields()) {
				if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || !f.getType().isEnum()) continue;
				try {
					f.setAccessible(true);
					Object v = f.get(panel);
					if (v != null && v.toString().startsWith("WEREWOLF")) return v.toString();
				} catch (Exception ignored) { }
			}
		}
		return null;
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
	private static final long SHOUT_TOTAL_VALUE = 500_000_000L;
	private static final long SHOUT_SHARE = 500_000L;

	/** An item worth calling out. min > 0 means group(1) of the regex must be at least min. */
	private static final class Shout {
		final Pattern pattern;
		final long min;

		Shout(String regex, long min) {
			this.pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
			this.min = min;
		}

		boolean hits(String tip) {
			Matcher m = pattern.matcher(tip);
			if (!m.find()) return false;
			if (min <= 0) return true;
			try { return Long.parseLong(m.group(1)) >= min; } catch (Exception e) { return false; }
		}
	}

	private static final List<Shout> SHOUTS = new ArrayList<>();
	static {
		SHOUTS.add(new Shout("Ippolito \\(", 0));
		SHOUTS.add(new Shout("Serpent \\(", 0));
		SHOUTS.add(new Shout("Lobster \\(", 0));
		SHOUTS.add(new Shout("Krakling \\(", 0));
		SHOUTS.add(new Shout("Ghostling \\(", 0));
		SHOUTS.add(new Shout("Bat \\(", 0));
		SHOUTS.add(new Shout("Squirrel \\(", 0));
		SHOUTS.add(new Shout("Karkinos \\(", 0));
		SHOUTS.add(new Shout("Gator \\(", 0));
		SHOUTS.add(new Shout("Ghost rat \\(", 0));
	}

	/** pirate -> (item -> count), in the order found. */
	private final Map<String, Map<String, Integer>> shouts = new LinkedHashMap<>();

	private void shout(String pirate, String text) {
		Map<String, Integer> m = shouts.get(pirate);
		if (m == null) { m = new LinkedHashMap<>(); shouts.put(pirate, m); }
		bump(m, text, 1);
	}

	/** Prints every pirate's loot (or only who's, if not null) followed by the special drops. */
	private void printLootReport(String who) {
		emitted = 0;
		shouts.clear();
		booty = new BootyDivision();
		// printBootyTips() and printShares() were present but not called in the jar build.
		printStatus("\n\n", false);
		printPirateTips(who);

		printStatus("\n\n", false);
		printShoutouts();
		booty.specialDrops.putAll(shouts);
	}

	/** What the last scan read from the booty screen, or null if no booty screen was found. */
	private BootyDivision booty;

	public BootyDivision getBootyDivision() {
		return booty == null || booty.isEmpty() ? null : booty;
	}

	private Container bootyContent() {
		List<Container> contents = new ArrayList<>();
		collectByChildLabel(window, contents);
		return contents.isEmpty() ? null : contents.get(0);
	}

	/** Money row: each child with a tooltip and a numeric value, e.g. "Total value: 123456". */
	private void printBootyTips() {
		Container content = bootyContent();
		Container money = content == null ? null : findMoneyPanel(content);
		if (money == null) { emit("booty: money row not found"); return; }

		for (int i = 0; i < money.getComponentCount(); i++) {
			Component c = money.getComponent(i);
			long v = parseAmount(textOf(c));
			String tip = tipOf(c);
			if (v < 0 || tip.length() == 0) continue;
			emit(tip + ": " + v);
			if (tip.toLowerCase().contains("total value") && v >= SHOUT_TOTAL_VALUE)
				shout("Voyage", "total value " + v);
		}
	}

	/** Shares list: "shares: name=amount name=amount ...". */
	private void printShares() {
		Container content = bootyContent();
		if (content == null) return;
		Container pairs = findPairsPanel(content, findMoneyPanel(content));
		if (pairs == null) return;

		StringBuilder sb = new StringBuilder("shares: ");
		for (int i = 0; i + 1 < pairs.getComponentCount(); i += 2) {
			String name = textOf(pairs.getComponent(i));
			long v = parseAmount(textOf(pairs.getComponent(i + 1)));
			sb.append(name).append('=').append(v).append(' ');
			if (v >= SHOUT_SHARE) shout(name, v + " Eights share");
		}
		emit(sb.toString());
	}

	private void printPirateTips(String who) {
		List<Container> panels = new ArrayList<>();
		collectLootPanels(window, panels);

		for (Container p : panels) {
			String name = buttonNameOf(p.getComponent(0));
			if (who != null && !who.equalsIgnoreCase(name)) continue;

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
				else checkShouts(name, tip);
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

	private void checkShouts(String pirate, String tip) {
		for (Shout s : SHOUTS) {
			if (s.hits(tip)) { shout(pirate, tip); return; }
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

	private void printShoutouts() {
		printStatus("== Special Drops ==", false);
		if (shouts.isEmpty()) { printStatus("(nothing flagged)", false); return; }
		for (Map.Entry<String, Map<String, Integer>> e : shouts.entrySet()) {
			StringBuilder sb = new StringBuilder(e.getKey()).append(": ");
			boolean first = true;
			for (Map.Entry<String, Integer> it : e.getValue().entrySet()) {
				if (!first) sb.append(", ");
				first = false;
				if (it.getValue() > 1) sb.append(it.getValue()).append("x ");
				sb.append(it.getKey());
			}
			printStatus(sb.toString(), false);
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
	
	

	
	
	
	
	/**
	 * this processes a "Who's online" given the right branch
	 * @param branch
	 */
	private void processWhoBranch(Container branch) {
		if (branch.getComponentCount() == 0) {
			printStatus("nothing to process", true);
			return;
		}
		
		
	}
	
	/**
	 * this processes a duty report given the right container
	 * todo make this method eat a twocolumnpanel instead of a difficult to track jpanel near the twocolumnpanel
	 * @param branch
	 */
	@SuppressWarnings("unchecked")
	private void processBranch(Container branch) {
		if (branch.getComponentCount() == 0) {
			printStatus("nothing to process", true);
			return;
		}

		printStatus("branches to process:", true);
		for (int j = 0; j < branch.getComponentCount(); j++) {
			printStatus(j + ": " + branch.getComponent(j).getClass().toString(), true);
		}
		for (int i = 0; i < branch.getComponentCount(); i++) {
			printStatus("branch " + i + ": " + branch.getComponent(i).getClass().toString(), true);
			Container station = (Container) descend(branch, i);
			Component stationLabel = descendNodes(station, new int[] {0, 1, 0});
			/*
			Component stationLabel = descend(station, 0);
			stationLabel = descend(stationLabel, 1);
			stationLabel = descend(stationLabel, 0);
			*/
			String stationName = ((JLabel) stationLabel).getText(); 
			printStatus(stationName, true);
			for (int j = 1; j < station.getComponentCount(); j++) {
				Component pirateData = descend(station, j);
				pirateData = descend(pirateData, 1);
				Component nameLabel = descend(pirateData, 0);
				String name = ((JLabel) nameLabel).getText();
				printStatus(name, true);
				Component drLabel = descend(pirateData, 1);
				Object innerLabel = getField(drLabel, "_label", Object.class);
				String dr = getField(innerLabel, "b", String.class);
				printStatus(dr, true);
				int[] tokens = null;
				Component tokenPanel = descend(pirateData, 2);
				if (tokenPanel != null) {
					ArrayList<int[]> tokensArrayList = getField(tokenPanel, "_rows", ArrayList.class);
					tokens = new int[tokensArrayList.get(0).length];
					
					//tokensArrayList has an array for every row of tokens
					for (int[] array : tokensArrayList) {
						for (int k = 0; k < array.length; k++) {
							tokens[k] += array[k];
						}
					}

					printStatus(Arrays.toString(tokens), true);
				}
				//printStatus(name + " " + dr + " " + Arrays.toString(tokens));
				data.put(stationName, name, dr, tokens, getTotalScore(stationName, tokens));
				//printStatus(name + " " + dr + " " + Arrays.toString(tokens));
			}
		}
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
			} else if (children[i].getClass().toString().equals("class com.threerings.piracy.swing.YoMultiLineLabel")) {
				Object innerLabel = getField(children[i], "_label", Object.class);
				String asdf = getField(innerLabel, "b", String.class);
				//printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + (((JLabel) innerLabel).getText()), true);
				printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + (asdf), true);
			} else if (children[i].getClass().toString().equals("class com.threerings.piracy.item.client.PoeContainer")) {
				printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + readPoeContainer(children[i]), true);
			} else if (children[i].getClass().toString().equals("class com.threerings.piracy.item.client.ItemContainer")) {
				try {
					printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + readItemContainer(children[i]), true);
					
				} catch (Exception e) {
					// TODO: handle exception
					printStatusStackTrace(e);
				}
			} else if (children[i].getClass().toString().equals("class com.threerings.piracy.item.client.InventoryItemContainer")) {
				try {
					printStatus("depth: " + depth + "{ " + path.toString().trim() + ")" + children[i].getClass().toString() + "\t\t" + readInventoryItemContainer(children[i]), true);
					
				} catch (Exception e) {
					// TODO: handle exception
					printStatusStackTrace(e);
				}
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
	
	private <T> T getField(Object o, String fieldName, Class<T> clazz) {
		Class<?> c = o.getClass();
		try {
			while (!c.equals(Object.class)) {
				final Field[] fields = c.getDeclaredFields();
				for (int i = 0; i < fields.length; ++i) {
					if (fieldName.equals(fields[i].getName())) {
						fields[i].setAccessible(true);
						Object ret = fields[i].get(o);
						try {
							return clazz.cast(ret);
						} catch (ClassCastException e) {
							return null;
						}
					}
				}
				c = c.getSuperclass();
			}
		} catch (Exception ignored) {}
		return null;
	}

	
	/**
	 * Pass this a poeContainer and it'll poop out the amount
	 * @return
	 */
	private int readPoeContainer(Object poeContainer) {
		
		Integer amount = getField(poeContainer, "_amount", Integer.class);
		return amount.intValue();
				
	}
	
	private String readItemContainer(Object itemContainer) {
		
		//get the _item field
		Object item = getField(itemContainer, "_item", Object.class);
		
		//now comes the tricky part, we have to get the name of the item
		//get the item class, this should be com.threerings.piracy.item.client.data.Item
		Class itemClass = item.getClass();
		
		//get the getName method
		Method methodGetName = null;
		try {
			methodGetName = itemClass.getMethod("getName", null);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		}
		
		//now we have to invoke the item.getName() method.
		String itemName = null;
		try {
			itemName = (String) methodGetName.invoke(item);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		}
				
		return itemName;
	}
		
	
	private String readInventoryItemContainer(Object inventoryItemContainer) {
		
		JPanel asdf = (JPanel) inventoryItemContainer;
		
		//replace breaks with newlines for fun
		String tooltipText = asdf.getToolTipText().replaceAll("<br>", "\n");
		
		//remove html tags
		//  this has its issues (items with custom messages and maybe more) but its good enough
		tooltipText = tooltipText.replaceAll("\\<.*?\\>", "");
		
		//printStatus(tooltipText, true);
		
		//get the _item field
		Object item = getField(inventoryItemContainer, "_item", Object.class);
		
		//now comes the tricky part, we have to get the name of the item
		//get the item class, this should be com.threerings.piracy.item.client.data.Item
		Class itemClass = item.getClass();
		
		//get the getName method
		Method methodGetName = null;
		try {
			methodGetName = itemClass.getMethod("getName", null);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		}
		
		//now we have to invoke the item.getName() method.
		String itemName = null;
		try {
			itemName = (String) methodGetName.invoke(item);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		}
		
		//get the ToString method
		Method methodToString = null;
		try {
			methodToString = itemClass.getMethod("toString", null);
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (NoSuchMethodException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		}
		
		//now we have to invoke the item.getName() method.
		String itemToString = null;
		try {
			itemToString = (String) methodToString.invoke(item);
		} catch (IllegalArgumentException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (IllegalAccessException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		} catch (InvocationTargetException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		}
				
		return tooltipText + ", " + itemName + ", " + itemToString;
		
	}

	
	private void printAllFieldsAndMethods(Class clazz) {
		printStatus("\n\n\nFields", true);
		printAllFields(clazz);
		printStatus("\nMethods", true);
		printAllMethods(clazz);
	}
	
	private void printAllFields(Class clazz) {
		//Class<?> clazz = o.getClass();
		
		/*
		Class<?> c = o.getClass();
		try {
			while (!c.equals(Object.class)) {
				final Field[] fields = c.getDeclaredFields();
				for (int i = 0; i < fields.length; ++i) {
					if (fieldName.equals(fields[i].getName())) {
						fields[i].setAccessible(true);
						Object ret = fields[i].get(o);
						try {
							return clazz.cast(ret);
						} catch (ClassCastException e) {
							return null;
						}
					}
				}
				c = c.getSuperclass();
			}
		} catch (Exception ignored) {}
		return null;
		*/
		
		

		try {
			Field[] fields = clazz.getFields();
	         for(int i = 0; i < fields.length; i++) {
	            printStatus(fields[i].getName(), true);
	            printStatus(fields[i].toString(), true);
	         }
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		}
	}
	
	
	private void printAllMethods(Class clazz) {
		try {
			Method[] methods = clazz.getMethods();
	         for(int i = 0; i < methods.length; i++) {
	            printStatus(methods[i].getName(), true);
	            printStatus(methods[i].toString(), true);
	         }
		} catch (SecurityException e) {
			// TODO Auto-generated catch block
			printStatusStackTrace(e);
		}
	}
	
	
	
}





