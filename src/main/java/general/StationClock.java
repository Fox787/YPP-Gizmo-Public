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

	void message(String msg, long now) {
		if (msg.equals(ARRIVAL)) { periods.clear(); return; }
		Matcher sv = SURVIVORS.matcher(msg);
		if (sv.matches()) {
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

	public Period current() {
		return periods.isEmpty() ? null : periods.get(periods.size() - 1);
	}
}
