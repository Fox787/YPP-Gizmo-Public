package main.java.general;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class VoyageSettingsTest {
    private enum Counter { CURSED_CHESTS, WEREWOLF_CHESTS, VAMPIRATE_CHESTS, VAMPIRATE_PATCHES }

    @Test
    void distinguishesVoyagesFromTheCounterAndLeavesAmbiguousOlderReportsUnknown() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var frame = new JFrame();
            try {
                var scan = new DRGizmo(frame, new JTextArea(), false);
                frame.setContentPane(station("Foraging", Counter.WEREWOLF_CHESTS));
                assertEquals(RecordChecker.DEN, scan.scanDutyReport().getActivity());
                frame.setContentPane(station("Foraging", Counter.CURSED_CHESTS));
                assertEquals(RecordChecker.CI, scan.findDutyReportQuietly().getActivity());
                frame.setContentPane(station("Treasure Haul", Counter.VAMPIRATE_CHESTS));
                assertEquals(RecordChecker.LAIR, scan.scanDutyReport().getActivity());
                frame.setContentPane(station("Carpentry", Counter.VAMPIRATE_PATCHES));
                assertEquals(RecordChecker.LAIR, scan.findDutyReportQuietly().getActivity());
            } finally {
                frame.dispose();
            }
        });
        var old = new DRGroupData();
        old.put("Foraging", "Alice", "Swift", new int[]{1, 2, 3}, 14);
        assertNull(RecordChecker.activityOf(old));
        old.put("Navigating", "Bob", "Swift", new int[]{1, 2, 3}, 14);
        assertEquals(RecordChecker.DEN, RecordChecker.activityOf(old));
    }

    @Test
    void preservesLairRoundIdentityWhenCopyingAndSavingReports() throws Exception {
        var original = new DRGroupData();
        original.setActivity(RecordChecker.LAIR);
        original.setLairRound(20_000, 50_000);
        var bytes = new ByteArrayOutputStream();
        try (var out = new ObjectOutputStream(bytes)) {
            out.writeObject(original.copy());
        }
        try (var in = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            var restored = (DRGroupData) in.readObject();
            assertEquals(RecordChecker.LAIR, restored.getActivity());
            assertEquals(20_000, restored.lairArrival());
            assertEquals(50_000, restored.lairRoundEnd());
        }
    }

    @Test
    void splitsBootyRunsByArrivalAndRecognizesCargoWhenArrivalIsMissing(@TempDir Path folder) throws Exception {
        Path log = Files.writeString(folder.resolve("chat.txt"), """
                ====== 2026/11/16 ======
                [12:00:00] Ye have taken aboard 2 cursed chests!
                [12:15:00] Ye have taken aboard 3 dire chests!
                [12:15:01] Ye have taken aboard 4 lunar lockers!
                [12:15:02] Ye have taken aboard 5 wolf boxes!
                [12:15:03] Ye abandoned 10 dire chests as the booty is full!
                [12:20:00] Welcome to the vampire sanctum.
                [12:24:00] Ye have taken aboard 7 immortal chests!
                [12:30:00] The crew opens 21 chests and finds 2 items and 300 pieces of eight.
                """);
        ChatLog.Segment booty = ChatLog.latestBooty(log);
        assertEquals(3, booty.runs.size());
        assertEquals("cursedIsles", booty.runs.get(0).voyage);
        assertArrayEquals(new int[]{2, 0, 0}, booty.runs.get(0).tiers);
        assertEquals("woodhallow", booty.runs.get(1).voyage);
        assertArrayEquals(new int[]{3, 4, 5}, booty.runs.get(1).tiers);
        assertEquals("vampireLairs", booty.runs.get(2).voyage);
        assertArrayEquals(new int[]{7, 0, 0}, booty.runs.get(2).tiers);
    }

    @Test
    void lairEntryRequiresEveryFrayAndTheCorrectHaul(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve(VoyageRecords.KNOWN), """
                [{"voyage":"vampireLairs","ocean":"emerald","kind":"chests","crewSize":2,"value":3},
                 {"voyage":"vampireLairs","ocean":"emerald","kind":"score","crewSize":2,"value":6}]
                """);
        Files.writeString(folder.resolve(VoyageRecords.SHEET), """
                [{"voyage":"vampireLairs","ocean":"emerald","category":"Best carpentry score","value":"6"}]
                """);
        var records = new VoyageRecords(folder);
        var clock = new StationClock();
        clock.message("Welcome to the vampire sanctum.", 20_000);
        clock.message("Ye hear the sound of rustling in coffins.", 35_000);
        assertNull(clock.lairRound(40_000));
        assertEquals(new StationClock.LairRound(20_000, 50_000), clock.lairRound(50_000));
        clock.message("Ye hear the sound of rustling in coffins.", 55_000);
        clock.message("Ye have taken aboard 4 immortal chests!", 72_000);
        clock.message("Ye have taken aboard 1 nocturnal locker!", 72_000);
        clock.message("Ye have taken aboard 2 blood boxes!", 72_000);
        clock.message("Welcome to the vampire sanctum.", 75_000);
        assertNull(clock.lairRound(75_000));

        var first = new DRGroupData();
        first.setLairRound(20_000, 50_000);
        first.put("Treasure Haul", "Hauler", "Swift", new int[]{1, 1, 2}, 4);
        first.put("Carpentry", "Carpenter", "Swift", new int[]{1, 2, 3}, 7);
        var second = new DRGroupData();
        second.setLairRound(20_000, 70_000);
        second.put("Treasure Haul", "Hauler", "Swift", new int[]{1, 0, 2}, 3);

        assertTrue(LairRecordChecks.fray(records, first, "emerald", "2026-11-16T12:00")
                .stream().anyMatch(line -> line.contains("best carpentry score") && line.contains("7 beats 6")));
        var entry = clock.lairEntry(20_000);
        assertEquals(Set.of(50_000L, 70_000L), entry.rounds());
        assertTrue(LairRecordChecks.verifiedRounds(List.of(first), entry).isEmpty());
        var wrong = new StationClock.LairEntry(entry.rounds(), new int[]{4, 1, 3});
        assertTrue(LairRecordChecks.verifiedRounds(List.of(first, second), wrong).isEmpty());
        var rounds = LairRecordChecks.verifiedRounds(List.of(first, first.copy(), second), entry);
        assertEquals(2, rounds.size());
        assertTrue(LairRecordChecks.entry(records, rounds, entry, "emerald").stream().anyMatch(s -> s.contains("4 beats 3")));
    }

    @Test
    void copiesVerifiedContributionsAndDropsOutdatedClaims(@TempDir Path folder) throws Exception {
        Path known = folder.resolve(VoyageRecords.KNOWN);
        Files.writeString(known, "[{\"voyage\":\"woodhallow\",\"ocean\":\"emerald\",\"crewSize\":3,\"kind\":\"chests\",\"value\":4}]");
        var records = new VoyageRecords(folder);
        var crew = List.of("Alice", "Bob", "Cora");
        var loot = Map.of("Alice", new int[]{2, 3, 0}, "Bob", new int[]{3, 0, 1});
        assertTrue(records.compare("woodhallow", "meridian", 3, VoyageRecords.CHESTS, 5,
                "2026-11-16T12:00", crew, new int[]{5, 3, 1}, loot).contains("5 beats 4"));
        var copied = new VoyageRecords(folder).claimMessage();
        assertTrue(copied.contains("Meridian"));
        assertTrue(copied.contains("Bob (3), Alice (2), Cora (0)"));   // biggest share first
        Files.writeString(known, Files.readString(known).replace("\"value\":4", "\"value\":5"));
        assertNull(new VoyageRecords(folder).claimMessage());
    }

    private static final class TokenPanel extends JPanel {
        private final Counter type;
        private final List<int[]> rows = List.of(new int[]{1, 2, 3});

        private TokenPanel(Counter type) { this.type = type; }
    }

    private static JPanel station(String name, Counter counter) {
        JPanel title = new JPanel();
        title.add(new JLabel(name));
        JPanel heading = new JPanel();
        heading.add(new JLabel());
        heading.add(title);

        JPanel pirate = new JPanel();
        pirate.add(new JLabel("Pirate"));
        pirate.add(new JLabel("Swift"));
        pirate.add(new TokenPanel(counter));
        JPanel row = new JPanel();
        row.add(new JLabel());
        row.add(pirate);

        JPanel station = new JPanel();
        station.add(heading);
        station.add(row);
        return station;
    }
}
