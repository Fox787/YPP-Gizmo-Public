package main.java.general;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The wolf count from chat on the live oceans: "half", then "Only 2 left", "Only 1 left", then the last wolf. */
class ProductionWolfLinesTest {
	@Test
	void chatFallbackFollowsHalfThenTheCountdownLines() {
		var clock = new StationClock();
		clock.message(StationClock.ARRIVAL, 500);
		clock.message("6 o' 6 still standin'! To yer stations!", 1_000);
		var period = clock.current();
		var wolves = new WolfCount();

		clock.message("Yer charting has turned back half the werewolves! 4 still to come in the next wave", 40_000);
		assertEquals(4, read(wolves, clock, period, 4, 41_000).remaining());
		clock.message("Yer charting has turned back more o' the werewolves! Only 2 left in the next wave", 60_000);
		assertEquals(2, read(wolves, clock, period, 4, 61_000).remaining());   // Ahoy still shows 4: chat is newer
		clock.message("Yer charting has turned back more o' the werewolves! Only 1 left in the next wave", 70_000);
		assertEquals(1, read(wolves, clock, period, -1, 71_000).remaining());
		clock.message("Yer charting has turned back the last werewolf! The next wave be all wolf-kin!", 80_000);
		assertEquals(0, read(wolves, clock, period, -1, 81_000).remaining());
		assertEquals(80_000, clock.lastWolfClearedAt());
	}

	private static WolfCount.Reading read(WolfCount wolves, StationClock clock, StationClock.Period period,
										  int ahoy, long now) {
		return wolves.read(() -> ahoy, clock.wolfProgress(period), 1_000, now, List.of());
	}
}
