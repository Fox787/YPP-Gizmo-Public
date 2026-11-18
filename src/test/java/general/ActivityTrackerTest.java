package main.java.general;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
		assertTrue(ActivityTracker.blocks(period, " ").get(0).startsWith("---Navigating---\n"));
		assertTrue(ActivityTracker.blocks(period, " ", false).get(0).startsWith("Jice "));
		String json = RunLog.toJson(period, "Jice", "Emerald", Path.of("run.jsonl"), "woodhallow");
		assertTrue(json.contains("\"score_per_minute\":6.52"));
	}

	@Test
	void leavesOutOnlyVisitsUnderTwoSeconds() {
		for (long visit : new long[]{1_999, 2_000, 4_999, 5_000}) {
			StationClock clock = new StationClock();
			clock.message("6 o' 6 still standin'! To yer stations!", 100_000);
			clock.message("The wolf-kin gather! 30 seconds!", 190_000);
			ActivityTracker tracker = new ActivityTracker(null, clock);
			tracker.setSelf("Pirate");
			tracker.ownPuzzle("NaviObject", 101_000);
			tracker.ownPuzzle("ForageObject", 220_000 - visit);
			DRGroupData report = new DRGroupData();
			report.setActivity(RecordChecker.DEN);
			report.put("Navigating", "Pirate", "Frenetic", new int[]{1, 0, 0}, 1);
			report.put("Foraging", "Pirate", "Slow", new int[]{1, 0, 0}, 1);
			tracker.report(report, 220_000);
			ActivityTracker.Period period = tracker.lastPeriod();
			assertEquals(visit, period.rows.get(1).ms);
			boolean counts = visit >= 2_000, rated = visit >= 5_000;
			assertEquals(rated, ActivityTracker.rates(period).containsKey("Foraging"));
			String blocks = String.join("\n", ActivityTracker.blocks(period, " "));
			String chat = String.join("\n", ActivityTracker.chatMessages(report, period, false));
			assertEquals(counts, blocks.contains("Foraging"));
			assertEquals(counts, chat.contains("Forage"));
			assertEquals(counts && !rated, chat.contains("Pirate 1 (-)"));   // 2-5 s: scored, no rate
			DRGroupData scored = ActivityTracker.scoredReport(report, period);
			assertEquals(counts, scored.data.containsKey("Foraging"));
			assertTrue(scored.data.get("Navigating").containsKey("Pirate"));
			assertTrue(report.data.get("Foraging").containsKey("Pirate"));   // the scanned report is untouched
			String json = RunLog.toJson(period, "Pirate", "Emerald", Path.of("run.jsonl"), "woodhallow");
			if (!counts) assertTrue(json.contains("\"seconds\":1.999,\"score_per_minute\":null"));
		}
	}

	@Test
	void unknownTimesStillCopy() {
		ActivityTracker.Row row = new ActivityTracker.Row();
		row.station = "Navigating";
		row.pirate = "Pirate";
		ActivityTracker.Period period = new ActivityTracker.Period();
		period.rows.add(row);
		assertFalse(row.tooBrief());
		assertTrue(ActivityTracker.chatMessages(new DRGroupData(), period, false).get(0).contains("Pirate 0 (-)"));
	}
}
