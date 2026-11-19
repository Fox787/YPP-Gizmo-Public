package main.java.general;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

public class DRPersistantData implements Serializable {

    private static final long serialVersionUID = -2006495106075743874L;

    private List<DRGroupData> data = new ArrayList<DRGroupData>();

    public DRPersistantData() {
    }

    public void add(DRGroupData d) {
        data.add(d);
    }

    /** The duty reports themselves (not copies), in order. */
    public List<DRGroupData> groups() {
        return data;
    }

    public List<DRGroupData> getAll() {
        List<DRGroupData> result = new ArrayList<DRGroupData>();
        for (DRGroupData d : data) {
            result.add(d.copy());
        }
        return result;
    }

    public void calculateCustomTokenScore(int circle, int diamond, int plus, int cross, int thrall,
                                          int cannonball, int smallChest, int mediumChest, int largeChest) {

        for (DRGroupData d : data) {
            d.calculateCustomTokenScore(circle, diamond, plus, cross, thrall, cannonball, smallChest, mediumChest, largeChest);
        }
    }

    public boolean isEmpty() {
        for (DRGroupData d : data) {
            if (!d.isEmpty())
                return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return this.toString(true, true, true, "Emerald", false, false, true, false, true, false, false);
    }

    public String toString(boolean ignoreAI, boolean writeDate, boolean useTabs, String ocean, boolean writeStationHeaders,
                           boolean writeStation, boolean writePirate, boolean writeRating, boolean writeTokenScore, boolean writeTokenArray, boolean writeSheetCombo) {
        //date, 		 pirate, ocean,
        //		station,				[rating, tokenScore, tokenArray]

        //go through and get a master list (DRGroupData) of all participants and their stations
        DRGroupData masterGroupData = new DRGroupData();
        for (DRGroupData groupData : data) {
            for (String station : groupData.data.keySet()) {
                for (String pirate : groupData.data.get(station).keySet()) {
                    if (ignoreAI && pirate.contains(" "))
                        continue;
                    masterGroupData.put(groupData.get(station, pirate));
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        String seperator = " ";
        if (useTabs)
            seperator = "\t";

        String date = new SimpleDateFormat("MM/dd/yyyy").format(new Date());

        for (String station : masterGroupData.data.keySet()) {
            if (writeStationHeaders)
                sb.append("---" + station + "---" + System.getProperty("line.separator"));
            for (String pirate : masterGroupData.data.get(station).keySet()) {
                StringBuilder sbTemp = new StringBuilder(); //this will let us .trim() at the end
                if (writeDate)
                    sbTemp.append(date + seperator);
                if (writeStation)
                    sbTemp.append(station + seperator);
                if (writePirate)
                    sbTemp.append(pirate + seperator);
                if (!ocean.equals("No Ocean"))
                    sbTemp.append(ocean + seperator);

                for (DRGroupData groupData : data) {
                    if (groupData.contains(station, pirate)) {
                        DRIndividualData d = groupData.data.get(station).get(pirate);
                        //append
                        if (writeRating)
                            sbTemp.append(d.getRating() + seperator);
                        if (writeTokenScore)
                            sbTemp.append(d.getTokenScore() + seperator);
                        if (writeTokenArray)
                            sbTemp.append(Arrays.toString(d.getTokens()) + seperator);
                        if (writeSheetCombo){
                            String fill = "   ";
                            if (d.getTokenScore() < 10){
                                fill = "     ";
                            }
                            sbTemp.append(d.getTokenScore() + fill + Arrays.toString(d.getTokens()) + seperator);
                        }
                    } else {
                        //NA but NA for all options selected to keep tabs in order
                        if (writeRating)
                            sbTemp.append("NA" + seperator);
                        if (writeTokenScore)
                            sbTemp.append("NA" + seperator);
                        if (writeTokenArray)
                            sbTemp.append("NA" + seperator);
                        if (writeSheetCombo){
                            sbTemp.append("NA" + seperator);
                        }
                    }
                }
                sb.append(sbTemp.toString().trim());
                sb.append(System.getProperty("line.separator"));
            }
        }
        return sb.toString();
    }

    public String toAvg(int childCount) {
        //go through and get a master list (DRGroupData) of all participants and their stations
        DRGroupData masterGroupData = new DRGroupData();
        for (DRGroupData groupData : data) {
            for (String station : groupData.data.keySet()) {
                if (station.contains("Foraging") || station.contains("Treasure Haul")) {
                    for (String pirate : groupData.data.get(station).keySet()) {
                        if (pirate.contains(" ")) {
                            continue;
                        }
                        masterGroupData.put(groupData.get(station, pirate));
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        String seperator = " ";

        // masterList only contains Forage and Haul Entries at this point
        List<Pair> pirateList = new ArrayList<Pair>();
        for (String station : masterGroupData.data.keySet()) {
            for (String pirate : masterGroupData.data.get(station).keySet()) {
                // adds pirates then attempts to sum all the scores the user has then returns the average
                for (DRGroupData groupData : data) {
                    if (groupData.contains(station, pirate)) {
                        DRIndividualData d = groupData.data.get(station).get(pirate);
                        // If Pirate already exists in Pirate list, then get the index of the pirate by name, and then add the token score to the score list
                        if (getIndexOf(pirateList, d.getName()) != -1) {
                            pirateList.get(getIndexOf(pirateList, d.getName())).scoreList.add(d.getTokenScore());
                        } else {
                            // Pirate doesn't exist in pirate list so add fresh
                            pirateList.add(new Pair(d.getTokenScore(), d.getName(), 0));
                        }
                    }
                }
            }
        }

        Comparator<Pair> rankPairs = new Comparator<Pair>() {
            public int compare(Pair p1, Pair p2) {
                if (p1.getAverage() > p2.getAverage())
                    return -1;
                else if (p1.getAverage() < p2.getAverage())
                    return 1;
                return 0;
            }
        };

        Collections.sort(pirateList, rankPairs);

        StringBuilder sbTemp = new StringBuilder();
        //Safe enough to assume size is the same for all.
        sbTemp.append("Averages (" + childCount + " Frays Won):");
        sbTemp.append(System.getProperty("line.separator"));

        for (Pair pirate : pirateList) {
            sbTemp.append(pirate.getPirate() + seperator + String.format("%.2f", pirate.getAverage()));
            sbTemp.append(System.getProperty("line.separator"));
        }
        sb.append(sbTemp.toString().trim());
        return sb.toString();
    }

    public String toAny(int arrayPositionOfChest, int childCount) {
        // local variable to determine if Ci or Lair
        boolean ci = true;


        //go through and get a master list (DRGroupData) of all participants and their stations
        DRGroupData masterGroupData = new DRGroupData();
        for (DRGroupData groupData : data) {
            for (String station : groupData.data.keySet()) {
                if (station.contains("Foraging") || station.contains("Treasure Haul")) {
                    ci = station.contains("Foraging");
                    for (String pirate : groupData.data.get(station).keySet()) {
                        if (pirate.contains(" ")) {
                            continue;
                        }
                        masterGroupData.put(groupData.get(station, pirate));
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        String seperator = " ";

        // masterList only contains Forage and Haul Entries at this point
        List<Pair> pirateList = new ArrayList<Pair>();
        for (String station : masterGroupData.data.keySet()) {
            for (String pirate : masterGroupData.data.get(station).keySet()) {
                // adds pirates then attempts to sum all the scores the user has then returns the average
                for (DRGroupData groupData : data) {
                    if (groupData.contains(station, pirate)) {
                        DRIndividualData d = groupData.data.get(station).get(pirate);
                        // If Pirate already exists in Pirate list, then get the index of the pirate by name, and then add the token score to the score list
                        // If people score 0 the token array is null, guard against it so method can work
                        if (d.getTokens() != null) {
                            int val = d.getTokens()[arrayPositionOfChest];
                            if (getIndexOf(pirateList, d.getName()) != -1) {
                                pirateList.get(getIndexOf(pirateList, d.getName())).ccList.add(val);
                                pirateList.get(getIndexOf(pirateList, d.getName())).scoreList.add(val);
                            } else {
                                // Pirate doesn't exist in pirate list so add fresh
                                pirateList.add(new Pair(val, d.getName(), val));
                            }
                        } else {
                            // if token array is null eg 0 is scored continue to add
                            if (getIndexOf(pirateList, d.getName()) != -1) {
                                pirateList.get(getIndexOf(pirateList, d.getName())).ccList.add(0);
                                pirateList.get(getIndexOf(pirateList, d.getName())).scoreList.add(0);
                            } else {
                                // Pirate doesn't exist in pirate list so add fresh
                                pirateList.add(new Pair(0, d.getName(), 0));
                            }
                        }
                    }
                }
            }
        }

        Comparator<Pair> rankPairs = new Comparator<Pair>() {
            public int compare(Pair p1, Pair p2) {
                if (p1.getScoreTotal() > p2.getScoreTotal())
                    return -1;
                else if (p1.getScoreTotal() < p2.getScoreTotal())
                    return 1;
                return 0;
            }
        };

        Collections.sort(pirateList, rankPairs);
        StringBuilder sbTemp = new StringBuilder();

        String chestType = arrayPositionOfChest == 0 ? "Box" : arrayPositionOfChest == 1 ? "Jar" : "Chest";
        // Display total of things obtained
        int total = 0;
        for (Pair pirate : pirateList) {
            total += pirate.getCCTotal();
        }

        if (ci) {
            sbTemp.append(chestType + " Totals (" + childCount + " Forages): " + total);
        } else {
            sbTemp.append(chestType + " Totals (" + childCount + " Hauls): " + total);
        }
        sbTemp.append(System.getProperty("line.separator"));
        for (Pair pirate : pirateList) {
            sbTemp.append(pirate.getPirate() + seperator + pirate.getCCTotal());
            sbTemp.append(System.getProperty("line.separator"));
        }
        sb.append(sbTemp.toString().trim());
        return sb.toString();
    }

    public String toCombined(int childCount) {
        // local variable to determine if Ci or Lair
        boolean ci = true;

        //go through and get a master list (DRGroupData) of all participants and their stations
        DRGroupData masterGroupData = new DRGroupData();
        for (DRGroupData groupData : data) {
            for (String station : groupData.data.keySet()) {
                if (station.contains("Foraging") || station.contains("Treasure Haul")) {
                    ci = station.contains("Foraging");
                    for (String pirate : groupData.data.get(station).keySet()) {
                        if (pirate.contains(" ")) {
                            continue;
                        }
                        masterGroupData.put(groupData.get(station, pirate));
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        String seperator = " ";

        // masterList only contains Forage and Haul Entries at this point
        List<Pair> pirateList = new ArrayList<Pair>();
        for (String station : masterGroupData.data.keySet()) {
            for (String pirate : masterGroupData.data.get(station).keySet()) {
                // adds pirates then attempts to sum all the scores the user has then returns the average
                for (DRGroupData groupData : data) {
                    if (groupData.contains(station, pirate)) {
                        DRIndividualData d = groupData.data.get(station).get(pirate);
                        // If Pirate already exists in Pirate list, then get the index of the pirate by name, and then add the token score to the score list
                        if (d.getTokens() != null) {
                            int val = d.getTokens()[2];
                            if (getIndexOf(pirateList, d.getName()) != -1) {
                                pirateList.get(getIndexOf(pirateList, d.getName())).scoreList.add(d.getTokenScore());
                                pirateList.get(getIndexOf(pirateList, d.getName())).ccList.add(val);
                            } else {
                                // Pirate doesn't exist in pirate list so add fresh
                                pirateList.add(new Pair(d.getTokenScore(), d.getName(), val));
                            }
                        } else {
                            // Even if the token array is null continue to add the pirate
                            if (getIndexOf(pirateList, d.getName()) != -1) {
                                pirateList.get(getIndexOf(pirateList, d.getName())).scoreList.add(d.getTokenScore());
                                pirateList.get(getIndexOf(pirateList, d.getName())).ccList.add(0);
                            } else {
                                // Pirate doesn't exist in pirate list so add fresh
                                pirateList.add(new Pair(d.getTokenScore(), d.getName(), 0));
                            }
                        }
                    }
                }
            }
        }
        Comparator<Pair> rankPairs = new Comparator<Pair>() {
            public int compare(Pair p1, Pair p2) {
                if (p1.getAverage() > p2.getAverage())
                    return -1;
                else if (p1.getAverage() < p2.getAverage())
                    return 1;
                return 0;
            }
        };

        Collections.sort(pirateList, rankPairs);

        StringBuilder sbTemp = new StringBuilder();
        // Display total of things obtained
        int total = 0;
        for (Pair pirate : pirateList) {
            total += pirate.getCCTotal();
        }

        if (ci) {
            sbTemp.append("Averages (" + total + " CC) (" + childCount + " Forages):");
        } else {
            sbTemp.append("Averages (" + total + " IC) (" + childCount + " Hauls):");
        }
        sbTemp.append(System.getProperty("line.separator"));

        for (Pair pirate : pirateList) {
            sbTemp.append(pirate.getPirate() + seperator + String.format("%.2f", pirate.getAverage()) + " (" + pirate.getCCTotal() + ")");
            sbTemp.append(System.getProperty("line.separator"));
        }
        sb.append(sbTemp.toString().trim());
        return sb.toString();
    }

    public String toCarpAvg(int childCount) {

        //go through and get a master list (DRGroupData) of all participants and their stations
        DRGroupData masterGroupData = new DRGroupData();
        for (DRGroupData groupData : data) {
            for (String station : groupData.data.keySet()) {
                if (station.contains("Carpentry")) {
                    for (String pirate : groupData.data.get(station).keySet()) {
                        if (pirate.contains(" ")) {
                            continue;
                        }
                        masterGroupData.put(groupData.get(station, pirate));
                    }
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        String seperator = " ";

        // masterList only contains LAIR Carp Entries at this point
        List<Pair> pirateList = new ArrayList<Pair>();
        for (String station : masterGroupData.data.keySet()) {
            for (String pirate : masterGroupData.data.get(station).keySet()) {
                // adds pirates then attempts to sum all the scores the user has then returns the average
                for (DRGroupData groupData : data) {
                    if (groupData.contains(station, pirate)) {
                        DRIndividualData d = groupData.data.get(station).get(pirate);
                        // If Pirate already exists in Pirate list, then get the index of the pirate by name, and then add the token score to the score list
                        if (getIndexOf(pirateList, d.getName()) != -1) {
                            pirateList.get(getIndexOf(pirateList, d.getName())).scoreList.add(d.getTokenScore());
                        } else {
                            // Pirate doesn't exist in pirate list so add fresh
                            pirateList.add(new Pair(d.getTokenScore(), d.getName(), 0));
                        }
                    }
                }
            }
        }

        Comparator<Pair> rankPairs = new Comparator<Pair>() {
            public int compare(Pair p1, Pair p2) {
                if (p1.getAverage() > p2.getAverage())
                    return -1;
                else if (p1.getAverage() < p2.getAverage())
                    return 1;
                return 0;
            }
        };

        Collections.sort(pirateList, rankPairs);

        StringBuilder sbTemp = new StringBuilder();
        //Safe enough to assume size is the same for all.
        sbTemp.append("Carp Avg: (" + childCount + " Frays Won):");
        sbTemp.append(System.getProperty("line.separator"));

        for (Pair pirate : pirateList) {
            sbTemp.append(pirate.getPirate() + seperator + String.format("%.2f", pirate.getAverage()));
            sbTemp.append(System.getProperty("line.separator"));
        }
        sb.append(sbTemp.toString().trim());
        return sb.toString();
    }

    private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException {
        aInputStream.defaultReadObject();
    }

    private void writeObject(ObjectOutputStream aOutputStream) throws IOException {
        aOutputStream.defaultWriteObject();
    }

    public class Pair implements Comparable<Pair> {
        private final ArrayList<Integer> scoreList;
        private String pirate;

        private final ArrayList<Integer> ccList;


        public Pair(int newScore, String pirate, int newCCs) {
            this.ccList = new ArrayList<Integer>();
            ccList.add(newCCs);
            this.scoreList = new ArrayList<Integer>();
            scoreList.add(newScore);
            this.pirate = pirate;
        }

        public double getAverage() {
            double average = getScoreTotal();
            return average / this.scoreList.size();
        }

        public int getScoreTotal() {
            int result = 0;
            for (Integer integer : this.scoreList) {
                result += integer;
            }
            return result;
        }

        public int getCCTotal() {
            int result = 0;
            for (Integer integer : this.ccList) {
                result += integer;
            }
            return result;
        }

        public String getPirate() {
            return pirate;
        }

        public void setPirate(String pirate) {
            this.pirate = pirate;
        }

        @Override
        public int compareTo(Pair p) {
            if (this.getScoreTotal() > p.getScoreTotal())
                return -1;
            else if (p.getScoreTotal() < this.getScoreTotal())
                return 1;
            return 0;
        }
    }

    // Returns index of given Pirate by Name
    public static int getIndexOf(List<Pair> list, String name) {
        int pos = 0;

        for (Pair pair : list) {
            if (name.equalsIgnoreCase(pair.pirate))
                return pos;
            pos++;
        }

        return -1;
    }

}
