package main.java.general;

import org.junit.jupiter.api.Test;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.*;
import static org.junit.jupiter.api.Assertions.*;

class RecordSettingsLayoutTest {
    @Test
    void narrowPanelsKeepEveryValueEditorWideEnoughWithLargerClientFonts() throws Exception {
        List<VoyageRecords.Record> records = VoyageRecords.parse(Files.readString(Path.of("src/test/resources/voyage-individual-records.json")));
        SwingUtilities.invokeAndWait(() -> {
            String[] defaults = {"Label.font", "TextField.font", "ToggleButton.font"};
            Map<String, Object> previous = new HashMap<>();
            for (String key : defaults) previous.put(key, UIManager.get(key));
            try {
                for (int fontSize : new int[]{12, 14, 16}) {
                    for (String key : defaults) UIManager.put(key, new Font(Font.DIALOG, Font.PLAIN, fontSize));
                    for (String voyage : List.of("woodhallow", "cursedIsles", "vampireLairs")) {
                        JPanel panel = panel();
                        List<JTextField> fields = new ArrayList<>();
                        RecordSettingsLayout.addRecords(panel, "Individual", individual(records, voyage), record -> {
                            JTextField field = new JTextField(Integer.toString(record.value), 4);
                            fields.add(field);
                            return field;
                        });
                        toggles(panel).forEach(JToggleButton::doClick);
                        for (int width : new int[]{130, 150, 180, 240}) {
                            panel.setSize(width, 1800);
                            layout(panel);
                            for (JTextField field : fields) {
                                Insets insets = field.getInsets();
                                int textWidth = field.getWidth() - insets.left - insets.right;
                                assertTrue(textWidth >= field.getFontMetrics(field.getFont()).stringWidth("0000"),
                                        voyage + ", font " + fontSize + ", width " + width + ": squeezed editor");
                                assertTrue(field.getX() >= 0 && field.getX() + field.getWidth() <= field.getParent().getWidth(),
                                        "editor extends outside the narrow panel");
                            }
                        }
                    }
                }
            } finally {
                previous.forEach(UIManager::put);
            }
        });
    }

    @Test
    void foragePointsUseOneExpandableHeadingAndNumberOnlyRows() throws Exception {
        List<VoyageRecords.Record> records = VoyageRecords.parse(Files.readString(Path.of("src/test/resources/voyage-individual-records.json")));
        SwingUtilities.invokeAndWait(() -> {
            JPanel panel = panel();
            List<JTextField> editors = new ArrayList<>();
            RecordSettingsLayout.addRecords(panel, "Individual", individual(records, "cursedIsles"), record -> {
                JTextField field = new JTextField(Integer.toString(record.value), 4);
                field.setToolTipText(VoyageRecords.recordLabel(record));
                editors.add(field);
                return field;
            });
            List<JToggleButton> toggles = toggles(panel);
            assertEquals(1, toggles.size());
            JToggleButton points = toggles.get(0);
            assertEquals("Points by forage", points.getText());
            assertFalse(points.isSelected());
            panel.setSize(160, 1800);
            layout(panel);
            int foldedHeight = panel.getPreferredSize().height;
            assertEquals(0, labels(panel).stream().filter(l -> l.getText().contains("Most points in one forage")).count());
            points.doClick();
            layout(panel);
            assertTrue(panel.getPreferredSize().height > foldedHeight);
            List<JLabel> numbered = labels(panel).stream().filter(l -> l.getText().matches("\\d+")).toList();
            assertEquals(17, numbered.size());
            for (int row = 0; row < numbered.size(); row++) assertEquals(Integer.toString(row + 1), numbered.get(row).getText());
            assertEquals(22, editors.size(), "folding must preserve every editable record");
        });
    }

    private static List<VoyageRecords.Record> individual(List<VoyageRecords.Record> records, String voyage) {
        Map<String, VoyageRecords.Record> unique = new java.util.LinkedHashMap<>();
        for (VoyageRecords.Record record : records)
            if (record.voyage.equals(voyage) && VoyageRecords.isIndividual(record))
                unique.putIfAbsent(VoyageRecords.recordLabel(record), record);
        return List.copyOf(unique.values());
    }

    private static JPanel panel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        return panel;
    }

    private static void layout(Container parent) {
        parent.doLayout();
        for (Component child : parent.getComponents())
            if (child instanceof Container) layout((Container) child);
    }

    private static List<JToggleButton> toggles(Container parent) {
        List<JToggleButton> result = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            if (child instanceof JToggleButton) result.add((JToggleButton) child);
            if (child instanceof Container) result.addAll(toggles((Container) child));
        }
        return result;
    }

    private static List<JLabel> labels(Container parent) {
        List<JLabel> result = new ArrayList<>();
        for (Component child : parent.getComponents()) {
            if (child instanceof JLabel) result.add((JLabel) child);
            if (child instanceof Container) result.addAll(labels((Container) child));
        }
        return result;
    }
}
