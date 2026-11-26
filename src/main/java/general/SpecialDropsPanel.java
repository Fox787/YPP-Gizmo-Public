package main.java.general;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

final class SpecialDropsPanel extends JPanel {
	private final Preferences prefs;
	private List<SpecialDrops.Rule> rules;
	private final DropTable model = new DropTable();
	private final JTable table;

	SpecialDropsPanel(Preferences prefs) {
		this.prefs = prefs;
		rules = SpecialDrops.load(prefs);
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setAlignmentX(LEFT_ALIGNMENT);

		table = new JTable(model);
		table.getAccessibleContext().setAccessibleName(SpecialDrops.TITLE);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getTableHeader().setReorderingAllowed(false);
		table.getColumnModel().getColumn(0).setPreferredWidth(110);
		JComboBox<SpecialDrops.Scope> scopes = new JComboBox<>(SpecialDrops.Scope.values());
		Dimension scopeSize = scopes.getPreferredSize();
		table.setRowHeight(Math.max(table.getRowHeight(), scopeSize.height));
		var alertColumn = table.getColumnModel().getColumn(1);
		int alertWidth = scopeSize.width + table.getIntercellSpacing().width;
		alertColumn.setMinWidth(alertWidth);
		alertColumn.setPreferredWidth(alertWidth);
		alertColumn.setCellEditor(new DefaultCellEditor(scopes));
		table.putClientProperty("terminateEditOnFocusLost", true);
		HoverText.set(table, "Click an Alert cell to choose whose wins Gizmo calls out on scan.");

		JScrollPane scroll = new JScrollPane(table);
		scroll.setPreferredSize(new Dimension(210, 125));
		scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 125));
		scroll.setAlignmentX(LEFT_ALIGNMENT);
		add(scroll);

		JButton add = new JButton("Add…");
		HoverText.set(add, "Match all or part of an item name, ignoring case. If rules overlap, the longest matching name wins.");
		add.addActionListener(event -> addRule());
		JButton remove = new JButton("Remove");
		remove.setEnabled(false);
		table.getSelectionModel().addListSelectionListener(event -> remove.setEnabled(table.getSelectedRow() >= 0));
		remove.addActionListener(event -> {
			int row = table.getSelectedRow();
			if (row < 0) return;
			List<SpecialDrops.Rule> updated = new ArrayList<>(rules);
			updated.remove(row);
			if (save(updated)) model.fireTableRowsDeleted(row, row);
		});
		JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 3));
		buttons.setAlignmentX(LEFT_ALIGNMENT);
		buttons.add(add);
		buttons.add(remove);
		add(buttons);
	}

	private void addRule() {
		String input = JOptionPane.showInputDialog(this, "Enter all or part of an item name from the booty tooltip.",
				"Add Booty Alert", JOptionPane.PLAIN_MESSAGE);
		if (input == null) return;
		String item = SpecialDrops.normalizeName(input);
		if (item.isEmpty()) return;
		for (int row = 0; row < rules.size(); row++) {
			if (item.equalsIgnoreCase(rules.get(row).item())) {
				select(row);
				return;
			}
		}
		int row = rules.size();
		List<SpecialDrops.Rule> updated = new ArrayList<>(rules);
		updated.add(new SpecialDrops.Rule(item, SpecialDrops.Scope.SHIPWIDE));
		if (save(updated)) {
			model.fireTableRowsInserted(row, row);
			select(row);
		}
	}

	private void select(int row) {
		table.setRowSelectionInterval(row, row);
		table.scrollRectToVisible(table.getCellRect(row, 0, true));
	}

	private boolean save(List<SpecialDrops.Rule> updated) {
		try {
			SpecialDrops.save(prefs, updated);
			rules = updated;
			return true;
		} catch (IllegalArgumentException | SecurityException e) {
			JOptionPane.showMessageDialog(this, "Could not save the " + SpecialDrops.TITLE + " settings.",
					SpecialDrops.TITLE, JOptionPane.ERROR_MESSAGE);
			return false;
		}
	}

	private final class DropTable extends AbstractTableModel {
		@Override public int getRowCount() { return rules.size(); }
		@Override public int getColumnCount() { return 2; }
		@Override public String getColumnName(int column) { return column == 0 ? "Item" : "Alert"; }
		@Override public boolean isCellEditable(int row, int column) { return column == 1; }

		@Override public Object getValueAt(int row, int column) {
			SpecialDrops.Rule rule = rules.get(row);
			return column == 0 ? rule.item() : rule.scope();
		}

		@Override public void setValueAt(Object value, int row, int column) {
			List<SpecialDrops.Rule> updated = new ArrayList<>(rules);
			updated.set(row, new SpecialDrops.Rule(rules.get(row).item(), (SpecialDrops.Scope) value));
			if (save(updated)) fireTableCellUpdated(row, column);
		}
	}
}
