package main.java.general;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Existing DR records alert live; missing/zero benchmarks wait for the entry's best result. */
final class RunRecordAlerts {
	static final long SETTLE_MS = 5_000;
	record Run(String voyage, String ocean, long arrival) {
		Run { if (ocean != null) ocean = ocean.toLowerCase(java.util.Locale.ROOT); }
	}
	private record Category(int crew, String kind, List<String> individual) { }
	private record Benchmark(int crew, String kind) { }
	private record Result(int value, String time, List<String> pirates, int[] loot, Map<String, int[]> shares) { }
	final Run run;
	private final VoyageRecords records;
	private final Map<Category, Result> best = new LinkedHashMap<>();
	private final Map<Category, Integer> announced = new LinkedHashMap<>();
	private final Map<Benchmark, Integer> beforeRun = new LinkedHashMap<>();
	private long ended;

	RunRecordAlerts(VoyageRecords records, Run run) {
		this.records = records;
		this.run = run;
		for (VoyageRecords.Record record : records.forVoyage(run.voyage))
			beforeRun.merge(new Benchmark(record.crewSize, record.kind), record.value, Math::max);
	}

	String compare(int crew, String kind, int value, String time, List<String> pirates, int[] loot) {
		return compare(crew, kind, value, time, pirates, loot, Map.of());
	}

	String compare(int crew, String kind, int value, String time, List<String> pirates, int[] loot, Map<String, int[]> shares) {
		if (!VoyageRecords.DR_CHESTS.equals(kind) && !VoyageRecords.DR_SCORE.equals(kind))
			return records.compare(run.voyage, run.ocean, crew, kind, value, time, pirates, loot, shares);
		Category category = new Category(crew, kind, crew == 0 ? List.copyOf(pirates) : List.of());
		Result previous = best.get(category);
		if (value > 0 && (previous == null || value > previous.value)) {
			Map<String, int[]> copy = new LinkedHashMap<>();
			shares.forEach((pirate, tiers) -> copy.put(pirate, tiers.clone()));
			best.put(category, new Result(value, time, List.copyOf(pirates), loot.clone(), copy));
		}
		if (beforeRun.getOrDefault(new Benchmark(crew, kind), 0) > 0
				&& value > announced.getOrDefault(category, 0) && isBest(category, value)) {
			announced.put(category, value);
			return records.compare(run.voyage, run.ocean, crew, kind, value, time, pirates, loot, shares);
		}
		return "";
	}

	private boolean isBest(Category category, int value) {
		return best.entrySet().stream().noneMatch(other -> other.getKey().crew == category.crew
				&& other.getKey().kind.equals(category.kind) && other.getValue().value > value);
	}

	void end(long at) { if (ended == 0) ended = at; }
	boolean ready(long now) { return ended != 0 && now - ended >= SETTLE_MS; }

	List<String> finish() {
		List<String> lines = new java.util.ArrayList<>();
		best.forEach((category, result) -> {
			if (announced.getOrDefault(category, 0) >= result.value || !isBest(category, result.value)) return;
			lines.add(records.compare(run.voyage, run.ocean, category.crew, category.kind, result.value,
					result.time, result.pirates, result.loot, result.shares));
			announced.put(category, result.value);
		});
		return lines;
	}
}
