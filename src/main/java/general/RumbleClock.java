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
 * and when the client shows a duty report at the end of a period (for auto scan):
 *   "Displaying game over duty report." is logged each time a puzzle game ends; mid-period a new game
 *   starts at once ("Game did start", within milliseconds). The Cursed Isles period's report is the one
 *   right after "Asking server to stop foraging"; any other with no new game after it is a final report
 *   (a station or lair round closing, or leaving the puzzle).
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

	private final GameScene scene = new GameScene();

	/** The scene the client is in (Den / CI / Lair), from the same game log. */
	GameScene scene() { return scene; }

	/** Milliseconds of foraging left, or -1 if no forage period is running. */
	public long forageLeft(long now) {
		long end = forageEndsAt;
		return end == 0 || now >= end ? -1 : end - now;
	}

	/** When the current forage period ends (ms), 0 if none: one key per period for its warning sound. */
	public long forageEndsAt() { return forageEndsAt; }

	/** How soon after "Asking server to stop foraging" the period's duty report shows (0.6 s at most in practice). */
	static final long CI_REPORT_AFTER_STOP_MS = 3_000;
	/** A report with no new game started this long after it is a final one. */
	static final long NO_NEW_GAME_MS = 1_000;
	private volatile long ciReportAt, finalReportAt;
	private long stopAskedAt, pendingReportAt;

	/** When the client last showed a Cursed Isles forage period's duty report (game log time), 0 if none. */
	public long lastCiReport() { return ciReportAt; }

	/** When the client last showed a duty report with no new game after it (game log time), 0 if none. */
	public long lastFinalReport() { return finalReportAt; }

	/** A shown report that no new game followed within NO_NEW_GAME_MS is final. */
	void settleReport(long now) {
		if (pendingReportAt != 0 && now - pendingReportAt >= NO_NEW_GAME_MS) {
			finalReportAt = pendingReportAt;
			pendingReportAt = 0;
		}
	}

	/** Milliseconds since the rumble started, or -1 if none is running. */
	public long elapsed(long now) {
		long start = startedAt;
		return start == 0 ? -1 : Math.max(0, now - start);
	}

	public void poll(long now) {
		settleReport(now);
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
				seedScene(newest, offset);
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

	/** Largest log read back for the scene; session logs run well under 1 MB. */
	private static final long SEED_MAX_BYTES = 8L * 1024 * 1024;

	/**
	 * Gizmo started (or the client began a new log) mid-session: take the scene the client is already in
	 * from the log so far, so activity tagging and the parlor check work before the next scene change.
	 * Only the scene lines are used; timers start fresh from the log's end.
	 */
	private void seedScene(Path log, long size) throws IOException {
		long from = Math.max(0, size - SEED_MAX_BYTES);
		byte[] buf;
		try (RandomAccessFile raf = new RandomAccessFile(log.toFile(), "r")) {
			raf.seek(from);
			buf = new byte[(int) (size - from)];
			raf.readFully(buf);
		}
		seedScene(new String(buf, StandardCharsets.UTF_8), System.currentTimeMillis());
	}

	void seedScene(String logText, long now) {
		for (String line : logText.split("\r?\n"))
			if (line.contains("chat display for ")) scene.feed(line, lineTime(line, now));
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
		if (line.contains("chat display for ")) {
			scene.feed(line, lineTime(line, now));
			return;
		}
		if (line.contains("Stopping foraging in ")) {
			var m = FORAGE.matcher(line);
			if (m.find()) { forageEndsAt = lineTime(line, now) + (Long.parseLong(m.group(1)) + 1) * 1000; startedAt = 0; }
			return;
		}
		if (line.contains("Displaying game over duty report.")) {
			long at = lineTime(line, now);
			if (stopAskedAt != 0 && at - stopAskedAt <= CI_REPORT_AFTER_STOP_MS) {
				ciReportAt = finalReportAt = at;
				pendingReportAt = 0;
			} else {
				pendingReportAt = at;
			}
			stopAskedAt = 0;
			return;
		}
		if (line.contains("Game did start")) {
			pendingReportAt = 0;
			return;
		}
		if (forageEndsAt != 0 && line.contains("Asking server to stop foraging")) {
			stopAskedAt = lineTime(line, now);
			forageEndsAt = 0;
			return;
		}
		var ready = READY.matcher(line);
		if (ready.find()) {
			String object = ready.group(1);
			if ("BoxingObject".equals(object)) {
				// Parlor rumbles are PvP: no fray timer or punch-size board for them.
				if (startedAt == 0 && !scene.inBuilding()) startedAt = lineTime(line, now);
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
