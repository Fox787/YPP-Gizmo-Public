package main.java.general;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** Compact record editors that reserve space for the values in a narrow Options panel. */
final class RecordSettingsLayout {
    private RecordSettingsLayout() { }

    static void addRecords(JPanel parent, String title, List<VoyageRecords.Record> records,
                           Function<VoyageRecords.Record, JTextField> editor) {
        if (records.isEmpty()) return;
        heading(parent, title);
        Map<String, VoyageRecords.Record[]> metrics = new LinkedHashMap<>();
        List<VoyageRecords.Record> carpentry = new ArrayList<>(), other = new ArrayList<>();
        Map<String, List<VoyageRecords.Record>> series = new LinkedHashMap<>();
        for (VoyageRecords.Record record : records) {
            String metric = metric(record);
            if (metric != null) {
                int column = record.kind.equals(VoyageRecords.CHESTS) || record.kind.equals(VoyageRecords.SCORE) ? 0 : 1;
                metrics.computeIfAbsent(metric, unused -> new VoyageRecords.Record[2])[column] = record;
            } else if (record.notes != null && !record.notes.isBlank()) {
                series.computeIfAbsent(record.category, unused -> new ArrayList<>()).add(record);
            } else if (record.kind.equals("carpScore") || record.kind.equals("carpHoles") || record.kind.equals("vampireProof")) {
                carpentry.add(record);
            } else other.add(record);
        }

        if (!metrics.isEmpty()) {
            boolean entry = metrics.values().stream().anyMatch(pair -> pair[0] != null);
            boolean dr = metrics.values().stream().anyMatch(pair -> pair[1] != null);
            JPanel table = table();
            List<JComponent> headers = new ArrayList<>();
            headers.add(new JLabel());
            if (entry) headers.add(new JLabel("Entry", SwingConstants.TRAILING));
            if (dr) headers.add(new JLabel("DR", SwingConstants.TRAILING));
            addRow(table, 0, headers.toArray(new JComponent[0]));
            int row = 1;
            for (var metric : metrics.entrySet()) {
                List<JComponent> cells = new ArrayList<>();
                VoyageRecords.Record[] pair = metric.getValue();
                cells.add(label(metric.getKey(), pair[0] != null ? pair[0] : pair[1]));
                if (entry) cells.add(value(metric.getValue()[0], editor));
                if (dr) cells.add(value(metric.getValue()[1], editor));
                addRow(table, row++, cells.toArray(new JComponent[0]));
            }
            finishTable(parent, table);
        }
        if (!carpentry.isEmpty()) {
            heading(parent, "Carpentry");
            JPanel table = table();
            int row = 0;
            for (VoyageRecords.Record record : carpentry) {
                String label = record.kind.equals("carpScore") ? "Score" : record.kind.equals("carpHoles") ? "Holes" : "VPs";
                addRow(table, row++, label(label, record), editor.apply(record));
            }
            finishTable(parent, table);
        }
        if (!other.isEmpty()) {
            JPanel table = table();
            int row = 0;
            for (VoyageRecords.Record record : other)
                addRow(table, row++, label(shortLabel(record), record), editor.apply(record));
            finishTable(parent, table);
        }
        for (var group : series.entrySet()) {
            boolean forages = group.getValue().stream().allMatch(r -> r.notes.matches("Forage \\d+"));
            String caption = "Most points in one forage, one pirate".equals(group.getKey()) ? "Points by forage"
                    : shortLabel(group.getValue().get(0));
            JPanel table = table();
            addRow(table, 0, new JLabel(forages ? "Forage" : "Record"), new JLabel(forages ? "Points" : "Value", SwingConstants.TRAILING));
            List<VoyageRecords.Record> ordered = new ArrayList<>(group.getValue());
            if (forages) ordered.sort(Comparator.comparingInt(r -> Integer.parseInt(r.notes.substring(7))));
            int row = 1;
            for (VoyageRecords.Record record : ordered)
                addRow(table, row++, label(forages ? record.notes.substring(7) : record.notes, record), editor.apply(record));
            table.setMaximumSize(new Dimension(Integer.MAX_VALUE, table.getPreferredSize().height));
            table.setVisible(false);
            JToggleButton toggle = new JToggleButton(caption);
            toggle.setHorizontalAlignment(SwingConstants.LEADING);
            toggle.setIcon(new OptionsPanel.DisclosureIcon(false));
            toggle.setSelectedIcon(new OptionsPanel.DisclosureIcon(true));
            toggle.setMargin(new Insets(3, 3, 3, 3));
            toggle.setAlignmentX(Component.LEFT_ALIGNMENT);
            toggle.setMaximumSize(new Dimension(Integer.MAX_VALUE, toggle.getPreferredSize().height));
            toggle.addActionListener(event -> {
                table.setVisible(toggle.isSelected());
                parent.revalidate();
                parent.repaint();
            });
            parent.add(Box.createVerticalStrut(4));
            parent.add(toggle);
            parent.add(table);
        }
    }

    private static String metric(VoyageRecords.Record record) {
        switch (record.kind) {
            case VoyageRecords.CHESTS: case VoyageRecords.DR_CHESTS:
                return record.voyage.equals("vampireLairs") ? "Immortals" : "Chests";
            case VoyageRecords.SCORE: case VoyageRecords.DR_SCORE:
                return record.voyage.equals("vampireLairs") ? "Chests" : "Score";
            case VoyageRecords.DR_NAV: return "Dnav";
            default: return null;
        }
    }

    private static String shortLabel(VoyageRecords.Record record) {
        return VoyageRecords.recordLabel(record).replaceFirst("^(Most|Best) ", "")
                .replace(", one pirate", "").replace("frays", "Frays")
                .replace("in one entry", "/ entry").replace("in one duty report", "/ DR")
                .replace("in one DR", "/ DR").replace("in one forage", "/ forage");
    }

    private static JLabel label(String text, VoyageRecords.Record record) {
        JLabel label = new JLabel(text);
        String help = switch (record.kind) {
            case "carpScore" -> "Best Lair Carpentry score in one DR.";
            case "carpHoles" -> "Most holes filled in one Lair Carpentry DR.";
            case "vampireProof" -> "Most VPs in one Lair Carpentry DR.";
            case VoyageRecords.DR_SCORE, VoyageRecords.SCORE -> "vampireLairs".equals(record.voyage)
                    ? "Most Treasure Haul chests across all three tiers." : VoyageRecords.recordLabel(record);
            case VoyageRecords.DR_NAV -> "Best Dnav score in one DR.";
            default -> VoyageRecords.recordLabel(record);
        };
        HoverText.set(label, help);
        return label;
    }

    private static JComponent value(VoyageRecords.Record record, Function<VoyageRecords.Record, JTextField> editor) {
        return record == null ? new JLabel("—", SwingConstants.TRAILING) : editor.apply(record);
    }

    private static JPanel table() {
        JPanel table = new JPanel(new GridBagLayout());
        table.setAlignmentX(Component.LEFT_ALIGNMENT);
        return table;
    }

    private static void heading(JPanel parent, String text) {
        if (parent.getComponentCount() > 0) parent.add(Box.createVerticalStrut(6));
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(Font.BOLD));
        parent.add(label);
    }

    private static void finishTable(JPanel parent, JPanel table) {
        table.setMaximumSize(new Dimension(Integer.MAX_VALUE, table.getPreferredSize().height));
        parent.add(table);
    }

    static void addRow(JPanel table, int row, JComponent... cells) {
        for (int column = 0; column < cells.length; column++) {
            JComponent cell = cells[column];
            if (column == 0) cell.setMinimumSize(new Dimension(0, cell.getPreferredSize().height));
            if (cell instanceof JTextField) sizeEditor((JTextField) cell);
            GridBagConstraints position = new GridBagConstraints();
            position.gridx = column;
            position.gridy = row;
            position.weightx = column == 0 ? 1 : 0;
            position.fill = GridBagConstraints.HORIZONTAL;
            position.insets = new Insets(1, 0, 1, column == cells.length - 1 ? 0 : 4);
            table.add(cell, position);
        }
    }

    static void sizeEditor(JTextField field) {
        Insets insets = field.getInsets();
        FontMetrics font = field.getFontMetrics(field.getFont());
        Dimension size = new Dimension(font.stringWidth("0000") + insets.left + insets.right + 2,
                Math.max(field.getPreferredSize().height, font.getHeight() + insets.top + insets.bottom));
        field.setPreferredSize(size);
        field.setMinimumSize(size);
    }
}
