package main.java.general;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a Puzzle Pirates chat log (e.g. "Pirate_emerald_x" in the chat log folder) and pulls out the
 * lines that belong to the latest booty division. The file layout follows Thalassa's log_parser:
 * "====== 2026/11/7 ======" date headers, then "[HH:MM:SS] text" lines.
 */
public final class ChatLog {

	public static final class Line {
		public final LocalDateTime time;
		public final String text;
		public final String raw;

		Line(LocalDateTime time, String text, String raw) {
			this.time = time;
			this.text = text;
			this.raw = raw;
		}
	}

	/** The voyage's booty lines, from just after the previous division up to this one. */
	public static final class Segment {
		public Path file;
		public LocalDateTime openedAt;
		public final List<Line> lines = new ArrayList<>();
		public final Map<String, Integer> takenAboard = new LinkedHashMap<>();
		public Integer chestsOpened, chestItems;
		public Long chestPoe;
		public int battlesWon, battlesLost;
		public long plunderedPoe;
		public Long myCut;
	}

	private static final Pattern DATE = Pattern.compile("^=+ (\\d{4})/(\\d{1,2})/(\\d{1,2}) =+$");
	private static final Pattern LINE = Pattern.compile("^\\[(\\d{1,2}):(\\d{2}):(\\d{2})\\] (.*)$");

	private static final Pattern TAKEN_ABOARD = Pattern.compile("^Ye have taken aboard ([\\d,]+) (.+)!$");
	private static final Pattern CREW_OPENS = Pattern.compile(
			"^The crew opens ([\\d,]+) chests? and finds (no|[\\d,]+) items? and ([\\d,]+) pieces? of eight\\.");
	private static final Pattern PLUNDERED = Pattern.compile("^The victors plundered ([\\d,]+) pieces? of eight");
	private static final Pattern MY_CUT = Pattern.compile("^You received ([\\d,]+) pieces? of eight as your cut of the booty!$");
	private static final String DIVIDED = "The booty has been divided!";

	/** Lines worth keeping alongside the booty screen. */
	private static final Pattern BOOTY_LINE = Pattern.compile(
			"^(Ye have taken aboard |The crew opens |The victors plundered |Ye received .* initial cut|You received .* cut of the booty"
			+ "|The defeat cost ye |The mercenaries have taken |.* is dividing the booty\\.$|The booty has been divided!)");

	private ChatLog() { }

	/** Usual chat log folders, first that exists wins. */
	public static Path guessFolder() {
		String home = System.getProperty("user.home");
		String[] candidates = {
				home + "\\OneDrive\\Documents\\YPP",
				home + "\\Documents\\YPP",
				home + "\\OneDrive\\Documents\\Puzzle Pirates",
				home + "\\Documents\\Puzzle Pirates",
		};
		for (String c : candidates) {
			Path p = Paths.get(c);
			if (Files.isDirectory(p)) return p;
		}
		return null;
	}

	/** Main log of the newest matching log family. */
	public static Path findLog(Path folder, String pirate, String ocean) {
		List<Path> files = findLogs(folder, pirate, ocean);
		return files.isEmpty() ? null : files.get(0);
	}

	private record LogFile(Path path, String stem, String extension, String ocean, long modified) { }

	/** Refresh directory discovery separately from the one-second tail poll. */
	static final class Discovery {
		private record Source(Path folder, String pirate, String ocean) { }
		private Source source;
		private long nextLookup;
		private List<Path> logs = List.of();

		List<Path> logs(Path folder, String pirate, String ocean, long now) {
			Source current = new Source(folder, pirate, ocean);
			if (!current.equals(source) || now >= nextLookup) {
				logs = findLogs(folder, pirate, ocean);
				source = current;
				nextLookup = now + 5_000;
			}
			return logs;
		}
	}

	/** The newest log family for this pirate and ocean, with the main log first. */
	public static List<Path> findLogs(Path folder, String pirate, String ocean) {
		if (folder == null) return List.of();
		List<LogFile> candidates = new ArrayList<>();
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder)) {
			for (Path p : ds) {
				String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
				String extension = n.endsWith(".txt") ? ".txt" : "";
				if (extension.isEmpty() && n.contains(".")) continue;
				String stem = n.substring(0, n.length() - extension.length());
				String[] parts = stem.split("_", 3);
				if (pirate != null && !parts[0].equalsIgnoreCase(pirate)) continue;
				if (!Files.isRegularFile(p)) continue;
				try {
					candidates.add(new LogFile(p, stem, extension, parts.length < 2 ? "" : parts[1],
							Files.getLastModifiedTime(p).toMillis()));
				} catch (IOException ignored) {
					// A log may have disappeared during the directory scan.
				}
			}
		} catch (IOException e) {
			return List.of();
		}
		candidates.sort(Comparator.comparingLong(LogFile::modified).reversed().thenComparing(LogFile::stem));
		if (candidates.isEmpty()) return List.of();
		LogFile newest = candidates.get(0);
		if (ocean != null) {
			for (LogFile candidate : candidates) {
				if (ocean.equalsIgnoreCase(candidate.ocean)) { newest = candidate; break; }
			}
		}
		// The game appends a sanitized tab name to the main log stem, before any extension.
		LogFile main = newest;
		for (LogFile candidate : candidates) {
			if (isTabOf(main, candidate)) main = candidate;
		}
		List<Path> files = new ArrayList<>();
		files.add(main.path);
		LogFile root = main;
		String[] parts = root.stem.split("_", 4);
		boolean tabsOnly = parts.length == 4 && parts[2].equals("x");
		String family = tabsOnly ? String.join("_", parts[0], parts[1], parts[2]) : root.stem;
		candidates.stream().filter(candidate -> !candidate.path.equals(root.path)
						&& candidate.extension.equals(root.extension) && candidate.stem.startsWith(family + "_"))
				.sorted(Comparator.comparing(LogFile::stem)).forEach(candidate -> files.add(candidate.path));
		if (tabsOnly) files.sort(Comparator.comparing(path -> path.toString().toLowerCase(Locale.ROOT)));
		return List.copyOf(files);
	}

	private static boolean isTabOf(LogFile tab, LogFile main) {
		return tab.extension.equals(main.extension) && tab.ocean.equals(main.ocean)
				&& tab.stem.startsWith(main.stem + "_");
	}

	/** Each file has its own date headers and midnight rollovers. */
	static final class Parser {
		private LocalDate date;
		private LocalTime last;

		Line readLine(String raw) {
			Matcher m = DATE.matcher(raw.trim());
			if (m.matches()) {
				try {
					date = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
				} catch (java.time.DateTimeException e) {
					date = null;
				}
				last = null;
				return null;
			}
			m = LINE.matcher(raw);
			if (!m.matches() || date == null) return null;
			int h = Integer.parseInt(m.group(1)), min = Integer.parseInt(m.group(2)), sec = Integer.parseInt(m.group(3));
			if (h > 24 || min > 59 || sec > 59) return null;
			LocalTime t = LocalTime.of(h % 24, min, sec);
			if (last != null && t.toSecondOfDay() < last.toSecondOfDay() - 3600) date = date.plusDays(1);
			last = t;
			return new Line(LocalDateTime.of(date, t), m.group(4), raw);
		}
	}

	private record SourcedLine(Path file, Line line) { }

	/** Merges in timestamp order, keeping the greatest number of copies seen in any one file. */
	static final class Merge {
		private final NavigableMap<LocalDateTime, Map<String, Map<Path, Integer>>> counts = new TreeMap<>();

		List<Line> accept(Map<Path, List<Line>> batches) {
			List<SourcedLine> ordered = new ArrayList<>();
			batches.forEach((file, lines) -> lines.forEach(line -> ordered.add(new SourcedLine(file, line))));
			ordered.sort(Comparator.comparing(entry -> entry.line.time));
			List<Line> merged = new ArrayList<>();
			for (SourcedLine entry : ordered) {
				Line line = entry.line;
				// Tab writes can straddle two polls. Keep recent counts so those copies still match.
				counts.headMap(line.time.minusMinutes(1), false).clear();
				Map<Path, Integer> copies = counts.computeIfAbsent(line.time, t -> new HashMap<>())
						.computeIfAbsent(line.text, text -> new HashMap<>());
				int previousMax = 0;
				for (int count : copies.values()) previousMax = Math.max(previousMax, count);
				if (copies.merge(entry.file, 1, Integer::sum) > previousMax) merged.add(line);
			}
			return merged;
		}
	}

	/** Reads every dated line, skipping lines before the first date header. */
	public static List<Line> read(Path file) throws IOException {
		return read(file, line -> true);
	}

	private static List<Line> read(Path file, java.util.function.Predicate<Line> include) throws IOException {
		Parser parser = new Parser();
		List<Line> lines = new ArrayList<>();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
			String raw;
			while ((raw = reader.readLine()) != null) {
				Line line = parser.readLine(raw);
				if (line != null && include.test(line)) lines.add(line);
			}
		}
		return lines;
	}

	/**
	 * The latest division: anchored on the last "The crew opens N chests..." line, and reaching back
	 * to the previous division (a "booty has been divided" more than a minute before the anchor).
	 */
	public static Segment latestBooty(Path file) throws IOException {
		return latestBooty(List.of(file));
	}

	/** Reads the most recent booty division across a main chat log and its tab logs. */
	public static Segment latestBooty(List<Path> files) throws IOException {
		Map<Path, List<Line>> batches = new LinkedHashMap<>();
		IOException failure = null;
		for (Path file : files) {
			try {
				batches.put(file, read(file, line -> BOOTY_LINE.matcher(line.text).find()));
			} catch (IOException e) {
				failure = e;
			}
		}
		if (batches.isEmpty() && failure != null) throw failure;
		List<Line> lines = new Merge().accept(batches);
		Segment s = new Segment();
		if (!batches.isEmpty()) s.file = batches.keySet().iterator().next();

		int anchor = -1;
		for (int i = lines.size() - 1; i >= 0; i--) {
			if (CREW_OPENS.matcher(lines.get(i).text).find()) { anchor = i; break; }
		}
		if (anchor < 0) return s;
		LocalDateTime openedAt = lines.get(anchor).time;
		s.openedAt = openedAt;

		// The previous division ends at its own "crew opens" line plus the few lines logged with it
		// ("divided", "You received ... cut"), which can land either side of it.
		int start = 0;
		for (int i = anchor - 1; i >= 0; i--) {
			Line l = lines.get(i);
			if (CREW_OPENS.matcher(l.text).find()) {
				start = closingEnd(lines, i) + 1;
				break;
			}
		}
		int end = closingEnd(lines, anchor);

		for (int i = start; i <= end; i++) {
			Line l = lines.get(i);
			if (!BOOTY_LINE.matcher(l.text).find()) continue;
			s.lines.add(l);
			Matcher m;
			if ((m = TAKEN_ABOARD.matcher(l.text)).matches()) {
				Integer prev = s.takenAboard.get(m.group(2));
				int n = parseInt(m.group(1));
				s.takenAboard.put(m.group(2), prev == null ? n : prev + n);
			} else if (i == anchor && (m = CREW_OPENS.matcher(l.text)).find()) {
				s.chestsOpened = parseInt(m.group(1));
				s.chestItems = m.group(2).equals("no") ? 0 : parseInt(m.group(2));
				s.chestPoe = (long) parseInt(m.group(3));
			} else if ((m = PLUNDERED.matcher(l.text)).find()) {
				s.battlesWon++;
				s.plunderedPoe += parseInt(m.group(1));
			} else if (l.text.startsWith("The defeat cost ye ")) {
				s.battlesLost++;
			} else if ((m = MY_CUT.matcher(l.text)).matches()) {
				s.myCut = (long) parseInt(m.group(1));
			}
		}
		return s;
	}

	/** Last index of the closing lines logged within 10 seconds after the "crew opens" line at i. */
	private static int closingEnd(List<Line> lines, int i) {
		LocalDateTime t = lines.get(i).time;
		int end = i;
		while (end + 1 < lines.size() && Duration.between(t, lines.get(end + 1).time).getSeconds() <= 10) {
			String next = lines.get(end + 1).text;
			if (!next.equals(DIVIDED) && !MY_CUT.matcher(next).matches() && !next.endsWith(" is dividing the booty.")) break;
			end++;
		}
		return end;
	}

	private static int parseInt(String s) {
		return Integer.parseInt(s.replace(",", ""));
	}
}
