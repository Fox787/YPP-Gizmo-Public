package main.java.general;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * When auto scan should look for a duty report, from log end times only (no scene reading):
 *   Den   a station period's end, from the chat log (StationClock periods)
 *   Lair  a round's end, from the chat log's 15-second warning (StationClock lair round)
 *   CI    the forage period's end, from this client's game log (RumbleClock)
 * Each end arms auto scan for WINDOW_MS after it (D3), and once a report has been scanned for an end,
 * that end doesn't arm it again.
 */
final class AutoScanTrigger {

	/** How long after a station end auto scan keeps looking for the duty report. */
	static final long WINDOW_MS = 5_000;

	private final Deque<Long> scanned = new ArrayDeque<>();

	/** The end (ms) that arms auto scan now, or 0 if none does. Ends of 0 are unknown. */
	long armed(long now, long... ends) {
		for (long end : ends)
			if (end > 0 && now >= end && now - end <= WINDOW_MS && !scanned.contains(end)) return end;
		return 0;
	}

	/** A report was scanned for this end: don't arm for it again. */
	void scanned(long end) {
		if (end <= 0 || scanned.contains(end)) return;
		scanned.addLast(end);
		while (scanned.size() > 8) scanned.removeFirst();
	}
}
