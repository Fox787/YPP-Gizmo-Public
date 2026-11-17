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
 * The log is read incrementally; only lines written after Gizmo started count.
 */
public final class RumbleClock {

	private Path file;
	private long offset = -1;
	private long startedAt;   // 0 when no rumble is on
	private long forageEndsAt;   // 0 when no forage period is on
	private final MercPlank plank = new MercPlank();

	/** Cursed Isles merc count and plank check, from the same game log. */
	public MercPlank plank() { return plank; }

	/** Milliseconds of foraging left, or -1 if no forage period is running. */
	public long forageLeft(long now) {
		if (forageEndsAt == 0 || now >= forageEndsAt) return -1;
		return forageEndsAt - now;
	}

	/** When the current forage period ends (ms), 0 if none: one key per period for its warning sound. */
	public long forageEndsAt() { return forageEndsAt; }

	/** Milliseconds since the rumble started, or -1 if none is running. */
	public long elapsed(long now) {
		return startedAt == 0 ? -1 : now - startedAt;
	}

	/** Which log is this client's is looked up again at most this often (it lists the log folder). */
	static final long FIND_LOG_EVERY_MS = 10_000;
	private Path ownLog;
	private long ownLogFoundAt;

	public void poll(long now) {
		try {
			// This client's own log: with two clients open, the newest log can be the other client's.
			if (ownLog == null || now - ownLogFoundAt >= FIND_LOG_EVERY_MS) {
				ownLog = GameLog.ownLog(GameLog.clientFolder());
				ownLogFoundAt = now;
			}
			Path newest = ownLog;
			if (newest == null) return;
			if (!newest.equals(file)) {           // new session log: start at its end
				file = newest;
				offset = Files.size(newest);
				startedAt = 0;
				forageEndsAt = 0;
				return;
			}
			long size = Files.size(file);
			if (size < offset) offset = 0;
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

	private static final java.util.regex.Pattern FORAGE = java.util.regex.Pattern.compile("Stopping foraging in (\\d+) seconds");

	void feed(String line, long now) {
		plank.feed(line);
		if (line.contains("Reporting ready data.BoxingObject")) { startedAt = now; forageEndsAt = 0; return; }
		if (startedAt != 0 && line.contains("replaced=class com.threerings.piracy.puzzle.boxing.client.")) { startedAt = 0; return; }
		if (line.contains("Stopping foraging in ")) {
			java.util.regex.Matcher m = FORAGE.matcher(line);
			if (m.find()) { forageEndsAt = now + (Long.parseLong(m.group(1)) + 1) * 1000; startedAt = 0; }
		} else if (forageEndsAt != 0 && line.contains("Asking server to stop foraging")) {
			forageEndsAt = 0;
		}
	}
}
