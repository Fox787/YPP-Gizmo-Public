package main.java.general;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Saves every scanned duty report, with station timings, the moment it is scanned, so nothing
 * depends on reaching a booty division or staying logged in.
 *
 * One file per run: exported_data/&lt;ocean&gt;/runs/run_&lt;start&gt;_&lt;pirate&gt;.jsonl, one JSON line per
 * station period. A run starts on arriving at Woodhallow (or with the first report if none is open)
 * and stays open across restarts. Booty exports list the run files written since the last export,
 * so a division after two runs links both, and a run with no division is still on disk.
 */
public final class RunLog {

	private static final String CURRENT = "runlog.current", PENDING = "runlog.pending";
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

	private final Preferences prefs;

	public RunLog(Preferences prefs) { this.prefs = prefs; }

	/** Next report goes into a new run file (called on arriving at Woodhallow). */
	public void startNewRun() {
		prefs.put(CURRENT, "");
	}

	/**
	 * Gizmo started while already at Woodhallow: keep adding to the current run if it was written
	 * in the last 90 minutes (a restart or reconnect mid-run), otherwise start a new one.
	 */
	public void startNewRunUnlessRecent() {
		Path cur = current();
		try {
			if (cur != null && Files.exists(cur)
					&& System.currentTimeMillis() - Files.getLastModifiedTime(cur).toMillis() < 90 * 60_000L) return;
		} catch (IOException ignored) { }
		startNewRun();
	}

	public Path currentFile() { return current(); }

	/** Appends one station period to the current run file, creating it if needed. Returns the file. */
	public Path record(ActivityTracker.Period p) throws IOException {
		GameLog.Info g = GameLog.read();
		String pirate = g == null || g.pirate == null ? "unknown" : g.pirate;
		String ocean = g == null || g.ocean == null ? "unknown" : g.ocean;

		Path file = current();
		if (file == null) {
			String start = LocalDateTime.ofInstant(Instant.ofEpochMilli(p.end), ZoneId.systemDefault()).format(STAMP);
			file = GameLog.clientFolder().resolve("exported_data").resolve(ocean.toLowerCase()).resolve("runs")
					.resolve("run_" + start + "_" + pirate + ".jsonl");
			prefs.put(CURRENT, file.toString());
			addPending(file);
		}
		Files.createDirectories(file.getParent());
		Files.write(file, (toJson(p, pirate, ocean, file) + "\n").getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		return file;
	}

	/** Run files written since the last booty export. */
	public List<Path> pending() {
		List<Path> out = new ArrayList<>();
		for (String s : prefs.get(PENDING, "").split("\\|")) if (!s.isEmpty()) out.add(Paths.get(s));
		return out;
	}

	/** After a booty export: those files are linked, so the next export starts with none. */
	public void clearPending() {
		prefs.put(PENDING, "");
	}

	private Path current() {
		String s = prefs.get(CURRENT, "");
		return s.isEmpty() ? null : Paths.get(s);
	}

	private void addPending(Path file) {
		List<Path> p = pending();
		if (!p.contains(file)) p.add(file);
		StringBuilder sb = new StringBuilder();
		for (Path x : p) { if (sb.length() > 0) sb.append('|'); sb.append(x); }
		prefs.put(PENDING, sb.toString());
	}

	static String toJson(ActivityTracker.Period p, String pirate, String ocean, Path file) {
		BootyDivision.Json j = new BootyDivision.Json();
		String name = file.getFileName().toString();
		j.open();
		j.field("run", name.endsWith(".jsonl") ? name.substring(0, name.length() - 6) : name);
		j.field("pirate", pirate);
		j.field("ocean", ocean);
		j.field("started", p.start);
		j.field("started_at", LocalDateTime.ofInstant(Instant.ofEpochMilli(p.start), ZoneId.systemDefault()).toString());
		j.field("ended", p.end);
		j.field("ended_at", LocalDateTime.ofInstant(Instant.ofEpochMilli(p.end), ZoneId.systemDefault()).toString());
		j.field("period_from_chat_log", p.timedToTimer);
		j.key("rows").openArray();
		for (ActivityTracker.Row r : p.rows) {
			j.open();
			j.field("station", r.station);
			j.field("pirate", r.pirate);
			j.field("rating", r.rating);
			j.field("score", r.score);
			j.key("tokens").openArray();
			if (r.tokens != null) for (int t : r.tokens) j.value(t);
			j.closeArray();
			j.field("seconds", r.ms > 0 ? r.ms / 1000.0 : null);
			j.field("score_per_minute", r.ms > 0 ? Math.round(r.score / (r.ms / 60000.0) * 100) / 100.0 : null);
			j.close();
		}
		j.closeArray();
		j.close();
		return j.toString();
	}
}
