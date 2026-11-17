package com.threerings.piracy.swing;

import javax.swing.JComponent;

/** Mirrors the native label's rendered-text getter, which differs from JLabel. */
public final class YoMultiLineLabel extends JComponent {
	private final String renderedText;

	public YoMultiLineLabel(String text) { renderedText = text; }

	public String w() { return renderedText; }
}
