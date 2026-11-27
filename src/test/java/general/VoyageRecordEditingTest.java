package main.java.general;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static main.java.general.GuiTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class VoyageRecordEditingTest {
    private static final String DOWNLOADED = """
            [{"voyage":"woodhallow","ocean":"emerald","kind":"chests","crewSize":2,"value":10,"date":"2026-11-01","pirates":["Alice","Bob"]},
             {"voyage":"woodhallow","ocean":"meridian","kind":"chests","crewSize":2,"value":12},
             {"voyage":"woodhallow","ocean":"emerald","kind":"score","crewSize":2,"value":30},
             {"voyage":"cursedIsles","ocean":"emerald","kind":"chests","crewSize":2,"value":15}]
            """;

    @Test
    void manualValuesSurviveRestartAndRefreshAndDriveRecordChecks(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve(VoyageRecords.RECORDS), DOWNLOADED);
        VoyageRecords records = new VoyageRecords(folder);
        VoyageRecords.Record original = records.get("woodhallow", "emerald", 2, VoyageRecords.CHESTS);
        records.saveManualValues(Map.of(original, 20));
        assertEquals(12, original.value, "editing must not mutate the downloaded record");
        assertEquals(DOWNLOADED, Files.readString(folder.resolve(VoyageRecords.RECORDS)));
        assertFalse(Files.exists(folder.resolve("record_claims.jsonl")), "manual edits are benchmarks, not record claims");

        records = new VoyageRecords(folder);
        assertEquals(20, records.get("woodhallow", "emerald", 2, VoyageRecords.CHESTS).value);
        assertEquals("", records.compare("woodhallow", "emerald", 2, VoyageRecords.CHESTS, 19,
                "2026-11-23T20:00", List.of("Alice", "Bob"), new int[]{19, 0, 0}));
        records.cacheDownloaded(DOWNLOADED.replace("\"value\":12", "\"value\":25")
                .replace("\"value\":30", "\"value\":40").getBytes(StandardCharsets.UTF_8));
        assertEquals(20, records.get("woodhallow", "emerald", 2, VoyageRecords.CHESTS).value);
        assertEquals(40, records.get("woodhallow", "emerald", 2, VoyageRecords.SCORE).value);
        assertEquals(15, records.get("cursedIsles", "emerald", 2, VoyageRecords.CHESTS).value);
        assertEquals(20, new VoyageRecords(folder).get("woodhallow", "emerald", 2, VoyageRecords.CHESTS).value);
    }

    @Test
    void loweringARecordReplacesTheGlobalBenchmarkAndClearingRestoresIt(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve(VoyageRecords.RECORDS), DOWNLOADED);
        VoyageRecords records = new VoyageRecords(folder);
        VoyageRecords.Record original = records.get("woodhallow", "emerald", 2, VoyageRecords.CHESTS);
        records.saveManualValues(Map.of(original, 7));
        assertEquals(7, records.get("woodhallow", "obsidian", 2, VoyageRecords.CHESTS).value);
        assertEquals(1, records.forVoyage("woodhallow").stream().filter(r -> r.kind.equals(VoyageRecords.CHESTS)).count());

        Map<VoyageRecords.Record, Integer> reset = new LinkedHashMap<>();
        reset.put(original, null);
        records.saveManualValues(reset);
        assertEquals(12, records.get("woodhallow", "emerald", 2, VoyageRecords.CHESTS).value);
        assertEquals(12, new VoyageRecords(folder).get("woodhallow", "emerald", 2, VoyageRecords.CHESTS).value);
    }

    @Test
    void canSetMissingRecordsOfflineAndKeepEachVoyageCrewAndKindIndependent(@TempDir Path folder) throws Exception {
        VoyageRecords records = new VoyageRecords(folder);
        VoyageRecords.Record entry = record("woodhallow", 3, VoyageRecords.CHESTS);
        VoyageRecords.Record dr = record("woodhallow", 3, VoyageRecords.DR_CHESTS);
        VoyageRecords.Record ci = record("cursedIsles", 3, VoyageRecords.CHESTS);
        VoyageRecords.Record solo = record("woodhallow", 0, VoyageRecords.DR_CHESTS);
        records.saveManualValues(Map.of(entry, 0, dr, 11, ci, 8, solo, 4));
        VoyageRecords restored = new VoyageRecords(folder);
        assertEquals(0, restored.get("woodhallow", "emerald", 3, VoyageRecords.CHESTS).value);
        assertEquals(11, restored.get("woodhallow", "emerald", 3, VoyageRecords.DR_CHESTS).value);
        assertEquals(8, restored.get("cursedIsles", "emerald", 3, VoyageRecords.CHESTS).value);
        assertEquals(4, restored.get("woodhallow", "emerald", 0, VoyageRecords.DR_CHESTS).value);
        assertNull(restored.get("woodhallow", "emerald", 4, VoyageRecords.DR_CHESTS));
    }

    @Test
    void failedSavesDoNotPublishPartialEdits(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve(VoyageRecords.RECORDS), DOWNLOADED);
        VoyageRecords records = new VoyageRecords(folder);
        VoyageRecords.Record record = records.get("woodhallow", "emerald", 2, VoyageRecords.CHESTS);
        assertThrows(IllegalArgumentException.class, () -> records.saveManualValues(Map.of(record, -1)));
        assertEquals(12, records.get("woodhallow", "emerald", 2, VoyageRecords.CHESTS).value);
        Files.createDirectory(folder.resolve(VoyageRecords.MANUAL));
        assertThrows(java.io.IOException.class, () -> records.saveManualValues(Map.of(record, 99)));
        assertEquals(12, records.get("woodhallow", "emerald", 2, VoyageRecords.CHESTS).value);
    }

    @Test
    void existingSettingsFieldsSaveWithoutOpeningAnotherEditor(@TempDir Path folder) throws Exception {
        Files.writeString(folder.resolve(VoyageRecords.RECORDS), DOWNLOADED);
        try (GuiTestFixture fixture = new GuiTestFixture(folder)) {
            SwingUtilities.invokeAndWait(() -> {
                DRGizmoGUI gui = fixture.open();
                VoyageRecords records = field(gui, "voyageRecords", VoyageRecords.class);
                JTextField den = recordField(gui, "Woodhallow, 2 crew, most dire chests");
                assertEquals("12", den.getText());
                den.setText("22");
                den.postActionEvent();
                assertEquals(22, records.get("woodhallow", "emerald", 2, VoyageRecords.CHESTS).value);
                assertTrue(den.getFont().isBold(), "manual entries are visibly marked in the current settings");
                assertEquals("most dire chests", den.getToolTipText());

                JTextField ci = recordField(gui, "Cursed Isles, 3 crew, most cursed chests");
                ci.setText("9");
                field(gui, "buttonSavePrefs", JButton.class).doClick();
                assertEquals(9, records.get("cursedIsles", "emerald", 3, VoyageRecords.CHESTS).value);

                den.setText("23");
                ci.setText("-2");
                field(gui, "buttonSavePrefs", JButton.class).doClick();
                assertEquals(22, records.get("woodhallow", "emerald", 2, VoyageRecords.CHESTS).value);
                assertEquals(9, records.get("cursedIsles", "emerald", 3, VoyageRecords.CHESTS).value);
                ci.setText("10");
                ci.postActionEvent();
                assertEquals(23, records.get("woodhallow", "emerald", 2, VoyageRecords.CHESTS).value);
                assertEquals(10, records.get("cursedIsles", "emerald", 3, VoyageRecords.CHESTS).value);

                den.setText("");
                den.postActionEvent();
                assertEquals("12", den.getText());
                assertEquals(12, records.get("woodhallow", "emerald", 2, VoyageRecords.CHESTS).value);
                assertFalse(den.getFont().isBold(), "restoring a downloaded record removes the manual marker");

                // A completed background refresh must also preserve an edit still being typed.
                den.setText("24");
                try {
                    records.cacheDownloaded(DOWNLOADED.replace("\"value\":12", "\"value\":25").getBytes(StandardCharsets.UTF_8));
                    var update = DRGizmoGUI.class.getDeclaredMethod("updateRecordPanels");
                    update.setAccessible(true);
                    update.invoke(gui);
                } catch (Exception e) {
                    throw new AssertionError(e);
                }
                assertEquals("24", recordField(gui, "Woodhallow, 2 crew, most dire chests").getText());
                assertEquals(24, records.get("woodhallow", "emerald", 2, VoyageRecords.CHESTS).value);
            });
        }
    }

    private static VoyageRecords.Record record(String voyage, int crew, String kind) {
        VoyageRecords.Record r = new VoyageRecords.Record();
        r.voyage = voyage;
        r.ocean = "global";
        r.crewSize = crew;
        r.kind = kind;
        r.category = VoyageRecords.categoryFor(voyage, kind, crew);
        return r;
    }
}
