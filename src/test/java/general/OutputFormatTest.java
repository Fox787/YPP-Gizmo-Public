package main.java.general;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.nio.file.Path;
import java.util.Map;

import static main.java.general.GuiTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class OutputFormatTest {
    @Test
    void everyScanFormatCombinationKeepsFieldsOnThePlayersExistingRow() {
        DRGroupData report = report();
        Map<String, Map<String, Double>> rates = Map.of("Foraging", Map.of("Alice", 2.8, "Bob", 1.2));
        for (int options = 0; options < 512; options++) {
            String text = report.toString(false, on(options, 0), on(options, 1), "Emerald", on(options, 2),
                    on(options, 3), true, on(options, 4), on(options, 5), on(options, 6), on(options, 7),
                    on(options, 8) ? rates : null);
            assertPlayerRows(text, options);
            for (String line : text.lines().filter(row -> row.contains("Alice") || row.contains("Bob")).toList()) {
                assertEquals(on(options, 8), line.contains("("), "rate must be on the same row: " + options);
            }
        }
    }

    @Test
    void everyScoreFormatCombinationExtendsTheSameRowsAcrossReports() {
        DRPersistantData reports = new DRPersistantData();
        reports.add(report());
        DRGroupData second = new DRGroupData();
        second.put("Foraging", "Alice", "Brisk", new int[]{2, 3, 4}, 20);
        reports.add(second);
        for (int options = 0; options < 256; options++) {
            String text = reports.toString(false, on(options, 0), on(options, 1), "Emerald", on(options, 2),
                    on(options, 3), true, on(options, 4), on(options, 5), on(options, 6), on(options, 7));
            assertPlayerRows(text, options);
            if (on(options, 5)) {
                String alice = text.lines().filter(line -> line.contains("Alice")).findFirst().orElseThrow();
                String bob = text.lines().filter(line -> line.contains("Bob")).findFirst().orElseThrow();
                assertTrue(alice.contains("14") && alice.contains("20"), "both reports belong on Alice's row");
                assertTrue(bob.contains("NA"), "missing reports keep the same column position");
            }
        }
    }

    @Test
    void timedScanNeverPrintsASecondReportWhenInlineRatesAreOff(@TempDir Path folder) throws Exception {
        try (GuiTestFixture fixture = new GuiTestFixture(folder)) {
            fixture.prefs.putBoolean("scoreperminute", true);
            fixture.prefs.putBoolean("autocopy", false);
            fixture.prefs.putBoolean("debug", false);
            fixture.prefs.putBoolean("brokenrecordalert", false);
            SwingUtilities.invokeAndWait(() -> {
                DRGizmoGUI gui = fixture.open();
                StationClock clock = field(gui, "stationClock", StationClock.class);
                long end = System.currentTimeMillis() - 1_000;
                clock.message("6 o' 6 still standin'! To yer stations!", end - 300_000);
                clock.message("The wolf-kin gather! 30 seconds!", end - 30_000);
                JTextArea output = field(gui, "textAreaOutput", JTextArea.class);
                JCheckBox inlineRates = field(gui, "checkBoxScan2ScorePerMinute", JCheckBox.class);
                for (boolean enabled : new boolean[]{false, true}) {
                    inlineRates.setSelected(enabled);
                    field(gui, "checkBoxScan2Pirate", JCheckBox.class).setSelected(true);
                    output.setText("");
                    process(gui, report());
                    assertPlayerRows(output.getText(), enabled ? 1 : 0);
                    String alice = output.getText().lines().filter(line -> line.contains("Alice")).findFirst().orElseThrow();
                    assertEquals(enabled, alice.contains("("), "rates only appear when selected");
                    assertFalse(field(gui, "activityTracker", ActivityTracker.class).periods().isEmpty(),
                            "tracking and exports still run when rates are hidden");
                }
            });
        }
    }

    private static DRGroupData report() {
        DRGroupData report = new DRGroupData();
        report.setActivity(RecordChecker.DEN);
        report.put("Foraging", "Alice", "Swift", new int[]{1, 2, 3}, 14);
        report.put("Foraging", "Bob", "Steady", new int[]{1, 1, 1}, 6);
        return report;
    }

    private static boolean on(int options, int bit) { return (options & (1 << bit)) != 0; }

    private static void assertPlayerRows(String text, int options) {
        assertEquals(1, text.lines().filter(line -> line.contains("Alice")).count(), "Alice duplicated: " + options + "\n" + text);
        assertEquals(1, text.lines().filter(line -> line.contains("Bob")).count(), "Bob duplicated: " + options + "\n" + text);
    }

    private static void process(DRGizmoGUI gui, DRGroupData report) {
        try {
            var method = DRGizmoGUI.class.getDeclaredMethod("processDutyReport", DRGroupData.class);
            method.setAccessible(true);
            method.invoke(gui, report);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
