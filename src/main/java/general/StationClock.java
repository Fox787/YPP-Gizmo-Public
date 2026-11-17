package main.java.general;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Woodhallow station periods from the live chat log, using the same boundary messages as
 * Thalassa's werewolves_performance:
 *   start  "N o' M still standin'! To yer stations!"  (or "... Plunder the lair!" for the lair)
 *   end    "The wolf-kin gather! 30 seconds!" / "The lair stirs! 30 seconds left to forage!" + 30 s,
 *          or earlier on "Wolf-kin approach!", "Beware! More wolf-kin approach!",
 *          "We've reached their lair! Almost done!", "The lair be plundered!...", the crew being
 *          overrun, falling back or retreating.
 * Arriving ("Hark the dread howlin'! 'Tis Woodhallow Isle...") starts a new run.
 * The chat log is read incrementally, like Thalassa's log_parser.
 */
public final class StationClock {

	public static final class Period {
		public long start;
		public long end;          // 0 while open
		public int survivors, crew;
		public boolean lair;

		public boolean closed() { return end > 0; }
	}

	static final String ARRIVAL = "Hark the dread howlin'! 'Tis Woodhallow Isle, realm o' the wolf-folk!";
	static final Pattern SURVIVORS = Pattern.compile("^(\\d+) o' (\\d+) still standin'! (To yer stations!|Plunder the lair!)$");
	private static final Pattern DATE = Pattern.compile("^=+ (\\d{4})/(\\d{1,2})/(\\d{1,2}) =+$");
	private static final Pattern LINE = Pattern.compile("^\\[(\\d{2}):(\\d{2}):(\\d{2})\\] (.*)$");

	private final List<Period> periods = new ArrayList<>();
	private Path file;
	private long offset;
	private LocalDate date;
	private int lastClock = -1;

	/** Follows this chat log; reading it from the start rebuilds the current run's periods. */
	public void follow(Path chatLog) {
		if (chatLog == null || chatLog.equals(file)) return;
		file = chatLog;
		offset = 0;
		date = null;
		lastClock = -1;
		periods.clear();
	}

	public Path file() { return file; }

	/** Called when the chat log shows a new arrival at Woodhallow (a new run starts). */
	private Runnable onArrival;
	public void setOnArrival(Runnable r) { onArrival = r; }

	/** Called with each live message (not ones replayed while catching up on the log at startup). */
	private java.util.function.Consumer<String> onLiveMessage;
	public void setOnLiveMessage(java.util.function.Consumer<String> c) { onLiveMessage = c; }

	/** Reads whatever the client has appended since the last call. */
	public void poll() {
		if (file == null) return;
		try {
			long size = Files.size(file);
			if (size < offset) { offset = 0; date = null; lastClock = -1; periods.clear(); } // truncated: start over
			if (size == offset) return;
			byte[] buf;
			try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
				raf.seek(offset);
				buf = new byte[(int) Math.min(size - offset, 64L * 1024 * 1024)];
				raf.readFully(buf);
			}
			int end = buf.length;
			while (end > 0 && buf[end - 1] != '\n') end--;   // leave a half-written line for next time
			if (end == 0) return;
			offset += end;
			for (String raw : new String(buf, 0, end, StandardCharsets.UTF_8).split("\r?\n")) feed(raw);
		} catch (IOException | RuntimeException ignored) {
			// Try again next poll.
		}
	}

	/** One raw chat log line. */
	void feed(String raw) {
		String line = raw.trim();
		Matcher m = DATE.matcher(line);
		if (m.matches()) {
			date = LocalDate.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)));
			lastClock = -1;
			return;
		}
		m = LINE.matcher(line);
		if (!m.matches() || date == null) return;
		int h = Integer.parseInt(m.group(1)), mi = Integer.parseInt(m.group(2)), s = Integer.parseInt(m.group(3));
		if (h > 23 || mi > 59 || s > 59) return;
		int clock = h * 3600 + mi * 60 + s;
		if (lastClock >= 0 && lastClock - clock > 43200) date = date.plusDays(1);   // past midnight
		lastClock = clock;
		long now = LocalDateTime.of(date, LocalTime.of(h, mi, s)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		message(m.group(4), now);
	}

	/** Crew size from the run's "N o' M still standin'!" lines (M), 0 until seen. */
	private int crew;
	/** Pirates eliminated in the current fray (reset when stations open). */
	private int frayDeaths;

	public int crew() { return crew; }
	public int frayDeaths() { return frayDeaths; }

	// "Name is eliminated!" - pirate names are one word; the wolves' names have two ("Sly Moe").
	private static final Pattern ELIMINATED = Pattern.compile("^([A-Z][A-Za-z]*) is eliminated!$");

	/**
	 * Elite werewolves (the ones that transform; they deal no damage) still up in the current fray:
	 * "Transformation! N werewolves have transformed!" minus elites eliminated since. -1 if unknown.
	 */
	private int elitesLeft = -1;
	public int elitesLeft() { return elitesLeft; }

	private static final Pattern TRANSFORMED = Pattern.compile("^Transformation! (\\d+) werewolves have transformed!$");
	private static final Pattern WOLF_ELIMINATED = Pattern.compile("^(\\S+) [A-Z][A-Za-z'-]* is eliminated!$");
	/** Name prefixes of transformed (elite) werewolves; the wolf-kin use a different set (Sly, Lean, ...). */
	static final java.util.Set<String> ELITE_PREFIXES = new java.util.HashSet<>(java.util.Arrays.asList(
			"Accursed", "Blood-moon", "Bone-cracking", "Dread", "Fell", "Flesh-rending", "Frenzied", "Ghastly",
			"Gore-jawed", "Grave-howling", "Hulking", "Iron-clawed", "Marrow-hungry", "Merciless", "Monstrous",
			"Moon-cursed", "Murderous", "Night-stalking", "Nightmarish", "Rabid", "Red-eyed", "Shadow-born",
			"Silver-scarred", "Skull-splitting", "Unholy"));

	void message(String msg, long now) {
		if (onLiveMessage != null && Math.abs(System.currentTimeMillis() - now) < 120_000) onLiveMessage.accept(msg);
		Matcher el = ELIMINATED.matcher(msg);
		if (el.matches()) { frayDeaths++; return; }
		Matcher tr = TRANSFORMED.matcher(msg);
		if (tr.matches()) { elitesLeft = Integer.parseInt(tr.group(1)); return; }
		Matcher we = WOLF_ELIMINATED.matcher(msg);
		if (we.matches()) {
			if (elitesLeft > 0 && ELITE_PREFIXES.contains(we.group(1))) elitesLeft--;
			return;
		}
		if (msg.equals(ARRIVAL)) {
			crew = 0;
			frayDeaths = 0;
			elitesLeft = -1;
			periods.clear();
			// Only a live arrival, not one replayed while catching up on the log at startup.
			if (onArrival != null && Math.abs(System.currentTimeMillis() - now) < 120_000) onArrival.run();
			return;
		}
		Matcher sv = SURVIVORS.matcher(msg);
		if (sv.matches()) {
			crew = Integer.parseInt(sv.group(2));
			frayDeaths = 0;
			Period last = periods.isEmpty() ? null : periods.get(periods.size() - 1);
			if (last != null && !last.closed()) return;   // same period announced twice
			Period p = new Period();
			p.start = now;
			p.survivors = Integer.parseInt(sv.group(1));
			p.crew = Integer.parseInt(sv.group(2));
			p.lair = sv.group(3).startsWith("Plunder");
			periods.add(p);
			return;
		}
		if (periods.isEmpty()) return;
		Period last = periods.get(periods.size() - 1);
		if (msg.equals("The wolf-kin gather! 30 seconds!") || msg.equals("The lair stirs! 30 seconds left to forage!")) {
			if (!last.closed()) last.end = now + 30_000;
		} else if (msg.equals("Wolf-kin approach!") || msg.equals("Beware! More wolf-kin approach!")
				|| msg.equals("We've reached their lair! Almost done!")
				|| msg.startsWith("The lair be plundered!") || msg.equals("The pack has overrun the crew! Ye flee back to the ship.")
				|| msg.startsWith("The crew falls back") || msg.startsWith("Ye retreat")) {
			if (!last.closed() || now < last.end) last.end = now;
		}
	}

	/** Latest station period that has ended by now (allowing for a report arriving a little early). */
	public Period lastEnded(long now) {
		for (int i = periods.size() - 1; i >= 0; i--) {
			Period p = periods.get(i);
			if (p.closed() && p.end <= now + 5000) return p;
		}
		return null;
	}

	/**
	 * When the given period's stations close: the "30 seconds" warning's time if seen, otherwise
	 * 2 minutes after the start for the run's first period and 5 minutes for the rest.
	 */
	public long expectedEnd(Period p) {
		if (p == null) return 0;
		if (p.closed()) return p.end;
		return p.start + duration(p);
	}

	/**
	 * How long stations last, from Thalassa's duty_seconds: 2:00 for the first wave, otherwise
	 * 2:00 + 3:00 x survivors / crew ("N o' M still standin'!"). So 5:00 with everyone standing,
	 * and e.g. 3:07.5 with 3 of 8.
	 */
	public long duration(Period p) {
		boolean first = !periods.isEmpty() && periods.get(0) == p;
		if (first || p.crew <= 0) return 120_000L;
		return 120_000L + Math.round(180_000.0 * p.survivors / p.crew);
	}

	public Period current() {
		return periods.isEmpty() ? null : periods.get(periods.size() - 1);
	}
}
