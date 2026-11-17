package main.java.general;

import java.util.Map;
import java.util.TreeMap;

/**
 * Solo-nav strategy for a Duty Nav round, by the number of werewolves at the start of stations.
 * From the community Woodhallow sheet's "meta" tab, "Strat 1" column (as updated 12 Nov 2026;
 * "(?)" marks the ones the sheet isn't sure of):
 * docs.google.com/spreadsheets/d/1_fV796jU1h1udHrK909HLz_PWqoaYvSF9MkIxYnm7bE
 * Counts the sheet leaves blank have no entry.
 */
public final class SoloNavStrategy {

	private static final Map<Integer, String> STRAT_1 = new TreeMap<>();
	static {
		STRAT_1.put(3, "Booch, 1 Trip, Single");
		STRAT_1.put(4, "2 Trips");
		STRAT_1.put(5, "Booch, 2 Trips");
		STRAT_1.put(6, "3 Trips");
		STRAT_1.put(7, "Booch, 3 Trips");
		STRAT_1.put(8, "Booch, 3 Trips");
		STRAT_1.put(9, "(?) Booch, 4 Trips, Single");
		STRAT_1.put(10, "Booch, 4 Trips");
		STRAT_1.put(11, "Booch, 4 Trips, Single");
		STRAT_1.put(13, "(?) Booch, 5 Trips");
		STRAT_1.put(15, "(?) Booch, 6 Trips");
		STRAT_1.put(16, "(?) Booch, 6 Trips, Single");
		STRAT_1.put(18, "(?) Booch, 7 Trips");
		STRAT_1.put(19, "(?) Booch, 7 Trips, Single");
		STRAT_1.put(26, "Booch, 10 Trips");
	}

	private SoloNavStrategy() { }

	/** The strategy for this many werewolves, or null if the sheet has none. */
	public static String forWerewolves(int werewolves) {
		return STRAT_1.get(werewolves);
	}
}
