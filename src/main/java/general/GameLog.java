package main.java.general;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
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

	// "Client object Ozpin (o:..."; since 2026/11/18 the client writes "Ozpin$ (o:...", so allow marks after the name.
	private static final Pattern CLIENT = Pattern.compile("Client object ([\\w-]+)[^\\s(]* \\(");
	private static final Pattern OCEAN = Pattern.compile("Configuring client \\[ocean=(\\w+)");
	private static final Pattern VESSEL_CHANGE = Pattern.compile("Vessel change \\[ovid=(-?\\d+), nvid=(-?\\d+)\\]");
	private static final Pattern VESSEL_NAME = Pattern.compile("vessel=([^(\\[\\]]+)\\((\\d+) ");
	private static final Pattern LOG_NAME = Pattern.compile("yohoho_(\\d+)\\.log");
	private static final Tail tail = new Tail();
	private static volatile Path currentLog;
	private static long nextLookup;
	private static volatile Info current;

	private GameLog() { }

	private static volatile Path outputFolder;

	static void setDataFolder(Path folder) { outputFolder = folder; }

	static Path dataFolder() {
		return safeOutputFolder(outputFolder);
	}

	static Path defaultDataFolder() {
		return Paths.get(System.getProperty("user.home"), "Gizmo").toAbsolutePath().normalize();
	}

	/** All generated data stays outside the game installation, including saved legacy folder choices. */
	static Path safeOutputFolder(Path folder) {
		Path candidate = folder == null ? defaultDataFolder() : folder.toAbsolutePath().normalize();
		if (isGamePath(candidate)) candidate = defaultDataFolder();
		if (isGamePath(candidate)) throw new IllegalStateException("Choose a Gizmo data folder outside Puzzle Pirates.");
		return candidate;
	}

	static void requireOutputPath(Path path) throws IOException {
		if (isGamePath(path)) throw new IOException("Gizmo cannot write inside the Puzzle Pirates folder.");
	}

	static Path temporaryFolder(String name) throws IOException {
		Path folder = Paths.get(System.getProperty("java.io.tmpdir"), name);
		if (isGamePath(folder)) folder = defaultDataFolder().resolve("temp").resolve(name);
		requireOutputPath(folder);
		return folder;
	}

	static boolean isGamePath(Path path) {
		Path requested = path.toAbsolutePath().normalize();
		Path target = resolvedPath(path);
		Path client = clientFolder();
		Path root = client;
		for (Path parent = client; parent != null; parent = parent.getParent()) {
			if (parent.getFileName() != null && parent.getFileName().toString().toLowerCase(java.util.Locale.ROOT).startsWith("puzzle pirates")) {
				root = parent;
				break;
			}
		}
		if (root.equals(client) && client.getFileName() != null && client.getFileName().toString().equalsIgnoreCase("app")
				&& client.getParent() != null) root = client.getParent();
		if (requested.startsWith(root.normalize()) || target.startsWith(resolvedPath(root))) return true;
		// Also reject another installed client's folder, even if this process runs elsewhere.
		for (Path part : target)
			if (part.toString().toLowerCase(java.util.Locale.ROOT).startsWith("puzzle pirates")) return true;
		for (Path part : requested)
			if (part.toString().toLowerCase(java.util.Locale.ROOT).startsWith("puzzle pirates")) return true;
		return false;
	}

	/** Resolve existing ancestors too, so a junction cannot hide an output inside the game folder. */
	private static Path resolvedPath(Path path) {
		Path absolute = path.toAbsolutePath().normalize();
		for (Path existing = absolute; existing != null; existing = existing.getParent()) {
			try { return existing.toRealPath().resolve(existing.relativize(absolute)).normalize(); }
			catch (java.nio.file.NoSuchFileException ignored) { }
			catch (IOException e) { throw new IllegalStateException("Cannot check output folder: " + path, e); }
		}
		return absolute;
	}

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
				if (!Files.isRegularFile(p)) continue;
				try {
					long t = Files.getLastModifiedTime(p).toMillis();
					if (t > bestTime) { best = p; bestTime = t; }
				} catch (IOException ignored) { }
			}
		} catch (IOException e) {
			return null;
		}
		return best;
	}

	/** A snapshot of the log identity, published by the background poll. */
	public static Info read() {
		return copyOf(current);
	}

	static Path activeLog() { return currentLog; }

	static synchronized void poll(long now) {
		if (now >= nextLookup) {
			long start = 0;
			try {
				start = ProcessHandle.current().info().startInstant().map(java.time.Instant::toEpochMilli).orElse(0L);
			} catch (SecurityException | UnsupportedOperationException ignored) { }
			currentLog = ownLog(clientFolder(), start);
			nextLookup = now + (currentLog == null ? 5_000 : 30_000);
		}
		try {
			current = tail.poll(currentLog);
		} catch (IOException ignored) {
			if (current == null || !current.file.equals(currentLog)) current = null;
			// A client can rotate or briefly lock the file. Try again on the next poll.
		}
	}

	static Path ownLog(Path folder, long processStart) {
		Path best = null;
		long bestGap = 120_001;
		if (processStart > 0) {
			try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder, "yohoho_*.log")) {
				for (Path path : ds) {
					Matcher name = LOG_NAME.matcher(path.getFileName().toString());
					if (!name.matches() || !Files.isRegularFile(path)) continue;
					try {
						long gap = Math.abs(Long.parseLong(name.group(1)) - processStart);
						if (gap < bestGap) { best = path; bestGap = gap; }
					} catch (NumberFormatException ignored) { }
				}
			} catch (IOException ignored) { }
		}
		return best != null ? best : newestLog(folder);
	}

	/** Tails complete lines and rebuilds identity when the selected log changes or shrinks. */
	static final class Tail {
		private Path file;
		private long offset;
		private Info info;
		private final Map<Long, String> names = new HashMap<>();

		Info poll(Path log) throws IOException {
			if (log == null) return null;
			try (RandomAccessFile input = new RandomAccessFile(log.toFile(), "r")) {
				long size = input.length();
				if (!log.equals(file) || size < offset) {
					file = log;
					offset = 0;
					info = new Info();
					info.file = log;
					names.clear();
				}
				if (size > offset) {
					byte[] bytes = new byte[(int) Math.min(size - offset, 8L * 1024 * 1024)];
					input.seek(offset);
					input.readFully(bytes);
					int end = bytes.length;
					while (end > 0 && bytes[end - 1] != '\n') end--;
					if (end > 0) {
						offset += end;
						for (String line : new String(bytes, 0, end, StandardCharsets.UTF_8).split("\\r?\\n"))
							readIdentityLine(info, names, line);
					}
				}
			}
			info.vesselName = names.get(info.vesselId);
			return info.pirate == null && info.ocean == null ? null : copyOf(info);
		}
	}

	private static Info copyOf(Info info) {
		if (info == null) return null;
		Info copy = new Info();
		copy.file = info.file;
		copy.pirate = info.pirate;
		copy.ocean = info.ocean;
		copy.vesselId = info.vesselId;
		copy.vesselName = info.vesselName;
		return copy;
	}

	/** Reads the whole log; the latest value of each field wins. Returns null if the file can't be read. */
	public static Info read(Path log) {
		if (log == null) return null;
		Info info = new Info();
		info.file = log;
		Map<Long, String> names = new HashMap<>();
		// InputStreamReader replaces bad bytes instead of throwing, unlike Files.newBufferedReader.
		try (BufferedReader r = new BufferedReader(new java.io.InputStreamReader(Files.newInputStream(log), StandardCharsets.UTF_8))) {
			String line;
			while ((line = r.readLine()) != null) readIdentityLine(info, names, line);
		} catch (IOException | RuntimeException e) {
			if (info.pirate == null && info.ocean == null) return null;
		}
		if (info.vesselId != null) info.vesselName = names.get(info.vesselId);
		return info;
	}

	private static void readIdentityLine(Info info, Map<Long, String> names, String line) {
		Matcher m;
		if ((m = CLIENT.matcher(line)).find()) info.pirate = m.group(1);
		else if ((m = OCEAN.matcher(line)).find()) info.ocean = capitalise(m.group(1));
		else if ((m = VESSEL_CHANGE.matcher(line)).find()) {
			long id = Long.parseLong(m.group(2));
			if (id > 0) info.vesselId = id;
		} else if (line.contains("vessel=") && (m = VESSEL_NAME.matcher(line)).find()) {
			names.put(Long.parseLong(m.group(2)), m.group(1).trim());
		}
	}

	static String capitalise(String s) {
		if (s == null || s.isEmpty()) return s;
		return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase();
	}
}
