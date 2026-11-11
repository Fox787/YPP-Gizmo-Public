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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

	/** Newest chat log for this pirate and ocean ("Pirate_ocean_*"), or the newest for the pirate, or null. */
	public static Path findLog(Path folder, String pirate, String ocean) {
		if (folder == null || !Files.isDirectory(folder)) return null;
		Path best = null, bestAnyOcean = null;
		long bestT = Long.MIN_VALUE, bestAnyT = Long.MIN_VALUE;
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder)) {
			for (Path p : ds) {
				if (!Files.isRegularFile(p)) continue;
				String n = p.getFileName().toString().toLowerCase();
				// Thalassa accepts files with no extension or .txt.
				if (n.contains(".") && !n.endsWith(".txt")) continue;
				String[] parts = n.split("_");
				if (pirate != null && !parts[0].equalsIgnoreCase(pirate)) continue;
				long t = Files.getLastModifiedTime(p).toMillis();
				if (t > bestAnyT) { bestAnyOcean = p; bestAnyT = t; }
				if (ocean != null && (parts.length < 2 || !parts[1].equalsIgnoreCase(ocean))) continue;
				if (t > bestT) { best = p; bestT = t; }
			}
		} catch (IOException e) {
			return null;
		}
		return best != null ? best : bestAnyOcean;
	}

	/** Reads every dated line. Lines before the first date header get no date and are skipped. */
	public static List<Line> read(Path file) throws IOException {
		List<Line> out = new ArrayList<>();
		LocalDate date = null;
		LocalTime last = null;
		try (BufferedReader r = new BufferedReader(new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8))) {
			String raw;
			while ((raw = r.readLine()) != null) {
				Matcher m = DATE.matcher(raw.trim());
				if (m.matches()) {
					date = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
					last = null;
					continue;
				}
				m = LINE.matcher(raw);
				if (!m.matches() || date == null) continue;
				LocalTime t = LocalTime.of(Integer.parseInt(m.group(1)) % 24, Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
				// The client only writes a date header at login, so a session past midnight rolls over here.
				if (last != null && t.toSecondOfDay() < last.toSecondOfDay() - 3600) date = date.plusDays(1);
				last = t;
				out.add(new Line(LocalDateTime.of(date, t), m.group(4), raw));
			}
		}
		return out;
	}

	/**
	 * The latest division: anchored on the last "The crew opens N chests..." line, and reaching back
	 * to the previous division (a "booty has been divided" more than a minute before the anchor).
	 */
	public static Segment latestBooty(Path file) throws IOException {
		List<Line> lines = read(file);
		Segment s = new Segment();
		s.file = file;

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
