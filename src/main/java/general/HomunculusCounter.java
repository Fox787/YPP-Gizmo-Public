package main.java.general;

import java.awt.*;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.*;

/**
 * Thalassa's sword-colour to drop-colour counter, in the main window's Next Dnav area. Its Copy and Reset
 * buttons ({@link #actions()}) go in the Scan button's row and show with it.
 */
final class HomunculusCounter extends JPanel {
	private static final String[] SWORDS = {"White", "Yellow", "Purple", "Orange", "Blue", "Red", "Green", "Black"};
	private static final String[] DROPS = {"Blue", "Blue", "Green", "Green", "Red", "Red", "Yellow", "Yellow"};
	private static final int[] COLOURS = {0xffffff, 0xffe033, 0x800080, 0xcc8844, 0x0275d8, 0xd9534f, 0x5cb85c, 0x343a40};
	private final int[] counts = new int[SWORDS.length];
	private final JButton[] swords = new JButton[SWORDS.length];
	private final Map<String, JLabel> drops = new LinkedHashMap<>();
	/** The buttons' text colour: the yellow sword's. */
	static final Color TEXT = new Color(COLOURS[1]);
	/** The swords in the grid, row by row: four columns, each drop colour's two swords one above the other. */
	private static final int[] GRID_ORDER = {0, 2, 4, 6, 1, 3, 5, 7};
	private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.TRAILING, 4, 0));

	HomunculusCounter() {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBorder(BorderFactory.createEmptyBorder(3, 5, 4, 5));
		JLabel heading = new JLabel("Homs");
		heading.setAlignmentX(Component.LEFT_ALIGNMENT);
		add(heading);
		JButton copy = new JButton("Copy"), reset = new JButton("Reset");
		for (JButton button : new JButton[]{copy, reset}) {
			button.setMargin(new Insets(1, 6, 1, 6));
			button.setForeground(TEXT);
			Dimension size = new Dimension(button.getPreferredSize().width, 23);
			button.setPreferredSize(size);
			button.setMaximumSize(size);
			actions.add(button);
		}
		HoverText.set(copy, "Copy drop-colour totals as /ve text");
		copy.addActionListener(event -> copy(null));
		reset.addActionListener(event -> reset());
		actions.setMaximumSize(actions.getPreferredSize());
		actions.setVisible(false);
		JPanel grid = new JPanel(new GridLayout(2, 4, 3, 2));
		grid.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (int i : GRID_ORDER) {
			final int index = i;
			JButton button = swords[i] = new JButton();
			button.setMargin(new Insets(2, 2, 2, 2));
			button.setBackground(new Color(COLOURS[i]));
			button.setForeground(TEXT);
			button.setOpaque(true);
			button.setContentAreaFilled(true);
			HoverText.set(button, SWORDS[i] + " sword → " + DROPS[i].toLowerCase() + " drops. Click +1; right-click −1; middle-click copies target.");
			button.addActionListener(event -> adjust(index, 1));
			button.addMouseListener(new MouseAdapter() {
				@Override public void mousePressed(MouseEvent event) {
					if (SwingUtilities.isRightMouseButton(event)) adjust(index, -1);
					else if (SwingUtilities.isMiddleMouseButton(event)) copy(DROPS[index]);
				}
			});
			grid.add(button);
		}
		add(grid);
		JPanel totals = new JPanel(new GridLayout(1, 4, 2, 0));
		totals.setAlignmentX(Component.LEFT_ALIGNMENT);
		for (String colour : new String[]{"Blue", "Green", "Red", "Yellow"}) {
			JLabel label = new JLabel("", SwingConstants.CENTER);
			drops.put(colour, label);
			totals.add(label);
		}
		add(totals);
		refresh();
		setVisible(false);
	}

	/** Copy and Reset, for the Scan button's row; shown and hidden with the counter. */
	JComponent actions() { return actions; }

	@Override public void setVisible(boolean visible) {
		super.setVisible(visible);
		if (actions != null) actions.setVisible(visible);
	}

	void adjust(int index, int amount) { counts[index] = Math.max(0, counts[index] + amount); refresh(); }
	void reset() { java.util.Arrays.fill(counts, 0); refresh(); }

	Map<String, Integer> totals() {
		Map<String, Integer> totals = new LinkedHashMap<>();
		for (String drop : DROPS) totals.putIfAbsent(drop, 0);
		for (int i = 0; i < counts.length; i++) totals.merge(DROPS[i], counts[i], Integer::sum);
		return totals;
	}

	String copyText(String target) {
		StringBuilder text = new StringBuilder("/ve Homunculus Count:");
		totals().entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).forEach(entry -> {
			if (entry.getValue() > 0) text.append('\n').append(entry.getValue()).append(' ').append(entry.getKey())
					.append(entry.getKey().equals(target) ? " <-- attacking" : "");
		});
		return text.toString();
	}

	private void copy(String target) {
		Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(copyText(target)), null);
	}

	private void refresh() {
		for (int i = 0; i < counts.length; i++) swords[i].setText(SWORDS[i] + ": " + counts[i]);
		totals().forEach((colour, count) -> drops.get(colour).setText(colour + ": " + count));
	}
}
