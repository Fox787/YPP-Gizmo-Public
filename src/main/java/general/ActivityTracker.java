package main.java.general;

import java.awt.*;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.List;

/**
 * Times Woodhallow duty reports (only while "Score per minute" is on), from:
 *   - the chat log: when each station period starts and ends (StationClock), so a pirate on one
 *     station gets the whole period;
 *   - this client's own game log: which puzzle the logged-in pirate entered and when (ownPuzzle,
 *     via RumbleClock), so your own split between Dnav and foraging is known;
 *   - the displayed duty report: each pirate's stations and scores.
 * Other pirates who switched stations are "unknown". Only with the opt-in "Scene-wide score per
 * minute" (pending developer approval) does it read the island scene every 250 ms: its occupants'
 * station activity, role and name, and whether the fray is on. With that off, nothing here polls.
 */
public final class ActivityTracker {

	/** A pirate's symbol changes, oldest first: {time, code}. */
	static final class PirateState {
		final List<long[]> changes = new ArrayList<>();
		long lastSeen;
		/** The symbol over their head right now (0 = none), for the live display. */
		int showing;

		int current() { return changes.isEmpty() ? 0 : (int) changes.get(changes.size() - 1)[1]; }

		/** The first station seen in this round gets the time from the start. */
		Map<Integer, Long> times(long start, long end) {
			Map<Integer, Long> times = new HashMap<>();
			int code = 0;
			long from = start;
			for (long[] change : changes) {
				long at = change[0];
				if (at < start - 1000) continue; // Chat timestamps and symbol lag can straddle the boundary.
				if (at >= end) break;
				int next = (int) change[1];
				if (code == 0 && at > start + SNAP_MS) break;
				if (next == code) continue;
				if (code != 0) {
					long boundary = Math.max(from, at);
					times.merge(code, boundary - from, Long::sum);
					from = boundary;
				}
				code = next;
			}
			if (code != 0) times.merge(code, end - from, Long::sum);
			return times;
		}
	}

	static final long MAX_PERIOD_MS = 5 * 60_000L;
	private static final long OTHERS_LAG_MS = 300;
	private static final long SNAP_MS = 15_000;
	private static final int NAV = 10, FORAGE = 56;
	static final int POLL_MS = 250;
	private static final long SCENE_SEARCH_MS = 5_000;
	private static final long ACTIVE_DEN_SEARCH_MS = 60_000;

	private String self = "";
	private final PirateState ownStations = new PirateState();
	private final Window window;
	private final StationClock clock;
	private final javax.swing.Timer timer;
	private final Map<String, PirateState> scenePirates = new LinkedHashMap<>();
	private long sceneTrackingSince = Long.MAX_VALUE;
	private Object scene;
	private long nextSceneSearch;

	void setSelf(String pirate) {
		String name = pirate == null ? "" : pirate;
		if (!self.equalsIgnoreCase(name)) ownStations.changes.clear();
		self = name;
	}

	void ownPuzzle(String object, long now) {
		int code = "NaviObject".equals(object) ? NAV : "ForageObject".equals(object) ? FORAGE : 0;
		if (code != 0) recordStation(ownStations, code, now);
	}

	void setSceneTrackingEnabled(boolean enabled) {
		setSceneTrackingEnabled(enabled, System.currentTimeMillis());
	}

	void setSceneTrackingEnabled(boolean enabled, long now) {
		scenePirates.clear();
		sceneTrackingSince = enabled ? now : Long.MAX_VALUE;
		// The scene is only read while this is on: with it off the poll timer doesn't run at all.
		if (!enabled) {
			timer.stop();
			scene = null;
		} else if (started) {
			timer.start();
		}
	}

	/** True while the opt-in scene reading is running. */
	boolean sceneTrackingEnabled() { return sceneTrackingSince != Long.MAX_VALUE; }

	private String lastReportKey;
	private String lastReportText;

	public ActivityTracker(Window window, StationClock clock) {
		this.window = window;
		this.clock = clock;
		this.timer = new javax.swing.Timer(POLL_MS, e -> poll(System.currentTimeMillis()));
	}

	private boolean started;

	/** Starts scene reading if "Scene-wide score per minute" is on; otherwise nothing polls. */
	public void start() {
		started = true;
		if (sceneTrackingEnabled()) timer.start();
	}

	public void stop() {
		started = false;
		timer.stop();
	}

	/** For tests: whether the scene poll timer is running. */
	boolean polling() { return timer.isRunning(); }

	// ---------------------------------------------------------------- polling

	private void poll(long now) {
		try {
			if (scene != null && !active(scene)) {
				scene = null;
				nextSceneSearch = now;
			}
			if (now >= nextSceneSearch) {
				Object found = findScene(window, 0);
				if (found != null) scene = found; // The view can be hidden during a puzzle.
				SceneFields cached = scene == null ? null : SCENE_FIELDS.get(scene.getClass());
				// An active Woodhallow scene remains valid even when a puzzle hides its view.
				boolean verifiedDen = cached != null && cached.active() != null && RecordChecker.DEN.equals(cached.kind());
				nextSceneSearch = now + (verifiedDen ? ACTIVE_DEN_SEARCH_MS : SCENE_SEARCH_MS);
			}
			if (scene == null) return;
			SceneFields fields = SCENE_FIELDS.get(scene.getClass());
			if (fields.kind() == null || fields.occupants() == null) return;
			boolean den = RecordChecker.DEN.equals(fields.kind());
			if (den) {
				if (now - lastAtWoodhallow > ARRIVAL_GAP_MS && onArrival != null)
					onArrival.accept(lastAtWoodhallow == 0);
				lastAtWoodhallow = now;
				if (fields.fraying() != null && fields.fraying().getBoolean(scene)) {
					for (PirateState state : scenePirates.values()) state.showing = 0;
					return;
				}
			}
			// Only Woodhallow's occupants are read (other pirates' station switches, for their score per minute).
			if (!den || now < sceneTrackingSince) return;
			if (!(fields.occupants().get(scene) instanceof Iterable<?> occupants)) return;
			for (Object info : occupants) {
				if (info == null) continue;
				OccupantFields occupant = OCCUPANT_FIELDS.get(info.getClass());
				if (occupant.activity() == null || isBot(info, occupant.role())) continue;
				int code = ((Number) occupant.activity().get(info)).intValue();
				if (occupant.username() == null) continue;
				Object name = occupant.username().get(info);
				if (name != null) observeSceneActivity(name.toString(), code, now);
			}
		} catch (ReflectiveOperationException | RuntimeException ignored) {
			// A client update can rename fields. Leave timing unknown if an observation is missing.
		}
	}

	private static boolean isBot(Object info, Field role) throws IllegalAccessException {
		if (role == null) return false;
		int value = ((Number) role.get(info)).intValue();
		return value == 4 || value == 12 || value == 13;
	}

	private record SceneFields(String kind, Field occupants, Field fraying, Method active) { }

	private static final ClassValue<SceneFields> SCENE_FIELDS = new ClassValue<>() {
		@Override protected SceneFields computeValue(Class<?> type) {
			Method active;
			try { active = type.getMethod("isActive"); }
			catch (NoSuchMethodException e) { active = null; }
			return new SceneFields(sceneKind(type), publicField(type, "occupantInfo"), publicField(type, "fraying"), active);
		}
	};

	private record OccupantFields(Field activity, Field username, Field role) { }

	private static final ClassValue<OccupantFields> OCCUPANT_FIELDS = new ClassValue<>() {
		@Override protected OccupantFields computeValue(Class<?> type) {
			for (Class<?> c = type; c != null; c = c.getSuperclass())
				if (c.getSimpleName().equals("NPPOccupantInfo")) return new OccupantFields(null, null, null);
			return new OccupantFields(publicField(type, "activity"), publicField(type, "username"), publicField(type, "role"));
		}
	};

	private static Field publicField(Class<?> type, String name) {
		try { return type.getField(name); }
		catch (NoSuchFieldException e) { return null; }
	}

	private static boolean active(Object candidate) throws ReflectiveOperationException {
		Method active = SCENE_FIELDS.get(candidate.getClass()).active();
		return active == null || Boolean.TRUE.equals(active.invoke(candidate));
	}

	/**
	 * Which station activity a scene is: RecordChecker.DEN (Woodhallow, WerewolfIsleSceneObject),
	 * CI (Cursed Isles forage, GauntletSceneObject) or LAIR (VampireLairSceneObject); null for anything else.
	 */
	private static String sceneKind(Class<?> type) {
		for (Class<?> k = type; k != null; k = k.getSuperclass()) {
			switch (k.getSimpleName()) {
				case "WerewolfIsleSceneObject": return RecordChecker.DEN;
				case "GauntletSceneObject": return RecordChecker.CI;
				case "VampireLairSceneObject": return RecordChecker.LAIR;
				default: break;
			}
		}
		return null;
	}


	void observeSceneActivity(String pirate, int code, long now) {
		if (now < sceneTrackingSince || pirate.equalsIgnoreCase(self)) return;
		if (code != NAV && code != FORAGE && code != 0) return;
		if (code == 0 && !scenePirates.containsKey(pirate)) return;
		PirateState state = scenePirates.computeIfAbsent(pirate, unused -> new PirateState());
		if (code != 0 && code != state.showing) recordStation(state, code, now - OTHERS_LAG_MS);
		state.lastSeen = now;
		state.showing = code;
	}

	private static void recordStation(PirateState pirate, int code, long at) {
		pirate.changes.add(new long[]{at, code});
		while (pirate.changes.size() > 2 && pirate.changes.get(1)[0] < at - 6 * 3600_000L)
			pirate.changes.remove(0);
	}

	private static final ClassValue<List<Field>> COMPONENT_FIELDS = new ClassValue<>() {
		@Override protected List<Field> computeValue(Class<?> type) {
			List<Field> fields = new ArrayList<>();
			for (Class<?> c = type; c != null && !c.getName().startsWith("java"); c = c.getSuperclass()) {
				for (Field field : c.getDeclaredFields()) {
					if (!Modifier.isStatic(field.getModifiers()) && isSceneObject(field.getType()) && field.trySetAccessible())
						fields.add(field);
				}
			}
			return fields;
		}
	};

	/** Finds an active scene, including non-station scenes so they replace the last station scene. */
	private Object findScene(Component component, int depth) throws ReflectiveOperationException {
		if (component == null || !component.isDisplayable() || depth > 40) return null;
		for (Field field : COMPONENT_FIELDS.get(component.getClass())) {
			Object value = field.get(component);
			if (value != null && active(value)) return value;
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				Object value = findScene(child, depth + 1);
				if (value != null) return value;
			}
		}
		return null;
	}

	private static boolean isSceneObject(Class<?> type) {
		for (Class<?> c = type; c != null; c = c.getSuperclass())
			if (c.getName().equals("com.threerings.whirled.data.SceneObject")) return true;
		return false;
	}

	// ---------------------------------------------------------------- duty report

	/** One pirate's line on one station of a scanned duty report, with their timing. */
	public static final class Row {
		public String station, pirate, rating;
		public int[] tokens;
		public int score;
		boolean woodhallowNav;
		public boolean exact;
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
	void finishExport(List<Period> exported) { periods.removeAll(exported); }

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
	private long lastStationStart = Long.MIN_VALUE;

	/** Score per minute for Woodhallow duty report rows with a known station duration. */
	public String report(DRGroupData dr) {
		return report(dr, System.currentTimeMillis());
	}

	String report(DRGroupData dr, long now) {
		StationClock.Period sp = clock == null ? null : clock.lastEnded(now);
		String key = (sp == null ? "" : sp.start) + "|" + dr;
		if (key.equals(lastReportKey)) return lastReportText;

		Period period = new Period();
		if (sp != null && sp.start > lastStationStart) {
			period.start = sp.start;
			period.end = Math.min(sp.end, now);
			period.timedToTimer = true;
			lastStationStart = sp.start;
		} else {
			// No station messages found (chat log missing?): from the last report, or the last 15 minutes, to now.
			period.start = Math.max(lastEnd, now - 15 * 60_000L);
			period.end = now;
		}
		period.end = Math.min(period.end, period.start + MAX_PERIOD_MS);
		if (period.timedToTimer) {
			long expected = clock.duration(sp);
			if (Math.abs(period.end - period.start - expected) <= SNAP_MS) period.end = period.start + expected;
		}
		periods.add(period);
		fresh = period;

		boolean woodhallow = RecordChecker.DEN.equals(RecordChecker.activityOf(dr));
		Map<String, Integer> stationsOf = new HashMap<>();
		for (Map<String, DRIndividualData> rows : dr.data.values())
			for (String name : rows.keySet()) stationsOf.merge(name, 1, Integer::sum);
		for (Map.Entry<String, LinkedHashMap<String, DRIndividualData>> station : dr.data.entrySet()) {
			int code = switch (station.getKey()) {
				case "Navigating" -> NAV;
				case "Foraging" -> FORAGE;
				default -> 0;
			};
			for (DRIndividualData d : station.getValue().values()) {
				String name = d.getName();
				boolean singleStation = stationsOf.get(name) == 1;
				boolean local = name.equalsIgnoreCase(self);
				long time = 0;
				if (period.timedToTimer && code != 0) {
					if (singleStation) time = period.end - period.start;
					else if (local) time = splitTime(ownStations, code, period);
					else if (period.start >= sceneTrackingSince) time = splitTime(scenePirates.get(name), code, period);
				}
				Row row = new Row();
				row.station = station.getKey();
				row.pirate = name;
				row.rating = d.getRating();
				row.tokens = d.getTokens();
				row.score = d.getTokenScore();
				row.woodhallowNav = woodhallow && code == NAV;
				row.ms = time < 5_000 ? 0 : time;
				row.exact = row.ms > 0 && (singleStation || local);
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

	private static long splitTime(PirateState pirate, int code, Period period) {
		if (pirate == null) return 0;
		Map<Integer, Long> times = pirate.times(period.start, period.end);
		return times.getOrDefault(NAV, 0L) > 0 && times.getOrDefault(FORAGE, 0L) > 0 ? times.get(code) : 0;
	}

	static String describeTimes(Period period) {
		StringBuilder text = new StringBuilder("Station times");
		for (Row row : period.rows) {
			text.append('\n').append(row.pirate).append(' ').append(row.station).append(' ');
			if (row.ms == 0) text.append("unknown");
			else {
				text.append(String.format(Locale.ROOT, "%.1fs", row.ms / 1000.0));
				if (!row.exact) text.append(" (scene estimate)");
			}
		}
		return text.toString();
	}

	private Period lastPeriod;

	/** The latest report's period (also after a repeat scan), or null. */
	public Period lastPeriod() { return lastPeriod; }

	/**
	 * Minimal per-station lists, Navigating first, then Foraging, then anything else:
	 * a "---Station---" line, then one "Name rate" line per pirate in duty report order ("-" if the time is unknown).
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
			// The station line as in the scan output: "---Navigating---".
			StringBuilder sb = new StringBuilder(header ? "---" + station + "---" : "");
			for (Row r : rows) {
				if (sb.length() > 0) sb.append('\n');
				sb.append(r.pirate).append(sep);
				sb.append(r.ms > 0 ? String.format("%.2f", rate(r)) : "-");
			}
			out.add(sb.toString());
		}
		return out;
	}

	/** Packs timed or untimed duty reports into whole vessel-chat messages. */
	static List<String> chatMessages(DRGroupData report, Period period, boolean ignoreNpc) {
		Map<String, List<String>> byStation = new LinkedHashMap<>();
		if (period == null) {
			for (var station : report.data.entrySet())
				for (DRIndividualData row : station.getValue().values())
					addChatRow(byStation, station.getKey(), row.getName(), row.getTokenScore(), "-", ignoreNpc);
		} else {
			for (Row row : period.rows) {
				String rate = row.ms > 0 ? String.format("%.2f", rate(row)) : "-";
				addChatRow(byStation, row.station, row.pirate, row.score, rate, ignoreNpc);
			}
		}
		if (byStation.isEmpty()) return List.of();

		List<String> stations = new ArrayList<>(byStation.keySet());
		stations.sort(Comparator.comparingInt(ActivityTracker::stationOrder));
		List<String> blocks = new ArrayList<>();
		for (String station : stations)
			blocks.add(chatStation(station) + "\n" + String.join("\n", byStation.get(station)));
		String combined = ReportClipboard.VESSEL_COMMAND + String.join("\n\n", blocks);
		if (combined.length() <= ReportClipboard.CHAT_LIMIT) return List.of(combined);
		combined = ReportClipboard.VESSEL_COMMAND + String.join("\n", blocks);
		if (combined.length() <= ReportClipboard.CHAT_LIMIT) return List.of(combined);

		List<String> messages = new ArrayList<>();
		for (String station : stations) {
			String header = ReportClipboard.VESSEL_COMMAND + chatStation(station);
			StringBuilder message = new StringBuilder(header);
			for (String row : byStation.get(station)) {
				if (header.length() + 1 + row.length() > ReportClipboard.CHAT_LIMIT) return List.of();
				if (message.length() + 1 + row.length() > ReportClipboard.CHAT_LIMIT) {
					messages.add(message.toString());
					message.setLength(header.length());
				}
				message.append('\n').append(row);
			}
			messages.add(message.toString());
		}
		return messages;
	}

	private static void addChatRow(Map<String, List<String>> byStation, String station, String pirate,
								   int score, String rate, boolean ignoreNpc) {
		if (ignoreNpc && pirate.contains(" ")) return;
		byStation.computeIfAbsent(station, unused -> new ArrayList<>()).add(pirate + " " + score + " (" + rate + ")");
	}

	private static String chatStation(String station) {
		return switch (station) {
			case "Navigating" -> "Nav";
			case "Foraging" -> "Forage";
			default -> station;
		};
	}

	/** station -> pirate -> score per minute, for rows with a known time; empty if period is null. */
	public static Map<String, Map<String, Double>> rates(Period period) {
		Map<String, Map<String, Double>> out = new HashMap<>();
		if (period == null) return out;
		for (Row r : period.rows)
			if (r.ms > 0) out.computeIfAbsent(r.station, k -> new HashMap<>()).put(r.pirate, rate(r));
		return out;
	}

	static double rate(Row r) {
		if (r.ms <= 0) return -1;
		double score = r.woodhallowNav ? DnavForecast.navPoints(r.tokens) / DnavForecast.POINTS_PER_SCORE : r.score;
		return score / (r.ms / 60_000.0);
	}

	private static int stationOrder(String s) {
		String l = s.toLowerCase();
		if (l.startsWith("navigat")) return 0;
		if (l.startsWith("forag")) return 1;
		return 2;
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
		for (Map.Entry<String, PirateState> e : scenePirates.entrySet()) {
			PirateState p = e.getValue();
			if (now - p.lastSeen > 60_000) continue;   // no longer in the scene
			long since = p.changes.isEmpty() ? now : p.changes.get(p.changes.size() - 1)[0];
			// [live symbol]; the station clock (last non-blank symbol) is shown after it.
			out.add(e.getKey() + " [" + p.showing + "]" + (p.current() == 0 ? "" : " station " + p.current() + " for " + (now - since) / 1000 + "s"));
		}
		return out;
	}
}
