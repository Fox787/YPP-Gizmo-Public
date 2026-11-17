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

	@Test
	void forageEndComesFromTheGameLog() {
		RumbleClock clock = new RumbleClock();
		String start = "2026/11/16 01:06:36:591 INFO ih.doLog: Stopping foraging in 119 seconds";
		long at = RumbleClock.lineTime(start, 0);
		clock.feed(start, at);
		assertEquals(0, clock.lastForageEnd(at + 60_000), "still foraging");
		String stop = "2026/11/16 01:08:36:200 INFO ih.doLog: Asking server to stop foraging";
		clock.feed(stop, at + 119_609);
		assertEquals(RumbleClock.lineTime(stop, 0), clock.lastForageEnd(at + 120_000));

		clock.feed(start.replace("01:06:36", "01:20:00"), at);
		long stated = RumbleClock.lineTime(start.replace("01:06:36", "01:20:00"), 0) + 120_000;
		assertEquals(stated, clock.lastForageEnd(stated + 1), "the stated end, without the server line");
	}

	@Test
	void sceneIsOnlyPolledWhenSceneWideTimingIsOn() {
		ActivityTracker tracker = new ActivityTracker(null, new StationClock());
		tracker.setSceneTrackingEnabled(false, 0);
		tracker.start();
		assertFalse(tracker.polling());
		tracker.setSceneTrackingEnabled(true, 0);
		assertTrue(tracker.polling());
		tracker.setSceneTrackingEnabled(false, 0);
		assertFalse(tracker.polling());
		tracker.stop();
	}
}
