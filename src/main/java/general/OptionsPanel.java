package main.java.general;

import javax.swing.*;
import java.awt.*;
import java.util.prefs.Preferences;

/** The Options tab's scrolling sections and fixed save button. */
final class OptionsPanel extends JPanel {
	private final Preferences prefs;
	private final Color headingColor, outlineColor;
	private final SectionList sections = new SectionList();

	OptionsPanel(Preferences prefs, JButton saveButton) {
		super(new BorderLayout());
		this.prefs = prefs;
		Color labelColor = UIManager.getColor("Label.foreground");
		headingColor = labelColor == null ? getForeground() : labelColor;
		outlineColor = blend(getBackground(), headingColor, 0.80);

		JScrollPane scroll = new JScrollPane(sections, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
				ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(BorderFactory.createEmptyBorder());
		add(scroll, BorderLayout.CENTER);

		JPanel footer = new JPanel(new BorderLayout());
		Color separator = blend(getBackground(), getForeground(), 0.48);
		footer.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, separator),
				BorderFactory.createEmptyBorder(4, 6, 5, 6)));
		footer.add(saveButton, BorderLayout.EAST);
		add(footer, BorderLayout.SOUTH);
	}

	JPanel section(String title, String key, boolean initiallyOpen) {
		return section(sections, title, "options." + key + ".expanded", initiallyOpen);
	}

	JPanel section(String title, String key, boolean initiallyOpen, String help) {
		return section(sections, title, "options." + key + ".expanded", initiallyOpen, help);
	}

	/** Hide dependent controls without changing their saved values; also follows settings loaded in code. */
	static void showWhenSelected(AbstractButton toggle, JComponent... controls) {
		Runnable update = () -> {
			for (JComponent control : controls) {
				control.setVisible(toggle.isSelected());
				Container parent = control.getParent();
				if (parent != null) { parent.revalidate(); parent.repaint(); }
			}
		};
		toggle.addItemListener(event -> update.run());
		update.run();
	}

	/** A compact heading and body within a section, without another disclosure control. */
	static JPanel subsection(JPanel parent, String title) {
		JPanel subsection = new JPanel();
		subsection.setLayout(new BoxLayout(subsection, BoxLayout.Y_AXIS));
		subsection.setAlignmentX(LEFT_ALIGNMENT);
		subsection.setBorder(BorderFactory.createEmptyBorder(6, 0, 0, 0));
		JLabel heading = new JLabel(title);
		heading.setFont(heading.getFont().deriveFont(Font.BOLD));
		heading.setAlignmentX(LEFT_ALIGNMENT);
		subsection.add(heading);
		parent.add(subsection);
		return subsection;
	}

	JPanel section(JPanel parent, String title, String preference, boolean initiallyOpen) {
		return section(parent, title, preference, initiallyOpen, null);
	}

	JPanel section(JPanel parent, String title, String preference, boolean initiallyOpen, String help) {
		JPanel card = new JPanel(new BorderLayout());
		card.setAlignmentX(LEFT_ALIGNMENT);
		card.setBorder(BorderFactory.createLineBorder(outlineColor));

		boolean open = prefs.getBoolean(preference, initiallyOpen);
		JToggleButton header = new JToggleButton(title, new DisclosureIcon(false), open);
		HoverText.set(header, help);
		header.setSelectedIcon(new DisclosureIcon(true));
		header.setHorizontalAlignment(SwingConstants.LEADING);
		header.setBorder(BorderFactory.createEmptyBorder(3, 6, 3, 6));
		header.setFont(header.getFont().deriveFont(Font.BOLD));
		header.setIconTextGap(7);
		header.setContentAreaFilled(false);
		header.setOpaque(true);
		header.setForeground(headingColor);
		Color closedColor = blend(getBackground(), Color.BLACK, 0.22);
		Color openColor = blend(getBackground(), Color.BLACK, 0.12);
		header.setBackground(open ? openColor : closedColor);
		card.add(header, BorderLayout.NORTH);

		JPanel body = new JPanel();
		body.setLayout(new BoxLayout(body, BoxLayout.Y_AXIS));
		body.setBorder(BorderFactory.createEmptyBorder(4, 6, 5, 6));
		body.setVisible(open);
		card.add(body, BorderLayout.CENTER);

		header.addActionListener(event -> {
			body.setVisible(header.isSelected());
			header.setBackground(header.isSelected() ? openColor : closedColor);
			prefs.putBoolean(preference, header.isSelected());
			card.revalidate();
			card.repaint();
		});

		parent.add(card);
		parent.add(Box.createVerticalStrut(3));
		return body;
	}

	private static Color blend(Color background, Color foreground, double amount) {
		double base = 1 - amount;
		return new Color(
				(int) Math.round(background.getRed() * base + foreground.getRed() * amount),
				(int) Math.round(background.getGreen() * base + foreground.getGreen() * amount),
				(int) Math.round(background.getBlue() * base + foreground.getBlue() * amount));
	}

	private static final class SectionList extends JPanel implements Scrollable {
		SectionList() {
			setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
			setBorder(BorderFactory.createEmptyBorder(6, 6, 0, 6));
		}

		@Override public Dimension getPreferredScrollableViewportSize() { return new Dimension(230, 440); }
		@Override public int getScrollableUnitIncrement(Rectangle visible, int axis, int direction) { return 22; }
		@Override public int getScrollableBlockIncrement(Rectangle visible, int axis, int direction) { return visible.height; }
		@Override public boolean getScrollableTracksViewportWidth() { return true; }
		@Override public boolean getScrollableTracksViewportHeight() { return false; }
	}

	record DisclosureIcon(boolean open) implements Icon {
		@Override public int getIconWidth() { return 9; }
		@Override public int getIconHeight() { return 9; }

		@Override
		public void paintIcon(Component component, Graphics graphics, int x, int y) {
			Graphics2D g = (Graphics2D) graphics.create();
			try {
				g.translate(x, y);
				g.setColor(component.getForeground());
				g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				g.setStroke(new BasicStroke(1.4f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
				if (open) g.drawPolyline(new int[]{1, 4, 7}, new int[]{3, 6, 3}, 3);
				else g.drawPolyline(new int[]{3, 6, 3}, new int[]{1, 4, 7}, 3);
			} finally {
				g.dispose();
			}
		}
	}
}
