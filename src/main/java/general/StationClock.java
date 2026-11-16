package main.java.general;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
	private final List<Period> periods = new ArrayList<>();
	private final Map<Path, Tail> tails = new LinkedHashMap<>();
	private final ArrayDeque<ChatLog.Line> ownChat = new ArrayDeque<>();
	private Path mainLog;
	private String pirate;
	private ChatLog.Merge merge = new ChatLog.Merge();
	private LocalDateTime latest;

	/** Tails the matched log family on the file thread and publishes small batches to the UI. */
	void poll(List<Path> logs, String self) {
		Path main = logs.isEmpty() ? null : logs.get(0);
		Map<Path, Long> sizes = new LinkedHashMap<>();
		boolean changed = !java.util.Objects.equals(main, mainLog) || !self.equals(pirate);
		for (Path log : logs) {
			try {
				long size = Files.size(log);
				sizes.put(log, size);
				Tail existing = tails.get(log);
				if (existing != null && size < existing.offset) changed = true;
			} catch (IOException ignored) {
				// Retry the missing or locked file on the next poll.
			}
		}
		if (changed) {
			mainLog = main;
			pirate = self;
			tails.clear();
			merge = new ChatLog.Merge();
			latest = null;
			synchronized (this) {
				periods.clear();
				ownChat.clear();
			}
		}

		String ownPrefix = self + " says, \"";
		LocalDateTime cutoff = latest == null ? null : latest.minusMinutes(1);
		Map<Path, List<ChatLog.Line>> batches = new LinkedHashMap<>();
		for (var log : sizes.entrySet()) {
			try {
				Tail tail = tails.computeIfAbsent(log.getKey(), file -> new Tail());
				batches.put(log.getKey(), tail.read(log.getKey(), log.getValue(), ownPrefix, cutoff));
			} catch (IOException ignored) { }
		}
		List<ChatLog.Line> lines = merge.accept(batches);
		if (lines.isEmpty()) return;
		LocalDateTime newest = lines.get(lines.size() - 1).time;
		if (latest == null || newest.isAfter(latest)) latest = newest;

		ZoneId zone = ZoneId.systemDefault();
		for (int i = 0; i < lines.size(); i += 1000) {
			synchronized (this) {
				for (int j = i; j < Math.min(lines.size(), i + 1000); j++) {
					ChatLog.Line line = lines.get(j);
					if (line.text.startsWith(ownPrefix) && line.text.endsWith("\"")
							&& line.text.length() > ownPrefix.length()) {
						String sent = line.text.substring(ownPrefix.length(), line.text.length() - 1);
						ownChat.add(new ChatLog.Line(line.time, sent, line.raw));
						while (ownChat.size() > 20) ownChat.remove();
					} else {
						message(line.text, line.time.atZone(zone).toInstant().toEpochMilli());
					}
				}
			}
		}
	}

	synchronized List<ChatLog.Line> drainOwnChat() {
		List<ChatLog.Line> lines = new ArrayList<>(ownChat);
		ownChat.clear();
		return lines;
	}

	private static final class Tail {
		private long offset;
		private final ChatLog.Parser parser = new ChatLog.Parser();
		private ChatLog.Line pendingOwn;
		private final StringBuilder ownText = new StringBuilder();

		List<ChatLog.Line> read(Path file, long size, String ownPrefix, LocalDateTime cutoff) throws IOException {
			if (size == offset) return List.of();
			byte[] buf;
			try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
				raf.seek(offset);
				buf = new byte[(int) Math.min(size - offset, 8L * 1024 * 1024)];
				raf.readFully(buf);
			}
			int end = buf.length;
			while (end > 0 && buf[end - 1] != '\n') end--;   // leave a half-written line for next time
			if (end == 0) return List.of();
			List<ChatLog.Line> lines = new ArrayList<>();
			for (String raw : new String(buf, 0, end - 1, StandardCharsets.UTF_8).split("\\r?\\n", -1)) {
				String text = raw.trim();
				ChatLog.Line line = parser.readLine(text);
				if (line != null) {
					pendingOwn = null;
					if (cutoff != null && line.time.isBefore(cutoff)) continue;
					if (line.text.startsWith(ownPrefix)
							&& (line.text.length() == ownPrefix.length() || !line.text.endsWith("\""))) {
						pendingOwn = line;
						ownText.setLength(0);
						ownText.append(line.text);
					} else {
						lines.add(line);
					}
				} else if (text.startsWith("====== ")) {
					pendingOwn = null;
				} else if (pendingOwn != null) {
					ownText.append('\n').append(text);
					if (text.endsWith("\"")) {
						lines.add(new ChatLog.Line(pendingOwn.time, ownText.toString(), pendingOwn.raw));
						pendingOwn = null;
					}
				}
			}
			offset += end;
			return lines;
		}
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
	public synchronized Period lastEnded(long now) {
		for (int i = periods.size() - 1; i >= 0; i--) {
			Period p = periods.get(i);
			if (p.closed() && p.end <= now + 5000) return copyOf(p);
		}
		return null;
	}

	public synchronized Period current() {
		return periods.isEmpty() ? null : copyOf(periods.get(periods.size() - 1));
	}
	private static Period copyOf(Period p) {
		Period copy = new Period();
		copy.start = p.start;
		copy.end = p.end;
		copy.survivors = p.survivors;
		copy.crew = p.crew;
		copy.lair = p.lair;
		return copy;
	}
}
