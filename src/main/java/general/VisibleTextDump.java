package main.java.general;

import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.text.JTextComponent;

/**
 * Debug: lists the showing text components in the game window whose text looks like a countdown
 * ("m:ss"), with their class and position in the component tree. Used to find whether the Den / Lair
 * station countdown is readable text (public getText() of a showing component, no private fields)
 * or painted inside the scene.
 */
final class VisibleTextDump {

	private static final Pattern CLOCK = Pattern.compile("\\b\\d{1,2}:\\d{2}\\b");

	private VisibleTextDump() { }

	/** One line per showing JLabel / button / text component whose text contains m:ss. */
	static List<String> clocks(Component root) {
		List<String> out = new ArrayList<>();
		walk(root, "0", out, 0);
		return out;
	}

	private static void walk(Component c, String path, List<String> out, int depth) {
		if (c == null || !c.isShowing() || depth > 60) return;
		String text = c instanceof JLabel l ? l.getText()
				: c instanceof AbstractButton b ? b.getText()
				: c instanceof JTextComponent t ? t.getText() : null;
		if (text != null && CLOCK.matcher(text).find())
			out.add(path + "  " + c.getClass().getName() + "  \"" + text.replaceAll("\\s+", " ").trim() + "\"");
		if (c instanceof Container k)
			for (int i = 0; i < k.getComponentCount(); i++) walk(k.getComponent(i), path + "." + i, out, depth + 1);
	}
}
