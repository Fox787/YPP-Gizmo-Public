package main.java.general;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LogFlowTest {
	private static final String DATE = "====== 2026/11/16 ======\n";

	@TempDir Path folder;

	@Test
	void selectsTheLogClosestToThisClientStartAndReadsCompleteLines() throws Exception {
		Path launcher = log("yohoho_980000.log", "");
		Path own = log("yohoho_1000100.log", "Client object Pirate (\nConfiguring client [ocean=emerald\n");
		Path other = log("yohoho_1020000.log", "Client object Other (\n");
		Files.setLastModifiedTime(launcher, FileTime.fromMillis(1_000));
		Files.setLastModifiedTime(own, FileTime.fromMillis(2_000));
		Files.setLastModifiedTime(other, FileTime.fromMillis(3_000));
		assertEquals(own, GameLog.ownLog(folder, 1_000_000));
		assertEquals(other, GameLog.ownLog(folder, 0));

		var tail = new GameLog.Tail();
		GameLog.Info first = tail.poll(own);
		assertEquals("Pirate", first.pirate);
		append(own, "Vessel change [ovid=0, nvid=7]\nvessel=Quiet Sloop(7 ");
		assertNull(tail.poll(own).vesselName);
		append(own, "\n");
		assertEquals("Quiet Sloop", tail.poll(own).vesselName);
		assertNull(first.vesselId);
		Files.writeString(own, "Client object Reconnected (\n");
		assertEquals("Reconnected", tail.poll(own).pirate);
		assertNull(tail.poll(own).vesselId);
	}

	@Test
	void discoversNewTabsAndCombinesSplitBootyLogsAcrossMidnight() throws Exception {
		String cargo = "[23:59:59] Ye have taken aboard 2 dire chests!\n";
		Path main = log("Pirate_emerald_x", DATE + cargo + cargo);
		var discovery = new ChatLog.Discovery();
		assertEquals(List.of(main), discovery.logs(folder, "Pirate", "emerald", 1_000));
		Path system = log("Pirate_emerald_x_System", DATE + cargo
				+ "[00:00:02] The crew opens 4 chests and finds 2 items and 300 pieces of eight.\n");
		assertEquals(List.of(main), discovery.logs(folder, "Pirate", "emerald", 2_000));
		assertEquals(List.of(main, system), discovery.logs(folder, "Pirate", "emerald", 6_000));
		ChatLog.Segment division = ChatLog.latestBooty(ChatLog.findLogs(folder, "Pirate", "emerald"));
		assertEquals(4, division.takenAboard.get("dire chests"));
		assertEquals(LocalDateTime.of(2026, 11, 17, 0, 0, 2), division.openedAt);
	}

	@Test
	void tailsTabOnlyFilesAndConfirmsMultilineChatOnce() throws Exception {
		Path system = log("Pirate_emerald_x_System", DATE + "[12:00:00] " + StationClock.ARRIVAL + "\n"
				+ "[12:00:10] 6 o' 6 still standin'! To yer stations!\n");
		Path vessel = log("Pirate_emerald_x_Vessel", DATE + "[12:00:20] Pirate says, \"Nav\n");
		var clock = new StationClock();
		List<Path> files = ChatLog.findLogs(folder, "Pirate", "emerald");
		assertEquals(2, files.size());
		clock.poll(files, "Pirate");
		assertEquals(6, clock.current().crew);
		assertTrue(clock.drainOwnChat().isEmpty());

		append(system, "[12:00:30] The wolf-kin gather! 30 seconds!\n");
		append(vessel, "Pirate 24 (12.00)\"\n");
		clock.poll(files, "Pirate");
		assertEquals(50_000, clock.current().end - clock.current().start);
		assertEquals(List.of("Nav\nPirate 24 (12.00)"), clock.drainOwnChat().stream().map(line -> line.text).toList());
		append(system, "[12:00:20] Pirate says, \"Nav\nPirate 24 (12.00)\"\n");
		clock.poll(files, "Pirate");
		assertTrue(clock.drainOwnChat().isEmpty());
	}

	@Test
	void clipboardWaitsForMatchingRecentChatAndRetriesABusyClipboard() {
		LocalDateTime now = LocalDateTime.of(2026, 11, 16, 12, 0, 0);
		var clipboard = new ReportClipboard();
		var copied = new ArrayList<String>();
		clipboard.replace(List.of("/ve Nav\nPirate 24 (12.00)", "/ve Forage"));
		clipboard.update(List.of(), now, copied::add);
		ChatLog.Line stale = new ChatLog.Line(now.minusSeconds(1), "Nav\nPirate 24 (12.00)", "");
		ChatLog.Line unrelated = new ChatLog.Line(now, "Something else", "");
		clipboard.update(List.of(stale, unrelated), now.plusSeconds(1), copied::add);
		assertEquals(1, copied.size());
		ChatLog.Line sent = new ChatLog.Line(now, "Nav\r\n Pirate 24 (12.00) ", "");
		clipboard.update(List.of(sent), now.plusSeconds(1), text -> { throw new IllegalStateException(); });
		assertTrue(clipboard.hasPending());
		clipboard.update(List.of(), now.plusSeconds(2), copied::add);
		assertEquals(List.of("/ve Nav\nPirate 24 (12.00)", "/ve Forage"), copied);
		assertFalse(clipboard.hasPending());
	}

	@Test
	void formatsVesselMessagesInStationOrderAndWithinTheLimit() {
		var period = new ActivityTracker.Period();
		period.rows.add(row("Foraging", "Forager"));
		period.rows.add(row("Navigating", "Navigator"));
		period.rows.add(row("Foraging", "NPC Mate"));
		assertEquals(List.of("/ve Nav\nNavigator 24 (" + String.format("%.2f", 12.0) + ")\n\nForage\nForager 24 ("
				+ String.format("%.2f", 12.0) + ")"), ActivityTracker.chatMessages(period, true));
		for (int i = 0; i < 30; i++) period.rows.add(row("Foraging", "AdditionalPirate" + i));
		List<String> messages = ActivityTracker.chatMessages(period, true);
		assertTrue(messages.size() > 1);
		assertTrue(messages.stream().allMatch(message -> message.startsWith("/ve ") && message.length() <= 240));
	}

	private static ActivityTracker.Row row(String station, String pirate) {
		var row = new ActivityTracker.Row();
		row.station = station;
		row.pirate = pirate;
		row.score = 24;
		row.ms = 120_000;
		return row;
	}

	private Path log(String name, String text) throws Exception {
		return Files.writeString(folder.resolve(name), text);
	}

	private static void append(Path file, String text) throws Exception {
		Files.writeString(file, text, StandardOpenOption.APPEND);
	}
}
