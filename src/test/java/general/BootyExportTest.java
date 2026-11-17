package main.java.general;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.prefs.Preferences;

import static org.junit.jupiter.api.Assertions.*;

class BootyExportTest {
	private static final LocalDateTime ARRIVAL = LocalDateTime.of(2026, 11, 16, 12, 0);

	@TempDir Path folder;

	@Test
	void exportsFullHoldLossesSeparatelyFromCollectedChests() throws Exception {
		Path log = Files.writeString(folder.resolve("chat.txt"), """
				====== 2026/11/16 ======
				[12:00:00] Hark the dread howlin'! 'Tis Woodhallow Isle, realm o' the wolf-folk!
				[12:10:00] Ye have taken aboard 2 dire chests!
				[12:11:00] Ye abandoned 1,000 lunar lockers as the booty is full!
				[12:12:00] Hark the dread howlin'! 'Tis Woodhallow Isle, realm o' the wolf-folk!
				[12:18:00] Ye have taken aboard 3 dire chests!
				[12:20:00] The crew opens 5 chests and finds 2 items and 300 pieces of eight.
				""");
		BootyDivision division = division(1);
		division.chat = ChatLog.latestBooty(log);
		assertEquals(Map.of("dire chests", 5), division.chat.takenAboard);
		assertEquals(2, division.chat.runs.size());
		assertArrayEquals(new int[]{2, 0, 0}, division.chat.runs.get(0).tiers);
		assertArrayEquals(new int[]{3, 0, 0}, division.chat.runs.get(1).tiers);
		assertTrue(division.toText().contains("Ye abandoned 1,000 lunar lockers"));
		assertTrue(division.toJson().contains("\"abandoned_hold_full\":{\"lunar lockers\":1000}"));
	}

	@Test
	void replacesRicherRescansAndPreservesTheTextNumberAndRunLinks() throws Exception {
		Path originalData = GameLog.dataFolder();
		GameLog.setDataFolder(folder.resolve("data"));
		try {
			Path text = folder.resolve("text");
			Path originalRun = folder.resolve("old folder/emerald/runs/run_original_Pirate.jsonl");
			BootyDivision first = division(1);
			first.runFiles.add(originalRun);
			BootyExporter.Result initial = BootyExporter.export(first, text);
			BootyDivision richer = division(3);
			richer.runFiles.add(folder.resolve("run_from_next_voyage_Pirate.jsonl"));
			BootyExporter.Result updated = BootyExporter.export(richer, text);
			assertTrue(updated.replaced);
			assertEquals(initial.text, updated.text);
			assertEquals(List.of(originalRun), richer.runFiles);
			assertEquals(1, Files.readAllLines(updated.jsonl).size());
			assertTrue(Files.readString(updated.text).contains("3x Item"));
			assertTrue(Files.readString(updated.jsonl).contains("run_original_Pirate.jsonl"));
			assertFalse(Files.readString(updated.jsonl).contains("run_from_next_voyage_Pirate.jsonl"));
			assertTrue(BootyExporter.export(division(2), text).duplicate);
			assertTrue(Files.readString(updated.jsonl).contains("\"Item\":3"));

			Files.delete(updated.text);
			assertTrue(BootyExporter.export(division(3), text).duplicate);
			assertTrue(Files.readString(updated.text).contains("3x Item"));
		} finally {
			GameLog.setDataFolder(originalData);
		}
	}

	@Test
	void keepsOneRunPerArrivalAndAdoptsAChangedDataFolder() throws Exception {
		var preferences = Preferences.userRoot().node("gizmo-booty-test-" + UUID.randomUUID());
		Path originalData = GameLog.dataFolder();
		GameLog.setDataFolder(folder);
		try {
			var clock = new StationClock();
			long firstArrival = stamp(ARRIVAL);
			clock.message(StationClock.ARRIVAL, firstArrival);
			var log = new RunLog(preferences);
			DRGroupData report = forage(1);
			Path first = log.recordUntimed(report, "woodhallow", clock.lastArrival("woodhallow"));
			assertNull(log.recordUntimed(report, "woodhallow", firstArrival));

			var resumed = new RunLog(preferences);
			clock.message(StationClock.ARRIVAL, firstArrival);
			assertEquals(first, resumed.recordUntimed(forage(2), "woodhallow", clock.lastArrival("woodhallow")));
			assertEquals(2, Files.readAllLines(first).size());
			clock.message(StationClock.ARRIVAL, firstArrival + 60_000);
			Path second = resumed.recordUntimed(forage(2), "woodhallow", clock.lastArrival("woodhallow"));
			assertNotEquals(first, second);

			Path nextFolder = folder.resolve("new");
			GameLog.setDataFolder(nextFolder);
			Path moved = resumed.recordUntimed(forage(3), "woodhallow", clock.lastArrival("woodhallow"));
			assertTrue(moved.startsWith(nextFolder));
			assertEquals(1, Files.readAllLines(second).size());
		} finally {
			preferences.removeNode();
			GameLog.setDataFolder(originalData);
		}
	}

	@Test
	void includesPirateCountsOnlyWhenVoyageAndCargoMatch() throws Exception {
		BootyDivision division = division(1);
		ChatLog.Run run = new ChatLog.Run();
		run.arrivedAt = ARRIVAL;
		run.voyage = "woodhallow";
		run.tiers[0] = 3;
		run.tiers[1] = 2;
		run.tiers[2] = 1;
		division.chat.runs.add(run);

		Path file = folder.resolve("run.jsonl");
		var period = new ActivityTracker.Period();
		period.start = stamp(ARRIVAL.plusSeconds(1));
		period.end = stamp(ARRIVAL.plusMinutes(1));
		period.timedToTimer = true;
		var row = new ActivityTracker.Row();
		row.station = "Foraging";
		row.pirate = "Forager";
		row.tokens = new int[]{1, 2, 3};
		period.rows.add(row);
		Files.writeString(file, RunLog.toJson(period, "Pirate", "Emerald", file, "woodhallow") + "\n"
				+ RunLog.toJson(period, "Pirate", "Emerald", file, "cursedIsles") + "\n");
		division.runFiles.add(file);
		assertTrue(division.toJson().contains("\"pirate_chest_counts\":{\"Forager\":[3,2,1]}"));
		run.tiers[2]++;
		assertFalse(division.toJson().contains("\"pirate_chest_counts\""));
	}

	private static BootyDivision division(int count) {
		var division = new BootyDivision();
		division.game = new GameLog.Info();
		division.game.pirate = "Pirate";
		division.game.ocean = "Emerald";
		division.chat = new ChatLog.Segment();
		division.chat.openedAt = ARRIVAL.plusMinutes(20);
		var loot = new BootyDivision.PirateLoot("Pirate");
		loot.items.put("Item", count);
		division.pirates.add(loot);
		return division;
	}

	private static DRGroupData forage(int chests) {
		var report = new DRGroupData();
		report.put("Foraging", "Forager", "Swift", new int[]{0, 0, chests}, chests);
		return report;
	}

	private static long stamp(LocalDateTime time) {
		return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
	}
	@Test
	void onlyOffersARunForALargeShortfall() {
		assertNull(BootyExporter.chestGap(null));
		assertNull(BootyExporter.chestGap(gapDivision(100, 51).chat));
		assertNull(BootyExporter.chestGap(gapDivision(100, 120).chat));
		assertEquals(50, BootyExporter.chestGap(gapDivision(1500, 1450).chat).missing());
	}

	@Test
	void linksTheConfirmedRunAndKeepsItOnRescan(@TempDir Path folder) throws IOException {
		Path previousFolder = GameLog.dataFolder();
		GameLog.setDataFolder(folder);
		try {
			Path textFolder = folder.resolve("text");
			Path observed = folder.resolve("emerald/runs/run_observed.jsonl");
			Files.createDirectories(observed.getParent());
			Files.createFile(observed);
			BootyDivision original = gapDivision(1480, 1284);
			original.runFiles.add(observed);

			BootyExporter.Result saved = BootyExporter.export(original, textFolder, gap -> {
				assertEquals(196, gap.missing());
				return true;
			});
			Path inferred = original.runFiles.get(1);
			String run = Files.readString(inferred);
			assertTrue(run.contains("\"chest_count\":196"));
			assertTrue(run.contains("\"rows\":[]"));
			assertTrue(run.contains("\"started_at\":null"));
			assertTrue(run.contains("\"source\":\"division_reconciliation\""));
			String references = "\"run_files\":[\"runs/" + observed.getFileName() + "\",\"runs/" + inferred.getFileName() + "\"]";
			assertTrue(Files.readString(saved.jsonl).contains(references));
			assertTrue(Files.readString(saved.text).contains(inferred.toString()));

			BootyDivision duplicate = gapDivision(1480, 1284);
			assertTrue(BootyExporter.export(duplicate, textFolder, gap -> fail("Prompted again")).duplicate);
			assertEquals(List.of(observed, inferred), duplicate.runFiles);

			BootyDivision moreTooltips = gapDivision(1480, 1284);
			moreTooltips.pirates.get(0).items.put("Barrel", 1);
			moreTooltips.runFiles.add(folder.resolve("emerald/runs/run_next_voyage.jsonl"));
			assertTrue(BootyExporter.export(moreTooltips, textFolder, gap -> fail("Prompted again")).replaced);
			String updated = Files.readString(saved.jsonl);
			assertTrue(updated.contains(references));
			assertTrue(updated.contains("\"taken_aboard\":{\"wolf boxes\":1284}"));
			assertEquals(1, updated.lines().count());
			assertEquals(run, Files.readString(inferred));
		} finally {
			GameLog.setDataFolder(previousFolder);
		}
	}

	@Test
	void skippingStillExportsTheDivision(@TempDir Path folder) throws IOException {
		Path previousFolder = GameLog.dataFolder();
		GameLog.setDataFolder(folder);
		try {
			BootyDivision division = gapDivision(1480, 1284);
			BootyExporter.Result saved = BootyExporter.export(division, null, gap -> false);
			assertTrue(Files.isRegularFile(saved.jsonl));
			assertTrue(division.runFiles.isEmpty());
			assertFalse(Files.exists(folder.resolve("emerald/runs")));
		} finally {
			GameLog.setDataFolder(previousFolder);
		}
	}

	private static BootyDivision gapDivision(int opened, int logged) {
		BootyDivision division = new BootyDivision();
		division.game = new GameLog.Info();
		division.game.pirate = "Jice";
		division.game.ocean = "Emerald";
		division.pirates.add(new BootyDivision.PirateLoot("Jice"));
		division.chat = new ChatLog.Segment();
		division.chat.openedAt = LocalDateTime.of(2026, 11, 17, 1, 21, 25);
		division.chat.chestsOpened = opened;
		division.chat.takenAboard.put("wolf boxes", logged);
		return division;
	}
}
