package com.threerings.yohoho.swing;

import java.awt.BorderLayout;
import java.awt.Component;
import javax.swing.JPanel;

/** Mirrors the visible-page layout of the client's native sidebar. */
public final class SunshineTab extends JPanel {
	private int _selected = -1;

	public SunshineTab() { super(new BorderLayout()); }

	public void select(int index, Component page) {
		removeAll();
		_selected = index;
		add(page, BorderLayout.CENTER);
	}
}
