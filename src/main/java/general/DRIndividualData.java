package main.java.general;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Arrays;

public class DRIndividualData implements Serializable {
    private static final long serialVersionUID = 1302284232672156036L;

    private String station;
    private String name;
    private String rating;
    private int[] tokens;
    private int tokenScore;

    public DRIndividualData() {

    }

    public DRIndividualData(String station, String name, String rating, int[] tokens, int tokenScore) {
        this.station = station;
        this.name = name;
        this.rating = rating;
        if (tokens == null)
            this.tokens = null;
        else
            this.tokens = Arrays.copyOf(tokens, tokens.length);
        this.tokenScore = tokenScore;
    }

    public DRIndividualData(DRIndividualData data) {
        this.station = data.station;
        this.name = data.name;
        this.rating = data.rating;
        if (data.tokens == null)
            this.tokens = null;
        else
            this.tokens = Arrays.copyOf(data.tokens, data.tokens.length);
        this.tokenScore = data.tokenScore;
    }

    public String getStation() {
        return station;
    }

    public void setStation(String station) {
        this.station = station;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getRating() {
        return rating;
    }

    public void setRating(String rating) {
        this.rating = rating;
    }

    public int[] getTokens() {
        return tokens;
    }

    public void setTokens(int[] tokens) {
        this.tokens = tokens;
    }

    public int getTokenScore() {
        return tokenScore;
    }

    public void setTokenScore(int tokenScore) {
        this.tokenScore = tokenScore;
    }

    /**
     *
     * We currently use a single use method to calculate token scores as tokens exists in many forms, but we receive them from the same data packets,
     * it's easier to just handle all the logic in a singular place as for now we can differentiate activities based on what's included in the Duty Report
     *
     * EG, If a Carpentry Array is returned of Size 3, we know it's a Lair and can apply the custom scoring logic to it
     * This may break in future, depending on what puzzles are involved in the wolves den.
     *
     *
     *
     * @param circle
     * @param diamond
     * @param plus
     * @param cross
     * @param thrall
     * @param cannonball
     * @param smallChest
     * @param mediumChest
     * @param largeChest
     */
    public void calculateCustomTokenScore(int circle, int diamond, int plus, int cross, int thrall,
                                          int cannonball, int smallChest, int mediumChest, int largeChest) {
        if (tokens == null) {
            tokenScore = 0;
            return;
        }

        int result = 0;
        if (station.matches("Foraging")) {
            result += tokens[0] * smallChest;
            result += tokens[1] * mediumChest;
            result += tokens[2] * largeChest;
        } else if (station.matches("Treasure Haul")) {
            result = result + tokens[0];
            result += tokens[1];
            result += tokens[2];
        } else if (station.matches("Gunning")) {
            result += tokens[0] * cannonball;
        } else if (station.matches("Carpentry") && tokens.length == 3) {
            result += tokens[0] * -1;
            result += tokens[1];
            result += tokens[2] * 2;
        } else {
            result += tokens[0] * diamond;
            result += tokens[1] * circle;
            result += tokens[2] * plus;
            result += tokens[3] * cross;
            result += tokens[6] * thrall;
        }
        tokenScore = result;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(name + " ");
        sb.append(rating + " ");
        sb.append(tokenScore + " ");
        sb.append(Arrays.toString(tokens) + " ");
        return sb.toString().trim();
    }

    private void readObject(ObjectInputStream aInputStream) throws ClassNotFoundException, IOException {
        aInputStream.defaultReadObject();
    }

    private void writeObject(ObjectOutputStream aOutputStream) throws IOException {
        aOutputStream.defaultWriteObject();
    }

}
