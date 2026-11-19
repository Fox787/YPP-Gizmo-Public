package main.java.general;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Solo Dnav plan from the crew size and the fray, using the exact (unrounded) size of the wave:
 *   pack     = max(crew, 4) x 1.3^(fray - 1), not rounded
 *   total    = 200 points x pack x werewolf share (0.4 + 0.1 x fray)
 *   cleared  once less than half a werewolf (100 points) is left, so needed = total - 100
 * Boards are worth 71 to start, 125 after a Booch and 173 after a triple; a triple scores 3 x the
 * board, and a single (at the very end only) 1 x the board. Pieces broken on their own score nothing.
 * Checked against a crew-of-4 test: the werewolf bar's percentages, and the werewolves left
 * (remaining / 200, rounded to the nearest), match these totals.
 */
final class DnavPlan {
	static final double POINTS_PER_WEREWOLF = 200;
	static final int START_BOARD = 71, BOOCH_BOARD = 125, TRIPLE_BOARD = 173;
	static final int BOOCH_SECONDS = 10;

	private DnavPlan() { }

	/** The pack before rounding: the wave's real size uses it as it is. */
	static double pack(int crew, int fray) {
		return Math.max(crew, 4) * Math.pow(1.3, fray - 1);
	}

	static double share(int fray) { return 0.4 + 0.1 * fray; }

	/** The wave's real size in werewolves, e.g. 10.28. */
	static double werewolves(int crew, int fray) { return pack(crew, fray) * share(fray); }

	/** Points in the whole wave. */
	static double total(int crew, int fray) { return POINTS_PER_WEREWOLF * werewolves(crew, fray); }

	/** Points to turn every werewolf back: the last one goes once less than half of it is left. */
	static double needed(int crew, int fray) { return total(crew, fray) - POINTS_PER_WEREWOLF / 2; }

	/** One step of a line: what to do, when it's done (s from the start) and the points by then. */
	record Step(String what, int seconds, int points) { }

	/** Optional Booch, then triples, then optionally one single at the very end. */
	record Line(boolean booch, int triples, boolean single, int seconds, int points, List<Step> steps) {
		String name() {
			List<String> parts = new ArrayList<>();
			if (booch) parts.add("Booch");
			if (triples > 0) parts.add(triples + (triples == 1 ? " Trip" : " Trips"));
			if (single) parts.add("Single");
			return String.join(", ", parts);
		}

		String time() { return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60); }
	}

	static Line line(boolean booch, int triples, boolean single, int tripleSeconds, int singleSeconds) {
		List<Step> steps = new ArrayList<>();
		int board = booch ? BOOCH_BOARD : START_BOARD, points = 0, t = 0;
		if (booch) { t += BOOCH_SECONDS; steps.add(new Step("Booch", t, points)); }
		for (int i = 0; i < triples; i++) {
			points += 3 * board;
			t += tripleSeconds;
			board = TRIPLE_BOARD;
			steps.add(new Step("Triple " + (i + 1), t, points));
		}
		if (single) { points += board; t += singleSeconds; steps.add(new Step("Single", t, points)); }
		return new Line(booch, triples, single, t, points, List.copyOf(steps));
	}

	/**
	 * The fastest line that clears {@code needed}, then the fastest one that makes the other Booch
	 * choice (so the two options are always one with and one without a Booch).
	 */
	static List<Line> options(double needed, int tripleSeconds, int singleSeconds) {
		List<Line> clears = new ArrayList<>();
		for (boolean booch : new boolean[]{false, true})
			for (int n = 0; n <= 20; n++)
				for (boolean single : new boolean[]{false, true}) {
					if (n == 0 && !single) continue;
					Line l = line(booch, n, single, tripleSeconds, singleSeconds);
					if (l.points() >= needed) clears.add(l);
				}
		clears.sort((a, b) -> a.seconds() != b.seconds() ? Integer.compare(a.seconds(), b.seconds())
				: Integer.compare(b.points(), a.points()));
		if (clears.isEmpty()) return List.of();
		Line best = clears.get(0);
		for (Line l : clears) if (l.booch() != best.booch()) return List.of(best, l);
		return List.of(best);
	}
}
