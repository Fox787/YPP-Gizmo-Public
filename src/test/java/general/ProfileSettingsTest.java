package main.java.general;

import main.java.prefobj.RadioButtonProfileSelector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;

import static main.java.general.GuiTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

class ProfileSettingsTest {
    private static final Map<String, String> FORMAT_FLAGS = Map.ofEntries(
            Map.entry("checkBoxCopyScanToVessel", "copyscantovessel"),
            Map.entry("checkBoxCopyScoreToVessel", "copyscoretovessel"),
            Map.entry("checkBoxScanWriteDate", "scanwritedate"),
            Map.entry("checkBoxScanUseTabs", "scanusetabs"),
            Map.entry("checkBoxScanStationHeaders", "scanstationheaders"),
            Map.entry("checkBoxScan2Station", "scanstation"),
            Map.entry("checkBoxScan2Pirate", "scanpirate"),
            Map.entry("checkBoxScan2Rating", "scanrating"),
            Map.entry("checkBoxScan2TokenScore", "scantokenscore"),
            Map.entry("checkBoxScan2TokenArray", "scantokenarray"),
            Map.entry("checkBoxScan2SheetCombo", "scansheetcombo"),
            Map.entry("checkBoxScan2ScorePerMinute", "scanscoreperminute"),
            Map.entry("checkBoxScoreWriteDate", "scorewritedate"),
            Map.entry("checkBoxScoreUseTabs", "scoreusetabs"),
            Map.entry("checkBoxScoreStationHeaders", "scorestationheaders"),
            Map.entry("checkBoxScore2Station", "scorestation"),
            Map.entry("checkBoxScore2Pirate", "scorepirate"),
            Map.entry("checkBoxScore2Rating", "scorerating"),
            Map.entry("checkBoxScore2TokenScore", "scoretokenscore"),
            Map.entry("checkBoxScore2TokenArray", "scoretokenarray"),
            Map.entry("checkBoxScore2SheetCombo", "scoresheetcombo"));
    private static final Map<String, String> WEIGHTS = Map.of(
            "formattedTextFieldTokenCircle", "tokencircle",
            "formattedTextFieldTokenDiamond", "tokendiamond",
            "formattedTextFieldTokenPlus", "tokenplus",
            "formattedTextFieldTokenCross", "tokencross",
            "formattedTextFieldTokenThrall", "tokenthrall",
            "formattedTextFieldCannonball", "tokencannonball",
            "formattedTextFieldSmallChest", "chestsmall",
            "formattedTextFieldMediumChest", "chestmedium",
            "formattedTextFieldLargeChest", "chestlarge");

    @TempDir Path folder;
    private Preferences prefs;
    private GuiTestFixture fixture;
    private DRGizmoGUI gui;

    @BeforeEach
    void setUp() {
        fixture = new GuiTestFixture(folder);
        prefs = fixture.prefs;
        prefs.put("currentProfile", "Cursed Isles");
        prefs.putBoolean("denProfileAdded", true);
        prefs.node("profiles/Cursed Isles");
        prefs.node("profiles/Werewolf Den");
    }

    @AfterEach
    void tearDown() throws Exception {
        fixture.close();
    }

    @Test
    void activitySwitchesShowEditableWeightsAndPreserveEachProfilesValues() throws Exception {
        prefs.node("profiles/Vampire Lair");
        prefs.node("profiles/Z Custom Den").put("activityMode", RecordChecker.DEN);
        prefs.node("profiles/Z Custom Lair").put("activityMode", RecordChecker.LAIR);
        SwingUtilities.invokeAndWait(() -> {
            open();
            assertTrue(field(gui, "tokenScoring", JPanel.class).isVisible());
            assertTrue(field(gui, "forageScoring", JPanel.class).isVisible());
            assertFalse(field(gui, "lairHaulScoring", JPanel.class).isVisible());
            assertFalse(field(gui, "dnavScoring", JPanel.class).isVisible());
            assertFalse(field(gui, "carpScoring", JPanel.class).isVisible());
            assertTrue(selector().selectForActivity(RecordChecker.DEN));
            assertEquals("Werewolf Den", selector().getSelectedProfile());
            assertFalse(field(gui, "tokenScoring", JPanel.class).isVisible());
            assertTrue(field(gui, "forageScoring", JPanel.class).isVisible());
            assertFalse(field(gui, "lairHaulScoring", JPanel.class).isVisible());
            assertTrue(field(gui, "dnavScoring", JPanel.class).isVisible());
            assertFalse(field(gui, "carpScoring", JPanel.class).isVisible());
            assertEquals(2, ((Number) stationField("navdouble").getValue()).intValue());
            stationField("navsingle").setText("7");
            stationField("navdouble").setText("8");
            stationField("navtriple").setText("9");
            assertTrue(selector().selectForActivity(RecordChecker.LAIR));
            assertFalse(field(gui, "tokenScoring", JPanel.class).isVisible());
            assertFalse(field(gui, "forageScoring", JPanel.class).isVisible());
            assertTrue(field(gui, "lairHaulScoring", JPanel.class).isVisible());
            assertTrue(field(gui, "carpScoring", JPanel.class).isVisible());
            assertFalse(field(gui, "dnavScoring", JPanel.class).isVisible());
            assertEquals(-1, ((Number) stationField("carpslipshod").getValue()).intValue());
            stationField("carpslipshod").setText("-4");
            stationField("carpcreaky").setText("3");
            stationField("carpproof").setText("7");
            DRGroupData carp = new DRGroupData();
            carp.put("Carpentry", "Alice", "Swift", new int[]{2, 3, 4}, 0);
            invoke("calculateReportScore", carp);
            assertEquals(29, carp.get("Carpentry", "Alice").getTokenScore());
            assertTrue(selector().selectForActivity(RecordChecker.DEN));
            DRGroupData nav = new DRGroupData();
            nav.put("Navigating", "Alice", "Swift", new int[]{2, 3, 4}, 0);
            invoke("calculateReportScore", nav);
            assertEquals(74, nav.get("Navigating", "Alice").getTokenScore());
            assertEquals(-4, prefs.node("profiles/Vampire Lair").getInt("carpslipshod", 0));
            assertEquals(7, prefs.node("profiles/Werewolf Den").getInt("navsingle", 0));
            for (String custom : List.of("Z Custom Den", "Z Custom Lair")) {
                select(custom);
                assertTrue(selector().showsAllScoringValues());
                for (String group : List.of("tokenScoring", "forageScoring", "lairHaulScoring", "dnavScoring", "carpScoring"))
                    assertTrue(field(gui, group, JPanel.class).isVisible(), custom + ": " + group);
            }
        });
    }

    @Test
    void detectedReportsSwitchProfilesAndExportRatingsWithWoodhallowTimingAndBootyExportOff() throws Exception {
        prefs.node("profiles/Vampire Lair");
        prefs.putBoolean("scoreperminute", false);
        prefs.putBoolean("bootyexport", false);
        prefs.putBoolean("drratingsexport", true);
        prefs.putBoolean("autocopy", false);
        prefs.putBoolean("brokenrecordalert", false);
        SwingUtilities.invokeAndWait(() -> {
            open();
            DRGroupData carp = new DRGroupData();
            carp.setActivity(RecordChecker.LAIR);
            carp.put("Carpentry", "Alice", "Swift", new int[]{2, 3, 4}, 0);
            invoke("processDutyReport", carp);
            assertEquals("Vampire Lair", selector().getSelectedProfile());
            assertTrue(field(gui, "carpScoring", JPanel.class).isVisible());
        });
        String ratings = java.nio.file.Files.readString(folder.resolve("dr_ratings.jsonl"));
        assertTrue(ratings.contains("\"station\":\"Carpentry\""));
        assertTrue(ratings.contains("\"rating\":\"Swift\""));
        assertTrue(ratings.contains("\"score\":9"));
        assertTrue(ratings.contains("\"periodMs\":null"));
        assertTrue(ratings.contains("\"exact\":false,\"perMin\":null"));
    }

    @Test
    void renamesLegacyDefaultsWithoutLosingValuesOrSelection() throws Exception {
        prefs.node("profiles").removeNode();
        prefs.put("currentProfile", "Den");
        prefs.node("profiles/CI").putInt("chestlarge", 17);
        prefs.node("profiles/Den").putInt("navsingle", 23);
        prefs.node("profiles/Lair").putInt("carpslipshod", -5);
        SwingUtilities.invokeAndWait(() -> {
            var selector = new RadioButtonProfileSelector(prefs);
            assertEquals("Werewolf Den", selector.getSelectedProfile());
            assertEquals("Den", selector.getCurrentActivity());
        });
        assertEquals(17, prefs.node("profiles/Cursed Isles").getInt("chestlarge", 0));
        assertEquals(23, prefs.node("profiles/Werewolf Den").getInt("navsingle", 0));
        assertEquals(-5, prefs.node("profiles/Vampire Lair").getInt("carpslipshod", 0));
        for (String old : List.of("CI", "Den", "Lair")) assertFalse(prefs.node("profiles").nodeExists(old));
    }

    @Test
    void activitySwitchesGoToTheNamedProfileNeverACopyOfIt() throws Exception {
        // "Custom" was made from Werewolf Den, so it carries Den's activityMode and sorts before it.
        prefs.node("profiles/Custom").put("activityMode", RecordChecker.DEN);
        prefs.put("currentProfile", "Custom");
        SwingUtilities.invokeAndWait(() -> {
            open();
            assertEquals("Custom", selector().getSelectedProfile());
            assertTrue(selector().selectForActivity(RecordChecker.DEN));
            assertEquals("Werewolf Den", selector().getSelectedProfile(), "a copy of the Den profile isn't the Den profile");
            assertFalse(selector().selectForActivity(RecordChecker.LAIR), "no Vampire Lair profile, so no switch");
            assertEquals("Werewolf Den", selector().getSelectedProfile());
        });
    }

    @Test
    void invalidNewWeightsBlockAutomaticSwitchesWithoutPartialWrites() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            open();
            stationField("navsingle").setText("invalid");
            assertFalse(selector().selectForActivity(RecordChecker.DEN));
            assertEquals("Cursed Isles", selector().getSelectedProfile());
            assertNull(prefs.node("profiles/Cursed Isles").get("navsingle", null));
        });
    }

    private JFormattedTextField stationField(String key) {
        return (JFormattedTextField) field(gui, "stationWeightFields", Map.class).get(key);
    }

    private void invoke(String method, DRGroupData report) {
        try {
            var call = DRGizmoGUI.class.getDeclaredMethod(method, DRGroupData.class);
            call.setAccessible(true);
            call.invoke(gui, report);
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @Test
    void switchingSavesEveryFormatAndWeightToTheOutgoingProfileOnly() throws Exception {
        Preferences den = prefs.node("profiles/Werewolf Den");
        FORMAT_FLAGS.values().forEach(key -> den.putBoolean(key, false));
        WEIGHTS.values().forEach(key -> den.putInt(key, 7));
        den.putInt("scanocean", 2);
        den.putInt("scoreocean", 2);

        SwingUtilities.invokeAndWait(() -> {
            open();
            FORMAT_FLAGS.keySet().forEach(name -> field(gui, name, JCheckBox.class).setSelected(true));
            WEIGHTS.keySet().forEach(name -> field(gui, name, JFormattedTextField.class).setText("13"));
            ocean("comboBoxScanOcean").setSelectedIndex(0);
            ocean("comboBoxScoreOcean").setSelectedIndex(0);

            select("Werewolf Den");
            assertProfileControls(false, 7, 2);
            FORMAT_FLAGS.values().forEach(key -> assertTrue(prefs.node("profiles/Cursed Isles").getBoolean(key, false), key));
            WEIGHTS.values().forEach(key -> assertEquals(13, prefs.node("profiles/Cursed Isles").getInt(key, -1), key));
            FORMAT_FLAGS.values().forEach(key -> assertFalse(den.getBoolean(key, true), key));
            WEIGHTS.values().forEach(key -> assertEquals(7, den.getInt(key, -1), key));

            WEIGHTS.keySet().forEach(name -> field(gui, name, JFormattedTextField.class).setText("19"));
            field(gui, "buttonSavePrefs", JButton.class).doClick();
            select("Cursed Isles");
            assertProfileControls(true, 13, 0);
            select("Werewolf Den");
            assertProfileControls(false, 19, 2);
            assertEquals("Werewolf Den", prefs.get("currentProfile", null));
            assertEquals("Werewolf Den", new RadioButtonProfileSelector(prefs).getSelectedProfile());
        });
    }

    @Test
    void switchingPreservesAllSharedOptionsAndUnsubmittedFolders() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            open();
            String[] shared = {"checkBoxAutoCopy", "checkBoxIgnoreAI", "checkBoxDebug", "checkBoxBrokenRecordAlert",
                    "checkBoxAutoScan", "checkBoxShowExtraButtons",
                    "checkBoxBootyExport", "checkBoxPromptMissingRun"};
            List<Boolean> values = new ArrayList<>();
            for (String name : shared) {
                JCheckBox box = field(gui, name, JCheckBox.class);
                box.doClick();
                values.add(box.isSelected());
            }
            String[] folders = {"textFieldChatLogFolder", "textFieldBootyTextFolder", "textFieldDataFolder"};
            for (String name : folders) field(gui, name, JTextField.class).setText(folder.resolve(name).toString());
            gui.setSize(420, 700);
            int reports = gui.drTreeRootNode.getChildCount();

            select("Werewolf Den");
            select("Cursed Isles");
            for (int i = 0; i < shared.length; i++)
                assertEquals(values.get(i), field(gui, shared[i], JCheckBox.class).isSelected(), shared[i]);
            for (String name : folders)
                assertEquals(folder.resolve(name).toString(), field(gui, name, JTextField.class).getText(), name);
            assertEquals(420, gui.getWidth());
            assertEquals(700, gui.getHeight());
            assertEquals(reports, gui.drTreeRootNode.getChildCount());

            field(gui, "buttonSavePrefs", JButton.class).doClick();
            for (String key : new String[]{"autocopy", "ignoreai", "debug", "brokenrecordalert"}) {
                assertNotNull(prefs.get(key, null), key);
                assertNull(prefs.node("profiles/Cursed Isles").get(key, null), key);
                assertNull(prefs.node("profiles/Werewolf Den").get(key, null), key);
            }
        });
    }

    @Test
    void migratesLegacySharedOptionsFromTheStartupProfileOnly() throws Exception {
        for (String key : new String[]{"autocopy", "ignoreai", "debug", "brokenrecordalert"}) {
            prefs.node("profiles/Cursed Isles").putBoolean(key, false);
            prefs.node("profiles/Werewolf Den").putBoolean(key, true);
        }
        SwingUtilities.invokeAndWait(() -> {
            open();
            select("Werewolf Den");
            for (String name : new String[]{"checkBoxAutoCopy", "checkBoxIgnoreAI", "checkBoxDebug", "checkBoxBrokenRecordAlert"})
                assertFalse(field(gui, name, JCheckBox.class).isSelected(), name);
            for (String key : new String[]{"autocopy", "ignoreai", "debug", "brokenrecordalert"})
                assertFalse(prefs.getBoolean(key, true), key);
        });
    }

    @Test
    void invalidWeightKeepsTheOldProfileSelectedWithoutPartialWrites() throws Exception {
        prefs.node("profiles/Cursed Isles").putBoolean("scanpirate", false);
        prefs.node("profiles/Cursed Isles").putInt("chestlarge", 3);
        SwingUtilities.invokeAndWait(() -> {
            open();
            field(gui, "checkBoxScan2Pirate", JCheckBox.class).setSelected(true);
            field(gui, "formattedTextFieldLargeChest", JFormattedTextField.class).setText("invalid");
            select("Werewolf Den");
            assertEquals("Cursed Isles", selector().getSelectedProfile());
            assertEquals("Cursed Isles", prefs.get("currentProfile", null));
            assertTrue(button(selector(), "Cursed Isles").isSelected());
            assertFalse(button(selector(), "Werewolf Den").isSelected());
            assertFalse(prefs.node("profiles/Cursed Isles").getBoolean("scanpirate", true));
            assertEquals(3, prefs.node("profiles/Cursed Isles").getInt("chestlarge", -1));
            field(gui, "formattedTextFieldLargeChest", JFormattedTextField.class).setText("5");
            select("Werewolf Den");
            assertEquals("Werewolf Den", selector().getSelectedProfile());
            assertTrue(prefs.node("profiles/Cursed Isles").getBoolean("scanpirate", false));
            assertEquals(5, prefs.node("profiles/Cursed Isles").getInt("chestlarge", -1));
        });
    }

    @Test
    void missingSelectionRecoversToAnExistingVisibleProfile() throws Exception {
        prefs.put("currentProfile", "Deleted profile");
        SwingUtilities.invokeAndWait(() -> {
            RadioButtonProfileSelector selector = new RadioButtonProfileSelector(prefs);
            assertEquals("Cursed Isles", selector.getSelectedProfile());
            assertTrue(button(selector, "Cursed Isles").isSelected());
            assertEquals("Cursed Isles", prefs.get("currentProfile", null));
        });
        assertFalse(prefs.node("profiles").nodeExists("Deleted profile"));
    }

    private void open() {
        gui = fixture.open();
    }

    private void assertProfileControls(boolean flag, int weight, int ocean) {
        FORMAT_FLAGS.keySet().forEach(name -> assertEquals(flag, field(gui, name, JCheckBox.class).isSelected(), name));
        WEIGHTS.keySet().forEach(name -> assertEquals(weight, ((Number) field(gui, name, JFormattedTextField.class).getValue()).intValue(), name));
        assertEquals(ocean, ocean("comboBoxScanOcean").getSelectedIndex());
        assertEquals(ocean, ocean("comboBoxScoreOcean").getSelectedIndex());
    }

    private JComboBox<?> ocean(String name) { return field(gui, name, JComboBox.class); }
    private RadioButtonProfileSelector selector() { return field(gui, "profileSelector", RadioButtonProfileSelector.class); }
    private void select(String name) { button(selector(), name).doClick(); }

    private static JRadioButton button(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JRadioButton && name.equals(((JRadioButton) child).getText())) return (JRadioButton) child;
            if (child instanceof Container) {
                JRadioButton found = button((Container) child, name);
                if (found != null) return found;
            }
        }
        return null;
    }
}
