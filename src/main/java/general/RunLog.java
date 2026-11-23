package main.java.general;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Saves every scanned duty report, with station timings, the moment it is scanned, so nothing
 * depends on reaching a booty division or staying logged in.
 *
 * One file per run under the data folder, in &lt;ocean&gt;/runs/run_&lt;start&gt;_&lt;pirate&gt;.jsonl, one JSON line per
 * station period. A run starts on a voyage arrival (or with the first report if no arrival is known)
 * and stays open across restarts. Booty exports list the run files written since the last export,
 * so a division after two runs links both, and a run with no division is still on disk.
 */
public final class RunLog {

	private static final String CURRENT = "runlog.current", PENDING = "runlog.pending";
	private static final String ARRIVAL = "runlog.arrival", LAST_UNTIMED = "runlog.lastUntimed";
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
	private static final Pattern REPORT_TIME = Pattern.compile("\"(scanned_at|ended_at)\":\"([^\"]+)\"");
	private static final Pattern REPORT_VOYAGE = Pattern.compile("\"voyage\":\"([^\"]+)\"");
	private static final Pattern LOOT_ROW = Pattern.compile(
			"\\{\"station\":\"(Foraging|Treasure Haul)\",\"pirate\":\"([^\"]+)\",[^{}]*?\"tokens\":\\[(\\d+),(\\d+),(\\d+)\\]");

	private final Preferences prefs;

	public RunLog(Preferences prefs) { this.prefs = prefs; }

	/** Next report goes into a new run file (called on arriving at Woodhallow). */
	public void startNewRun() {
		prefs.remove(CURRENT);
		prefs.remove(ARRIVAL);
		prefs.remove(LAST_UNTIMED);
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

	static String voyageOf(DRGroupData report) {
		String activity = RecordChecker.activityOf(report);
		if (RecordChecker.DEN.equals(activity)) return "woodhallow";
		if (RecordChecker.CI.equals(activity)) return "cursedIsles";
		if (RecordChecker.LAIR.equals(activity)) return "vampireLairs";
		return null;
	}

	/** Archives confirmed missing chests without adding a duty report or opening a tracked run. */
	static Path writeUnobservedRun(Path divisionFolder, BootyDivision division, String pirate, int chests)
			throws IOException {
		String name = "run_" + division.chat.openedAt.format(STAMP) + "_" + (pirate == null ? "unknown" : pirate) + "_unobserved";
		Path folder = divisionFolder.resolve("runs");
		Path file = folder.resolve(name + ".jsonl");
		BootyDivision.Json j = new BootyDivision.Json();
		j.open();
		j.field("run", name);
		j.field("pirate", null);
		j.field("ocean", division.ocean());
		j.field("voyage", null);
		j.field("scanned_at", null);
		j.field("started", null);
		j.field("started_at", null);
		j.field("ended", null);
		j.field("ended_at", null);
		j.field("period_from_chat_log", false);
		j.key("rows").openArray().closeArray();
		j.field("chest_count", chests);
		j.field("source", "division_reconciliation");
		j.field("division_opened_at", division.chat.openedAt.toString());
		j.close();

		Files.createDirectories(folder);
		Path staged = Files.createTempFile(folder, name, ".tmp");
		try {
			Files.writeString(staged, j + "\n", StandardCharsets.UTF_8);
			try {
				Files.move(staged, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(staged, file, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(staged);
		}
		return file;
	}

	Path recordUntimed(DRGroupData report, String voyage, long arrival) throws IOException {
		ActivityTracker.Period period = untimedPeriod(report, System.currentTimeMillis());
		String key = Integer.toHexString(report.toString(true, false, true).hashCode());
		return record(period, voyage, arrival, key);
	}

	static ActivityTracker.Period untimedPeriod(DRGroupData report, long scannedAt) {
		ActivityTracker.Period period = new ActivityTracker.Period();
		period.start = period.end = scannedAt;
		report.data.forEach((station, pirates) -> pirates.values().forEach(pirate -> {
			ActivityTracker.Row row = new ActivityTracker.Row();
			row.station = station;
			row.pirate = pirate.getName();
			row.rating = pirate.getRating();
			row.score = pirate.getTokenScore();
			row.tokens = pirate.getTokens();
			period.rows.add(row);
		}));
		return period;
	}

	/** Appends one timed report, reusing the file for this arrival after a restart. */
	Path record(ActivityTracker.Period period, String voyage, long arrival) throws IOException {
		return record(period, voyage, arrival, null);
	}

	private Path record(ActivityTracker.Period period, String voyage, long arrival, String untimedKey) throws IOException {
		GameLog.Info game = GameLog.read();
		String pirate = game == null || game.pirate == null ? "unknown" : game.pirate;
		String ocean = game == null || game.ocean == null ? "unknown" : game.ocean;
		Path directory = GameLog.dataFolder().resolve(ocean.toLowerCase(Locale.ROOT)).resolve("runs")
				.toAbsolutePath().normalize();
		Path file = current();
		boolean newRun = file == null || !directory.equals(file.getParent()) || !thisPirate(file, pirate, ocean)
				|| (arrival > 0 && prefs.getLong(ARRIVAL, 0) != arrival);
		if (!newRun && untimedKey != null && untimedKey.equals(prefs.get(LAST_UNTIMED, ""))) return null;

		Files.createDirectories(directory);
		if (newRun) file = createRun(directory, pirate, arrival > 0 ? arrival : period.end);
		try {
			Files.writeString(file, toJson(period, pirate, ocean, file, voyage) + "\n", StandardCharsets.UTF_8,
					StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException e) {
			if (newRun) Files.deleteIfExists(file);
			throw e;
		}
		prefs.put(CURRENT, file.toString());
		prefs.putLong(ARRIVAL, arrival);
		if (untimedKey == null) prefs.remove(LAST_UNTIMED);
		else prefs.put(LAST_UNTIMED, untimedKey);
		addPending(file);
		return file;
	}

	private static Path createRun(Path directory, String pirate, long start) throws IOException {
		String prefix = "run_" + LocalDateTime.ofInstant(Instant.ofEpochMilli(start), ZoneId.systemDefault()).format(STAMP);
		for (int copy = 1; ; copy++) {
			String suffix = copy == 1 ? "" : "-" + copy;
			try {
				return Files.createFile(directory.resolve(prefix + suffix + "_" + pirate + ".jsonl"));
			} catch (FileAlreadyExistsException ignored) { }
		}
	}

	private static boolean thisPirate(Path file, String pirate, String ocean) {
		Path directory = file.getParent();
		return directory != null && directory.getParent() != null && directory.getParent().getFileName() != null
				&& ocean.equalsIgnoreCase(directory.getParent().getFileName().toString())
				&& file.getFileName().toString().toLowerCase(Locale.ROOT)
						.endsWith("_" + pirate.toLowerCase(Locale.ROOT) + ".jsonl");
	}

	/** Run files for this pirate and ocean that have yet to be linked to an export. */
	List<Path> pending(String pirate, String ocean) {
		String name = pirate == null ? "unknown" : pirate;
		String sea = ocean == null ? "unknown" : ocean;
		return pending().stream().filter(file -> thisPirate(file, name, sea)).toList();
	}

	/** Uses the scanned reports only when their chest totals account for all three tiers taken aboard. */
	static Map<String, int[]> pirateLoot(List<Path> files, ChatLog.Run run, LocalDateTime end) {
		if (run.voyage == null || run.arrivedAt == null || end == null || Arrays.stream(run.tiers).sum() <= 0)
			return Map.of();
		String station = "vampireLairs".equals(run.voyage) ? "Treasure Haul" : "Foraging";
		Map<String, int[]> byPirate = new LinkedHashMap<>();
		int[] total = new int[3];
		for (Path file : files) {
			try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
				String report;
				while ((report = reader.readLine()) != null) {
					if (!report.endsWith("}")) continue;
					Matcher voyage = REPORT_VOYAGE.matcher(report);
					if (voyage.find() && !run.voyage.equals(voyage.group(1))) continue;
					LocalDateTime time = reportTime(report);
					if (time == null || time.isBefore(run.arrivedAt) || !time.isBefore(end)) continue;
					Matcher row = LOOT_ROW.matcher(report);
					while (row.find()) {
						if (!station.equals(row.group(1))) continue;
						int[] loot = byPirate.computeIfAbsent(row.group(2), name -> new int[3]);
						for (int tier = 0; tier < 3; tier++) {
							int count = Integer.parseInt(row.group(5 - tier));
							loot[tier] += count;
							total[tier] += count;
						}
					}
				}
			} catch (IOException | DateTimeParseException | NumberFormatException e) {
				return Map.of();
			}
		}
		return Arrays.equals(total, run.tiers) ? byPirate : Map.of();
	}

	private static LocalDateTime reportTime(String report) {
		Matcher time = REPORT_TIME.matcher(report);
		boolean timed = report.contains("\"period_from_chat_log\":true");
		while (time.find()) {
			if (!timed || "ended_at".equals(time.group(1))) return LocalDateTime.parse(time.group(2));
		}
		return null;
	}

	void clearPending(List<Path> exported) {
		List<Path> remaining = pending();
		remaining.removeAll(exported);
		savePending(remaining);
		Path file = current();
		if (file != null && exported.contains(file)) startNewRun();
	}

	private Path current() {
		String value = prefs.get(CURRENT, "");
		return value.isEmpty() ? null : Paths.get(value).toAbsolutePath().normalize();
	}

	private List<Path> pending() {
		List<Path> files = new ArrayList<>();
		for (String value : prefs.get(PENDING, "").split("\\|"))
			if (!value.isEmpty()) files.add(Paths.get(value).toAbsolutePath().normalize());
		return files;
	}

	private void addPending(Path file) {
		List<Path> files = pending();
		if (!files.contains(file)) {
			files.add(file);
			savePending(files);
		}
	}

	private void savePending(List<Path> paths) {
		prefs.put(PENDING, String.join("|", paths.stream().map(Path::toString).toList()));
	}

	void recordRatings(ActivityTracker.Period period) throws IOException {
		Path output = GameLog.dataFolder().resolve("dr_ratings.jsonl");
		Files.createDirectories(output.getParent());
		Files.writeString(output, ratingsJsonl(period), StandardCharsets.UTF_8,
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);
	}

	void recordRatings(DRGroupData report, long scannedAt) throws IOException {
		recordRatings(untimedPeriod(report, scannedAt));
	}

	static String ratingsJsonl(ActivityTracker.Period period) {
		StringBuilder lines = new StringBuilder();
		for (ActivityTracker.Row row : period.rows) {
			var json = new BootyDivision.Json().open()
					.field("time", period.end)
					.field("periodMs", period.timedToTimer ? period.end - period.start : null)
					.field("station", row.station)
					.field("pirate", row.pirate)
					.field("rating", row.rating)
					.field("score", row.score);
			json.key("tokens").openArray();
			if (row.tokens != null) for (int token : row.tokens) json.value(token);
			json.closeArray()
					.field("ms", row.ms)
					.field("exact", row.exact)
					.field("perMin", row.exact ? Math.round(ActivityTracker.rate(row) * 1000) / 1000.0 : null)
					.close();
			lines.append(json).append('\n');
		}
		return lines.toString();
	}

	static String toJson(ActivityTracker.Period p, String pirate, String ocean, Path file, String voyage) {
		BootyDivision.Json j = new BootyDivision.Json();
		String name = file.getFileName().toString();
		j.open();
		j.field("run", name.endsWith(".jsonl") ? name.substring(0, name.length() - 6) : name);
		j.field("pirate", pirate);
		j.field("ocean", ocean);
		j.field("voyage", voyage);
		j.field("scanned_at", LocalDateTime.now().toString());
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
			j.field("score_per_minute", r.hasRate() ? Math.round(ActivityTracker.rate(r) * 100) / 100.0 : null);
			j.close();
		}
		j.closeArray();
		j.close();
		return j.toString();
	}
}
