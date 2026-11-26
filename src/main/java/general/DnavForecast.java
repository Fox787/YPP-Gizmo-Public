package main.java.general;

/**
 * During a Woodhallow rumble, a forecast for the Dnav stations that follow it, using the community
 * sheet's pack model as Thalassa implements it (werewolves.py pack_size / duty_seconds):
 *   wolves(people, fray)  = max(people, 4) x 1.3^(fray - 1)
 *   werewolves            = rounded pack x share (0.4 + 0.1 x fray), rounded half up
 *   Dnav score needed     = werewolves x 200 points / 125 points per score = 1.6 per werewolf
 *   stations after fray n = 2:00 if n = 1, else 2:00 + 3:00 x survivors / people
 * The stations after fray n prepare fray n + 1. The team's pace is the navigators' score per
 * minute from the last duty report.
 */
public final class DnavForecast {

	public int nextFray, werewolves, needed;
	public long stationsMs;
	/** Navigators' combined score per minute from the last report; 0 if unknown. */
	public double teamRate;
	/** When the team would reach the needed score, from stations opening; 0 if no pace. */
	public long clearMs;
	/** The wave's real size in werewolves (unrounded, see DnavPlan), and the points it takes to clear it. */
	public double realWerewolves, neededPoints;

	/**
	 * The game rounds the count at random by its fraction: 14.1 is 14 nine times in ten and 15 one
	 * time in ten. Returns {lower count, chance of the lower count}.
	 */
	public static double[] rounding(double raw) {
		double lo = Math.floor(raw + 1e-9), frac = raw - lo;
		if (frac < 1e-6) return new double[]{lo, 1};
		return new double[]{lo, 1 - frac};
	}

	/** The pack before rounding, e.g. 13.52. */
	public double rawPack;
	/** Chance of each werewolf count, most likely first. */
	public java.util.Map<Integer, Double> odds;

	public static double packRaw(int people, int fray) {
		return Math.max(people, 4) * Math.pow(1.3, fray - 1);
	}

	/**
	 * Chance of each werewolf count. Past chat logs show the pack itself is a whole number rounded
	 * at random by its fraction (13.52 is 13 or 14). The werewolf share of that whole pack is then
	 * rounded to the nearest, halves up (checked against Emerald dens, Nov 2026).
	 */
	public static java.util.Map<Integer, Double> werewolfOdds(int people, int fray) {
		java.util.Map<Integer, Double> m = new java.util.HashMap<>();
		double share = 0.4 + 0.1 * fray;
		double[] p = rounding(packRaw(people, fray));
		for (int k = 0; k < 2; k++) {
			double pPack = k == 0 ? p[1] : 1 - p[1];
			if (pPack <= 0) continue;
			int pack = (int) p[0] + k;
			m.merge((int) Math.floor(pack * share + 0.5 + 1e-9), pPack, Double::sum);
		}
		java.util.Map<Integer, Double> sorted = new java.util.LinkedHashMap<>();
		m.entrySet().stream().filter(e -> e.getValue() > 1e-9)
				.sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
				.forEach(e -> sorted.put(e.getKey(), e.getValue()));
		return sorted;
	}

	/** Each werewolf takes 200 points to turn back; a Dnav score point is worth 125, so 1.6 per werewolf. */
	static final double POINTS_PER_WEREWOLF = 200, POINTS_PER_SCORE = DnavPlan.BOOCH_BOARD;
	private static final double TRIPLE_STRATEGY_POINTS_PER_SCORE = DnavPlan.TRIPLE_BOARD;

	public static int dnavNeeded(int werewolves) {
		return (int) Math.ceil(werewolves * POINTS_PER_WEREWOLF / POINTS_PER_SCORE - 1e-9);
	}

	/**
	 * The fewest werewolf points one navigator's moons [singles, doubles, triples] can be worth: the worst
	 * order, still starting with a Booch (a 125 board). A triple scores 3 x the board and leaves 173; a
	 * double or single scores 2 x / 1 x and leaves 125. So 2 singles + 4 triples is worth at least
	 * triple, single, triple, single, triple, triple = 1990.
	 */
	public static double navPoints(int[] moons) {
		if (moons == null) return 0;
		int singles = Math.max(0, moons.length > 0 ? moons[0] : 0);
		int doubles = Math.max(0, moons.length > 1 ? moons[1] : 0);
		int triples = Math.max(0, moons.length > 2 ? moons[2] : 0);
		// worst[s][d][t][b]: least points from s singles, d doubles and t triples on board b (0 = 125, 1 = 173).
		double[][][][] worst = new double[singles + 1][doubles + 1][triples + 1][2];
		double[] board = {POINTS_PER_SCORE, TRIPLE_STRATEGY_POINTS_PER_SCORE};
		for (int s = 0; s <= singles; s++)
			for (int d = 0; d <= doubles; d++)
				for (int t = 0; t <= triples; t++)
					for (int b = 0; b < 2; b++) {
						if (s + d + t == 0) continue;
						double best = Double.MAX_VALUE;
						if (t > 0) best = Math.min(best, 3 * board[b] + worst[s][d][t - 1][1]);
						if (d > 0) best = Math.min(best, 2 * board[b] + worst[s][d - 1][t][0]);
						if (s > 0) best = Math.min(best, board[b] + worst[s - 1][d][t][0]);
						worst[s][d][t][b] = best;
					}
		return worst[singles][doubles][triples][0];
	}

	/**
	 * Least points a lone navigator charted when the chat said {@code shown} werewolves were left for
	 * fray {@code fray}'s wave (0 after "turned back the last werewolf"). The wave is 200 x the pack x
	 * its share; the count shown is what's left / 200 rounded, and the last one goes under 100 left.
	 */
	public static double waveFloor(int crew, int fray, int shown) {
		double total = POINTS_PER_WEREWOLF * packRaw(crew, fray) * (0.4 + 0.1 * fray);
		return Math.max(0, shown == 0 ? total - 100 : total - POINTS_PER_WEREWOLF * (shown + 0.5));
	}

	public static long stationsMs(int people, int deaths, int fray) {
		if (fray <= 1 || people <= 0) return 120_000L;
		return 120_000L + Math.round(180_000.0 * Math.max(0, people - deaths) / people);
	}

	/**
	 * Forecast during fray {@code fray} (1-5) for a crew of {@code people} with {@code deaths} so far.
	 * {@code teamPointsPerMin} is the navigators' werewolf points per minute (see navPoints); rates
	 * shown are in score per minute (125 points to a score point).
	 */
	public static DnavForecast during(int fray, int people, int deaths, double teamPointsPerMin) {
		DnavForecast f = new DnavForecast();
		f.nextFray = fray + 1;
		f.rawPack = packRaw(people, f.nextFray);
		f.odds = werewolfOdds(people, f.nextFray);
		f.werewolves = f.odds.keySet().iterator().next();
		// The work is the wave's exact size, whatever count it shows (DnavPlan).
		f.realWerewolves = DnavPlan.werewolves(people, f.nextFray);
		f.neededPoints = DnavPlan.needed(people, f.nextFray);
		f.needed = (int) Math.ceil(f.neededPoints / POINTS_PER_SCORE - 1e-9);
		double neededPoints = f.neededPoints;
		f.stationsMs = stationsMs(people, deaths, fray);
		f.teamRate = teamPointsPerMin / POINTS_PER_SCORE;
		if (teamPointsPerMin > 0) f.clearMs = Math.round(neededPoints / teamPointsPerMin * 60_000);
		return f;
	}

	static String mmss(long ms) {
		long s = Math.abs(ms) / 1000;
		return (ms < 0 ? "-" : "") + s / 60 + ":" + String.format("%02d", s % 60);
	}

	/** Score per minute the whole team needs to finish with 30 s to spare. */
	public double needRate() {
		long target = stationsMs - 30_000;
		return target > 0 ? neededPoints / POINTS_PER_SCORE / (target / 60_000.0) : 0;
	}

}
