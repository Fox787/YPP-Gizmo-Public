package main.java.general;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Phase 5: auto scan and timing from logs only; the scene is read only when opted in. */
class LessInvasiveTest {

	@Test
	void autoScanArmsForAFewSecondsAfterEachLogEndAndOnlyOnce() {
		AutoScanTrigger trigger = new AutoScanTrigger();
		long den = 1_000_000, lair = 2_000_000, ci = 3_000_000;
		assertEquals(0, trigger.armed(den - 1, den, 0, 0), "not before the end");
		assertEquals(den, trigger.armed(den + 4_000, den, 0, 0));
		assertEquals(0, trigger.armed(den + AutoScanTrigger.WINDOW_MS + 1, den, 0, 0), "window closed");
		trigger.scanned(den);
		assertEquals(0, trigger.armed(den + 1_000, den, 0, 0), "already scanned for this end");
		assertEquals(lair, trigger.armed(lair + 1_000, den, lair, 0));
		assertEquals(ci, trigger.armed(ci + 2_000, den, lair, ci));
		assertEquals(0, trigger.armed(5_000_000, 0, 0, 0), "nothing known");
	}

	@Test
	void lairRoundEndComesFromTheChatWarning() {
		StationClock clock = new StationClock();
		long at = 10_000_000;
		clock.message("Welcome to the vampire sanctum.", at);
		assertEquals(0, clock.lastLairRoundEnd());
		clock.message("Ye hear the sound of rustling in coffins.", at + 100_000);
		assertEquals(at + 115_000, clock.lastLairRoundEnd());
		clock.message(StationClock.ARRIVAL, at + 200_000);   // left for Woodhallow
		assertEquals(0, clock.lastLairRoundEnd());
	}

	private static long feed(RumbleClock clock, String line) {
		long at = RumbleClock.lineTime(line, 0);
		clock.feed(line, at);
		return at;
	}

	@Test
	void ciReportIsTheOneRightAfterForagingStops() {
		// Cursed Isles, 2026/11/18 (game log): two forage games end mid-period, then the period's report.
		RumbleClock clock = new RumbleClock();
		feed(clock, "2026/11/18 05:41:56:299 INFO ih.doLog: Stopping foraging in 119 seconds");
		feed(clock, "2026/11/18 05:42:59:331 INFO ih.doLog: Displaying game over duty report.");
		long mid = feed(clock, "2026/11/18 05:42:59:333 INFO ih.doLog: Game did start [diff=8]");
		clock.settleReport(mid + 5_000);
		assertEquals(0, clock.lastCiReport(), "a mid-period report");
		assertEquals(0, clock.lastFinalReport(), "a new game started right after it");
		feed(clock, "2026/11/18 05:43:55:921 INFO ih.doLog: Asking server to stop foraging");
		long report = feed(clock, "2026/11/18 05:43:56:421 INFO ih.doLog: Displaying game over duty report.");
		assertEquals(report, clock.lastCiReport());
		assertEquals(report, clock.lastFinalReport());
	}

	@Test
	void stationEndReportIsFinalWhenNoNewGameFollows() {
		// Woodhallow stations closing, 2026/11/18 06:26:50 (game log).
		RumbleClock clock = new RumbleClock();
		long report = feed(clock, "2026/11/18 06:26:50:987 INFO ih.doLog: Displaying game over duty report.");
		clock.settleReport(report + 500);
		assertEquals(0, clock.lastFinalReport(), "a new game could still start");
		clock.settleReport(report + RumbleClock.NO_NEW_GAME_MS);
		assertEquals(report, clock.lastFinalReport());
		assertEquals(0, clock.lastCiReport(), "not a forage period");
	}

	@Test
	void lairArmsOnTheReportAtTheRoundEndElseTheChatTime() {
		long round = 1_000_000;
		assertEquals(round + 400, AutoScanTrigger.lairEnd(round + 500, round, round + 400));
		assertEquals(0, AutoScanTrigger.lairEnd(round + 1_000, round, round - 60_000), "an earlier report, chat end not late yet");
		assertEquals(round, AutoScanTrigger.lairEnd(round + AutoScanTrigger.LAIR_FALLBACK_MS, round, 0), "no report logged");
		assertEquals(0, AutoScanTrigger.lairEnd(round + 10_000, 0, round), "not in a lair round");
	}
}
