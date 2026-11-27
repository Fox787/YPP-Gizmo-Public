package main.java.general;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import javax.swing.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import static main.java.general.GuiTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class RunRecordAlertsTest {
	@TempDir Path folder;

	private void benchmarks(String voyage, int drBenchmark) throws Exception {
		StringBuilder json = new StringBuilder("[");
		for (int crew : new int[]{0, 2})
			for (String kind : new String[]{"drChests", "drScore", "chests", "score"}) {
				if (json.length() > 1) json.append(',');
				json.append(String.format("{\"voyage\":\"%s\",\"ocean\":\"emerald\",\"crewSize\":%d,\"kind\":\"%s\",\"value\":%d}",
						voyage, crew, kind, kind.startsWith("dr") ? drBenchmark : 1));
			}
		Files.writeString(folder.resolve(VoyageRecords.RECORDS), json.append(']').toString());
	}

	@Test
	void recordOutputStaysBriefAndSuppressesRepeatsWhileKeepingClaimsAndImprovements() throws Exception {
		benchmarks("woodhallow", 1);
		try (GuiTestFixture fixture = new GuiTestFixture(folder)) {
			fixture.prefs.putBoolean("autocopy", false);
			fixture.prefs.putBoolean("autoscan", false);
			SwingUtilities.invokeAndWait(() -> {
				DRGizmoGUI gui = fixture.open();
				field(gui, "comboBoxScanOcean", JComboBox.class).setSelectedItem("Emerald");
				VoyageRecords records = field(gui, "voyageRecords", VoyageRecords.class);
				JTextArea output = field(gui, "textAreaOutput", JTextArea.class);
				output.setText("");
				List<String> pirates = List.of("Alice", "Bob");
				String first = records.compare("woodhallow", "emerald", 2, "chests", 4,
						"2026-11-25T12:00", pirates, new int[]{4, 0, 0});
				String score = records.compare("woodhallow", "emerald", 2, "score", 12,
						"2026-11-25T12:00", pirates, new int[]{4, 0, 0});
				invoke(gui, "printRecordLines", List.class, List.of(first, score, ""));
				String data = records.claimData();
				assertTrue(data.startsWith("[{") && data.endsWith("}]"), data);
				assertEquals("We beat the 2-pirate record for most dire chests: 4 (beats 1)", records.shipLine(first));
				assertEquals(List.of(records.shipLine(first), records.shipLine(score), data),
						output.getText().lines().filter(line -> !line.isBlank()).toList(),
						"a short line for the crew, then only the array Puzzle Practice reads");
				assertTrue(records.claimMessage().contains("Data for the records:"), "full submission remains available to Copy claims");
				String before = output.getText();
				invoke(gui, "printRecordLines", List.class, List.of(first, score));
				assertEquals(before, output.getText(), "rechecking a saved or exported result must stay quiet");
				String better = records.compare("woodhallow", "emerald", 2, "chests", 5,
						"2026-11-25T12:10", pirates, new int[]{5, 0, 0});
				invoke(gui, "printRecordLines", List.class, List.of(better));
				assertEquals(List.of(records.shipLine(first), records.shipLine(score), data, records.shipLine(better), records.claimData()),
						output.getText().lines().filter(line -> !line.isBlank()).toList());
				assertTrue(records.claimMessage().contains("\"value\":5"));
			});
		}
	}

	@Test
	void keepsBestSnapshotsAndContributionsWithoutPublishingIntermediateClaims() throws Exception {
		benchmarks("woodhallow", 0);
		VoyageRecords records = new VoyageRecords(folder);
		RunRecordAlerts alerts = new RunRecordAlerts(records, new RunRecordAlerts.Run("woodhallow", "Emerald", 10));
		int[] loot = {4, 0, 0}, alice = {3, 0, 0};
		assertEquals("", alerts.compare(2, "drChests", 4, "2026-11-24T12:00", List.of("Alice", "Bob"), loot,
				Map.of("Alice", alice, "Bob", new int[]{1, 0, 0})));
		alerts.compare(2, "drChests", 2, "2026-11-24T12:02", List.of("Alice", "Bob"), new int[]{2, 0, 0});
		loot[0] = alice[0] = 99;
		assertNull(records.claimMessage());
		alerts.end(1000);
		assertFalse(alerts.ready(5999));
		assertTrue(alerts.ready(6000));
		assertTrue(alerts.finish().get(0).contains("4 beats 0"));
		assertTrue(records.claimMessage().contains("Alice (3)"));
		assertTrue(records.claimMessage().contains("Bob (1)"));
		assertTrue(alerts.finish().isEmpty());
		alerts.compare(2, "drChests", 4, "2026-11-24T12:03", List.of("Alice", "Bob"), new int[]{4, 0, 0});
		assertTrue(alerts.finish().isEmpty(), "a rescan cannot reannounce the same result");
	}

	@ParameterizedTest
	@CsvSource({"CI,0", "Den,0", "Lair,0", "CI,1", "Den,1", "Lair,1"})
	void incomingReportsAlertLiveForEstablishedRecordsAndDeferFirstRecordsUntilReturning(String activity, int drBenchmark) throws Exception {
		String voyage = activity.equals("CI") ? "cursedIsles" : activity.equals("Den") ? "woodhallow" : "vampireLairs";
		benchmarks(voyage, drBenchmark);
		try (GuiTestFixture fixture = new GuiTestFixture(folder)) {
			fixture.prefs.putBoolean("autocopy", false);
			fixture.prefs.putBoolean("autoscan", false);
			fixture.prefs.putBoolean("brokenrecordalert", true);
			SwingUtilities.invokeAndWait(() -> {
				DRGizmoGUI gui = fixture.open();
				field(gui, "profileSelector", main.java.prefobj.RadioButtonProfileSelector.class).selectForActivity(activity);
				field(gui, "comboBoxScanOcean", JComboBox.class).setSelectedItem("Emerald");
				StationClock clock = field(gui, "stationClock", StationClock.class);
				JTextArea output = field(gui, "textAreaOutput", JTextArea.class);
				long now = System.currentTimeMillis(), arrival = now - 300_000;
				String arrivalLine = activity.equals("CI")
						? "Ye land on the island, but an angry mob of its inhabitants stands between ye and yer rightful plunderin'!"
						: activity.equals("Den") ? StationClock.ARRIVAL : "Welcome to the vampire sanctum.";
				clock.message(arrivalLine, arrival);
				clock.message("Game over.  Winners: Alice, Bob.", arrival + 1000);
				if (activity.equals("Den")) clock.message("2 o' 2 still standin'! To yer stations!", arrival + 2000);
				for (int round = 0; round < 2; round++) {
					if (activity.equals("Lair")) clock.message("Ye hear the sound of rustling in coffins.", now - 20_000 + round * 1000);
					DRGroupData report = report(activity, round == 0 ? new int[]{1, 0, 2} : new int[]{0, 0, 4});
					invoke(gui, "processDutyReport", DRGroupData.class, report);
				}
				long live = lootAlerts(output.getText());
				if (activity.equals("Lair")) assertTrue(output.getText().contains("best carpentry score"), "carpentry checks stay live");
				if (drBenchmark == 0) assertEquals(0, live, "first DR loot records wait for the end");
				else assertTrue(live > 0, "established records must alert immediately for screenshots");
				String large = activity.equals("CI") ? "cursed chests" : activity.equals("Den") ? "dire chests" : "immortal chests";
				String small = activity.equals("CI") ? "bone box" : activity.equals("Den") ? "wolf box" : "blood box";
				clock.message("Ye have taken aboard 6 " + large + "!", now);
				clock.message("Ye have taken aboard 1 " + small + "!", now + 1);
				invoke(gui, "finishRunRecords", long.class, now + 2);
				assertEquals(live, lootAlerts(output.getText()), "allow the final report and cargo lines to settle");
				invoke(gui, "finishRunRecords", long.class, now + 6000);
				long alerts = lootAlerts(output.getText());
				assertTrue(alerts > 0, output.getText());
				assertTrue(output.getText().contains("4 (beats " + drBenchmark + ")"));
				assertTrue(output.getText().contains("6 (beats 1)"), "entry totals are checked without Score All or booty export");
				assertFalse(output.getText().contains("Vampire Lair entry "), "automatic checks print record alerts, not a run summary");
				invoke(gui, "processDutyReport", DRGroupData.class, report(activity, new int[]{0, 0, 4}));
				invoke(gui, "finishRunRecords", long.class, now + 7000);
				assertEquals(alerts, lootAlerts(output.getText()), "rescans and timer ticks must not repeat the summary");
			});
		}
	}

	@Test
	void missingBenchmarksAreDeferredAndMidRunRefreshDoesNotChangeTheTimingPolicy() throws Exception {
		benchmarks("cursedIsles", 0);
		VoyageRecords records = new VoyageRecords(folder);
		RunRecordAlerts alerts = new RunRecordAlerts(records, new RunRecordAlerts.Run("cursedIsles", "emerald", 10));
		String updated = Files.readString(folder.resolve(VoyageRecords.RECORDS)).replace("\"value\":0", "\"value\":2");
		records.cacheDownloaded(updated.getBytes(java.nio.charset.StandardCharsets.UTF_8));
		assertEquals("", alerts.compare(0, "drChests", 4, "2026-11-24T12:00", List.of("Alice"), new int[]{4, 0, 0}));
		alerts.compare(0, "drChests", 3, "2026-11-24T12:01", List.of("Bob"), new int[]{3, 0, 0});
		assertNull(records.claimMessage());
		List<String> lines = alerts.finish();
		assertEquals(1, lines.size(), "only the best result in an individual category is announced");
		assertTrue(lines.get(0).contains("Alice"));
		assertTrue(lines.get(0).contains("4 beats 2"));
		RunRecordAlerts missing = new RunRecordAlerts(records, new RunRecordAlerts.Run("cursedIsles", "emerald", 20));
		assertEquals("", missing.compare(3, "drChests", 5, "2026-11-24T12:02", List.of("Alice", "Bob", "Cara"), new int[]{5, 0, 0}));
		assertTrue(missing.finish().get(0).startsWith("New record?"));
	}

	@Test
	void arrivalFreezesThePolicyBeforeTheFirstReportEvenIfRecordsRefresh() throws Exception {
		benchmarks("cursedIsles", 0);
		try (GuiTestFixture fixture = new GuiTestFixture(folder)) {
			fixture.prefs.putBoolean("autocopy", false);
			java.util.concurrent.atomic.AtomicReference<DRGizmoGUI> window = new java.util.concurrent.atomic.AtomicReference<>();
			SwingUtilities.invokeAndWait(() -> {
				DRGizmoGUI gui = fixture.open();
				window.set(gui);
				field(gui, "comboBoxScanOcean", JComboBox.class).setSelectedItem("Emerald");
				field(gui, "stationClock", StationClock.class).message(
						"Ye land on the island, but an angry mob of its inhabitants stands between ye and yer rightful plunderin'!", System.currentTimeMillis());
			});
			SwingUtilities.invokeAndWait(() -> {
				DRGizmoGUI gui = window.get();
				VoyageRecords records = field(gui, "voyageRecords", VoyageRecords.class);
				try {
					String updated = Files.readString(folder.resolve(VoyageRecords.RECORDS)).replace("\"value\":0", "\"value\":1");
					records.cacheDownloaded(updated.getBytes(java.nio.charset.StandardCharsets.UTF_8));
				} catch (Exception error) { throw new AssertionError(error); }
				invoke(gui, "processDutyReport", DRGroupData.class, report("CI", new int[]{0, 0, 4}));
				assertEquals(0, lootAlerts(field(gui, "textAreaOutput", JTextArea.class).getText()));
			});
		}
	}

	@Test
	void aFinalReportDuringTheSettlingWindowReplacesTheEarlierCandidate() throws Exception {
		benchmarks("woodhallow", 0);
		RunRecordAlerts alerts = new RunRecordAlerts(new VoyageRecords(folder), new RunRecordAlerts.Run("woodhallow", "emerald", 10));
		alerts.compare(0, "drChests", 2, "2026-11-24T12:00", List.of("Alice"), new int[]{2, 0, 0});
		alerts.end(1000);
		assertFalse(alerts.ready(2000));
		alerts.compare(0, "drChests", 5, "2026-11-24T12:01", List.of("Alice"), new int[]{5, 0, 0});
		assertTrue(alerts.ready(6000));
		assertEquals(1, alerts.finish().size());
		assertTrue(alerts.finish().isEmpty());
	}

	@Test
	void aDenIsOverWhenTheLairIsPlunderedAndItsLastReportPublishesAtOnce() throws Exception {
		benchmarks("woodhallow", 0);
		try (GuiTestFixture fixture = new GuiTestFixture(folder)) {
			fixture.prefs.putBoolean("autocopy", false);
			fixture.prefs.putBoolean("autoscan", false);
			fixture.prefs.putBoolean("brokenrecordalert", true);
			SwingUtilities.invokeAndWait(() -> {
				DRGizmoGUI gui = fixture.open();
				field(gui, "profileSelector", main.java.prefobj.RadioButtonProfileSelector.class).selectForActivity("Den");
				field(gui, "comboBoxScanOcean", JComboBox.class).setSelectedItem("Emerald");
				StationClock clock = field(gui, "stationClock", StationClock.class);
				JTextArea output = field(gui, "textAreaOutput", JTextArea.class);
				long now = System.currentTimeMillis(), arrival = now - 300_000;
				clock.message(StationClock.ARRIVAL, arrival);
				clock.message("2 o' 2 still standin'! To yer stations!", arrival + 2000);
				invoke(gui, "processDutyReport", DRGroupData.class, report("Den", new int[]{1, 0, 2}));
				clock.message("2 o' 2 still standin'! Plunder the lair!", now - 10_000);
				clock.message("The lair be plundered! The night be yers!", now - 1000);
				invoke(gui, "finishRunRecords", long.class, now);
				assertEquals(0, lootAlerts(output.getText()), "the lair's report hasn't been read yet");
				invoke(gui, "processDutyReport", DRGroupData.class, report("Den", new int[]{0, 0, 3}));
				assertTrue(output.getText().contains("3 (beats 0)"), "no wait for the chests to reach the ship: " + output.getText());
			});
		}
	}

	@Test
	void cursedIslesIsOverOnALostFrayOrARetreatNotOnEachForagesChests() {
		ChatLog.Run won = new ChatLog.Run();
		won.voyage = "cursedIsles";
		won.observe("Game over.  Winners: Rudis, Jice, McCoy Brutish, Jice's Thrall,", 1000);
		won.observe("Ye have taken aboard 4 cursed chests!", 1000);
		assertFalse(won.over(), "chests loaded after a won fray are one forage's");
		won.observe("The islanders reclaimed 8 cursed chests!", 90_000);
		assertTrue(won.over(), "a lost fray ends it");
		assertFalse(won.finished(), "the rest of the chests are still to load");
		won.observe("Ye have taken aboard 7 cursed chests!", 113_000);
		assertTrue(won.finished());
		assertEquals(11, won.tiers[0]);

		ChatLog.Run retreat = new ChatLog.Run();
		retreat.voyage = "cursedIsles";
		retreat.observe("Game over.  Winners: Alice, Bob.", 1000);
		retreat.observe("Ye have taken aboard 4 cursed chests!", 1000);
		retreat.observe("Ye have taken aboard 3 cursed chests!", 400_000);
		assertTrue(retreat.over() && retreat.finished(), "chests loaded with no fray won just before: a retreat");
	}

	@Test
	void sceneDepartureEndsTheEntryBeforeTheReportTaggingGraceExpires() {
		GameScene scene = new GameScene();
		scene.feed("Adding chat display for entering scene [scene=data.GauntletSceneObject:1]", 1000);
		assertEquals(1000, scene.enteredAt("CI"));
		assertEquals(0, scene.leftAt("CI", 1000));
		scene.feed("Removing chat display for leaving scene [scene=data.GauntletSceneObject:1]", 2000);
		assertEquals("CI", scene.activity(2100));
		assertEquals(2000, scene.leftAt("CI", 1000));
		assertEquals(0, scene.leftAt("CI", 3000), "an earlier entry's departure cannot end a later one");
	}

	private static DRGroupData report(String activity, int[] loot) {
		DRGroupData report = new DRGroupData();
		report.setActivity(activity);
		String station = activity.equals("Lair") ? "Treasure Haul" : "Foraging";
		report.put(station, "Alice", "Swift", loot, 0);
		report.put(activity.equals("Lair") ? "Carpentry" : "Navigating", "Bob", "Swift", new int[]{0, 1, 1}, 0);
		return report;
	}

	private static long lootAlerts(String output) {
		return output.lines().filter(line -> (line.contains(" beat the ") || line.contains(" set the first ")) && !line.contains("dnav")
				&& (line.contains("in one DR") || line.contains("in one fray") || line.contains("in one forage"))).count();
	}

	private static void invoke(DRGizmoGUI gui, String name, Class<?> type, Object argument) {
		try {
			var method = DRGizmoGUI.class.getDeclaredMethod(name, type);
			method.setAccessible(true);
			method.invoke(gui, argument);
		} catch (ReflectiveOperationException e) { throw new AssertionError(e); }
	}
}
