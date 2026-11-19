package main.java.general;

/**
 * Works out which activity a duty report is from (Cursed Isles, Vampire Lair or Wolves Den), and
 * reads the Lair carpentry counters for the voyage record checks (LairRecordChecks).
 *   Lair Carpentry tokens are three numbers scored -1, +1, +2: Holes = all three added up,
 *   Vampire Proof = the third (+2) number, Carp Score = -1*a + b + 2*c.
 */
public final class RecordChecker {

    public static final String CI = "CI", LAIR = "Lair", DEN = "Den";

    public static final String HAUL = "Treasure Haul", CARPENTRY = "Carpentry", FORAGE = "Foraging";

    private RecordChecker() {
    }

    /** CI, LAIR or DEN; null if it can't be told. Uses the scanner's tag when there is one. */
    public static String activityOf(DRGroupData dr) {
        if (dr == null) return null;
        if (dr.getActivity() != null) return dr.getActivity();
        if (dr.data.containsKey(HAUL)) return LAIR;
        if (dr.data.containsKey(CARPENTRY)) {
            for (DRIndividualData d : dr.data.get(CARPENTRY).values())
                if (hasThreeCounters(d)) return LAIR;
        }
        var navigators = dr.data.get("Navigating");
        if (navigators != null && navigators.values().stream().anyMatch(RecordChecker::hasThreeCounters)) return DEN;
        return null;
    }

    static String voyageOf(DRGroupData report) {
        String activity = activityOf(report);
        if (CI.equals(activity)) return "cursedIsles";
        if (LAIR.equals(activity)) return "vampireLairs";
        if (DEN.equals(activity)) return "woodhallow";
        return null;
    }

    /** Only the reports from this activity, in order. */
    public static DRPersistantData only(DRPersistantData all, String activity) {
        DRPersistantData result = new DRPersistantData();
        for (DRGroupData g : all.groups())
            if (activity.equals(activityOf(g))) result.add(g);
        return result;
    }

    // ---- Lair --------------------------------------------------------------------------------

    static boolean hasThreeCounters(DRIndividualData d) {
        return d.getTokens() != null && d.getTokens().length == 3;
    }

    static int carpScore(DRIndividualData d) {
        int[] t = d.getTokens();
        return -t[0] + t[1] + 2 * t[2];
    }

    static int carpHoles(DRIndividualData d) {
        int[] t = d.getTokens();
        return t[0] + t[1] + t[2];
    }

    static int vampireProof(DRIndividualData d) {
        return d.getTokens()[2];
    }
}
