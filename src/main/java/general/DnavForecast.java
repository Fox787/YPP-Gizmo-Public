package main.java.general;

/**
 * During a Woodhallow rumble, a forecast for the Dnav stations that follow it, using the community
 * sheet's pack model as Thalassa implements it (werewolves.py pack_size / duty_seconds):
 *   wolves(people, fray)  = max(people, 4) x 1.3^(fray - 1)
 *   werewolves            = rounded pack x share (0.4 + 0.1 x fray), rounded half up
 *   Dnav score needed     = werewolves x 200 points / 125 points per score = 1.6 per werewolf
 *   (a navigator's triples after their first are worth 173 points per score point, not 125)
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
	static final double POINTS_PER_WEREWOLF = 200, POINTS_PER_SCORE = 125, LATER_TRIPLE_POINTS = 173;

	public static int dnavNeeded(int werewolves) {
		return (int) Math.ceil(werewolves * POINTS_PER_WEREWOLF / POINTS_PER_SCORE - 1e-9);
	}

	/**
	 * Werewolf points from one navigator's moons [singles, doubles, triples] (score 1, 2, 3 each).
	 * Every score point is worth 125, except that a navigator breaking mostly triples (at most one
	 * single, no doubles) gets 173 per score point for each triple after their first.
	 */
	public static double navPoints(int[] moons) {
		if (moons == null) return 0;
		int singles = moons.length > 0 ? moons[0] : 0, doubles = moons.length > 1 ? moons[1] : 0, triples = moons.length > 2 ? moons[2] : 0;
		double pts = (singles + 2.0 * doubles) * POINTS_PER_SCORE;
		// The 173 rate only for a navigator breaking (almost) only triples: at most one single and no
		// doubles. Anyone breaking more singles or any doubles gets 125 for everything.
		boolean triplesPlayer = singles <= 1 && doubles == 0;
		if (triples > 0) pts += triplesPlayer
				? 3 * POINTS_PER_SCORE + (triples - 1) * 3 * LATER_TRIPLE_POINTS
				: triples * 3 * POINTS_PER_SCORE;
		return pts;
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
		f.needed = dnavNeeded(f.werewolves);
		double neededPoints = f.werewolves * POINTS_PER_WEREWOLF;
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
		return target > 0 ? werewolves * POINTS_PER_WEREWOLF / POINTS_PER_SCORE / (target / 60_000.0) : 0;
	}

}
