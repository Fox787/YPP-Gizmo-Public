package main.java.general;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VoyageCrewTest {
	@TempDir Path folder;

	@Test
	void sevenPirateRunCannotClaimEightPirateRecordsFromItsBootyRecipients() throws Exception {
		var division = new BootyDivision();
		division.chat = parse("""
				[18:08:39] Xiatch has left the vessel.
				[18:09:00] Hark the dread howlin'! 'Tis Woodhallow Isle, realm o' the wolf-folk!
				[18:11:58] Yer crew member Xiatch has logged off.
				[18:12:05] Game over.  Winners: Dancter, Xioch, Dien, Imalioncub, Kriosx, Trushtsthtme, Jice.
				[18:12:05] 7 o' 7 still standin'! To yer stations!
				[18:44:09] Game over.  Winners: Dancter, Xioch, Dien, Imalioncub, Kriosx, Trushtsthtme, Jice.
				[18:44:09] 4 o' 7 still standin'! To yer stations!
				[18:52:30] Game over.  Winners: Dancter, Xioch, Dien, Imalioncub, Kriosx, Trushtsthtme, Jice.
				[18:52:30] 7 o' 7 still standin'! Plunder the lair!
				[18:57:44] Ye have taken aboard 682 wolf boxes!
				[18:57:44] Ye have taken aboard 361 lunar lockers!
				[18:57:44] Ye have taken aboard 131 dire chests!
				[18:57:57] The crew opens 1174 chests and finds 566 items and 233,596 pieces of eight.
				""");
		var run = division.chat.runs.get(0);
		for (String name : run.recordCrew()) division.pirates.add(new BootyDivision.PirateLoot(name));
		division.pirates.add(new BootyDivision.PirateLoot("Xiatch"));
		assertEquals(8, division.pirates.size());
		assertEquals(7, run.crewSize);
		assertEquals(7, run.recordCrew().size());
		assertFalse(run.recordCrew().contains("Xiatch"));
		Files.writeString(folder.resolve(VoyageRecords.RECORDS), """
				[{"voyage":"woodhallow","ocean":"emerald","category":"Most dire chests","crewSize":7,"value":154},
				 {"voyage":"woodhallow","ocean":"emerald","category":"Best score","crewSize":7,"value":1956},
				 {"voyage":"woodhallow","ocean":"emerald","category":"Most dire chests","crewSize":8,"value":100},
				 {"voyage":"woodhallow","ocean":"emerald","category":"Best score","crewSize":8,"value":1700}]
				""");
		var records = new VoyageRecords(folder);
		assertEquals("", records.check(run, "emerald", Map.of()));
		assertNull(records.claimMessage());
		assertFalse(Files.exists(folder.resolve("record_claims.jsonl")));
		assertTrue(division.toJson().contains("\"crew_size\":7,\"crew\":[\"Dancter\",\"Dien\",\"Imalioncub\",\"Jice\",\"Kriosx\",\"Trushtsthtme\",\"Xioch\"]"));
	}

	@Test
	void checksEachRunSeparatelyAndKeepsZeroLootCombatants() throws Exception {
		var segment = parse("""
				[12:00:00] Hark the dread howlin'! 'Tis Woodhallow Isle, realm o' the wolf-folk!
				[12:02:00] Game over.  Winners: Alice, Bob, Cora.
				[12:02:00] 2 o' 3 still standin'! To yer stations!
				[12:10:00] Ye have taken aboard 5 dire chests!
				[12:11:00] Game over.  Winners: Unrelated, Boarding, Fight, Later.
				[12:20:00] Hark the dread howlin'! 'Tis Woodhallow Isle, realm o' the wolf-folk!
				[12:22:00] Game over.  Winners: Alice, Dan.
				[12:22:00] 2 o' 2 still standin'! To yer stations!
				[12:30:00] Ye have taken aboard 2 dire chests!
				[12:31:00] The crew opens 7 chests and finds 1 item and 100 pieces of eight.
				""");
		assertEquals(List.of("Alice", "Bob", "Cora"), segment.runs.get(0).recordCrew());
		assertEquals(List.of("Alice", "Dan"), segment.runs.get(1).recordCrew());
		Files.writeString(folder.resolve(VoyageRecords.KNOWN), """
				[{"voyage":"woodhallow","ocean":"emerald","kind":"chests","crewSize":3,"value":4}]
				""");
		var records = new VoyageRecords(folder);
		assertTrue(records.check(segment.runs.get(0), "emerald", Map.of("Alice", new int[]{5, 0, 0})).contains("3 pirates"));
		String claims = records.claimMessage();
		assertTrue(claims.contains("Alice (5), Bob (0), Cora (0)"));
		assertFalse(claims.contains("Dan"));
	}

	@Test
	void doesNotShrinkTheCrewWhenPiratesLeaveOrAreKnockedOut() throws Exception {
		var run = onlyRun("""
				[12:02:00] Game over.  Winners: Alice, Bob, Cora.
				[12:02:00] 1 o' 3 still standin'! To yer stations!
				[12:05:00] Bob has abandoned the challenge and gone back to the ship.
				[12:07:00] Game over.  Winners: Alice, Cora.
				[12:07:00] 2 o' 2 still standin'! Plunder the lair!
				""");
		assertEquals(3, run.crewSize);
		assertEquals(List.of("Alice", "Bob", "Cora"), run.recordCrew());
	}

	@Test
	void requiresCompleteConsistentCrewEvidenceBeforeClaiming() throws Exception {
		Files.writeString(folder.resolve(VoyageRecords.KNOWN), """
				[{"voyage":"woodhallow","ocean":"emerald","kind":"chests","crewSize":2,"value":1}]
				""");
		var records = new VoyageRecords(folder);
		for (String evidence : List.of("",
				"[12:02:00] Game over.  Winners: Alice, Bob.\n",
				"[12:02:00] 2 o' 2 still standin'! To yer stations!\n",
				"[12:02:00] Game over.  Winners: Alice.\n[12:02:00] 1 o' 2 still standin'! To yer stations!\n")) {
			var run = onlyRun(evidence);
			assertTrue(run.recordCrew().isEmpty());
			assertEquals("", records.check(run, "emerald", Map.of()));
		}
		assertNull(records.claimMessage());
	}

	@Test
	void rejectsLootEvidenceFromPiratesOutsideTheCombatRoster() throws Exception {
		Files.writeString(folder.resolve(VoyageRecords.KNOWN), """
				[{"voyage":"woodhallow","ocean":"emerald","kind":"chests","crewSize":2,"value":1}]
				""");
		var run = onlyRun("""
				[12:02:00] Game over.  Winners: Alice, Bob.
				[12:02:00] 2 o' 2 still standin'! To yer stations!
				""");
		var records = new VoyageRecords(folder);
		assertEquals("", records.check(run, "emerald", Map.of("Other", new int[]{5, 0, 0})));
		assertNull(records.claimMessage());
	}

	@Test
	void excludesEnemiesAndThrallsFromOtherVoyageCrews() throws Exception {
		var segment = parse("""
				[12:00:00] Ye land on the island, but an angry mob of its inhabitants stands between ye and yer rightful plunderin'!
				[12:02:00] Game over.  Winners: Alice, Bob, Alice's Thrall, Servile Zombie.
				[12:10:00] Ye have taken aboard 5 cursed chests!
				[12:20:00] Welcome to the vampire sanctum.
				[12:22:00] Game over.  Winners: Alice, Cora.
				[12:30:00] Ye have taken aboard 2 immortal chests!
				[12:31:00] The crew opens 7 chests and finds 1 item and 100 pieces of eight.
				""");
		assertEquals(List.of("Alice", "Bob"), segment.runs.get(0).recordCrew());
		assertEquals(List.of("Alice", "Cora"), segment.runs.get(1).recordCrew());
	}

	private ChatLog.Run onlyRun(String evidence) throws Exception {
		return parse("[12:00:00] " + StationClock.ARRIVAL + "\n" + evidence
				+ "[12:10:00] Ye have taken aboard 5 dire chests!\n"
				+ "[12:11:00] The crew opens 5 chests and finds 1 item and 100 pieces of eight.\n").runs.get(0);
	}

	private ChatLog.Segment parse(String text) throws Exception {
		return ChatLog.latestBooty(Files.writeString(folder.resolve("chat.txt"), "====== 2026/11/23 ======\n" + text));
	}
}
