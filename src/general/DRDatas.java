package general;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

public class DRDatas {
}


class DRIndividualData implements Serializable {
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

class DRGroupData implements Serializable {
	private static final long serialVersionUID = -1993883381083736794L;

	LinkedHashMap<String, LinkedHashMap<String, DRIndividualData>> data;

	public DRGroupData() {
		data = new LinkedHashMap<String, LinkedHashMap<String, DRIndividualData>>();
	}

	public String getBrokenRecords(int individual, int man1, int man2, int man3, int man4, int man5, int man6, int man7) {
		//String result = "", forage = "Foraging", newLine =  System.getProperty("line.separator"); //doesn't work in 1.6
		String result = "", forage = "Foraging", newLine =  "\n";
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
					case 7 : if (ccCount >= man7) {
						if (ccCount > man7)
							result += "We got " + ccCount + " CCs in one forage which beats the old record of " + man7 + "." + newLine;
						else
							result += "We got " + ccCount + " CCs in one forage which ties the current record. " + newLine;
					} break;
					case 6 : if (ccCount >= man6) {
						if (ccCount > man6)
							result += "We got " + ccCount + " CCs in one forage which beats the old record of " + man6 + "." + newLine;
						else
							result += "We got " + ccCount + " CCs in one forage which ties the current record. " + newLine;
					} break;
					case 5 : if (ccCount >= man5) {
						if (ccCount > man5)
							result += "We got " + ccCount + " CCs in one forage which beats the old record of " + man5 + "." + newLine;
						else
							result += "We got " + ccCount + " CCs in one forage which ties the current record. " + newLine;
					} break;
					case 4 : if (ccCount >= man4) {
						if (ccCount > man4)
							result += "We got " + ccCount + " CCs in one forage which beats the old record of " + man4 + "." + newLine;
						else
							result += "We got " + ccCount + " CCs in one forage which ties the current record. " + newLine;
					} break;
					case 3 : if (ccCount >= man3) {
						if (ccCount > man3)
							result += "We got " + ccCount + " CCs in one forage which beats the old record of " + man3 + "." + newLine;
						else
							result += "We got " + ccCount + " CCs in one forage which ties the current record. " + newLine;
					} break;
					case 2 : if (ccCount >= man2) {
						if (ccCount > man2)
							result += "We got " + ccCount + " CCs in one forage which beats the old record of " + man2 + "." + newLine;
						else
							result += "We got " + ccCount + " CCs in one forage which ties the current record. " + newLine;
					} break;
					case 1 : if (ccCount >= man1) {
						if (ccCount > man1)
							result += "You got " + ccCount + " CCs in one forage which beats the old record of " + man1 + "." + newLine;
						else
							result += "You got " + ccCount + " CCs in one forage which ties the current record. " + newLine;
					} break;
					default : break; //shouldn't get here
				}
			}
		}
		return result.trim();
	}

	public String getBrokenRecords(double individual, double man1, double man2, double man3, double man4, double man5, double man6, double man7) {
		//String result = "", forage = "Foraging", newLine =  System.getProperty("line.separator"); //doesn't work in 1.6
		String result = "", forage = "Foraging", newLine =  "\n";
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
				ccCount = Math.floor(ccCount * 100) /100;

				//see if we beat any records
				switch (size()) {
					case 7 : if (ccCount >= man7) {
						if (ccCount > man7)
							result += "We got " + ccCount + " Score in one forage which beats the old record of " + man7 + "." + newLine;
						else
							result += "We got " + ccCount + " Score in one forage which ties the current record. " + newLine;
					} break;
					case 6 : if (ccCount >= man6) {
						if (ccCount > man6)
							result += "We got " + ccCount + " Score in one forage which beats the old record of " + man6 + "." + newLine;
						else
							result += "We got " + ccCount + " Score in one forage which ties the current record. " + newLine;
					} break;
					case 5 : if (ccCount >= man5) {
						if (ccCount > man5)
							result += "We got " + ccCount + " Score in one forage which beats the old record of " + man5 + "." + newLine;
						else
							result += "We got " + ccCount + " Score in one forage which ties the current record. " + newLine;
					} break;
					case 4 : if (ccCount >= man4) {
						if (ccCount > man4)
							result += "We got " + ccCount + " Score in one forage which beats the old record of " + man4 + "." + newLine;
						else
							result += "We got " + ccCount + " Score in one forage which ties the current record. " + newLine;
					} break;
					case 3 : if (ccCount >= man3) {
						if (ccCount > man3)
							result += "We got " + ccCount + " Score in one forage which beats the old record of " + man3 + "." + newLine;
						else
							result += "We got " + ccCount + " Score in one forage which ties the current record. " + newLine;
					} break;
					case 2 : if (ccCount >= man2) {
						if (ccCount > man2)
							result += "We got " + ccCount + " Score in one forage which beats the old record of " + man2 + "." + newLine;
						else
							result += "We got " + ccCount + " Score in one forage which ties the current record. " + newLine;
					} break;
					case 1 : if (ccCount >= man1) {
						if (ccCount > man1)
							result += "You got " + ccCount + " Score in one forage which beats the old record of " + man1 + "." + newLine;
						else
							result += "You got " + ccCount + " Score in one forage which ties the current record. " + newLine;
					} break;
					default : break; //shouldn't get here
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


class DRPersistantData implements Serializable {

	private static final long serialVersionUID = -2006495106075743874L;

	private List<DRGroupData> data = new ArrayList<DRGroupData>();

	public DRPersistantData() {
	}

	public void add(DRGroupData d) {
		data.add(d);
	}

	public List<DRGroupData> getAll() {
		List<DRGroupData> result = new ArrayList<DRGroupData>();
		for (DRGroupData d : data) {
			result.add(d.copy());
		}
		return result;
	}

	public String getBrokenRecords(int individual, int man1, int man2, int man3, int man4, int man5, int man6, int man7) {
		String result = "", forage = "Foraging", newLine = System.getProperty("line.separator");
		DRPersistantData tempData = new DRPersistantData();
		//copy and take only forage so we can safely calculate for CCs only
		for (DRGroupData group : data) {
			tempData.add(group.get(forage).copy());
		}
		tempData.calculateCustomTokenScore(0, 0, 0, 0, 0, 0, 0, 0, 1);

		//create a master list

		//go through and get a master list (DRGroupData) of all participants and their stations
		DRGroupData masterGroupData = new DRGroupData();
		for (DRGroupData groupData : tempData.data) {
			for (String station : groupData.data.keySet()) {
				for (String pirate : groupData.data.get(station).keySet()) {
					masterGroupData.put(groupData.get(station, pirate));
				}
			}
		}

		//check for entry individual record
		for (String pirate : masterGroupData.data.get(forage).keySet()) {
			int ccCountInividual = 0;
			for (DRGroupData groupData : tempData.data) {
				if (groupData.contains(forage, pirate))
					ccCountInividual += groupData.get(forage, pirate).getTokenScore();
			}
			if (ccCountInividual >= individual) {
				if (ccCountInividual > individual) //beat
					result += (pirate + " got " + ccCountInividual + ""
							+ " CCs in one entry which beats the old record of " + individual + "." + newLine);
				else //tied
					result += (pirate + " got " + ccCountInividual + ""
							+ " CCs in one entry which ties the current record." + newLine);

			}

		}

		if (masterGroupData.size() <= 7) {
			//get the number of CCs
			int ccCount = 0;
			for (DRGroupData groupData : tempData.data)
				for (String pirate : groupData.data.get(forage).keySet())
					ccCount += groupData.get(forage, pirate).getTokenScore();

			//see if we beat any records
			switch (masterGroupData.size()) {
				case 7:
					if (ccCount >= man7) {
						if (ccCount > man7)
							result += "We got " + ccCount + " CCs in one entry which beats the old record of " + man7 + "." + newLine;
						else
							result += "We got " + ccCount + " CCs in one entry which ties the current record. " + newLine;
					}
					break;
				case 6:
					if (ccCount >= man6) {
						if (ccCount > man6)
							result += "We got " + ccCount + " CCs in one entry which beats the old record of " + man6 + "." + newLine;
						else
							result += "We got " + ccCount + " CCs in one entry which ties the current record. " + newLine;
					}
					break;
				case 5:
					if (ccCount >= man5) {
						if (ccCount > man5)
							result += "We got " + ccCount + " CCs in one entry which beats the old record of " + man5 + "." + newLine;
						else
							result += "We got " + ccCount + " CCs in one entry which ties the current record. " + newLine;
					}
					break;
				case 4:
					if (ccCount >= man4) {
						if (ccCount > man4)
							result += "We got " + ccCount + " CCs in one entry which beats the old record of " + man4 + "." + newLine;
						else
							result += "We got " + ccCount + " CCs in one entry which ties the current record. " + newLine;
					}
					break;
				case 3:
					if (ccCount >= man3) {
						if (ccCount > man3)
							result += "We got " + ccCount + " CCs in one entry which beats the old record of " + man3 + "." + newLine;
						else
							result += "We got " + ccCount + " CCs in one entry which ties the current record. " + newLine;
					}
					break;
				case 2:
					if (ccCount >= man2) {
						if (ccCount > man2)
							result += "We got " + ccCount + " CCs in one entry which beats the old record of " + man2 + "." + newLine;
						else
							result += "We got " + ccCount + " CCs in one entry which ties the current record. " + newLine;
					}
					break;
				case 1:
					if (ccCount >= man1) {
						if (ccCount > man1)
							result += "You got " + ccCount + " CCs in one entry which beats the old record of " + man1 + "." + newLine;
						else
							result += "You got " + ccCount + " CCs in one entry which ties the current record. " + newLine;
					}
					break;
				default:
					break; //shouldn't get here
			}
		}
		return result.trim();
	}

	public String getBrokenRecords(double individual, double man1, double man2, double man3, double man4, double man5, double man6, double man7) {
		String result = "", forage = "Foraging", newLine = System.getProperty("line.separator");
		DRPersistantData tempData = new DRPersistantData();
		//copy and take only forage so we can safely calculate for CCs only
		for (DRGroupData group : data) {
			tempData.add(group.get(forage).copy());
		}
		tempData.calculateCustomTokenScore(0, 0, 0, 0, 0, 0, 1, 2, 3);

		//create a master list

		//go through and get a master list (DRGroupData) of all participants and their stations
		DRGroupData masterGroupData = new DRGroupData();
		for (DRGroupData groupData : tempData.data) {
			for (String station : groupData.data.keySet()) {
				for (String pirate : groupData.data.get(station).keySet()) {
					masterGroupData.put(groupData.get(station, pirate));
				}
			}
		}

		//check for entry individual record
		for (String pirate : masterGroupData.data.get(forage).keySet()) {
			int ccCountInividual = 0;
			for (DRGroupData groupData : tempData.data) {
				if (groupData.contains(forage, pirate))
					ccCountInividual += groupData.get(forage, pirate).getTokenScore();
			}
			if (ccCountInividual >= individual) {
				if (ccCountInividual > individual) //beat
					result += (pirate + " got " + ccCountInividual + ""
							+ " Score in one entry which beats the old record of " + individual + "." + newLine);
				else //tied
					result += (pirate + " got " + ccCountInividual + ""
							+ " Score in one entry which ties the current record." + newLine);

			}

		}

		if (masterGroupData.size() <= 7) {
			//get the number of CCs
			double ccCount = 0;
			for (DRGroupData groupData : tempData.data)
				for (String pirate : groupData.data.get(forage).keySet())
					ccCount += groupData.get(forage, pirate).getTokenScore();

			// devide by ship size, and round to 2 decimal places
			ccCount = ccCount / masterGroupData.size();
			ccCount = Math.floor(ccCount * 100) /100;

			//see if we beat any records
			switch (masterGroupData.size()) {
				case 7:
					if (ccCount >= man7) {
						if (ccCount > man7)
							result += "We got " + ccCount + " Score in one entry which beats the old record of " + man7 + "." + newLine;
						else
							result += "We got " + ccCount + " Score in one entry which ties the current record. " + newLine;
					}
					break;
				case 6:
					if (ccCount >= man6) {
						if (ccCount > man6)
							result += "We got " + ccCount + " Score in one entry which beats the old record of " + man6 + "." + newLine;
						else
							result += "We got " + ccCount + " Score in one entry which ties the current record. " + newLine;
					}
					break;
				case 5:
					if (ccCount >= man5) {
						if (ccCount > man5)
							result += "We got " + ccCount + " Score in one entry which beats the old record of " + man5 + "." + newLine;
						else
							result += "We got " + ccCount + " Score in one entry which ties the current record. " + newLine;
					}
					break;
				case 4:
					if (ccCount >= man4) {
						if (ccCount > man4)
							result += "We got " + ccCount + " Score in one entry which beats the old record of " + man4 + "." + newLine;
						else
							result += "We got " + ccCount + " Score in one entry which ties the current record. " + newLine;
					}
					break;
				case 3:
					if (ccCount >= man3) {
						if (ccCount > man3)
							result += "We got " + ccCount + " Score in one entry which beats the old record of " + man3 + "." + newLine;
						else
							result += "We got " + ccCount + " Score in one entry which ties the current record. " + newLine;
					}
					break;
				case 2:
					if (ccCount >= man2) {
						if (ccCount > man2)
							result += "We got " + ccCount + " Score in one entry which beats the old record of " + man2 + "." + newLine;
						else
							result += "We got " + ccCount + " Score in one entry which ties the current record. " + newLine;
					}
					break;
				case 1:
					if (ccCount >= man1) {
						if (ccCount > man1)
							result += "You got " + ccCount + " Score in one entry which beats the old record of " + man1 + "." + newLine;
						else
							result += "You got " + ccCount + " Score in one entry which ties the current record. " + newLine;
					}
					break;
				default:
					break; //shouldn't get here
			}
		}
		return result.trim();
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
		return this.toString(true, true, true, "Emerald", false, false, true, false, true, false);
	}

	public String toString(boolean ignoreAI, boolean writeDate, boolean useTabs, String ocean, boolean writeStationHeaders,
						   boolean writeStation, boolean writePirate, boolean writeRating, boolean writeTokenScore, boolean writeTokenArray) {
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
					} else {
						//NA but NA for all options selected to keep tabs in order
						if (writeRating)
							sbTemp.append("NA" + seperator);
						if (writeTokenScore)
							sbTemp.append("NA" + seperator);
						if (writeTokenArray)
							sbTemp.append("NA" + seperator);
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
				if (station.contains("Foraging") || station.contains("Treasure Haul")){
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
						if (getIndexOf(pirateList,d.getName()) != -1){
							pirateList.get(getIndexOf(pirateList,d.getName())).scoreList.add(d.getTokenScore());
						}else {
							// Pirate doesn't exist in pirate list so add fresh
							pirateList.add(new Pair(d.getTokenScore(),d.getName(),0));
						}
					}
				}
			}
		}

		Comparator<Pair> rankPairs =  new Comparator<Pair>() {
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
		sbTemp.append("Averages ("+ childCount +" Frays Won):");
		sbTemp.append(System.getProperty("line.separator"));

		for (Pair pirate: pirateList) {
			sbTemp.append(pirate.getPirate()+ seperator + String.format("%.2f",pirate.getAverage()));
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
				if (station.contains("Foraging") || station.contains("Treasure Haul")){
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
								pirateList.add(new Pair(val, d.getName(),val));
							}
						}else {
							// if token array is null eg 0 is scored continue to add
							if (getIndexOf(pirateList, d.getName()) != -1) {
								pirateList.get(getIndexOf(pirateList, d.getName())).ccList.add(0);
								pirateList.get(getIndexOf(pirateList, d.getName())).scoreList.add(0);
							} else {
								// Pirate doesn't exist in pirate list so add fresh
								pirateList.add(new Pair(0, d.getName(),0));
							}
						}
					}
				}
			}
		}

		Comparator<Pair> rankPairs =  new Comparator<Pair>() {
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
		for (Pair pirate: pirateList) {
			total += pirate.getCCTotal();
		}

		if (ci){
			sbTemp.append(chestType + " Totals ("+ childCount +" Forages): "+ total);
		}else {
			sbTemp.append(chestType + " Totals ("+ childCount +" Hauls): " + total);
		}
		sbTemp.append(System.getProperty("line.separator"));
		for (Pair pirate: pirateList) {
			sbTemp.append(pirate.getPirate()+ seperator + pirate.getCCTotal());
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
				if (station.contains("Foraging") || station.contains("Treasure Haul")){
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
		Comparator<Pair> rankPairs =  new Comparator<Pair>() {
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
		for (Pair pirate: pirateList) {
			total += pirate.getCCTotal();
		}

		if (ci){
			sbTemp.append("Averages ("+ total +" CC) ("+ childCount +" Forages):");
		}else {
			sbTemp.append("Averages ("+ total +" IC)("+ childCount +" Hauls):");
		}
		sbTemp.append(System.getProperty("line.separator"));

		for (Pair pirate: pirateList) {
			sbTemp.append(pirate.getPirate()+ seperator + String.format("%.2f",pirate.getAverage()) + " (" + pirate.getCCTotal() + ")");
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
				if (station.contains("Carpentry")){
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
						if (getIndexOf(pirateList,d.getName()) != -1){
							pirateList.get(getIndexOf(pirateList,d.getName())).scoreList.add(d.getTokenScore());
						}else {
							// Pirate doesn't exist in pirate list so add fresh
							pirateList.add(new Pair(d.getTokenScore(),d.getName(),0));
						}
					}
				}
			}
		}

		Comparator<Pair> rankPairs =  new Comparator<Pair>() {
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
		sbTemp.append("Carp Avg: ("+ childCount +" Frays Won):");
		sbTemp.append(System.getProperty("line.separator"));

		for (Pair pirate: pirateList) {
			sbTemp.append(pirate.getPirate()+ seperator + String.format("%.2f",pirate.getAverage()));
			sbTemp.append(System.getProperty("line.separator"));
		}
		sb.append(sbTemp.toString().trim());
		return sb.toString();
	}

	private void readObject (ObjectInputStream aInputStream) throws ClassNotFoundException, IOException {
		aInputStream.defaultReadObject();
	}

	private void writeObject (ObjectOutputStream aOutputStream) throws IOException {
		aOutputStream.defaultWriteObject();
	}

	public class Pair implements Comparable<Pair>{
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
			return average/this.scoreList.size();
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

		for(Pair pair : list) {
			if(name.equalsIgnoreCase(pair.pirate))
				return pos;
			pos++;
		}

		return -1;
	}

}
