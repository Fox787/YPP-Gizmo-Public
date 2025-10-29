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

    public DRGroupData() {
        data = new LinkedHashMap<String, LinkedHashMap<String, DRIndividualData>>();
    }

    public String getBrokenRecords(int individual, int man1, int man2, int man3, int man4, int man5, int man6, int man7) {
        //String result = "", forage = "Foraging", newLine =  System.getProperty("line.separator"); //doesn't work in 1.6
        String result = "", forage = "Foraging", newLine = "\n";
        DRGroupData tempData = new DRGroupData();
        if (data.containsKey(forage)) {

            //copy this to a new DRGroupData so we can calculate the score for CCs only
            for (String pirate : data.get(forage).keySet())
                tempData.put(new DRIndividualData(data.get(forage).get(pirate)));
            tempData.calculateCustomTokenScore(0, 0, 0, 0, 0, 0, 0, 0, 1);

            //check individuals for all
            for (String pirate : tempData.data.get(forage).keySet()) {
                if (tempData.get(forage, pirate).getTokenScore() >= individual) {
                    //if its bigger than the current record
                    if (tempData.get(forage, pirate).getTokenScore() > individual)
                        result += (pirate + " got " + tempData.get(forage, pirate).getTokenScore() + ""
                                + " CCs in one forage which beats the old record of " + individual + "." + newLine);
                    else //tied
                        result += (pirate + " got " + tempData.get(forage, pirate).getTokenScore() + ""
                                + " CCs in one forage which ties the current record." + newLine);
                }
            }

            if (size() <= 7) {
                //get the number of CCs
                int ccCount = 0;
                for (String pirate : tempData.data.get(forage).keySet())
                    ccCount += tempData.get(forage, pirate).getTokenScore();

                //see if we beat any records
                switch (size()) {
                    case 7:
                        if (ccCount >= man7) {
                            if (ccCount > man7)
                                result += "We got " + ccCount + " CCs in one forage which beats the old record of " + man7 + "." + newLine;
                            else
                                result += "We got " + ccCount + " CCs in one forage which ties the current record. " + newLine;
                        }
                        break;
                    case 6:
                        if (ccCount >= man6) {
                            if (ccCount > man6)
                                result += "We got " + ccCount + " CCs in one forage which beats the old record of " + man6 + "." + newLine;
                            else
                                result += "We got " + ccCount + " CCs in one forage which ties the current record. " + newLine;
                        }
                        break;
                    case 5:
                        if (ccCount >= man5) {
                            if (ccCount > man5)
                                result += "We got " + ccCount + " CCs in one forage which beats the old record of " + man5 + "." + newLine;
                            else
                                result += "We got " + ccCount + " CCs in one forage which ties the current record. " + newLine;
                        }
                        break;
                    case 4:
                        if (ccCount >= man4) {
                            if (ccCount > man4)
                                result += "We got " + ccCount + " CCs in one forage which beats the old record of " + man4 + "." + newLine;
                            else
                                result += "We got " + ccCount + " CCs in one forage which ties the current record. " + newLine;
                        }
                        break;
                    case 3:
                        if (ccCount >= man3) {
                            if (ccCount > man3)
                                result += "We got " + ccCount + " CCs in one forage which beats the old record of " + man3 + "." + newLine;
                            else
                                result += "We got " + ccCount + " CCs in one forage which ties the current record. " + newLine;
                        }
                        break;
                    case 2:
                        if (ccCount >= man2) {
                            if (ccCount > man2)
                                result += "We got " + ccCount + " CCs in one forage which beats the old record of " + man2 + "." + newLine;
                            else
                                result += "We got " + ccCount + " CCs in one forage which ties the current record. " + newLine;
                        }
                        break;
                    case 1:
                        if (ccCount >= man1) {
                            if (ccCount > man1)
                                result += "You got " + ccCount + " CCs in one forage which beats the old record of " + man1 + "." + newLine;
                            else
                                result += "You got " + ccCount + " CCs in one forage which ties the current record. " + newLine;
                        }
                        break;
                    default:
                        break; //shouldn't get here
                }
            }
        }
        return result.trim();
    }

    public String getBrokenRecords(double individual, double man1, double man2, double man3, double man4, double man5, double man6, double man7) {
        //String result = "", forage = "Foraging", newLine =  System.getProperty("line.separator"); //doesn't work in 1.6
        String result = "", forage = "Foraging", newLine = "\n";
        DRGroupData tempData = new DRGroupData();
        if (data.containsKey(forage)) {

            //copy this to a new DRGroupData so we can calculate the score for CCs only
            for (String pirate : data.get(forage).keySet()) {
                tempData.put(new DRIndividualData(data.get(forage).get(pirate)));
                tempData.calculateCustomTokenScore(0, 0, 0, 0, 0, 0, 1, 2, 3);
            }
            //check individuals for all
            for (String pirate : tempData.data.get(forage).keySet()) {
                if (tempData.get(forage, pirate).getTokenScore() >= individual) {
                    //if its bigger than the current record
                    if (tempData.get(forage, pirate).getTokenScore() > individual)
                        result += (pirate + " got " + tempData.get(forage, pirate).getTokenScore() + ""
                                + " Score in one forage which beats the old record of " + individual + "." + newLine);
                    else //tied
                        result += (pirate + " got " + tempData.get(forage, pirate).getTokenScore() + ""
                                + " Score in one forage which ties the current record." + newLine);
                }
            }


            if (size() <= 7) {
                //get the number of CCs
                double ccCount = 0;
                for (String pirate : tempData.data.get(forage).keySet()) {
                    ccCount += tempData.get(forage, pirate).getTokenScore();
                }
                // we need to Divide score by number of people on boat and round to 2 decimal places
                ccCount = ccCount / size();
                ccCount = Math.floor(ccCount * 100) / 100;

                //see if we beat any records
                switch (size()) {
                    case 7:
                        if (ccCount >= man7) {
                            if (ccCount > man7)
                                result += "We got " + ccCount + " Score in one forage which beats the old record of " + man7 + "." + newLine;
                            else
                                result += "We got " + ccCount + " Score in one forage which ties the current record. " + newLine;
                        }
                        break;
                    case 6:
                        if (ccCount >= man6) {
                            if (ccCount > man6)
                                result += "We got " + ccCount + " Score in one forage which beats the old record of " + man6 + "." + newLine;
                            else
                                result += "We got " + ccCount + " Score in one forage which ties the current record. " + newLine;
                        }
                        break;
                    case 5:
                        if (ccCount >= man5) {
                            if (ccCount > man5)
                                result += "We got " + ccCount + " Score in one forage which beats the old record of " + man5 + "." + newLine;
                            else
                                result += "We got " + ccCount + " Score in one forage which ties the current record. " + newLine;
                        }
                        break;
                    case 4:
                        if (ccCount >= man4) {
                            if (ccCount > man4)
                                result += "We got " + ccCount + " Score in one forage which beats the old record of " + man4 + "." + newLine;
                            else
                                result += "We got " + ccCount + " Score in one forage which ties the current record. " + newLine;
                        }
                        break;
                    case 3:
                        if (ccCount >= man3) {
                            if (ccCount > man3)
                                result += "We got " + ccCount + " Score in one forage which beats the old record of " + man3 + "." + newLine;
                            else
                                result += "We got " + ccCount + " Score in one forage which ties the current record. " + newLine;
                        }
                        break;
                    case 2:
                        if (ccCount >= man2) {
                            if (ccCount > man2)
                                result += "We got " + ccCount + " Score in one forage which beats the old record of " + man2 + "." + newLine;
                            else
                                result += "We got " + ccCount + " Score in one forage which ties the current record. " + newLine;
                        }
                        break;
                    case 1:
                        if (ccCount >= man1) {
                            if (ccCount > man1)
                                result += "You got " + ccCount + " Score in one forage which beats the old record of " + man1 + "." + newLine;
                            else
                                result += "You got " + ccCount + " Score in one forage which ties the current record. " + newLine;
                        }
                        break;
                    default:
                        break; //shouldn't get here
                }
            }
        }
        return result.trim();
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
        for (LinkedHashMap<String, DRIndividualData> m : data.values()) {
            for (DRIndividualData d : m.values()) {
                d.calculateCustomTokenScore(circle, diamond, plus, cross, thrall, cannonball, smallChest, mediumChest, largeChest);
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
        return this.toString(false, false, false, "No Ocean", true, false, true, true, true, true);
    }


    public String toString(boolean ignoreAI, boolean writeDate, boolean useTabs, String ocean, boolean writeStationHeaders,
                           boolean writeStation, boolean writePirate, boolean writeRating, boolean writeTokenScore, boolean writeTokenArray) {
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
