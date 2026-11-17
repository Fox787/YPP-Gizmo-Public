package main.java.general;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.swing.AbstractButton;
import javax.swing.JLabel;
import javax.swing.JTabbedPane;
import javax.swing.text.JTextComponent;

/** Reads the rendered wolf count from the selected, visible Ahoy tab. */
final class AhoyWolfCount {
	private static final String SUNSHINE_TAB = "com.threerings.yohoho.swing.SunshineTab";
	// SunshineTab highlights this index as Ahoy in the client sidebar.
	private static final int AHOY_INDEX = 4;
	private static final ClassValue<Optional<Field>> SELECTED_TAB = new ClassValue<>() {
		@Override protected Optional<Field> computeValue(Class<?> type) {
			try {
				Field selected = type.getDeclaredField("_selected");
				return selected.trySetAccessible() ? Optional.of(selected) : Optional.empty();
			} catch (NoSuchFieldException | SecurityException ignored) {
				return Optional.empty();
			}
		}
	};
	private static final String MULTILINE_LABEL = "com.threerings.piracy.swing.YoMultiLineLabel";
	private static final Pattern HTML_TAG = Pattern.compile("<[^>]*>");
	private static final Pattern PACK = Pattern.compile(
			"next\\s+pack\\s*:\\s*\\d+\\s+wolf[- ]kin\\s+and\\s+(\\d+)\\s+werewol(?:f|ves)\\b",
			Pattern.CASE_INSENSITIVE);

	private AhoyWolfCount() { }

	static int read(Container game) {
		if (game == null || !game.isShowing()) return -1;
		Component ahoy = selectedAhoy(game);
		if (ahoy == null) return -1;
		StringBuilder visible = new StringBuilder();
		appendVisibleText(ahoy, visible);
		var match = PACK.matcher(visible);
		if (!match.find()) return -1;
		try {
			return Integer.parseInt(match.group(1));
		} catch (NumberFormatException ignored) {
			return -1;
		}
	}

	private static Component selectedAhoy(Component component) {
		if (!component.isShowing()) return null;
		if (component instanceof JTabbedPane tabs) {
			int selected = tabs.getSelectedIndex();
			if (selected >= 0 && isAhoy(tabs.getTitleAt(selected))) return tabs.getSelectedComponent();
		}
		if (component instanceof AbstractButton button && button.isSelected() && isAhoy(button.getText())) {
			Container strip = button.getParent();
			return strip == null ? null : strip.getParent();
		}
		if (component instanceof Container container) {
			// SunshineTab mounts only the selected page in the center.
			if (container.getClass().getName().equals(SUNSHINE_TAB)) return sunshineAhoy(container);
			for (Component child : container.getComponents()) {
				Component found = selectedAhoy(child);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static Component sunshineAhoy(Container tabs) {
		Field selected = SELECTED_TAB.get(tabs.getClass()).orElse(null);
		if (selected == null || !(tabs.getLayout() instanceof BorderLayout layout)) return null;
		try {
			return selected.getInt(tabs) == AHOY_INDEX ? layout.getLayoutComponent(BorderLayout.CENTER) : null;
		} catch (IllegalAccessException | IllegalArgumentException ignored) {
			return null;
		}
	}

	private static boolean isAhoy(String title) {
		return title != null && ("Ahoy".equalsIgnoreCase(title.trim()) || "Ahoy!".equalsIgnoreCase(title.trim()));
	}

	private static void appendVisibleText(Component component, StringBuilder visible) {
		if (!component.isShowing()) return;
		String text = component instanceof JLabel label ? label.getText()
				: component instanceof JTextComponent field ? field.getText() : multilineText(component);
		if (text != null) visible.append(' ').append(HTML_TAG.matcher(text).replaceAll(" ").replace("&nbsp;", " ").replace('\u00a0', ' '));
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) appendVisibleText(child, visible);
		}
	}

	private static String multilineText(Component component) {
		if (!component.getClass().getName().equals(MULTILINE_LABEL)) return null;
		try {
			// YoMultiLineLabel inherits w(), the getter for its rendered text.
			return component.getClass().getMethod("w").invoke(component) instanceof String text ? text : null;
		} catch (ReflectiveOperationException ignored) {
			return null;
		}
	}
}
