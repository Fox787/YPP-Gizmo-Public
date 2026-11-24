package main.java.general;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.BorderLayout;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VoyageRecordRefreshTest {
    private static final String DOWNLOADED = """
            [{"voyage":"woodhallow","ocean":"emerald","kind":"chests","crewSize":2,"value":10},
             {"voyage":"woodhallow","ocean":"meridian","kind":"chests","crewSize":2,"value":12},
             {"voyage":"woodhallow","ocean":"emerald","kind":"score","crewSize":2,"value":30},
             {"voyage":"cursedIsles","ocean":"emerald","kind":"chests","crewSize":2,"value":15}]
            """;

    @Test
    void previewCountsManualBenchmarksOnceAndListsBothValues(@TempDir Path folder) throws Exception {
        VoyageRecords records = editedRecords(folder);
        var preview = records.manualReplacements(download());
        assertEquals(3, preview.size(), "a tie on another ocean must not double-count the same benchmark");
        assertEquals(1, preview.stream().filter(VoyageRecords.ManualReplacement::incomingIsHigher).count());
        assertEquals(4, new VoyageRecords(folder).manualRecords().size(), "provenance survives restart");
        SwingUtilities.invokeAndWait(() -> {
            JPanel warning = DRGizmoGUI.recordRefreshWarning(preview);
            BorderLayout layout = (BorderLayout) warning.getLayout();
            String summary = ((JTextArea) layout.getLayoutComponent(BorderLayout.NORTH)).getText();
            assertTrue(summary.contains("overwrite 3 manual records"));
            assertTrue(summary.contains("replaces only 1"));
            JTable table = (JTable) ((JScrollPane) layout.getLayoutComponent(BorderLayout.CENTER)).getViewport().getView();
            assertEquals(3, table.getRowCount());
            int denChests = -1;
            for (int row = 0; row < table.getRowCount(); row++) {
                if (table.getValueAt(row, 0).equals("Woodhallow") && table.getValueAt(row, 2).toString().contains("dire chests"))
                    denChests = row;
            }
            assertTrue(denChests >= 0);
            assertEquals(2, table.getValueAt(denChests, 1));
            assertEquals(22, table.getValueAt(denChests, 3));
            assertEquals("12", table.getValueAt(denChests, 4));
        });
    }

    @Test
    void keepHigherReplacesOnlyStrictlyHigherPuzzlePracticeValues(@TempDir Path folder) throws Exception {
        VoyageRecords records = editedRecords(folder);
        var download = download();
        records.applyDownload(download, VoyageRecords.RefreshPolicy.KEEP_HIGHER, records.manualReplacements(download));
        assertRecord(records, "woodhallow", 2, VoyageRecords.CHESTS, 22, true);
        assertRecord(records, "woodhallow", 2, VoyageRecords.SCORE, 30, false);
        assertRecord(records, "cursedIsles", 2, VoyageRecords.CHESTS, 15, true);
        assertRecord(records, "woodhallow", 3, VoyageRecords.CHESTS, 9, true);
        VoyageRecords restored = new VoyageRecords(folder);
        assertEquals(3, restored.manualRecords().size());
        assertRecord(restored, "woodhallow", 2, VoyageRecords.SCORE, 30, false);
        assertRecord(restored, "woodhallow", 2, VoyageRecords.CHESTS, 22, true);
    }

    @Test
    void replacingAllClearsOnlyTheListedManualMarkersAndPersistsTheDownload(@TempDir Path folder) throws Exception {
        VoyageRecords records = editedRecords(folder);
        var download = download();
        records.applyDownload(download, VoyageRecords.RefreshPolicy.REPLACE_MANUAL, records.manualReplacements(download));
        assertRecord(records, "woodhallow", 2, VoyageRecords.CHESTS, 12, false);
        assertRecord(records, "woodhallow", 2, VoyageRecords.SCORE, 30, false);
        assertRecord(records, "cursedIsles", 2, VoyageRecords.CHESTS, 15, false);
        assertRecord(records, "woodhallow", 3, VoyageRecords.CHESTS, 9, true);
        assertEquals(1, new VoyageRecords(folder).manualRecords().size());
    }

    @Test
    void cancellingAfterDownloadPreparationLeavesFilesAndManualValuesUntouched(@TempDir Path folder) throws Exception {
        VoyageRecords records = editedRecords(folder);
        String beforeManual = Files.readString(folder.resolve(VoyageRecords.MANUAL));
        String beforeDownload = Files.readString(folder.resolve(VoyageRecords.RECORDS));
        var changed = VoyageRecords.prepareDownload(DOWNLOADED.replace("\"value\":12", "\"value\":99").getBytes(StandardCharsets.UTF_8));
        assertEquals(3, records.manualReplacements(changed).size());
        assertEquals(beforeManual, Files.readString(folder.resolve(VoyageRecords.MANUAL)));
        assertEquals(beforeDownload, Files.readString(folder.resolve(VoyageRecords.RECORDS)));
        assertRecord(records, "woodhallow", 2, VoyageRecords.CHESTS, 22, true);
        assertThrows(java.io.IOException.class, () -> VoyageRecords.prepareDownload("[]".getBytes(StandardCharsets.UTF_8)));
        assertEquals(beforeManual, Files.readString(folder.resolve(VoyageRecords.MANUAL)));
    }

    @Test
    void editsMadeAfterTheWarningAreNotOverwrittenByTheReviewedPlan(@TempDir Path folder) throws Exception {
        VoyageRecords records = editedRecords(folder);
        var download = download();
        var reviewed = records.manualReplacements(download);
        records.saveManualValues(Map.of(records.get("woodhallow", "emerald", 2, VoyageRecords.CHESTS), 24));
        records.applyDownload(download, VoyageRecords.RefreshPolicy.REPLACE_MANUAL, reviewed);
        assertRecord(records, "woodhallow", 2, VoyageRecords.CHESTS, 24, true);
        assertRecord(records, "woodhallow", 2, VoyageRecords.SCORE, 30, false);
    }

    @Test
    void higherNumericTextValuesCountAsHigherRecords(@TempDir Path folder) throws Exception {
        VoyageRecords records = editedRecords(folder);
        var download = VoyageRecords.prepareDownload(DOWNLOADED.replace("\"value\":30", "\"value\":\"35\"").getBytes(StandardCharsets.UTF_8));
        records.applyDownload(download, VoyageRecords.RefreshPolicy.KEEP_HIGHER, records.manualReplacements(download));
        assertRecord(records, "woodhallow", 2, VoyageRecords.SCORE, 35, false);
    }

    private static VoyageRecords editedRecords(Path folder) throws Exception {
        Files.writeString(folder.resolve(VoyageRecords.RECORDS), DOWNLOADED);
        VoyageRecords records = new VoyageRecords(folder);
        VoyageRecords.Record missing = new VoyageRecords.Record();
        missing.voyage = "woodhallow";
        missing.ocean = "global";
        missing.kind = VoyageRecords.CHESTS;
        missing.crewSize = 3;
        records.saveManualValues(Map.of(
                records.get("woodhallow", "emerald", 2, VoyageRecords.CHESTS), 22,
                records.get("woodhallow", "emerald", 2, VoyageRecords.SCORE), 25,
                records.get("cursedIsles", "emerald", 2, VoyageRecords.CHESTS), 15,
                missing, 9));
        return records;
    }

    private static VoyageRecords.Download download() throws Exception {
        return VoyageRecords.prepareDownload(DOWNLOADED.getBytes(StandardCharsets.UTF_8));
    }

    private static void assertRecord(VoyageRecords records, String voyage, int crew, String kind, int value, boolean manual) {
        VoyageRecords.Record record = records.get(voyage, "emerald", crew, kind);
        assertEquals(value, record.value);
        assertEquals(manual, records.isManual(record));
    }
}
