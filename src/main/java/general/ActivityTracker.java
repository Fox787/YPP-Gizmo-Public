package main.java.general;

import java.awt.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Times how long each pirate spends on each station, so a duty report's scores can be turned into
 * score per minute.
 *
 * Read-only. Four times a second, at Woodhallow, it reads the scene's occupant list: one
 * YoOccupantInfo per pirate in the scene (on screen or not), whose public field "activity" is the
 * code of the symbol over their head. Only changes to a different non-blank symbol are kept.
 *
 * Timing uses the station period from the chat log (StationClock): every pirate on a station gets
 * the whole period, from "To yer stations!" to the end, except that switching to the other
 * station's symbol splits the period at that moment. Nothing else counts: blank symbols, idling,
 * leaving the scene or the puzzle being finished early don't shorten anyone's time, so idling can't
 * raise a rate. Time before a pirate's first symbol in the period goes to that first station.
 *
 * Activity codes are numbers. Which code is which station is learned from duty reports: for each
 * station, the code its pirates spent longest on wins, and is remembered for next time.
 */
public final class ActivityTracker {

	/** A pirate's symbol changes, oldest first: {time, code}. */
	static final class PirateState {
		final List<long[]> changes = new ArrayList<>();
		long lastSeen;
		/** The symbol over their head right now (0 = none), for the live display. */
		int showing;

		int current() { return changes.isEmpty() ? 0 : (int) changes.get(changes.size() - 1)[1]; }

		/** Milliseconds on each code within [start, end]. */
		Map<Integer, Long> times(long start, long end) {
			Map<Integer, Long> out = new HashMap<>();
			if (end <= start) return out;
			// Symbol at the start: the last change before it, else the first change inside the window.
			int code = 0;
			int i = 0;
			for (; i < changes.size() && changes.get(i)[0] <= start; i++) code = (int) changes.get(i)[1];
			if (code == 0 && i < changes.size() && changes.get(i)[0] < end) code = (int) changes.get(i)[1];
			if (code == 0) return out;
			long from = start;
			for (; i < changes.size() && changes.get(i)[0] < end; i++) {
				long t = changes.get(i)[0];
				int c = (int) changes.get(i)[1];
				if (c == code) continue;
				out.merge(code, t - from, Long::sum);
				code = c;
				from = t;
			}
			out.merge(code, end - from, Long::sum);
			return out;
		}

		int longestCode(long start, long end) {
			int best = 0;
			long bestT = 0;
			for (Map.Entry<Integer, Long> e : times(start, end).entrySet())
				if (e.getValue() > bestT) { best = e.getKey(); bestT = e.getValue(); }
			return best;
		}
	}

	static final long MAX_PERIOD_MS = 5 * 60_000L;
	static final long OTHERS_LAG_MS = 300;
	/** Station periods are 2 or 5 minutes; a measured length this close to one is snapped to it. */
	static final long SNAP_MS = 15_000;

	/** The pirate running this client, whose symbols aren't delayed by the server. */
	private String self = "";
	public void setSelf(String pirate) { self = pirate == null ? "" : pirate; }

	static long snap(long len) {
		for (long target : new long[]{2 * 60_000L, 5 * 60_000L})
			if (Math.abs(len - target) <= SNAP_MS) return target;
		return len;
	}
	static final int POLL_MS = 250;   // 4 times a second; the scene search stays at every 5 s

	private final Window window;
	private final Preferences prefs;
	private final StationClock clock;
	private final javax.swing.Timer timer;
	private final Map<String, PirateState> pirates = new LinkedHashMap<>();
	private Object scene;
	private long lastSearch;
	private String lastReportKey;
	private String lastReportText;

	public ActivityTracker(Window window, Preferences prefs, StationClock clock) {
		this.window = window;
		this.prefs = prefs;
		this.clock = clock;
		this.timer = new javax.swing.Timer(POLL_MS, e -> poll(System.currentTimeMillis()));
	}

	public void start() { timer.start(); }
	public void stop() { timer.stop(); }

	// ---------------------------------------------------------------- polling

	private void poll(long now) {
		try {
			if (now - lastSearch > 5000 || scene == null) {
				lastSearch = now;
				Object found = findScene(window, 0);
				if (found != null) scene = found; // while puzzling the scene view is hidden; keep the last one
			}
			String kind = scene == null ? null : sceneKind(scene);
			if (kind == null) return;
			if (!kind.equals(RecordChecker.DEN)) {
				// Cursed Isles / Vampire Lair: only note that station symbols are showing, for auto scan.
				Object set = scene.getClass().getField("occupantInfo").get(scene);
				if (!(set instanceof Iterable)) return;
				for (Object info : (Iterable<?>) set) {
					if (observe(info, now) != 0) { lastSymbolAt = now; lastSymbolKind = kind; }
				}
				return;
			}
			if (now - lastAtWoodhallow > ARRIVAL_GAP_MS && onArrival != null) onArrival.accept(lastAtWoodhallow == 0);
			lastAtWoodhallow = now;
			if (fraying(scene)) return;
			Object set = scene.getClass().getField("occupantInfo").get(scene);
			if (!(set instanceof Iterable)) return;
			for (Object info : (Iterable<?>) set) {
				if (observe(info, now) != 0) { lastSymbolAt = now; lastSymbolKind = kind; }
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// A client update can rename things; just stop counting rather than break anything.
		}
	}

	/** Feeds one occupant; returns the symbol code over their head (0 = none, NPC or unreadable). */
	private int observe(Object info, long now) throws ReflectiveOperationException {
		if (isNpc(info)) return 0;
		Field f;
		try { f = info.getClass().getField("activity"); } catch (NoSuchFieldException e) { return 0; } // NPCs, pets
		int code = ((Number) f.get(info)).intValue();
		Object name = info.getClass().getField("username").get(info);
		if (name == null) return 0;
		update(name.toString(), code, now);
		return code;
	}

	/**
	 * Wolves, pets and other NPCs are NPPOccupantInfo subclasses; bots carry a bot role
	 * (YoOccupantInfo.BOT = 4, SKILLED_BOT = 12, DANGEROUS_BOT = 13). Only real pirates are tracked.
	 */
	static boolean isNpc(Object info) {
		for (Class<?> k = info.getClass(); k != null; k = k.getSuperclass())
			if (k.getSimpleName().equals("NPPOccupantInfo")) return true;
		try {
			int role = ((Number) info.getClass().getField("role").get(info)).intValue();
			return role == 4 || role == 12 || role == 13;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return false;
		}
	}

	/** Only Woodhallow is timed. */
	static boolean isWoodhallow(Object scene) {
		return RecordChecker.DEN.equals(sceneKind(scene));
	}

	/**
	 * Which station activity a scene is: RecordChecker.DEN (Woodhallow, WerewolfIsleSceneObject),
	 * CI (Cursed Isles forage, GauntletSceneObject) or LAIR (VampireLairSceneObject); null for anything else.
	 */
	static String sceneKind(Object scene) {
		for (Class<?> k = scene.getClass(); k != null; k = k.getSuperclass()) {
			switch (k.getSimpleName()) {
				case "WerewolfIsleSceneObject": return RecordChecker.DEN;
				case "GauntletSceneObject": return RecordChecker.CI;
				case "VampireLairSceneObject": return RecordChecker.LAIR;
				default: break;
			}
		}
		return null;
	}

	/** Last time any pirate in a Den / CI / Lair scene had a station symbol over their head, and which scene. */
	private long lastSymbolAt;
	private String lastSymbolKind;

	/** When a station symbol was last seen in a Den, CI or Lair scene; 0 if never. For auto scan. */
	public long lastSymbolAt() { return lastSymbolAt; }

	/** RecordChecker.DEN / CI / LAIR for lastSymbolAt(); null if never seen. */
	public String lastSymbolKind() { return lastSymbolKind; }

	static boolean fraying(Object scene) {
		try { return scene.getClass().getField("fraying").getBoolean(scene); }
		catch (ReflectiveOperationException | RuntimeException e) { return false; }
	}

	/** Feeds one sighting; package-private so it can be tested without a client. */
	void update(String pirate, int code, long now) {
		PirateState p = pirates.computeIfAbsent(pirate, k -> new PirateState());
		p.lastSeen = now;
		p.showing = code;
		if (code == 0 || code == p.current()) return;
		// Other pirates' symbols reach this client through the server, about 0.3 s after they really
		// switched; date their switch (leaving one station, joining the next) that much earlier.
		long when = pirate.equalsIgnoreCase(self) ? now : now - OTHERS_LAG_MS;
		p.changes.add(new long[]{when, code});
		// Keep about 6 hours.
		while (p.changes.size() > 2 && p.changes.get(1)[0] < now - 6 * 3600_000L) p.changes.remove(0);
	}

	/** First scene object (not a puzzle's) referenced by a displayable component. */
	private Object findScene(Component c, int depth) {
		if (c == null || depth > 40) return null;
		if (c.isDisplayable()) {
			for (Class<?> k = c.getClass(); k != null && !k.getName().startsWith("java"); k = k.getSuperclass()) {
				for (Field f : k.getDeclaredFields()) {
					if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getType().isPrimitive()) continue;
					if (!isSceneObject(f.getType())) continue;
					try {
						f.setAccessible(true);
						Object v = f.get(c);
						if (v != null && isSceneObject(v.getClass())) return v;
					} catch (Exception ignored) { }
				}
			}
		}
		if (c instanceof Container) {
			Container k = (Container) c;
			for (int i = 0; i < k.getComponentCount(); i++) {
				Object v = findScene(k.getComponent(i), depth + 1);
				if (v != null) return v;
			}
		}
		return null;
	}

	private static boolean isSceneObject(Class<?> k) {
		for (; k != null; k = k.getSuperclass())
			if (k.getName().equals("com.threerings.whirled.data.SceneObject")) return true;
		return false;
	}

	// ---------------------------------------------------------------- duty report

	/** One pirate's line on one station of a scanned duty report, with their timing. */
	public static final class Row {
		public String station, pirate, rating;
		public int[] tokens;
		public int score;
		/** Time on the station; 0 if unknown. */
		public long ms;
	}

	/** One scanned duty report (station period). */
	public static final class Period {
		public long start, end;
		/** True when the period came from the chat log's station messages. */
		public boolean timedToTimer;
		public final List<Row> rows = new ArrayList<>();
	}

	/** Reports scanned since the last booty export, oldest first. */
	private final List<Period> periods = new ArrayList<>();

	public List<Period> periods() { return new ArrayList<>(periods); }
	public void clearPeriods() { periods.clear(); }

	/** The period added by the latest report() call, once; null if that report was a repeat. */
	private Period fresh;
	public Period takeNewPeriod() { Period p = fresh; fresh = null; return p; }

	/** Called when tracking starts at Woodhallow after at least ARRIVAL_GAP_MS elsewhere (a new run). */
	private java.util.function.Consumer<Boolean> onArrival;
	/** Gets true for the first sighting since Gizmo started (maybe a restart mid-run). */
	public void setOnArrival(java.util.function.Consumer<Boolean> r) { onArrival = r; }
	private long lastAtWoodhallow;
	static final long ARRIVAL_GAP_MS = 10 * 60_000L;

	/** End of the last report's period, so a fallback period never overlaps it. */
	private long lastEnd;

	/** Score per minute for every pirate on the report. Learns or reuses each station's activity code. */
	public String report(DRGroupData dr) {
		return report(dr, System.currentTimeMillis());
	}

	String report(DRGroupData dr, long now) {
		String key = dr.toString();
		if (key.equals(lastReportKey)) return lastReportText;   // same report scanned again

		Period period = new Period();
		StationClock.Period sp = clock == null ? null : clock.lastEnded(now);
		if (sp != null && sp.end > lastEnd) {
			period.start = sp.start;
			period.end = Math.min(sp.end, now);
			period.timedToTimer = true;
		} else {
			// No station messages found (chat log missing?): from the last report, or the last 15 minutes, to now.
			period.start = Math.max(lastEnd, now - 15 * 60_000L);
			period.end = now;
		}
		// A station period never runs past 5 minutes, whatever the messages say, and a length within
		// 15 s of 2 or 5 minutes is taken as exactly that (chat timestamps are only to the second).
		period.end = Math.min(period.end, period.start + MAX_PERIOD_MS);
		if (period.timedToTimer) period.end = period.start + snap(period.end - period.start);
		periods.add(period);
		fresh = period;

		Map<String, Integer> codes = assignCodes(dr, period);
		for (Map.Entry<String, LinkedHashMap<String, DRIndividualData>> st : dr.data.entrySet()) {
			String station = st.getKey();
			int code = codes.getOrDefault(station, 0);
			for (DRIndividualData d : st.getValue().values()) {
				PirateState p = pirates.get(d.getName());
				Long ms = (p == null || code == 0) ? null : p.times(period.start, period.end).get(code);
				long t = ms == null ? 0 : ms;
				Row row = new Row();
				row.station = station;
				row.pirate = d.getName();
				row.rating = d.getRating();
				row.tokens = d.getTokens();
				row.score = d.getTokenScore();
				row.ms = t < 5_000 ? 0 : t;
				period.rows.add(row);
			}
		}
		lastEnd = period.end;
		lastReportKey = key;
		lastPeriod = period;
		StringBuilder sb = new StringBuilder();
		for (String block : blocks(period, " ")) sb.append(block).append("\n\n");
		lastReportText = sb.toString().trim();
		return lastReportText;
	}

	private Period lastPeriod;

	/** The latest report's period (also after a repeat scan), or null. */
	public Period lastPeriod() { return lastPeriod; }

	/**
	 * Minimal per-station lists, Navigating first, then Foraging, then anything else:
	 * a station name line, then one "Name rate" line per pirate in duty report order ("-" if the time is unknown).
	 */
	public static List<String> blocks(Period period, String sep) { return blocks(period, sep, true); }

	/** As above; without the station name line when header is false (for copying). */
	public static List<String> blocks(Period period, String sep, boolean header) {
		Map<String, List<Row>> byStation = new LinkedHashMap<>();
		for (Row r : period.rows) byStation.computeIfAbsent(r.station, k -> new ArrayList<>()).add(r);
		List<String> order = new ArrayList<>(byStation.keySet());
		order.sort(Comparator.comparingInt(ActivityTracker::stationOrder));
		List<String> out = new ArrayList<>();
		for (String station : order) {
			List<Row> rows = byStation.get(station);   // in duty report order
			StringBuilder sb = new StringBuilder(header ? station : "");
			for (Row r : rows) {
				if (sb.length() > 0) sb.append('\n');
				sb.append(r.pirate).append(sep);
				sb.append(r.ms > 0 ? String.format("%.2f", rate(r)) : "-");
			}
			out.add(sb.toString());
		}
		return out;
	}

	/** station -> pirate -> score per minute, for rows with a known time; empty if period is null. */
	public static Map<String, Map<String, Double>> rates(Period period) {
		Map<String, Map<String, Double>> out = new HashMap<>();
		if (period == null) return out;
		for (Row r : period.rows)
			if (r.ms > 0) out.computeIfAbsent(r.station, k -> new HashMap<>()).put(r.pirate, rate(r));
		return out;
	}

	static double rate(Row r) { return r.ms > 0 ? r.score / (r.ms / 60000.0) : -1; }

	private static int stationOrder(String s) {
		String l = s.toLowerCase();
		if (l.startsWith("navigat")) return 0;
		if (l.startsWith("forag")) return 1;
		return 2;
	}

	/**
	 * Which symbol code is which station. Evidence for a station comes from pirates listed on that
	 * station only (someone on both can't tell them apart), weighted by time. Two stations never share
	 * a code; with too little evidence the remembered code is used.
	 */
	private Map<String, Integer> assignCodes(DRGroupData dr, Period period) {
		Map<String, Integer> stationsOf = new HashMap<>();
		for (LinkedHashMap<String, DRIndividualData> m : dr.data.values())
			for (String n : m.keySet()) stationsOf.merge(n, 1, Integer::sum);

		Map<String, Map<Integer, Long>> evidence = new LinkedHashMap<>();
		for (Map.Entry<String, LinkedHashMap<String, DRIndividualData>> st : dr.data.entrySet()) {
			Map<Integer, Long> ev = new HashMap<>();
			for (String n : st.getValue().keySet()) {
				PirateState p = pirates.get(n);
				if (p == null || stationsOf.get(n) > 1) continue;
				for (Map.Entry<Integer, Long> e : p.times(period.start, period.end).entrySet()) ev.merge(e.getKey(), e.getValue(), Long::sum);
			}
			evidence.put(st.getKey(), ev);
		}

		Map<String, Integer> out = new HashMap<>();
		Set<Integer> taken = new HashSet<>();
		// Strongest evidence first, so a clear station claims its code before a doubtful one.
		List<String> order = new ArrayList<>(evidence.keySet());
		order.sort((x, y) -> Long.compare(max(evidence.get(y)), max(evidence.get(x))));
		for (String station : order) {
			String prefKey = "activitycode." + station;
			int remembered = prefs == null ? 0 : prefs.getInt(prefKey, 0);
			int best = 0;
			long bestT = 0;
			for (Map.Entry<Integer, Long> e : evidence.get(station).entrySet())
				if (!taken.contains(e.getKey()) && e.getValue() > bestT) { best = e.getKey(); bestT = e.getValue(); }
			int code;
			if (best != 0 && (bestT >= 60_000 || remembered == 0)) {
				code = best;
				if (prefs != null) prefs.putInt(prefKey, code);
			} else {
				code = taken.contains(remembered) ? 0 : remembered;
			}
			if (code != 0) taken.add(code);
			out.put(station, code);
		}
		return out;
	}

	private static long max(Map<Integer, Long> m) {
		long b = 0;
		for (long v : m.values()) b = Math.max(b, v);
		return b;
	}

	/** When the latest station period ended, for auto scan; 0 if none. */
	public long lastStationEnd(long now) {
		StationClock.Period p = clock == null ? null : clock.lastEnded(now);
		return p == null ? 0 : p.end;
	}

	/** Pirates and their current symbol code, for the debug view. */
	public List<String> snapshot() {
		List<String> out = new ArrayList<>();
		long now = System.currentTimeMillis();
		for (Map.Entry<String, PirateState> e : pirates.entrySet()) {
			PirateState p = e.getValue();
			if (now - p.lastSeen > 60_000) continue;   // no longer in the scene
			long since = p.changes.isEmpty() ? now : p.changes.get(p.changes.size() - 1)[0];
			// [live symbol]; the station clock (last non-blank symbol) is shown after it.
			out.add(e.getKey() + " [" + p.showing + "]" + (p.current() == 0 ? "" : " station " + p.current() + " for " + (now - since) / 1000 + "s"));
		}
		return out;
	}
}
