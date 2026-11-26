package main.java.general;

import javax.swing.*;
import java.awt.*;
import java.util.Map;
import java.util.TreeMap;

/**
 * Rumble punch table, ported from Thalassa's rumble.py. For each minute of the rumble it shows, per
 * number of charged groups, how many rows that punch sends: a stacked bar per group size (bottom up),
 * with the group count on the left and the expected drop-off inside. The shorthand is Thalassa's
 * "mini" text: the recommended group range for the current minute.
 *
 * Data per minute: charged groups -> {drop_off, rows, difference, base_width}; drop_off assumes
 * 3 balls per group.
 */
public final class RumbleBoard extends JComponent {

	static final Map<Integer, Map<Integer, int[]>> TABLE = new TreeMap<>();
	static final String[] SHORTHAND = {"15-16", "12-13", "10-11", "8-10", "7-9", "6-8", "6-7", "6-7",
			"6", "5", "5", "5", "4", "4", "4", "4"};

	private static void m(int minute, int... v) {
		Map<Integer, int[]> row = new TreeMap<>();
		for (int i = 0; i < v.length; i += 5) row.put(v[i], new int[]{v[i + 1], v[i + 2], v[i + 3], v[i + 4]});
		TABLE.put(minute, row);
	}

	static {
		m(0, 2,2,1,1,7, 3,6,2,1,5, 4,8,3,1,4, 6,6,4,1,4, 7,8,5,1,4, 8,8,6,1,4, 10,8,7,1,4, 11,10,9,2,3,
				12,12,11,2,3, 13,12,12,1,3, 14,12,14,2,3, 15,14,16,2,2, 16,16,18,2,2, 17,14,19,1,2, 18,14,20,1,2);
		m(1, 2,2,1,1,8, 3,4,2,1,6, 4,5,3,1,5, 5,5,4,1,4, 6,7,5,1,4, 7,7,6,1,4, 9,5,7,1,4, 10,10,11,4,3,
				11,10,12,1,3, 12,10,14,2,3, 13,12,16,2,3, 14,12,18,2,2, 15,12,20,2,2);
		m(2, 2,0,1,1,9, 3,6,3,2,4, 5,6,5,2,4, 6,6,6,1,4, 8,8,9,3,3, 9,8,11,2,3, 10,9,14,3,3, 11,10,16,2,2,
				12,10,18,2,2, 13,10,20,2,2);
		m(3, 2,4,2,2,5, 3,4,3,1,5, 4,4,4,1,5, 5,5,6,2,4, 7,7,9,3,3, 8,7,11,2,3, 9,8,14,3,3, 10,10,18,4,2,
				11,9,19,1,2, 12,9,20,1,2);
		m(4, 2,3,2,2,6, 3,3,3,1,6, 4,5,5,2,4, 5,4,6,1,4, 6,4,7,1,4, 7,6,11,4,3, 8,7,14,3,3, 9,8,18,4,2, 10,8,20,2,2);
		m(5, 2,4,3,3,4, 3,3,4,1,5, 4,4,6,2,4, 5,4,7,1,4, 6,6,11,4,3, 7,6,14,3,3, 8,7,18,4,2, 9,7,20,2,2);
		m(6, 2,3,3,3,5, 3,3,4,1,5, 4,4,6,2,4, 5,5,9,3,3, 6,6,12,3,3, 7,7,16,4,3, 8,7,19,3,2, 9,7,20,1,3);
		m(7, 2,3,3,3,5, 3,3,5,2,4, 4,3,6,1,5, 5,5,11,5,3, 6,5,14,3,3, 7,6,18,4,2, 8,6,20,2,3);
		m(8, 2,2,3,3,6, 3,3,5,2,5, 4,3,7,2,4, 5,5,12,5,3, 6,6,16,4,3, 7,6,20,4,2);
		m(9, 2,2,3,3,6, 3,3,6,3,4, 4,4,9,3,4, 5,5,14,5,3, 6,5,18,4,2, 7,5,20,2,3);
		m(10, 2,2,4,4,5, 3,2,6,2,5, 4,4,11,5,3, 5,5,16,5,3, 6,5,20,4,2);
		m(11, 2,2,4,4,5, 3,2,6,2,5, 4,4,11,5,3, 5,5,18,7,2, 6,5,20,2,3);
		m(12, 2,2,4,4,5, 3,2,7,3,4, 4,3,12,5,3, 5,5,19,7,2, 6,4,20,1,3);
		m(13, 2,2,5,5,5, 3,2,7,2,5, 4,4,14,7,3, 5,4,20,6,2);
		m(14, 2,2,5,5,5, 3,3,9,4,4, 4,4,16,7,3, 5,4,20,4,3);
		m(15, 2,2,6,6,4, 3,3,11,5,3, 4,4,18,7,2, 5,3,20,2,3);
	}

	private static final Color YELLOW = new Color(0xffd700), GOLD = new Color(0xdaa520),
			GREY = new Color(0x808080), LIGHT_GREY = new Color(0xd3d3d3), BG = new Color(0x2b2b2b);

	private int minute = 0;
	/** A Woodhallow fray: draw the dotted max-damage line. */
	private boolean werewolfFight;
	/** No elite werewolves left, so every enemy deals max damage: highlight the 75% window. */
	private boolean maxDamage;

	/** The recommended window under max damage is 75% of the usual one. */
	static final double MAX_DAMAGE_SHARE = 0.75;
	/**
	 * Rows max damage blocks: the client's BoxingBoard takes level x 5 x (21 - 1) / 600 rows, and
	 * standard max boarding damage is level 60, so 10 rows from the top (as Thalassa draws it).
	 */
	static final int MAX_DAMAGE_ROWS = 10;

	public void setWerewolfFight(boolean fight, boolean maxDamage) {
		maxDamage &= fight;
		if (fight != werewolfFight || maxDamage != this.maxDamage) {
			werewolfFight = fight;
			this.maxDamage = maxDamage;
			repaint();
		}
	}

	/** The highlighted window: more than 7 and fewer than 19 rows, or 75% of that under max damage. */
	static boolean inWindow(int rows, boolean maxDamage) {
		double k = maxDamage ? MAX_DAMAGE_SHARE : 1;
		return rows > 7 * k && rows < 19 * k;
	}

	public RumbleBoard() {
		setPreferredSize(new Dimension(150, 20 * ROW_H + 4));
		HoverText.set(this, "Groups charged (left), drop-off (inside). Dotted line: max damage (10 rows blocked) at Woodhallow. Yellow/orange: recommended window (75% of it once no elite werewolves are left).");
		setAlignmentX(Component.LEFT_ALIGNMENT);
		Font f = UIManager.getFont("Label.font");
		setFont(f != null ? f : new Font(Font.DIALOG, Font.PLAIN, 11));
	}

	static final int ROW_H = 9;

	/** Minute of the rumble, 0-15 (later minutes use 15). */
	public void setMinute(int m) {
		m = Math.max(0, Math.min(15, m));
		if (m != minute) { minute = m; repaint(); }
	}

	public static String shorthand(int minute) {
		return SHORTHAND[Math.max(0, Math.min(15, minute))];
	}

	/**
	 * The shorthand under max damage: the usual recommendation's rows scaled to 75%, as the groups
	 * that send that many rows this minute (the closest one if none falls inside).
	 */
	public static String shorthand(int minute, boolean maxDamage) {
		String usual = shorthand(minute);
		if (!maxDamage) return usual;
		Map<Integer, int[]> row = TABLE.get(Math.max(0, Math.min(15, minute)));
		String[] ends = usual.split("-");
		int lo = rowsFor(row, Integer.parseInt(ends[0])), hi = rowsFor(row, Integer.parseInt(ends[ends.length - 1]));
		double from = lo * MAX_DAMAGE_SHARE, to = hi * MAX_DAMAGE_SHARE, mid = (from + to) / 2;
		int first = -1, last = -1, closest = -1;
		double best = Double.MAX_VALUE;
		for (Map.Entry<Integer, int[]> e : row.entrySet()) {
			int rows = e.getValue()[1];
			if (rows >= Math.floor(from) && rows <= Math.ceil(to)) {
				if (first < 0) first = e.getKey();
				last = e.getKey();
			}
			if (Math.abs(rows - mid) < best) { best = Math.abs(rows - mid); closest = e.getKey(); }
		}
		if (first < 0) return String.valueOf(closest);
		return first == last ? String.valueOf(first) : first + "-" + last;
	}

	/**
	 * Insta knockout on a white name at Woodhallow. A punch as tall as the rows left free on the target's
	 * board fills it. White-named werewolves deal max damage, so 10 of the 20 rows are blocked (the white
	 * dotted line): 10 rows takes one out.
	 */
	static final int WHITE_INSTA_ROWS = 20 - MAX_DAMAGE_ROWS;

	/** The fewest groups that send at least this many rows this minute; -1 if none does. */
	public static int instaGroups(int minute, int rows) {
		for (Map.Entry<Integer, int[]> e : TABLE.get(Math.max(0, Math.min(15, minute))).entrySet())
			if (e.getValue()[1] >= rows) return e.getKey();
		return -1;
	}

	/**
	 * Woodhallow's shorthand as HTML: "(white-name insta) (recommended range)", the insta in white and the
	 * range in the label's own colour.
	 */
	public static String instaShorthand(int minute, boolean maxDamage) {
		return "<html><font color=#ffffff>(" + instaGroups(minute, WHITE_INSTA_ROWS) + ")</font> ("
				+ shorthand(minute, maxDamage) + ")</html>";
	}

	/** Rows sent by charging this many groups (the nearest smaller listed count if not listed). */
	private static int rowsFor(Map<Integer, int[]> row, int groups) {
		Map.Entry<Integer, int[]> e = ((TreeMap<Integer, int[]>) row).floorEntry(groups);
		if (e == null) e = ((TreeMap<Integer, int[]>) row).firstEntry();
		return e.getValue()[1];
	}

	@Override
	protected void paintComponent(Graphics g0) {
		Graphics2D g = (Graphics2D) g0.create();
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		// Draw inside any border (e.g. a titled one).
		Insets in = getInsets();
		int w = getWidth() - in.left - in.right, hgt = getHeight() - in.top - in.bottom;
		g.translate(in.left, in.top);
		g.setColor(BG);
		g.fillRect(0, 0, w, hgt);
		// Fill the space: 20 rows over the full height (at least the compact 9 px per row).
		int rowH = Math.max(ROW_H, (hgt - 4) / 20);
		float big = Math.max(9f, Math.min(16f, rowH * 0.8f)), small = Math.max(8f, Math.min(14f, rowH * 0.7f));
		int left = (int) (big * 2.2f), barW = Math.max(40, w - left - 4);
		int y = 2 + 20 * rowH;
		boolean colourGold = false, greyLight = false;
		Font groups = getFont().deriveFont(Font.BOLD, big), drop = getFont().deriveFont(small);
		for (Map.Entry<Integer, int[]> e : TABLE.get(minute).entrySet()) {
			int[] d = e.getValue();   // drop_off, rows, difference, base_width
			int h = d[2] * rowH;
			y -= h;
			Color c;
			if (inWindow(d[1], maxDamage)) { c = colourGold ? GOLD : YELLOW; colourGold = !colourGold; }
			else { c = greyLight ? LIGHT_GREY : GREY; greyLight = !greyLight; }
			g.setColor(c);
			g.fillRect(left, y, barW, h);
			g.setColor(Color.BLACK);
			g.drawRect(left, y, barW, h);
			g.setFont(groups);
			g.setColor(c);
			g.drawString(String.valueOf(e.getKey()), 1, y + Math.round(big));
			if (h >= small) {
				g.setFont(drop);
				g.setColor(Color.BLACK);
				g.drawString(String.valueOf(d[0]), left + 3, y + Math.round(small));
			}
		}
		if (werewolfFight) {
			// Max damage blocks the top 10 rows: a dotted line 10 rows down.
			int ly = 2 + MAX_DAMAGE_ROWS * rowH;
			g.setColor(Color.WHITE);
			g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{5f, 3f}, 0f));
			g.drawLine(left, ly, left + barW, ly);
		}
		g.dispose();
	}
}
