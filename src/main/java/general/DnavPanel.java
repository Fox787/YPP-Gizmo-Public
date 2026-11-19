package main.java.general;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The main window's "Next Dnav" box (under the Symbols label): short labels, numbers in fixed spots, hover text on each.
 *   Wolves 10      Score 16
 *   Need   3.88    Now   -1.68
 *   Time   4:37    Left  -2:38
 *   Booch, 4 Trips          90%   (these two lines only with the Extended Dnav option)
 *   Booch, 4 Trips, Single  10%   (grey)
 */
public final class DnavPanel extends JPanel {

	private static final Color GREY = new Color(0x808080), LABEL = Color.WHITE, RED = new Color(0xe03030), GREEN = new Color(0x2e9e40);

	private final TitledBorder title = new TitledBorder(null, "Next Dnav", TitledBorder.LEADING, TitledBorder.TOP, null, null);
	private final JLabel wolves = value(), fray = value(), score = value(), need = value(), now = value(), time = value(), left = value();
	private final JLabel wolvesL = name("Wolves"), frayL = name("Fray"), scoreL = name("Score"), needL = name("Need"),
			nowL = name("Now"), timeL = name("Time"), leftL = name("Left");
	private final JLabel[] strat = {new JLabel(), new JLabel()};
	private final JLabel[] stratPct = {new JLabel(), new JLabel()};
	private final Color normal;
	private String shown;
	/** The two solo strategy lines are the "Extended Dnav" option (off by default: most crews don't solo nav). */
	private boolean extended;

	public void setExtended(boolean on) {
		extended = on;
		shown = null;   // the next show() lays the lines out again
		if (!on) for (int i = 0; i < 2; i++) { strat[i].setVisible(false); stratPct[i].setVisible(false); }
		revalidate();
	}

	public DnavPanel(Font font) {
		super(new GridBagLayout());
		setBorder(BorderFactory.createCompoundBorder(title, BorderFactory.createEmptyBorder(0, 3, 2, 3)));
		normal = UIManager.getColor("Label.foreground") != null ? UIManager.getColor("Label.foreground") : Color.BLACK;
		JLabel[][] grid = {
				{wolvesL, wolves, scoreL, score, null, null},
				{needL, need, nowL, now, null, null},
				{timeL, time, leftL, left, null, null}};
		for (int r = 0; r < grid.length; r++)
			for (int c = 0; c < 6; c++) {
				JLabel l = grid[r][c];
				if (l == null) continue;
				l.setFont(font.deriveFont(c % 2 == 1 ? Font.BOLD : Font.PLAIN));
				GridBagConstraints g = new GridBagConstraints();
				g.gridx = c; g.gridy = r;
				g.anchor = c % 2 == 1 ? GridBagConstraints.EAST : GridBagConstraints.WEST;
				g.insets = new Insets(0, c % 2 == 1 ? 3 : (c == 0 ? 0 : 8), 0, 0);
				add(l, g);
			}
		for (int i = 0; i < 2; i++) {
			strat[i].setFont(font.deriveFont(Font.PLAIN));   // plain so the longest strategy fits the 250 px window
			stratPct[i].setFont(font.deriveFont(i == 0 ? Font.BOLD : Font.PLAIN));
			if (i == 1) { strat[i].setForeground(GREY); stratPct[i].setForeground(GREY); }
			// Chance first (fixed spot under the labels), then the strategy across the rest of the width.
			GridBagConstraints g = new GridBagConstraints();
			g.gridx = 0; g.gridy = 3 + i; g.anchor = GridBagConstraints.WEST;
			add(stratPct[i], g);
			g = new GridBagConstraints();
			g.gridx = 1; g.gridy = 3 + i; g.gridwidth = 6; g.anchor = GridBagConstraints.WEST;
			g.insets = new Insets(0, 3, 0, 0);
			add(strat[i], g);
		}
		// A filler column so the grid sits on the left.
		GridBagConstraints g = new GridBagConstraints();
		g.gridx = 6; g.gridy = 0; g.weightx = 1;
		add(Box.createHorizontalGlue(), g);

		tip("Werewolves to turn back in the next Dnav", wolvesL, wolves);
		tip("The fray these stations prepare", frayL, fray);
		tip("Dnav score needed to turn them all back (1.6 per werewolf)", scoreL, score);
		tip("Score per minute the navigators need to finish with 0:30 to spare", needL, need);
		tip("<html>Current navigators' score per minute (last duty report) minus Need.<br>0 or more: they finish with the 0:30 safety net. Negative: add this much score/min.</html>", nowL, now);
		tip("Length of the stations (shorter for each pirate who dies this fray)", timeL, time);
		tip("<html>Time left on the stations when the current navigators would finish<br>(- means they'd run out of time)</html>", leftL, left);
	}

	private static JLabel value() { return new JLabel("-"); }
	private static JLabel name(String s) { JLabel l = new JLabel(s); l.setForeground(LABEL); return l; }
	private static void tip(String t, JLabel... ls) { for (JLabel l : ls) l.setToolTipText(t); }

	/** Shows the forecast and up to two solo lines (line -> its time), fastest first. */
	public void show(DnavForecast f, Map<String, String> strategies) {
		// Called every second: change nothing (so nothing repaints) unless the forecast did.
		String key = f.nextFray + "|" + f.werewolves + "|" + f.needed + "|" + f.stationsMs + "|" + f.teamRate + "|" + f.clearMs
				+ "|" + f.rawPack + "|" + f.odds + "|" + strategies;
		if (key.equals(shown)) return;
		shown = key;
		title.setTitle("Next Dnav");
		wolves.setText(String.valueOf(f.werewolves));
		StringBuilder odds = new StringBuilder();
		for (Map.Entry<Integer, Double> e : f.odds.entrySet())
			odds.append(odds.length() > 0 ? ", " : "").append(String.format("%d (%.0f%%)", e.getKey(), e.getValue() * 100));
		wolves.setToolTipText(String.format("<html>Werewolves the next Dnav will show: %s<br>Its real size is %.2f werewolves"
				+ " (pack of %.2f x %.0f%%):<br>%.0f points to clear, whatever count it shows</html>",
				odds, f.realWerewolves, f.rawPack, (0.4 + 0.1 * f.nextFray) * 100, f.neededPoints));
		fray.setText(String.valueOf(f.nextFray));
		score.setText(String.valueOf(f.needed));
		need.setText(String.format("%.2f", f.needRate()));
		time.setText(DnavForecast.mmss(f.stationsMs));
		boolean pace = f.teamRate > 0;
		if (pace) {
			double diff = f.teamRate - f.needRate();
			now.setText(String.format("%+.2f", diff));
			now.setForeground(diff < 0 ? RED : GREEN);
			long spare = f.stationsMs - f.clearMs;
			left.setText((spare < 0 ? "-" : "+") + DnavForecast.mmss(Math.abs(spare)));
			left.setForeground(spare < 0 ? RED : spare >= 30_000 ? GREEN : normal);
		} else {
			now.setText("-"); now.setForeground(normal);
			left.setText("-"); left.setForeground(normal);
		}
		int i = 0;
		for (Map.Entry<String, String> e : strategies.entrySet()) {
			if (i > 1 || !extended) break;
			strat[i].setText(e.getKey());
			stratPct[i].setText(e.getValue());
			strat[i].setToolTipText(i == 0 ? "Fastest solo line for this crew and fray (Options: triple and single times)"
					: "The fastest line with the other Booch choice");
			stratPct[i].setToolTipText("How long the line takes");
			strat[i].setVisible(true); stratPct[i].setVisible(true);
			i++;
		}
		for (; i < 2; i++) { strat[i].setVisible(false); stratPct[i].setVisible(false); }
	}

	/** Solo strategies for the next fray's werewolf count, with the chance of each, most likely first. */
	public static Map<String, Double> strategies(Map<Integer, Double> werewolfOdds) {
		Map<String, Double> m = new LinkedHashMap<>();
		for (Map.Entry<Integer, Double> e : werewolfOdds.entrySet()) add(m, e.getKey(), e.getValue());
		// Most likely first (counts with the same strategy were merged).
		Map<String, Double> sorted = new LinkedHashMap<>();
		m.entrySet().stream().sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
				.forEach(e -> sorted.put(e.getKey(), e.getValue()));
		return sorted;
	}

	private static void add(Map<String, Double> m, int werewolves, double p) {
		String s = SoloNavStrategy.forWerewolves(werewolves);
		if (s == null) s = werewolves + " werewolves: no strategy on the sheet";
		m.merge(s, p, Double::sum);
	}
}
