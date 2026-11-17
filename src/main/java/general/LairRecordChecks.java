package main.java.general;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

final class LairRecordChecks {
	static final String VOYAGE = "vampireLairs";
	private static final String HAUL = "Treasure Haul", CARPENTRY = "Carpentry";
	private static final int[] NO_LOOT = new int[3];

	private LairRecordChecks() { }

	static List<String> fray(VoyageRecords records, DRGroupData report, String ocean, String time) {
		List<String> lines = new ArrayList<>();
		DRIndividualData best = null;
		int bestTotal = 0;
		boolean hasTotalRecord = records.get(VOYAGE, ocean, 0, VoyageRecords.DR_SCORE) != null;
		for (DRIndividualData pirate : station(report, HAUL).values()) {
			int[] tiers = tiers(pirate);
			if (tiers == null) continue;
			lines.add(records.compare(VOYAGE, ocean, 0, VoyageRecords.DR_CHESTS, tiers[0], time,
					List.of(pirate.getName()), tiers));
			int total = VoyageRecords.lootScore(VOYAGE, tiers);
			if (hasTotalRecord) lines.add(records.compare(VOYAGE, ocean, 0, VoyageRecords.DR_SCORE, total, time,
					List.of(pirate.getName()), tiers));
			if (total > bestTotal) { best = pirate; bestTotal = total; }
		}
		if (best != null && !hasTotalRecord) lines.add(best.getName() + " hauled " + bestTotal
				+ " total chests in one fray (no published benchmark).");

		for (DRIndividualData pirate : station(report, CARPENTRY).values()) {
			int[] t = pirate.getTokens();
			if (t == null || t.length != 3) continue;
			List<String> name = List.of(pirate.getName());
			lines.add(records.compare(VOYAGE, ocean, 0, "carpScore", RecordChecker.carpScore(pirate), time, name, NO_LOOT));
			lines.add(records.compare(VOYAGE, ocean, 0, "carpHoles", RecordChecker.carpHoles(pirate), time, name, NO_LOOT));
			lines.add(records.compare(VOYAGE, ocean, 0, "vampireProof", RecordChecker.vampireProof(pirate), time, name, NO_LOOT));
		}
		return lines;
	}

	/** Deduplicate rescans and require every logged fray and a matching three-tier haul. */
	static List<DRGroupData> verifiedRounds(List<DRGroupData> reports, StationClock.LairEntry entry) {
		if (entry == null || entry.rounds().isEmpty() || VoyageRecords.lootScore(VOYAGE, entry.haul()) == 0)
			return List.of();

		Map<Long, DRGroupData> rounds = new LinkedHashMap<>();
		for (DRGroupData report : reports) rounds.put(report.lairRoundEnd(), report);
		if (!rounds.keySet().equals(entry.rounds())) return List.of();

		int[] total = new int[3];
		for (DRGroupData report : rounds.values()) {
			for (DRIndividualData pirate : station(report, HAUL).values()) {
				int[] tiers = tiers(pirate);
				if (tiers == null) continue;
				for (int i = 0; i < tiers.length; i++) total[i] += tiers[i];
			}
		}
		return Arrays.equals(total, entry.haul()) ? List.copyOf(rounds.values()) : List.of();
	}

	static List<String> entry(VoyageRecords records, List<DRGroupData> rounds,
							  StationClock.LairEntry entry, String ocean) {
		Map<String, int[]> loot = new LinkedHashMap<>();
		TreeSet<String> crew = new TreeSet<>();
		for (DRGroupData report : rounds) {
			for (var station : report.data.values()) crew.addAll(station.keySet());
			for (DRIndividualData pirate : station(report, HAUL).values()) {
				int[] tiers = tiers(pirate);
				if (tiers == null) continue;
				int[] count = loot.computeIfAbsent(pirate.getName(), name -> new int[3]);
				for (int i = 0; i < tiers.length; i++) count[i] += tiers[i];
			}
		}
		int[] total = entry.haul();

		String time = LocalDateTime.ofInstant(Instant.ofEpochMilli(rounds.get(0).lairArrival()), ZoneId.systemDefault()).toString();
		List<String> lines = new ArrayList<>();
		lines.add("Vampire Lair entry " + time + ": " + rounds.size() + " frays, " + crew.size() + " pirates, "
				+ VoyageRecords.lootScore(VOYAGE, total) + " chests (" + total[0] + " immortal).");
		for (String pirate : crew) {
			int[] tiers = loot.get(pirate);
			if (tiers == null) continue;
			int count = VoyageRecords.lootScore(VOYAGE, tiers);
			lines.add(pirate + ": " + count + " chests (" + tiers[0] + " immortal).");
			if (records.get(VOYAGE, ocean, 0, VoyageRecords.CHESTS) != null)
				lines.add(records.compare(VOYAGE, ocean, 0, VoyageRecords.CHESTS, tiers[0], time, List.of(pirate), tiers));
			if (records.get(VOYAGE, ocean, 0, VoyageRecords.SCORE) != null)
				lines.add(records.compare(VOYAGE, ocean, 0, VoyageRecords.SCORE, count, time, List.of(pirate), tiers));
		}
		List<String> names = List.copyOf(crew);
		lines.add(records.compare(VOYAGE, ocean, crew.size(), VoyageRecords.CHESTS, total[0], time, names, total, loot));
		lines.add(records.compare(VOYAGE, ocean, crew.size(), VoyageRecords.SCORE, VoyageRecords.lootScore(VOYAGE, total),
				time, names, total, loot));
		return lines;
	}

	private static Map<String, DRIndividualData> station(DRGroupData report, String station) {
		Map<String, DRIndividualData> rows = report.data.get(station);
		return rows == null ? Map.of() : rows;
	}

	private static int[] tiers(DRIndividualData pirate) {
		int[] t = pirate.getTokens();
		return t == null || t.length != 3 ? null : new int[]{t[2], t[1], t[0]};
	}
}
