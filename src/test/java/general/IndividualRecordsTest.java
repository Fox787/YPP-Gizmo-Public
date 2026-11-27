package main.java.general;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static main.java.general.GuiTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class IndividualRecordsTest {
    private static String fixture() throws Exception {
        return Files.readString(Path.of("src/test/resources/voyage-individual-records.json"));
    }

    @Test
    void everyIndividualCategoryFromPuzzlePracticeHasItsOwnSettingsEntry(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve(VoyageRecords.RECORDS), fixture());
        VoyageRecords records = new VoyageRecords(folder);
        Map<String, Integer> expectedCounts = Map.of("woodhallow", 3, "cursedIsles", 22, "vampireLairs", 5);
        for (String voyage : expectedCounts.keySet()) {
            Set<String> expected = VoyageRecords.parse(fixture()).stream()
                    .filter(r -> r.voyage.equals(voyage) && VoyageRecords.isIndividual(r))
                    .map(VoyageRecords::recordLabel).collect(Collectors.toSet());
            List<VoyageRecords.Record> entries = records.individualRecords(voyage);
            Set<String> actual = entries.stream().map(VoyageRecords::recordLabel).collect(Collectors.toSet());
            assertEquals(expectedCounts.get(voyage).intValue(), expected.size(), voyage);
            assertEquals(expected, actual, voyage);
            assertEquals(actual.size(), entries.size(), "tied records must not create duplicate editors");
            for (VoyageRecords.Record record : entries) assertNotNull(records.get(record), VoyageRecords.recordLabel(record));
        }
        assertEquals(1, records.otherRecords("vampireLairs").size());
        assertEquals("All crews", VoyageRecords.recordScope(records.otherRecords("vampireLairs").get(0)));
    }

    @Test
    void lairChestRecordUsesOneTreasureHaulDrIncludingLegacyManualValues(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve(VoyageRecords.RECORDS), fixture());
        VoyageRecords records = new VoyageRecords(folder);
        VoyageRecords.Record chests = records.get("vampireLairs", "emerald", 0, VoyageRecords.DR_SCORE);
        assertNotNull(chests);
        assertEquals(14, chests.value);
        assertEquals("Most chests in one DR, one pirate", VoyageRecords.recordLabel(chests));
        assertNull(records.get("vampireLairs", "emerald", 0, VoyageRecords.SCORE));

        DRGroupData report = new DRGroupData();
        report.put("Treasure Haul", "Hauler", "Swift", new int[]{5, 5, 5}, 15);
        report.put("Carpentry", "Carpenter", "Swift", new int[]{0, 0, 20}, 40);
        List<String> lines = LairRecordChecks.fray(records, report, "emerald", "2026-11-26T12:00");
        assertTrue(lines.stream().anyMatch(line -> line.contains("Hauler, most chests in one DR")
                && line.contains("15 beats 14")));
        assertFalse(lines.stream().anyMatch(line -> line.contains("Carpenter, most chests")));

        Files.writeString(folder.resolve(VoyageRecords.MANUAL), """
                [{"voyage":"vampireLairs","ocean":"emerald","kind":"other","crewSize":0,
                  "category":"Most 🎁 in one entry, one pirate","value":16}]
                """);
        VoyageRecords restored = new VoyageRecords(folder);
        VoyageRecords.Record manual = restored.get("vampireLairs", "emerald", 0, VoyageRecords.DR_SCORE);
        assertEquals(16, manual.value);
        assertTrue(restored.isManual(manual));
        assertEquals(5, restored.individualRecords("vampireLairs").size());
    }

    @Test
    void dnavAndCarpentryRecordsCanBeEnteredBeforeAFirstDownload(@TempDir Path folder) {
        VoyageRecords records = new VoyageRecords(folder);
        assertTrue(records.individualRecords("woodhallow").stream().anyMatch(r -> r.kind.equals(VoyageRecords.DR_NAV)));
        Set<String> lair = records.individualRecords("vampireLairs").stream().map(r -> r.kind).collect(Collectors.toSet());
        assertTrue(lair.containsAll(Set.of(VoyageRecords.DR_CHESTS, "carpScore", "carpHoles", "vampireProof")));
    }

    @Test
    void forageNotesKeepManualValuesAndRefreshComparisonsSeparate(@TempDir Path folder) throws Exception {
        String json = fixture();
        Files.writeString(folder.resolve(VoyageRecords.RECORDS), json);
        VoyageRecords records = new VoyageRecords(folder);
        VoyageRecords.Record first = forage(records, "Forage 1");
        VoyageRecords.Record second = forage(records, "Forage 2");
        assertEquals(35, records.get(first).value);
        assertEquals(38, records.get(second).value);
        records.saveManualValues(Map.of(first, 50));

        VoyageRecords restored = new VoyageRecords(folder);
        assertEquals(50, restored.get(first).value);
        assertEquals(38, restored.get(second).value);
        assertTrue(restored.isManual(first));
        assertFalse(restored.isManual(second));
        var download = VoyageRecords.prepareDownload(json.getBytes(StandardCharsets.UTF_8));
        var replacements = restored.manualReplacements(download);
        assertEquals(1, replacements.size());
        assertEquals("Forage 1", replacements.get(0).incoming().notes);
        assertEquals(35, replacements.get(0).incoming().value);
        restored.applyDownload(download, VoyageRecords.RefreshPolicy.KEEP_HIGHER, replacements);
        assertEquals(50, restored.get(first).value);
        restored.applyDownload(download, VoyageRecords.RefreshPolicy.REPLACE_MANUAL, restored.manualReplacements(download));
        assertEquals(35, restored.get(first).value);
        assertFalse(restored.isManual(first));
    }

    @Test
    void missingIndividualFieldsAreEditableInsideTheExistingVoyageSettings(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve(VoyageRecords.RECORDS), fixture());
        try (GuiTestFixture fixture = new GuiTestFixture(folder)) {
            SwingUtilities.invokeAndWait(() -> {
                DRGizmoGUI gui = fixture.open();
                VoyageRecords records = field(gui, "voyageRecords", VoyageRecords.class);
                Set<String> accessible = new HashSet<>();
                for (Object value : field(gui, "recordValueFields", List.class))
                    accessible.add(((JTextField) value).getAccessibleContext().getAccessibleName());
                for (String voyage : List.of("woodhallow", "cursedIsles", "vampireLairs")) {
                    for (VoyageRecords.Record record : records.individualRecords(voyage)) {
                        String name = VoyageRecords.voyageName(voyage) + ", individual, " + VoyageRecords.recordLabel(record);
                        assertTrue(accessible.contains(name), name);
                    }
                }
                JTextField dnav = recordField(gui, "Woodhallow, individual, Best dnav score in one DR, one pirate");
                assertEquals("31", dnav.getText());
                dnav.setText("40");
                dnav.postActionEvent();
                assertEquals(40, records.get("woodhallow", "emerald", 0, VoyageRecords.DR_NAV).value);
                assertTrue(dnav.getFont().isBold());
                JTextField holes = recordField(gui, "Vampire Lairs, individual, Most carpentry holes");
                assertEquals("25", holes.getText());
                holes.setText("28");
                field(gui, "buttonSavePrefs", JButton.class).doClick();
                assertEquals(28, records.get("vampireLairs", "emerald", 0, "carpHoles").value);
            });
        }
    }

    private static VoyageRecords.Record forage(VoyageRecords records, String notes) {
        return records.individualRecords("cursedIsles").stream()
                .filter(r -> notes.equals(r.notes)).findFirst().orElseThrow();
    }
}
