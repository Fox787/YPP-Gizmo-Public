package main.java.general;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * When auto scan should look for a duty report, from log end times only (no scene reading):
 *   Den   a station period's end, from the chat log (StationClock periods)
 *   Lair  the final duty report the client shows (game log) at a round's end from the chat log's
 *         15-second warning; the chat round end itself if the game log has no such report
 *   CI    the forage period's duty report the client shows, from its game log (RumbleClock)
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

	/** How close the client's final report must be to the chat's lair round end to be that round's. */
	static final long LAIR_MATCH_MS = 5_000;
	/** With no matching report this long after the chat's round end, the round end arms auto scan itself. */
	static final long LAIR_FALLBACK_MS = 3_000;

	/** The lair round end that arms auto scan: the matching final report, else the chat round end once it's late. */
	static long lairEnd(long now, long chatRoundEnd, long finalReport) {
		if (chatRoundEnd <= 0) return 0;
		if (finalReport > 0 && Math.abs(finalReport - chatRoundEnd) <= LAIR_MATCH_MS) return finalReport;
		return now - chatRoundEnd >= LAIR_FALLBACK_MS ? chatRoundEnd : 0;
	}

	/** A report was scanned for this end: don't arm for it again. */
	void scanned(long end) {
		if (end <= 0 || scanned.contains(end)) return;
		scanned.addLast(end);
		while (scanned.size() > 8) scanned.removeFirst();
	}
}
