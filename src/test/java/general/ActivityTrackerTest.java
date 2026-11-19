package main.java.general;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivityTrackerTest {
	@Test
	void appliesForecastWeightsToNavRatesUsingTimeOnNav() {
		StationClock clock = new StationClock();
		clock.message("6 o' 6 still standin'! To yer stations!", 100_000);
		clock.message("The wolf-kin gather! 30 seconds!", 190_000);
		clock.message("6 o' 6 still standin'! To yer stations!", 300_000);
		clock.message("The wolf-kin gather! 30 seconds!", 570_000);
		ActivityTracker tracker = new ActivityTracker(null, clock);
		tracker.setSelf("Jice");
		tracker.ownPuzzle("NaviObject", 302_621);
		tracker.ownPuzzle("ForageObject", 482_993);

		DRGroupData report = new DRGroupData();
		report.setActivity(RecordChecker.DEN);
		report.put("Navigating", "Jice", "Frenetic", new int[]{0, 0, 5}, 15);
		report.put("Foraging", "Jice", "Frenetic", new int[]{9, 7, 3}, 32);
		tracker.report(report, 600_000);

		ActivityTracker.Period period = tracker.lastPeriod();
		var rates = ActivityTracker.rates(period);
		assertEquals(6.52353, rates.get("Navigating").get("Jice"), 0.00001);
		assertEquals(16.40927, rates.get("Foraging").get("Jice"), 0.00001);
		assertEquals(15, period.rows.get(0).score);
		assertTrue(ActivityTracker.blocks(period, " ").get(0).contains(String.format("Jice %.2f", 6.52)));
		assertTrue(ActivityTracker.blocks(period, " ").get(0).startsWith("---Navigating---
"));
		assertTrue(ActivityTracker.blocks(period, " ", false).get(0).startsWith("Jice "));
		String json = RunLog.toJson(period, "Jice", "Emerald", Path.of("run.jsonl"), "woodhallow");
		assertTrue(json.contains("\"score_per_minute\":6.52"));
	}
}
