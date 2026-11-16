package main.java.general;

import java.awt.Component;
import java.awt.Container;
import java.util.regex.Pattern;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.text.JTextComponent;

/** Reads the rendered wolf count from the selected, visible Ahoy tab. */
final class AhoyWolfCount {
	private static final Pattern PACK = Pattern.compile(
			"next\\s+pack\\s*:\\s*\\d+\\s+wolf[- ]kin\\s+and\\s+(\\d+)\\s+werewol(?:f|ves)\\b",
			Pattern.CASE_INSENSITIVE);

	private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");

	private AhoyWolfCount() { }

	static int read(Container game) {
		if (game == null || !game.isShowing()) return -1;
		Component ahoy = selectedAhoy(game);
		if (ahoy == null) return -1;
		StringBuilder visible = new StringBuilder();
		appendVisibleText(ahoy, visible);
		var match = PACK.matcher(visible);
		return match.find() ? Integer.parseInt(match.group(1)) : -1;
	}

	private static Component selectedAhoy(Component component) {
		if (!component.isShowing()) return null;
		if (component instanceof JTabbedPane tabs) {
			int selected = tabs.getSelectedIndex();
			if (selected >= 0 && isAhoy(tabs.getTitleAt(selected))) return tabs.getSelectedComponent();
		}
		if (component instanceof AbstractButton button && button.isSelected() && isAhoy(button.getText())) {
			Container strip = button.getParent();
			return strip == null ? null : strip.getParent(); // The strip and its selected content share a container.
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				Component found = selectedAhoy(child);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static boolean isAhoy(String title) {
		return title != null && ("Ahoy".equalsIgnoreCase(title.trim()) || "Ahoy!".equalsIgnoreCase(title.trim()));
	}

	private static void appendVisibleText(Component component, StringBuilder visible) {
		if (!component.isShowing()) return;
		String text = component instanceof JLabel label ? label.getText()
				: component instanceof JTextComponent field ? field.getText() : null;
		if (text != null) visible.append(' ').append(HTML_TAG.matcher(text).replaceAll(" ").replace("&nbsp;", " ").replace('\u00a0', ' '));
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) appendVisibleText(child, visible);
		}
	}
}
