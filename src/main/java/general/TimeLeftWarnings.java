package main.java.general;

import java.util.HashMap;
import java.util.Map;

/**
 * The Options sound matrix: a 30 s and a 15 s time-left warning for each activity, each ticked on its
 * own, so ticking both warns twice. Each cell plays once per end time.
 *
 *   Woodhallow     end = the chat's "30 seconds!" line + 30 s (StationClock.thirtySecondsAt)
 *   Cursed Isles   end = the forage end from the game log (RumbleClock.forageEndsAt)
 *   Vampire Lair   30 s: the round timer (fray end + 2:03, StationClock.lairTimerEnd)
 *                  15 s: the chat's coffin warning + 15 s (StationClock.lastLairRoundEnd)
 *
 * A warning is due from its lead time until WINDOW_MS later, so a late tick still plays it, but an end
 * that moves (the lair timer corrected at 15 s) or an old one read back at startup doesn't.
 */
final class TimeLeftWarnings {

	enum Activity {
		WOODHALLOW("Woodhallow", "warningsound", true, "den15sound", false),
		CURSED_ISLES("Cursed Isles", "foragesound", true, "ci15sound", false),
		VAMPIRE_LAIR("Vampire Lair", "lair30sound", false, "lairsound", true);

		final String label, key30, key15;
		final boolean default30, default15;

		Activity(String label, String key30, boolean default30, String key15, boolean default15) {
			this.label = label;
			this.key30 = key30;
			this.default30 = default30;
			this.key15 = key15;
			this.default15 = default15;
		}
	}

	static final long THIRTY_MS = 30_000, FIFTEEN_MS = 15_000;
	/** How long after its moment a warning may still play (the sound ticks run once a second). */
	static final long WINDOW_MS = 10_000;

	private final Map<String, Long> playedFor = new HashMap<>();

	/** True once per end when {@code now} is within WINDOW_MS after {@code end - lead}; records it. */
	boolean due(Activity activity, long lead, long end, long now) {
		if (end <= 0) return false;
		long left = end - now;
		if (left > lead || left <= lead - WINDOW_MS) return false;
		String key = activity.name() + lead;
		Long played = playedFor.get(key);
		if (played != null && played == end) return false;
		playedFor.put(key, end);
		return true;
	}
}
