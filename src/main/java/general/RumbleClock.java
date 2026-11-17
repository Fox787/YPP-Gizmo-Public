package main.java.general;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * When a rumble (boxing puzzle) is running, from the client's own game log, as Thalassa's log_parser does:
 *   start  "Reporting ready data.BoxingObject:"
 *   end    a view change replacing a com.threerings.piracy.puzzle.boxing.client.* panel
 * and, like Thalassa's Cursed Isles forage timer, when the forage period ends:
 *   start  "Stopping foraging in 119 seconds" (Cursed Isles only)
 *   end    "Asking server to stop foraging" (119.6 s later in practice), or the stated time + 1 s
 * It also feeds MercPlank (Cursed Isles crew and frays won). The log is read incrementally; only lines
 * written after Gizmo started count.
 */
public final class RumbleClock {

	private Path file;
	private long offset = -1;
	private volatile long startedAt;   // 0 when no rumble is on (written on the file thread, read on the UI thread)
	private volatile long forageEndsAt;   // 0 when no forage period is on
	private final MercPlank plank = new MercPlank();

	/** Cursed Isles merc count and plank check, from the same game log. */
	public MercPlank plank() { return plank; }

	/** Milliseconds of foraging left, or -1 if no forage period is running. */
	public long forageLeft(long now) {
		long end = forageEndsAt;
		return end == 0 || now >= end ? -1 : end - now;
	}

	/** When the current forage period ends (ms), 0 if none: one key per period for its warning sound. */
	public long forageEndsAt() { return forageEndsAt; }

	private volatile long forageEndedAt;   // when the last forage period ended ("Asking server to stop foraging")

	/** When the latest forage period ended (or its stated end has passed), 0 if none. For auto scan. */
	public long lastForageEnd(long now) {
		long end = forageEndsAt;
		return end != 0 && now >= end ? end : forageEndedAt;
	}

	/** Milliseconds since the rumble started, or -1 if none is running. */
	public long elapsed(long now) {
		long start = startedAt;
		return start == 0 ? -1 : Math.max(0, now - start);
	}

	public void poll(long now) {
		try {
			Path newest = GameLog.activeLog();
			if (newest == null) {
				file = null;
				startedAt = 0;
				forageEndsAt = 0;
				return;
			}
			if (!newest.equals(file)) {           // new session log: start at its end
				file = newest;
				offset = Files.size(newest);
				startedAt = 0;
				forageEndsAt = 0;
				return;
			}
			long size = Files.size(file);
			if (size < offset) {
				offset = 0;
				startedAt = 0;
			}
			if (size == offset) return;
			byte[] buf;
			try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
				raf.seek(offset);
				buf = new byte[(int) Math.min(size - offset, 8L * 1024 * 1024)];
				raf.readFully(buf);
			}
			int end = buf.length;
			while (end > 0 && buf[end - 1] != '\n') end--;
			if (end == 0) return;
			offset += end;
			for (String line : new String(buf, 0, end, StandardCharsets.UTF_8).split("\r?\n")) feed(line, now);
		} catch (IOException | RuntimeException ignored) {
			// Try again next poll.
		}
	}

	/** Called (on the file thread) when this client enters a duty puzzle: its data object's name and the log time. */
	public interface PuzzleListener { void entered(String puzzleObject, long at); }
	private volatile PuzzleListener puzzleListener;
	public void setPuzzleListener(PuzzleListener l) { puzzleListener = l; }

	// "Reporting ready data.ForageObject:..." / "data.BoxingObject:..." but "Reporting ready navi.NaviObject:..." for Dnav.
	private static final java.util.regex.Pattern READY = java.util.regex.Pattern.compile("Reporting ready (?:\\w+\\.)*(\\w+Object)");
	private static final java.util.regex.Pattern STAMP = java.util.regex.Pattern.compile(
			"^(\\d{4})/(\\d{2})/(\\d{2}) (\\d{2}):(\\d{2}):(\\d{2}):(\\d{3}) ");

	/** The game log's own time for a line ("2026/11/14 12:04:18:398 INFO ..."), or {@code fallback}. */
	static long lineTime(String line, long fallback) {
		java.util.regex.Matcher m = STAMP.matcher(line);
		if (!m.find()) return fallback;
		try {
			return java.time.LocalDateTime.of(Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
					Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)), Integer.parseInt(m.group(7)) * 1_000_000)
					.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
		} catch (RuntimeException e) {
			return fallback;
		}
	}

	private static final java.util.regex.Pattern FORAGE = java.util.regex.Pattern.compile("Stopping foraging in (\\d+) seconds");

	void feed(String line, long now) {
		plank.feed(line);
		if (line.contains("Stopping foraging in ")) {
			var m = FORAGE.matcher(line);
			if (m.find()) { forageEndsAt = lineTime(line, now) + (Long.parseLong(m.group(1)) + 1) * 1000; startedAt = 0; }
			return;
		}
		if (forageEndsAt != 0 && line.contains("Asking server to stop foraging")) {
			forageEndedAt = lineTime(line, now);
			forageEndsAt = 0;
			return;
		}
		var ready = READY.matcher(line);
		if (ready.find()) {
			String object = ready.group(1);
			if ("BoxingObject".equals(object)) {
				if (startedAt == 0) startedAt = lineTime(line, now);
				forageEndsAt = 0;
			} else {
				startedAt = 0;
				PuzzleListener listener = puzzleListener;
				if (listener != null) listener.entered(object, lineTime(line, now));
			}
		} else if (line.contains("replaced=class com.threerings.piracy.puzzle.boxing.client.")) {
			startedAt = 0;
		}
	}
}
