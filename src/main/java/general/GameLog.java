package main.java.general;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the client's own game log (yohoho_&lt;millis&gt;.log beside Gizmo.jar) for who is logged in,
 * on which ocean, and aboard which vessel. Same patterns Thalassa's log_parser uses.
 */
public final class GameLog {

	public static final class Info {
		public Path file;
		public String pirate;
		public String ocean;
		public Long vesselId;
		public String vesselName;
	}

	private static final Pattern CLIENT = Pattern.compile("Client object ([\\w-]+) \\(");
	private static final Pattern OCEAN = Pattern.compile("Configuring client \\[ocean=(\\w+)");
	private static final Pattern VESSEL_CHANGE = Pattern.compile("Vessel change \\[ovid=(-?\\d+), nvid=(-?\\d+)\\]");
	private static final Pattern VESSEL_NAME = Pattern.compile("vessel=([^(\\[\\]]+)\\((\\d+) ");

	private GameLog() { }

	/** The folder the client runs from: Gizmo is loaded with -javaagent:Gizmo.jar, so it's the working directory. */
	public static Path clientFolder() {
		return Paths.get(System.getProperty("user.dir")).toAbsolutePath();
	}

	/** Newest yohoho_*.log in folder, or null. */
	public static Path newestLog(Path folder) {
		Path best = null;
		long bestTime = Long.MIN_VALUE;
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder, "yohoho_*.log")) {
			for (Path p : ds) {
				long t = Files.getLastModifiedTime(p).toMillis();
				if (t > bestTime) { best = p; bestTime = t; }
			}
		} catch (IOException e) {
			return null;
		}
		return best;
	}

	public static Info read() {
		return read(ownLog(clientFolder()));
	}

	private static final java.util.regex.Pattern LOG_NAME = java.util.regex.Pattern.compile("^yohoho_(\\d+)\\.log$");

	/**
	 * This client's own game log. With two clients open, both write yohoho_<start millis>.log into the
	 * same folder, so "newest" flips between them. Each client names its log after when it started, so
	 * take the log whose name is closest to when this Java process started (within two minutes).
	 * Falls back to the newest log.
	 */
	public static Path ownLog(Path folder) {
		long jvmStart;
		try {
			// ProcessHandle is in java.base, so it's there even in a trimmed-down bundled Java.
			java.util.Optional<java.time.Instant> started = ProcessHandle.current().info().startInstant();
			if (!started.isPresent()) return newestLog(folder);
			jvmStart = started.get().toEpochMilli();
		} catch (RuntimeException | Error e) {
			return newestLog(folder);
		}
		// The log named closest to this process's start. (The launcher writes a short log of its own
		// ~20 s before the game starts, so "first after" picked the launcher's log, not the game's.)
		Path best = null;
		long bestGap = Long.MAX_VALUE;
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder, "yohoho_*.log")) {
			for (Path p : ds) {
				Matcher m = LOG_NAME.matcher(p.getFileName().toString());
				if (!m.matches()) continue;
				long gap = Math.abs(Long.parseLong(m.group(1)) - jvmStart);
				if (gap <= 120_000 && gap < bestGap) { best = p; bestGap = gap; }
			}
		} catch (IOException | RuntimeException e) {
			return newestLog(folder);
		}
		return best != null ? best : newestLog(folder);
	}

	/** Reads the whole log; the latest value of each field wins. Returns null if the file can't be read. */
	public static Info read(Path log) {
		if (log == null) return null;
		Info info = new Info();
		info.file = log;
		java.util.Map<Long, String> names = new java.util.HashMap<>();
		// InputStreamReader replaces bad bytes instead of throwing, unlike Files.newBufferedReader.
		try (BufferedReader r = new BufferedReader(new java.io.InputStreamReader(Files.newInputStream(log), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) {
				Matcher m;
				if ((m = CLIENT.matcher(line)).find()) info.pirate = m.group(1);
				else if ((m = OCEAN.matcher(line)).find()) info.ocean = capitalise(m.group(1));
				else if ((m = VESSEL_CHANGE.matcher(line)).find()) {
					long nvid = Long.parseLong(m.group(2));
					if (nvid > 0) info.vesselId = nvid;
				} else if (line.contains("vessel=") && (m = VESSEL_NAME.matcher(line)).find()) {
					names.put(Long.parseLong(m.group(2)), m.group(1).trim());
				}
			}
		} catch (IOException | RuntimeException e) {
			if (info.pirate == null && info.ocean == null) return null;
		}
		if (info.vesselId != null) info.vesselName = names.get(info.vesselId);
		return info;
	}

	static String capitalise(String s) {
		if (s == null || s.isEmpty()) return s;
		return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
	}
}
