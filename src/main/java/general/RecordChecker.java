package main.java.general;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Works out which activity a duty report is from (Cursed Isles, Vampire Lair or Wolves Den) and
 * checks the Lair records. The CI records are still checked by DRGroupData / DRPersistantData.
 *
 * Lair, as for CI: Scan checks one fray, Score All checks the whole entry (every DR in the tree).
 *   Treasure Haul tokens are [box, jar, chest]: ICs = the chest column, Chests = all three.
 *   Lair Carpentry tokens are three numbers scored -1, +1, +2: Holes = all three added up,
 *   Vampire Proof = the third (+2) number, Carp Score = -1*a + b + 2*c.
 */
public final class RecordChecker {

    public static final String CI = "CI", LAIR = "Lair", DEN = "Den";

    public static final String HAUL = "Treasure Haul", CARPENTRY = "Carpentry", FORAGE = "Foraging";

    /** Crew sizes the Lair crew records are kept for. */
    public static final int[] LAIR_CREW_SIZES = {12, 7, 6, 5, 4};

    private static final String NL = System.getProperty("line.separator");

    private RecordChecker() {
    }

    /** The current Lair records, as typed on the Records tab. Crew arrays follow LAIR_CREW_SIZES. */
    public static final class LairRecords {
        public final int[] crewICs = new int[LAIR_CREW_SIZES.length];
        public final int[] crewChests = new int[LAIR_CREW_SIZES.length];
        public int totalChests, fray, ic, carpScore, carpHoles, vampireProof;
    }

    /** CI, LAIR or DEN; null if it can't be told. Uses the scanner's tag when there is one. */
    public static String activityOf(DRGroupData dr) {
        if (dr == null) return null;
        if (dr.getActivity() != null) return dr.getActivity();
        if (dr.data.containsKey(HAUL)) return LAIR;
        if (dr.data.containsKey(CARPENTRY)) {
            for (DRIndividualData d : dr.data.get(CARPENTRY).values())
                if (d.getTokens() != null && d.getTokens().length == 3) return LAIR;
        }
        if (dr.data.containsKey(FORAGE)) return CI;
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

    static int ics(DRIndividualData d) {
        int[] t = d.getTokens();
        return t == null || t.length < 3 ? 0 : t[2];
    }

    static int chests(DRIndividualData d) {
        int[] t = d.getTokens();
        if (t == null) return 0;
        int n = 0;
        for (int i = 0; i < Math.min(3, t.length); i++) n += t[i];
        return n;
    }

    static boolean isLairCarp(DRIndividualData d) {
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

    /** Everyone on any station of these reports. */
    static int crewSize(Iterable<DRGroupData> reports) {
        Set<String> names = new LinkedHashSet<>();
        for (DRGroupData g : reports)
            for (Map<String, DRIndividualData> station : g.data.values()) names.addAll(station.keySet());
        return names.size();
    }

    /** One fray (Scan): Fray Record (one pirate's chests) and the three carp records. */
    public static String lairFray(DRGroupData dr, LairRecords r) {
        StringBuilder sb = new StringBuilder();
        if (dr.data.containsKey(HAUL)) {
            for (DRIndividualData d : dr.data.get(HAUL).values())
                compare(sb, d.getName() + " got", chests(d), "chests in one fray", r.fray);
        }
        if (dr.data.containsKey(CARPENTRY)) {
            for (DRIndividualData d : dr.data.get(CARPENTRY).values()) {
                if (!isLairCarp(d)) continue;
                compare(sb, d.getName() + " got", carpScore(d), "carp score in one fray", r.carpScore);
                compare(sb, d.getName() + " got", carpHoles(d), "carp holes in one fray", r.carpHoles);
                compare(sb, d.getName() + " got", vampireProof(d), "vampire proof in one fray", r.vampireProof);
            }
        }
        return sb.toString().trim();
    }

    /** Whole entry (Score All): crew ICs and Chests for the crew size, and each pirate's total chests and ICs. */
    public static String lairEntry(DRPersistantData lair, LairRecords r) {
        StringBuilder sb = new StringBuilder();
        List<DRGroupData> reports = lair.groups();

        Map<String, int[]> perPirate = new LinkedHashMap<>();   // pirate -> {chests, ics}
        int crewICs = 0, crewChests = 0;
        for (DRGroupData g : reports) {
            if (!g.data.containsKey(HAUL)) continue;
            for (DRIndividualData d : g.data.get(HAUL).values()) {
                int[] p = perPirate.computeIfAbsent(d.getName(), k -> new int[2]);
                p[0] += chests(d);
                p[1] += ics(d);
                crewChests += chests(d);
                crewICs += ics(d);
            }
        }
        for (Map.Entry<String, int[]> e : perPirate.entrySet()) {
            compare(sb, e.getKey() + " got", e.getValue()[0], "chests in one entry", r.totalChests);
            compare(sb, e.getKey() + " got", e.getValue()[1], "ICs in one entry", r.ic);
        }

        int crew = crewSize(reports);
        for (int i = 0; i < LAIR_CREW_SIZES.length; i++) {
            if (LAIR_CREW_SIZES[i] != crew) continue;
            compare(sb, "We got", crewICs, "ICs in one entry with " + crew + " pirates", r.crewICs[i]);
            compare(sb, "We got", crewChests, "chests in one entry with " + crew + " pirates", r.crewChests[i]);
        }
        return sb.toString().trim();
    }

    /** Adds a line when value beats or ties the record. A record of 0 counts as not set. */
    private static void compare(StringBuilder sb, String who, int value, String what, int record) {
        if (record <= 0 || value <= 0 || value < record) return;
        if (value > record)
            sb.append(who).append(' ').append(value).append(' ').append(what)
              .append(", which beats the old record of ").append(record).append('.').append(NL);
        else
            sb.append(who).append(' ').append(value).append(' ').append(what)
              .append(", which ties the current record.").append(NL);
    }
}
