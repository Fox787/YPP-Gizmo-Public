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
 *
 * Booch-donkey (worked out with Jared, 19 Nov 2026, and checked in a real Dnav): a Booch straight
 * into a x5 combo. The x5 always scores on the 125 board a Booch leaves (5 x 125 = 625) and leaves a
 * 4-2-1 board worth 173, like a triple. It can only come straight after a Booch, but a Booch-donkey can
 * come at any point and repeat, and triples can follow it. Since it scores the same wherever it goes
 * and every triple after one scores on 173, the fastest lines put the Booch-donkeys first.
 */
final class DnavPlan {
	static final double POINTS_PER_WEREWOLF = 200;
	static final int START_BOARD = 71, BOOCH_BOARD = 125, TRIPLE_BOARD = 173;
	static final int BOOCH_SECONDS = 9;
	static final int DONKEY_POINTS = 5 * BOOCH_BOARD;

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

	/** Optional Booch (or Booch-donkeys), then triples, then optionally one single at the very end. */
	record Line(boolean booch, int donkeys, int triples, boolean single, int seconds, int points, List<Step> steps) {
		String name() {
			List<String> parts = new ArrayList<>();
			if (donkeys > 0) parts.add(donkeys == 1 ? "Booch-donkey" : donkeys + " Booch-donkeys");
			else if (booch) parts.add("Booch");
			if (triples > 0) parts.add(triples + (triples == 1 ? " Trip" : " Trips"));
			if (single) parts.add("Single");
			return String.join(", ", parts);
		}

		String time() { return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60); }
	}

	static Line line(boolean booch, int triples, boolean single, int tripleSeconds, int singleSeconds) {
		return line(booch, 0, triples, single, tripleSeconds, singleSeconds, 0);
	}

	/** {@code donkeys} Booch-donkeys first (each a Booch then a x5), then the triples and the single. */
	static Line line(boolean booch, int donkeys, int triples, boolean single, int tripleSeconds, int singleSeconds, int donkeySeconds) {
		List<Step> steps = new ArrayList<>();
		int board = START_BOARD, points = 0, t = 0;
		for (int i = 0; i < donkeys; i++) {
			t += BOOCH_SECONDS + donkeySeconds;
			points += DONKEY_POINTS;
			board = TRIPLE_BOARD;
			steps.add(new Step(donkeys == 1 ? "Booch-donkey" : "Booch-donkey " + (i + 1), t, points));
		}
		boolean opening = booch && donkeys == 0;
		if (opening) { t += BOOCH_SECONDS; board = BOOCH_BOARD; steps.add(new Step("Booch", t, points)); }
		for (int i = 0; i < triples; i++) {
			points += 3 * board;
			t += tripleSeconds;
			board = TRIPLE_BOARD;
			steps.add(new Step("Triple " + (i + 1), t, points));
		}
		if (single) { points += board; t += singleSeconds; steps.add(new Step("Single", t, points)); }
		return new Line(opening, donkeys, triples, single, t, points, List.copyOf(steps));
	}

	/** The fastest line, then the fastest with the other Booch choice (no Booch-donkeys). */
	static List<Line> options(double needed, int tripleSeconds, int singleSeconds) {
		return options(needed, tripleSeconds, singleSeconds, -1);
	}

	/**
	 * Up to three lines, fastest first: the fastest without a Booch-donkey, the fastest that makes the
	 * other Booch choice, and (when {@code donkeySeconds} > 0) the fastest with at least one Booch-donkey.
	 */
	static List<Line> options(double needed, int tripleSeconds, int singleSeconds, int donkeySeconds) {
		List<Line> plain = new ArrayList<>(), donkey = new ArrayList<>();
		for (int d = 0; d <= (donkeySeconds > 0 ? 12 : 0); d++)
			for (boolean booch : new boolean[]{false, true}) {
				if (d > 0 && booch) continue;   // a Booch-donkey already starts with its Booch
				for (int n = 0; n <= 40; n++)
					for (boolean single : new boolean[]{false, true}) {
						if (d == 0 && n == 0 && !single) continue;
						Line l = line(booch, d, n, single, tripleSeconds, singleSeconds, Math.max(donkeySeconds, 0));
						if (l.points() >= needed) (d > 0 ? donkey : plain).add(l);
					}
			}
		java.util.Comparator<Line> fastest = (a, b) -> a.seconds() != b.seconds() ? Integer.compare(a.seconds(), b.seconds())
				: Integer.compare(b.points(), a.points());
		plain.sort(fastest);
		donkey.sort(fastest);
		List<Line> out = new ArrayList<>();
		if (!plain.isEmpty()) {
			Line best = plain.get(0);
			out.add(best);
			for (Line l : plain) if (l.booch() != best.booch()) { out.add(l); break; }
		}
		if (!donkey.isEmpty()) out.add(donkey.get(0));
		out.sort(fastest);
		return out;
	}
}
