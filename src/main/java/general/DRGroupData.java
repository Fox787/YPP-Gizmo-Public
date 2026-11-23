package main.java.general;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.LinkedHashMap;

public class DRGroupData implements Serializable {
    private static final long serialVersionUID = -1993883381083736794L;

    public LinkedHashMap<String, LinkedHashMap<String, DRIndividualData>> data;

    /**
     * Where this report came from: one of RecordChecker.CI / LAIR / DEN, or null when unknown
     * (reports saved before this was recorded). Set by the scanner, which knows whether it read
     * werewolf counters; RecordChecker.activityOf falls back to the stations when it's null.
     */
    private String activity;
    private long lairArrival, lairRoundEnd;

    public DRGroupData() {
        data = new LinkedHashMap<String, LinkedHashMap<String, DRIndividualData>>();
    }

    void setLairRound(long arrival, long roundEnd) {
        lairArrival = arrival;
        lairRoundEnd = roundEnd;
    }

    long lairArrival() { return lairArrival; }
    long lairRoundEnd() { return lairRoundEnd; }

    public String getActivity() {
        return activity;
    }

    public void setActivity(String activity) {
        this.activity = activity;
    }

    public void put(DRIndividualData individualData) {
        if (!data.containsKey(individualData.getStation()))
            data.put(individualData.getStation(), new LinkedHashMap<String, DRIndividualData>());

        LinkedHashMap<String, DRIndividualData> map = data.get(individualData.getStation());

        map.put(individualData.getName(), individualData);
    }

    public void put(String station, String name, String dr, int[] tokens, int tokenScore) {
        put(new DRIndividualData(station, name, dr, tokens, tokenScore));
    }

    public DRIndividualData get(String station, String name) {
        if (!data.containsKey(station)) return null;
        return data.get(station).get(name);
    }

    public DRGroupData get(String station) {
        if (!data.containsKey(station)) return null;
        DRGroupData result = new DRGroupData();
        for (String pirate : data.get(station).keySet())
            result.put(data.get(station).get(pirate));
        return result;
    }

    public void calculateCustomTokenScore(int circle, int diamond, int plus, int cross, int thrall,
                                          int cannonball, int smallChest, int mediumChest, int largeChest) {
        calculateCustomTokenScore(circle, diamond, plus, cross, thrall, cannonball, smallChest,
                mediumChest, largeChest, DRIndividualData.StationWeights.DEFAULT);
    }

    public void calculateCustomTokenScore(int circle, int diamond, int plus, int cross, int thrall,
                                          int cannonball, int smallChest, int mediumChest, int largeChest,
                                          DRIndividualData.StationWeights weights) {
        for (LinkedHashMap<String, DRIndividualData> m : data.values()) {
            for (DRIndividualData d : m.values()) {
                d.calculateCustomTokenScore(circle, diamond, plus, cross, thrall, cannonball, smallChest, mediumChest, largeChest, weights);
            }
        }
    }

    public boolean contains(DRIndividualData i) {
        if (data.containsKey(i.getStation()))
            return data.get(i.getStation()).containsKey(i.getName());
        return false;
    }

    public boolean contains(String station, String pirate) {
        if (data.containsKey(station))
            return data.get(station).containsKey(pirate);
        return false;
    }

    public int size() {
        int answer = 0;

        for (LinkedHashMap<String, DRIndividualData> m : data.values())
            answer += m.size();
        return answer;
    }

    public boolean isEmpty() {
        return data.isEmpty();
    }

    public DRGroupData copy() {
        DRGroupData result = new DRGroupData();
        result.activity = activity;
        result.setLairRound(lairArrival, lairRoundEnd);
        for (String station : data.keySet()) {
            for (String pirate : data.get(station).keySet()) {
                result.put(new DRIndividualData(data.get(station).get(pirate)));
            }
        }
        return result;
    }


    public String toString(boolean rating, boolean tokenScore, boolean tokenArray) {
        StringBuilder s = new StringBuilder();
        for (String station : data.keySet()) {
            s.append("---" + station + "---" + System.getProperty("line.separator"));
            for (String pirate : data.get(station).keySet()) {
                StringBuilder sTemp = new StringBuilder(); //this will let us .trim() at the end

                sTemp.append(pirate + " ");

                DRIndividualData d = data.get(station).get(pirate);
                if (rating)
                    sTemp.append(d.getRating() + " ");
                if ((tokenScore) && (d.getTokenScore() != 0))
                    sTemp.append(d.getTokenScore() + " ");
                if ((tokenArray) && (d.getTokens() != null))
                    sTemp.append(Arrays.toString(d.getTokens()) + " ");

                s.append(sTemp.toString().trim());
                s.append(System.getProperty("line.separator"));
            }
        }
        return s.toString().trim();
    }

    @Override
    public String toString() {
        return this.toString(false, false, false, "No Ocean", true, false, true, true, true, true, true);
    }


    public String toString(boolean ignoreAI, boolean writeDate, boolean useTabs, String ocean, boolean writeStationHeaders,
                           boolean writeStation, boolean writePirate, boolean writeRating, boolean writeTokenScore, boolean writeTokenArray, boolean writeSheetCombo) {
        return toString(ignoreAI, writeDate, useTabs, ocean, writeStationHeaders, writeStation, writePirate, writeRating,
                writeTokenScore, writeTokenArray, writeSheetCombo, null);
    }

    /** As above; with rates (station -> pirate -> score per minute), each line ends with "(rate)" when known. */
    public String toString(boolean ignoreAI, boolean writeDate, boolean useTabs, String ocean, boolean writeStationHeaders,
                           boolean writeStation, boolean writePirate, boolean writeRating, boolean writeTokenScore, boolean writeTokenArray, boolean writeSheetCombo,
                           java.util.Map<String, java.util.Map<String, Double>> rates) {
        StringBuilder s = new StringBuilder();

        String seperator = " ";
        if (useTabs)
            seperator = "\t";

        String date = new SimpleDateFormat("MM/dd/yyyy").format(new Date());

        for (String station : data.keySet()) {
            if (writeStationHeaders)
                s.append("---" + station + "---" + System.getProperty("line.separator"));
            for (String pirate : data.get(station).keySet()) {
                if (ignoreAI && pirate.contains(" "))
                    continue;

                StringBuilder sTemp = new StringBuilder(); //this will let us .trim() at the end

                if (writeDate)
                    sTemp.append(date + seperator);

                if (!ocean.equals("No Ocean"))
                    sTemp.append(ocean + seperator);

                DRIndividualData d = data.get(station).get(pirate);

                if (writeStation)
                    sTemp.append(d.getStation() + seperator);
                if (writePirate)
                    sTemp.append(d.getName() + seperator);
                if (writeRating)
                    sTemp.append(d.getRating() + seperator);
                if (writeTokenScore)
                    sTemp.append(d.getTokenScore() + seperator);
                if (writeTokenArray)
                    sTemp.append(Arrays.toString(d.getTokens()) + seperator);
                if (writeSheetCombo){
                    String fill = "   ";
                    if (d.getTokenScore() < 10){
                        fill = "     ";
                    }
                    sTemp.append(d.getTokenScore() + fill + Arrays.toString(d.getTokens()) + seperator);
                }
                if (rates != null && rates.containsKey(station) && rates.get(station).containsKey(pirate))
                    sTemp.append(String.format("(%.2f)", rates.get(station).get(pirate)) + seperator);

                s.append(sTemp.toString().trim());
                s.append(System.getProperty("line.separator"));
            }
        }
        return s.toString().trim();
    }

    private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException {
        aInputStream.defaultReadObject();
    }

    private void writeObject(ObjectOutputStream aOutputStream) throws IOException {
        aOutputStream.defaultWriteObject();
    }

}
