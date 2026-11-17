package main.java.general;

import java.util.List;
import java.util.function.IntSupplier;

/** Tracks the visible Ahoy readings for one Woodhallow station period. */
final class WolfCount {
	// Allow for the one-second chat and UI polls when stations open.
	private static final long OPENING_WINDOW_MS = 5_000;
	private int openingCount = -1;
	private int highestSeen = -1;
	private int remaining = -1;
	private long changedAt;

	record Reading(int remaining, int starting, long changedAt) { }

	Reading read(boolean chatOnly, IntSupplier readAhoy, StationClock.WolfProgress chat,
				 long startedAt, long now, List<Integer> candidates) {
		if (chatOnly) return new Reading(chat.remaining(), -1, chat.changedAt());
		observe(readAhoy.getAsInt(), chat, startedAt, now);
		return new Reading(remaining, startingCount(candidates), changedAt);
	}

	private void observe(int shown, StationClock.WolfProgress chat, long startedAt, long now) {
		if (shown < 0) return;
		if (shown > 0 && chat.remaining() < 0 && now >= startedAt && now - startedAt <= OPENING_WINDOW_MS) {
			openingCount = Math.max(openingCount, shown);
		}
		highestSeen = Math.max(highestSeen, shown);
		if (shown != remaining) {
			remaining = shown;
			changedAt = shown == chat.remaining() ? chat.changedAt() : now;
		}
	}

	private int startingCount(List<Integer> candidates) {
		if (openingCount >= 0) return openingCount;
		// A late reading can confirm a starting count only if no higher candidate could explain it.
		if (!candidates.contains(highestSeen)) return -1;
		for (int count : candidates) if (count > highestSeen) return -1;
		return highestSeen;
	}

	void clearRemaining() {
		remaining = -1;
		changedAt = 0;
	}
}
