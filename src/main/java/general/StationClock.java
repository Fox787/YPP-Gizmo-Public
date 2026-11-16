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
		public volatile long end;          // 0 while open
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
	public synchronized void follow(Path chatLog) {
		if (chatLog == null || chatLog.equals(file)) return;
		file = chatLog;
		offset = 0;
		date = null;
		lastClock = -1;
		resetRun();
	}

	public Path file() { return file; }

	/** Reads whatever the client has appended since the last call. */
	public void poll() {
		if (file == null) return;
		try {
			long size = Files.size(file);
			if (size < offset) { offset = 0; date = null; lastClock = -1; resetRun(); } // truncated: start over
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
	synchronized void feed(String raw) {
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

	private synchronized void resetRun() {
		periods.clear();
		inDen = false;
		crew = fraysDone = frayDeaths = 0;
		wolvesAtHalf = elitesLeft = wolvesLeft = -1;
		wolvesChangedAt = lastWolfClearedAt = thirtySecondsAt = 0;
	}

	private int crew;
	/** Pirates eliminated in the current fray (reset when stations open). */
	private int frayDeaths;

	public synchronized int crew() { return crew; }
	public synchronized int frayDeaths() { return frayDeaths; }

	// "Name is eliminated!" - pirate names are one word; the wolves' names have two ("Sly Moe").
	private static final Pattern ELIMINATED = Pattern.compile("^([A-Z][A-Za-z]*) is eliminated!$");

	/**
	 * Elite werewolves (the ones that transform; they deal no damage) still up in the current fray:
	 * "Transformation! N werewolves have transformed!" minus elites eliminated since. -1 if unknown.
	 */
	private int elitesLeft = -1;
	public synchronized int elitesLeft() { return elitesLeft; }

	private static final Pattern TRANSFORMED = Pattern.compile("^Transformation! (\\d+) werewolves have transformed!$");
	private static final Pattern WOLF_ELIMINATED = Pattern.compile("^(\\S+) [A-Z][A-Za-z'-]* is eliminated!$");
	/** Name prefixes of transformed (elite) werewolves; the wolf-kin use a different set (Sly, Lean, ...). */
	static final java.util.Set<String> ELITE_PREFIXES = java.util.Set.of(
			"Accursed", "Blood-moon", "Bone-cracking", "Dread", "Fell", "Flesh-rending", "Frenzied", "Ghastly",
			"Gore-jawed", "Grave-howling", "Hulking", "Iron-clawed", "Marrow-hungry", "Merciless", "Monstrous",
			"Moon-cursed", "Murderous", "Night-stalking", "Nightmarish", "Rabid", "Red-eyed", "Shadow-born",
			"Silver-scarred", "Skull-splitting", "Unholy");

	/** In a den: from "Hark the dread howlin'!" until the den ends (lair plundered, overrun, retreat). */
	private boolean inDen;
	/** Frays finished in this den (each ends with "N o' M still standin'!"). */
	private int fraysDone;
	/** Werewolves left for the next wave as the chat last said; -1 until "turned back half". */
	private int wolvesLeft = -1;
	private int wolvesAtHalf = -1;
	/** When the chat last changed wolvesLeft, and when stations opened (for the pace). */
	private long wolvesChangedAt;
	/** Times of the last "turned back the last werewolf" and "30 seconds" messages (for the sounds). */
	private long lastWolfClearedAt, thirtySecondsAt;

	public synchronized boolean inDen() { return inDen; }
	/** The fray being fought or next: frays finished + 1. */
	public synchronized int frayNumber() { return fraysDone + 1; }
	record WolfProgress(int remaining, int atHalf, long changedAt) { }

	synchronized WolfProgress wolfProgress(Period period) {
		return current() == period ? new WolfProgress(wolvesLeft, wolvesAtHalf, wolvesChangedAt) : null;
	}
	public synchronized long lastWolfClearedAt() { return lastWolfClearedAt; }
	public synchronized long thirtySecondsAt() { return thirtySecondsAt; }

	private static final Pattern HALF = Pattern.compile("charting has turned back half the werewolves! (\\d+) still to come");
	private static final Pattern ONLY = Pattern.compile("charting has turned back more o' the werewolves! Only (\\d+) left");

	synchronized void message(String msg, long now) {
		if (msg.equals(ARRIVAL)) {
			resetRun();
			inDen = true;
			return;
		}
		Matcher sv = SURVIVORS.matcher(msg);
		if (!inDen && !sv.matches()) return;
		Matcher hw = HALF.matcher(msg);
		if (hw.find()) {
			wolvesAtHalf = wolvesLeft = Integer.parseInt(hw.group(1));
			wolvesChangedAt = now;
			return;
		}
		Matcher on = ONLY.matcher(msg);
		if (on.find()) { wolvesLeft = Integer.parseInt(on.group(1)); wolvesChangedAt = now; return; }
		if (msg.contains("charting has turned back the last werewolf!")) {
			wolvesLeft = 0; wolvesChangedAt = now; lastWolfClearedAt = now; return;
		}
		if (msg.startsWith("The lair be plundered!") || msg.equals("The pack has overrun the crew! Ye flee back to the ship.")
				|| msg.startsWith("The crew falls back") || msg.startsWith("Ye retreat")) inDen = false;
		Matcher el = ELIMINATED.matcher(msg);
		if (el.matches()) { frayDeaths++; return; }
		Matcher tr = TRANSFORMED.matcher(msg);
		if (tr.matches()) { elitesLeft = Integer.parseInt(tr.group(1)); return; }
		Matcher we = WOLF_ELIMINATED.matcher(msg);
		if (we.matches()) {
			if (elitesLeft > 0 && ELITE_PREFIXES.contains(we.group(1))) elitesLeft--;
			return;
		}
		if (sv.matches()) {
			inDen = true;
			Period last = current();
			if (last != null && (!last.closed() || now < last.end)) return;
			// Pirates who leave still count for the next pack.
			crew = Math.max(crew, Integer.parseInt(sv.group(2)));
			frayDeaths = 0;
			elitesLeft = -1;
			fraysDone++;
			wolvesAtHalf = wolvesLeft = -1;
			wolvesChangedAt = 0;
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
			if (!last.closed()) {
				last.end = now + 30_000;
				thirtySecondsAt = now;
			}
		} else if (msg.equals("Wolf-kin approach!") || msg.equals("Beware! More wolf-kin approach!")
				|| msg.equals("We've reached their lair! Almost done!")
				|| msg.startsWith("The lair be plundered!") || msg.equals("The pack has overrun the crew! Ye flee back to the ship.")
				|| msg.startsWith("The crew falls back") || msg.startsWith("Ye retreat")) {
			if (!last.closed() || now < last.end) last.end = now;
		}
	}

	/** Latest station period that has ended by now (allowing for a report arriving a little early). */
	public synchronized Period lastEnded(long now) {
		for (int i = periods.size() - 1; i >= 0; i--) {
			Period p = periods.get(i);
			if (p.closed() && p.end <= now + 5000) return p;
		}
		return null;
	}

	/**
	 * When the given period's stations close: the "30 seconds" warning's time if seen, otherwise
	 * the expected end from the survivors and crew counts.
	 */
	public synchronized long expectedEnd(Period p) {
		if (p == null) return 0;
		if (p.closed()) return p.end;
		return p.start + duration(p);
	}

	/**
	 * How long stations last, from Thalassa's duty_seconds: 2:00 for the first wave, otherwise
	 * 2:00 + 3:00 x survivors / crew ("N o' M still standin'!"). So 5:00 with everyone standing,
	 * and e.g. 3:07.5 with 3 of 8.
	 */
	public synchronized long duration(Period p) {
		boolean first = !periods.isEmpty() && periods.get(0) == p;
		if (first || p.crew <= 0) return 120_000L;
		return 120_000L + Math.round(180_000.0 * Math.min(p.survivors, p.crew) / p.crew);
	}

	public synchronized Period current() {
		return periods.isEmpty() ? null : periods.get(periods.size() - 1);
	}
}
