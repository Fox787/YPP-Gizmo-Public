package main.java.general;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.PriorityQueue;

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

	/** A solo line with optional Booch combinations, scoring moves and a final single. */
	record Line(boolean booch, int donkeys, int triples, int doubles, int bingos, int vegases,
			boolean single, int seconds, int points, List<Step> steps) {
		String name() {
			List<String> parts = new ArrayList<>();
			String previous = null;
			int count = 0;
			for (Step step : steps) {
				String move = step.what().replaceFirst(" \\d+$", "");
				if (!move.equals(previous) && previous != null) { parts.add(moveName(previous, count)); count = 0; }
				previous = move;
				count++;
			}
			if (previous != null) parts.add(moveName(previous, count));
			return String.join(", ", parts);
		}

		String time() { return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60); }
	}

	private static String moveName(String move, int count) {
		if (move.equals("Single") || move.equals("Booch")) return move;
		if (move.startsWith("Booch-") && count == 1) return move;
		String label = move.equals("Triple") ? "Trip" : move;
		return count + " " + label + (count == 1 ? "" : move.equals("Booch-Vegas") ? "es" : "s");
	}

	static Line line(boolean booch, int triples, boolean single, int tripleSeconds, int singleSeconds) {
		return line(booch, 0, triples, 0, single, tripleSeconds, singleSeconds, 0, 0);
	}

	static Line line(boolean booch, int donkeys, int triples, boolean single, int tripleSeconds, int singleSeconds, int donkeySeconds) {
		return line(booch, donkeys, triples, 0, single, tripleSeconds, singleSeconds, donkeySeconds, 0);
	}

	/**
	 * {@code donkeys} Booch-donkeys first (each a Booch then a x5), then the triples, the doubles and the
	 * single. A double scores 2 x the board and leaves a 125 board, like a Booch (Jared, 21 Nov 2026).
	 */
	static Line line(boolean booch, int donkeys, int triples, int doubles, boolean single, int tripleSeconds, int singleSeconds,
					 int donkeySeconds, int doubleSeconds) {
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
		for (int i = 0; i < doubles; i++) {
			points += 2 * board;
			board = BOOCH_BOARD;
			t += doubleSeconds;
			steps.add(new Step("Double " + (i + 1), t, points));
		}
		if (single) { points += board; t += singleSeconds; steps.add(new Step("Single", t, points)); }
		return new Line(opening, donkeys, triples, doubles, 0, 0, single, t, points, List.copyOf(steps));
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
		return options(needed, tripleSeconds, singleSeconds, donkeySeconds, -1);
	}

	/** As above; a move whose time is -1 (left blank in Options) is never used. */
	static List<Line> options(double needed, int tripleSeconds, int singleSeconds, int donkeySeconds, int doubleSeconds) {
		List<Line> plain = new ArrayList<>(), donkey = new ArrayList<>();
		int maxTriples = tripleSeconds > 0 ? 40 : 0, maxDoubles = doubleSeconds > 0 ? 40 : 0;
		boolean[] singles = singleSeconds > 0 ? new boolean[]{false, true} : new boolean[]{false};
		for (int d = 0; d <= (donkeySeconds > 0 ? 12 : 0); d++)
			for (boolean booch : new boolean[]{false, true}) {
				if (d > 0 && booch) continue;   // a Booch-donkey already starts with its Booch
				for (int n = 0; n <= maxTriples; n++) {
					boolean cleared = false;
					for (int m = 0; m <= maxDoubles && !cleared; m++)   // once a count of doubles clears, more only add time
						for (boolean single : singles) {
							if (d == 0 && n == 0 && m == 0 && !single) continue;
							Line l = line(booch, d, n, m, single, Math.max(tripleSeconds, 0), Math.max(singleSeconds, 0),
									Math.max(donkeySeconds, 0), Math.max(doubleSeconds, 0));
							if (l.points() >= needed) { (d > 0 ? donkey : plain).add(l); cleared = true; }
						}
					if (cleared && maxDoubles == 0 && n > 0) break;   // more triples only add time too
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

	/** Bingo works on the current board; Donkey and Vegas each include their own preceding Booch. */
	static List<Line> options(double needed, int tripleSeconds, int singleSeconds, int donkeySeconds,
			int doubleSeconds, int bingoSeconds, int vegasSeconds) {
		return options(needed, tripleSeconds, singleSeconds, donkeySeconds, doubleSeconds, bingoSeconds, vegasSeconds, BOOCH_SECONDS);
	}

	static List<Line> options(double needed, int tripleSeconds, int singleSeconds, int donkeySeconds,
			int doubleSeconds, int bingoSeconds, int vegasSeconds, int boochSeconds) {
		if (bingoSeconds <= 0 && vegasSeconds <= 0 && boochSeconds == BOOCH_SECONDS)
			return options(needed, tripleSeconds, singleSeconds, donkeySeconds, doubleSeconds);
		if (!Double.isFinite(needed) || needed <= 0) return List.of();
		int[] times = {singleSeconds, doubleSeconds, tripleSeconds, bingoSeconds, donkeySeconds, vegasSeconds};
		var queue = new PriorityQueue<Search>((a, b) -> a.seconds != b.seconds
				? Integer.compare(a.seconds, b.seconds) : Integer.compare(b.points, a.points));
		Map<SearchKey, Integer> fastest = new HashMap<>();
		Search start = new Search(START_BOARD, 0, 0, false, false, null, null);
		queue.add(start);
		if (boochSeconds > 0) queue.add(new Search(BOOCH_BOARD, 0, boochSeconds, true, false, start, Move.BOOCH));
		Search[] best = new Search[3]; // plain, other Booch choice, and a line with Donkey/Vegas
		while (!queue.isEmpty()) {
			Search current = queue.remove();
			int category = current.special ? 2 : current.booch ? 1 : 0;
			if (best[category] != null && current.seconds > best[category].seconds) continue;
			SearchKey key = new SearchKey(current.board, current.points, current.booch, current.special);
			Integer seen = fastest.get(key);
			if (seen != null && seen < current.seconds) continue;
			if (current.points >= needed) {
				if (best[category] == null || current.seconds < best[category].seconds
						|| current.seconds == best[category].seconds && current.points > best[category].points)
					best[category] = current;
				continue;
			}
			for (Move move : Move.values()) {
				if (move == Move.BOOCH || times[move.ordinal()] <= 0) continue;
				boolean needsBooch = move == Move.DONKEY || move == Move.VEGAS;
				if (needsBooch && boochSeconds <= 0) continue;
				int points = current.points + move.multiplier * (needsBooch ? BOOCH_BOARD : current.board);
				if (move == Move.SINGLE && points < needed) continue; // singles finish the line
				int seconds = current.seconds + times[move.ordinal()] + (needsBooch ? boochSeconds : 0);
				Search next = new Search(move.board, points, seconds, current.booch,
						current.special || needsBooch, current, move);
				SearchKey nextKey = new SearchKey(next.board, next.points, next.booch, next.special);
				Integer previous = fastest.get(nextKey);
				if (previous == null || seconds < previous) { fastest.put(nextKey, seconds); queue.add(next); }
			}
		}
		List<Line> lines = new ArrayList<>();
		for (Search result : best) if (result != null) lines.add(result.line());
		lines.sort(java.util.Comparator.comparingInt(Line::seconds).thenComparing(
				java.util.Comparator.comparingInt(Line::points).reversed()));
		return List.copyOf(lines);
	}

	private enum Move {
		SINGLE(1, TRIPLE_BOARD, "Single"), DOUBLE(2, BOOCH_BOARD, "Double"),
		TRIPLE(3, TRIPLE_BOARD, "Triple"), BINGO(4, TRIPLE_BOARD, "Bingo"),
		DONKEY(5, TRIPLE_BOARD, "Booch-donkey"), VEGAS(6, TRIPLE_BOARD, "Booch-Vegas"),
		BOOCH(0, BOOCH_BOARD, "Booch");
		final int multiplier, board;
		final String label;
		Move(int multiplier, int board, String label) { this.multiplier = multiplier; this.board = board; this.label = label; }
	}

	private record SearchKey(int board, int points, boolean booch, boolean special) { }
	private record Search(int board, int points, int seconds, boolean booch, boolean special, Search previous, Move move) {
		Line line() {
			List<Search> path = new ArrayList<>();
			for (Search node = this; node.previous != null; node = node.previous) path.add(node);
			java.util.Collections.reverse(path);
			int[] counts = new int[Move.values().length];
			List<Step> steps = new ArrayList<>();
			for (Search node : path) {
				int count = ++counts[node.move.ordinal()];
				String label = node.move.label;
				if (node.move != Move.BOOCH && node.move != Move.SINGLE) label += " " + count;
				steps.add(new Step(label, node.seconds, node.points));
			}
			return new Line(booch, counts[Move.DONKEY.ordinal()], counts[Move.TRIPLE.ordinal()], counts[Move.DOUBLE.ordinal()],
					counts[Move.BINGO.ordinal()], counts[Move.VEGAS.ordinal()], counts[Move.SINGLE.ordinal()] > 0,
					seconds, points, List.copyOf(steps));
		}
	}
}
