package main.java.general;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Voyage records from Puzzle Practice (puzzle-practice/Puzzle_Practice, main):
 * src/voyage-records/records.json holds every global record in one format, each with its category
 * ("Most dire chests", "Best forage score in one DR, one pirate", ...). Gizmo maps the categories it
 * can check to its kinds (SHEET_KINDS); the rest are kept as "other" for the records window.
 * "Get records" downloads the latest copy to the data folder; Gizmo reads that, so it works offline
 * afterwards. Only a plain HTTPS GET to GitHub: nothing is sent about the player.
 */
public final class VoyageRecords {

	static final String BASE = "https://raw.githubusercontent.com/puzzle-practice/Puzzle_Practice/main/src/voyage-records/";
	static final String RECORDS = "records.json";
	static final String MANUAL = "manual-records.json";
	/** Puzzle Practice's older two files, still read from the cache until records.json has been downloaded. */
	static final String KNOWN = "known-records.json", SHEET = "sheet-records.json";

	public static final class Record {
		public String voyage, ocean, kind, category, date, source, notes;
		/** The value as the file writes it (sheet records can be text, e.g. a time). */
		public String valueText;
		/** 0 for a one-pirate record. */
		public int crewSize, value;
		public List<String> pirates = new ArrayList<>();
		/** Each pirate's contribution to this record, in the same order as pirates. */
		public List<Integer> pirateScores = List.of();
	}

	private record Snapshot(List<Record> records, long loadedAt) { }

	private volatile Snapshot snapshot = new Snapshot(List.of(), 0);
	private Snapshot downloaded = new Snapshot(List.of(), 0);
	private List<Record> manual = List.of();
	private final Path folder;

	public VoyageRecords() {
		this(GameLog.dataFolder());
	}

	VoyageRecords(Path folder) {
		this.folder = folder;
		List<Record> cached = new ArrayList<>();
		long loadedAt = 0;
		String[] names = Files.exists(folder.resolve(RECORDS)) ? new String[]{RECORDS} : new String[]{KNOWN, SHEET};
		for (String name : names) {
			Path file = folder.resolve(name);
			try {
				cached.addAll(parse(Files.readString(file)));
				loadedAt = Math.max(loadedAt, Files.getLastModifiedTime(file).toMillis());
			} catch (IOException | IllegalArgumentException e) {
				// Either cache can stand on its own.
			}
		}
		downloaded = new Snapshot(List.copyOf(cached), loadedAt);
		try {
			manual = List.copyOf(parse(Files.readString(folder.resolve(MANUAL))));
		} catch (IOException | IllegalArgumentException ignored) {
			// Manual records are optional; downloaded records still work on their own.
		}
		publishRecords();
	}

	Path folder() { return folder; }

	public int size() { return snapshot.records().size(); }
	public long loadedAt() { return snapshot.loadedAt(); }
	synchronized List<Record> manualRecords() { return List.copyOf(manual); }
	synchronized boolean isManual(Record record) {
		return manual.stream().anyMatch(r -> recordKey(r).equals(recordKey(record)));
	}

	enum RefreshPolicy { KEEP_MANUAL, KEEP_HIGHER, REPLACE_MANUAL }
	record Download(byte[] contents, List<Record> records) { }
	record ManualReplacement(Record manual, Record incoming) {
		boolean incomingIsHigher() {
			return incoming.value > manual.value && (incoming.valueText == null || incoming.valueText.trim().matches("\\d+"));
		}
	}

	private static byte[] fetch(String name) throws IOException {
		HttpURLConnection connection = (HttpURLConnection) new URL(BASE + name).openConnection();
		try {
			connection.setConnectTimeout(10_000);
			connection.setReadTimeout(15_000);
			connection.setRequestProperty("User-Agent", "Gizmo");
			int status = connection.getResponseCode();
			if (status != 200) throw new IOException("GitHub answered " + status + " for " + name);
			try (InputStream in = connection.getInputStream()) { return in.readAllBytes(); }
		} finally {
			connection.disconnect();
		}
	}

	/** Fetches and parses records.json off the UI thread, then publishes the new records. */
	public int download() throws IOException {
		return applyDownload(fetchDownload(), RefreshPolicy.KEEP_MANUAL, List.of());
	}

	static Download fetchDownload() throws IOException { return prepareDownload(fetch(RECORDS)); }

	static Download prepareDownload(byte[] all) throws IOException {
		List<Record> parsed = parse(new String(all, StandardCharsets.UTF_8));
		if (parsed.isEmpty()) throw new IOException("records.json contained no usable records");
		return new Download(all.clone(), List.copyOf(parsed));
	}

	int cacheDownloaded(byte[] all) throws IOException {
		return applyDownload(prepareDownload(all), RefreshPolicy.KEEP_MANUAL, List.of());
	}

	/** Preview only the manual benchmarks present in this download, using the global best for ties. */
	synchronized List<ManualReplacement> manualReplacements(Download download) {
		Map<String, Record> incoming = new java.util.LinkedHashMap<>();
		for (Record r : download.records()) {
			Record previous = incoming.get(recordKey(r));
			if (previous == null || r.value > previous.value) incoming.put(recordKey(r), r);
		}
		List<ManualReplacement> replacements = new ArrayList<>();
		for (Record r : manual) {
			Record next = incoming.get(recordKey(r));
			if (next != null) replacements.add(new ManualReplacement(r, next));
		}
		replacements.sort(java.util.Comparator.comparing((ManualReplacement r) -> r.manual().voyage)
				.thenComparingInt(r -> r.manual().crewSize).thenComparing(r -> r.manual().kind));
		return List.copyOf(replacements);
	}

	/** Apply only reviewed replacements; manual edits made since the preview are kept. */
	synchronized int applyDownload(Download download, RefreshPolicy policy, List<ManualReplacement> reviewed) throws IOException {
		Map<String, ManualReplacement> approved = new java.util.LinkedHashMap<>();
		for (ManualReplacement replacement : reviewed) approved.put(recordKey(replacement.manual()), replacement);
		List<Record> retained = new ArrayList<>();
		for (Record r : manual) {
			ManualReplacement replacement = approved.get(recordKey(r));
			boolean replace = replacement != null && replacement.manual() == r
					&& (policy == RefreshPolicy.REPLACE_MANUAL || policy == RefreshPolicy.KEEP_HIGHER && replacement.incomingIsHigher());
			if (!replace) retained.add(r);
		}
		Files.createDirectories(folder);
		writeCache(folder.resolve(RECORDS), download.contents());
		if (retained.size() != manual.size()) writeManualRecords(retained);
		manual = List.copyOf(retained);
		downloaded = new Snapshot(download.records(), System.currentTimeMillis());
		publishRecords();
		return size();
	}

	/** Benchmarks are global across oceans, just like get(); each unrelated category stays separate. */
	private static String recordKey(Record r) {
		return r.voyage.toLowerCase(Locale.ROOT) + "|" + r.crewSize + "|" + r.kind
				+ ("other".equals(r.kind) ? "|" + r.category + "|" + (r.notes == null ? "" : r.notes) : "");
	}

	private void publishRecords() {
		java.util.Set<String> keys = new HashSet<>();
		for (Record r : manual) keys.add(recordKey(r));
		List<Record> records = new ArrayList<>();
		for (Record r : downloaded.records()) if (!keys.contains(recordKey(r))) records.add(r);
		records.addAll(manual);
		snapshot = new Snapshot(List.copyOf(records), downloaded.loadedAt());
	}

	/** Save edits from the current settings. A blank value restores the downloaded benchmark. */
	synchronized void saveManualValues(Map<Record, Integer> edits) throws IOException {
		if (edits.isEmpty()) return;
		Map<String, Record> updated = new java.util.LinkedHashMap<>();
		for (Record r : manual) updated.put(recordKey(r), r);
		for (var edit : edits.entrySet()) {
			Record original = edit.getKey();
			Integer value = edit.getValue();
			if (value == null) {
				updated.remove(recordKey(original));
				continue;
			}
			if (value < 0) throw new IllegalArgumentException("A record must be a non-negative whole number.");
			Record r = new Record();
			r.voyage = original.voyage;
			r.ocean = original.ocean;
			r.kind = original.kind;
			r.category = original.category;
			r.crewSize = original.crewSize;
			r.value = value;
			r.valueText = Integer.toString(value);
			r.date = original.date;
			r.source = original.source;
			r.notes = original.notes;
			r.pirates = List.copyOf(original.pirates);
			r.pirateScores = List.copyOf(original.pirateScores);
			updated.put(recordKey(r), r);
		}
		writeManualRecords(List.copyOf(updated.values()));
		manual = List.copyOf(updated.values());
		publishRecords();
	}

	private void writeManualRecords(List<Record> records) throws IOException {
		StringBuilder json = new StringBuilder("[");
		for (Record r : records) {
			if (json.length() > 1) json.append(',');
			json.append("{\"voyage\":").append(q(r.voyage)).append(",\"ocean\":").append(q(r.ocean))
					.append(",\"kind\":").append(q(r.kind)).append(",\"crewSize\":").append(r.crewSize)
					.append(",\"category\":").append(q(r.category)).append(",\"value\":").append(r.value)
					.append(",\"date\":").append(q(r.date)).append(",\"source\":").append(q(r.source))
					.append(",\"notes\":").append(q(r.notes))
					.append(",\"pirates\":[");
			for (int i = 0; i < r.pirates.size(); i++) json.append(i == 0 ? "" : ",").append(q(r.pirates.get(i)));
			json.append(']').append(scoresField(r.pirateScores)).append('}');
		}
		json.append(']');
		Files.createDirectories(folder);
		writeCache(folder.resolve(MANUAL), json.toString().getBytes(StandardCharsets.UTF_8));
	}

	private static void writeCache(Path path, byte[] contents) throws IOException {
		Path temp = Files.createTempFile(path.getParent(), path.getFileName().toString(), ".tmp");
		try {
			Files.write(temp, contents);
			try {
				Files.move(temp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			} catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
				Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(temp);
		}
	}

	/**
	 * Puzzle Practice categories Gizmo checks, as {voyage, category, kind, one pirate}. Kinds: "chests"
	 * and "score" are per voyage (entry), "drChests", "drScore" and "drNav" per duty report.
	 */
	static final String[][] SHEET_KINDS = {
			{"woodhallow", "Most dire chests", "chests", ""},
			{"woodhallow", "Best score", "score", ""},
			{"cursedIsles", "Most cursed chests", "chests", ""},
			{"cursedIsles", "Best score", "score", ""},
			{"vampireLairs", "Most immortal chests", "chests", ""},
			{"vampireLairs", "Best score", "score", ""},
			{"woodhallow", "Best dnav score in one DR, one pirate", "drNav", "1"},
			{"woodhallow", "Most dire chests in one DR", "drChests", ""},
			{"woodhallow", "Best forage score in one DR", "drScore", ""},
			{"woodhallow", "Most DCs in one forage", "drChests", "1"},
			{"woodhallow", "Best forage score in one DR, one pirate", "drScore", "1"},
			{"cursedIsles", "Most cursed chests in one forage", "drChests", ""},
			{"cursedIsles", "Best score in one duty report", "drScore", ""},
			{"cursedIsles", "Most cursed chests in one forage, one pirate", "drChests", "1"},
			{"cursedIsles", "Best score in one duty report, one pirate", "drScore", "1"},
			{"cursedIsles", "Most cursed chests in one entry, one pirate", "chests", "1"},
			{"cursedIsles", "Best score in one entry, one pirate", "score", "1"},
			{"vampireLairs", "Most Immortal Chests in one DR, one pirate", "drChests", "1"},
			{"vampireLairs", "Best carpentry score", "carpScore", "1"},
			{"vampireLairs", "Most carpentry holes", "carpHoles", "1"},
			{"vampireLairs", "Most vampire proof", "vampireProof", "1"}};

	/** The sheet category for a kind (crewSize 0 = one pirate), or null if it's a known-records kind. */
	static String categoryFor(String voyage, String kind, int crewSize) {
		for (String[] k : SHEET_KINDS)
			if (k[0].equals(voyage) && k[2].equals(kind) && (crewSize == 0) == !k[3].isEmpty()) return k[1];
		return null;
	}

	/** Every record for this voyage: the per-crew ones first (by kind, then crew size), then the rest. */
	public List<Record> forVoyage(String voyage) {
		List<Record> out = new ArrayList<>();
		for (Record r : snapshot.records()) if (r.voyage.equalsIgnoreCase(voyage)) out.add(r);
		out.sort(java.util.Comparator.comparingInt((Record r) -> r.category != null ? 1 : 0)
				.thenComparing(r -> r.category != null ? r.category : r.kind)
				.thenComparingInt(r -> r.crewSize));
		return out;
	}

	/** The oceans that have records for this voyage (lower case). */
	public java.util.Set<String> oceans(String voyage) {
		java.util.Set<String> s = new java.util.TreeSet<>();
		for (Record r : snapshot.records()) if (r.voyage.equalsIgnoreCase(voyage)) s.add(r.ocean.toLowerCase(Locale.ROOT));
		return s;
	}

	/** The record for this voyage ("woodhallow", "cursedIsles", "vampireLairs"), ocean, crew size and kind. */
	public Record get(String voyage, String ocean, int crewSize, String kind) {
		Record best = null;   // the sheets list ties more than once
		for (Record r : snapshot.records())
			// The player's ocean labels the claim. The benchmark includes all oceans.
			if (r.voyage.equalsIgnoreCase(voyage) && r.crewSize == crewSize && r.kind.equals(kind)
					&& (best == null || r.value > best.value))
				best = r;
		return best;
	}

	/** Match an editor's complete category, including a particular forage's notes. */
	Record get(Record template) {
		Record best = null;
		String key = recordKey(template);
		for (Record r : snapshot.records())
			if (recordKey(r).equals(key) && (best == null || r.value > best.value)) best = r;
		return best;
	}

	static boolean isIndividual(Record record) {
		return record.crewSize == 0 && (!"other".equals(record.kind)
				|| record.category != null && record.category.toLowerCase(Locale.ROOT).contains("one pirate"));
	}

	/** All individual categories, including imported categories Gizmo cannot automatically verify. */
	List<Record> individualRecords(String voyage) {
		Map<String, Record> records = new java.util.LinkedHashMap<>();
		for (String[] category : SHEET_KINDS) {
			if (!category[0].equals(voyage) || category[3].isEmpty()) continue;
			Record template = new Record();
			template.voyage = voyage;
			template.ocean = "global";
			template.kind = category[2];
			template.category = category[1];
			records.put(recordKey(template), template);
		}
		Map<String, Record> imported = new java.util.LinkedHashMap<>();
		for (Record record : forVoyage(voyage))
			if (isIndividual(record)) imported.merge(recordKey(record), record, (a, b) -> b.value > a.value ? b : a);
		records.putAll(imported);
		return List.copyOf(records.values());
	}

	List<Record> otherRecords(String voyage) {
		Map<String, Record> records = new java.util.LinkedHashMap<>();
		for (Record record : forVoyage(voyage)) {
			if (record.crewSize != 0 || isIndividual(record)) continue;
			Record existing = records.get(recordKey(record));
			if (existing == null || record.value > existing.value) records.put(recordKey(record), record);
		}
		return List.copyOf(records.values());
	}

	static String recordLabel(Record record) {
		String category = record.category != null ? record.category : describe(record.voyage, record.kind, record.crewSize);
		return category + (record.notes == null || record.notes.isBlank() ? "" : " — " + record.notes);
	}

	static String recordScope(Record record) {
		return record.crewSize > 0 ? Integer.toString(record.crewSize) : isIndividual(record) ? "Individual" : "All crews";
	}

	static String voyageName(String voyage) {
		if ("woodhallow".equals(voyage)) return "Woodhallow";
		if ("cursedIsles".equals(voyage)) return "Cursed Isles";
		if ("vampireLairs".equals(voyage)) return "Vampire Lairs";
		return voyage;
	}

	/** Puzzle Practice's kinds, plus the Woodhallow one-DR records it is adding. */
	public static final String CHESTS = "chests", SCORE = "score", DR_CHESTS = "drChests", DR_SCORE = "drScore", DR_NAV = "drNav";

	static String topChests(String voyage) {
		if ("woodhallow".equals(voyage)) return "dire chests";
		if ("cursedIsles".equals(voyage)) return "cursed chests";
		if ("vampireLairs".equals(voyage)) return "immortal chests";
		return "top-tier chests";
	}

	/** As Puzzle Practice words it: "best score", "most dire chests", plus the one-DR kinds. */
	static String describe(String voyage, String kind) {
		switch (kind) {
			case SCORE: return "vampireLairs".equals(voyage) ? "most total chests" : "best score";
			case CHESTS: return "most " + topChests(voyage);
			case DR_CHESTS: return "most " + topChests(voyage) + " in one DR";
			case DR_SCORE: return "vampireLairs".equals(voyage) ? "most total chests in one fray" : "best forage score in one DR";
			case DR_NAV: return "best dnav score in one DR";
			default: return kind;
		}
	}

	/** One-pirate records use the sheet's own wording. */
	static String describe(String voyage, String kind, int crewSize) {
		if (crewSize > 0) return describe(voyage, kind);
		String c = categoryFor(voyage, kind, 0);
		return c != null ? c.substring(0, 1).toLowerCase() + c.substring(1) : describe(voyage, kind) + ", one pirate";
	}

	/** Puzzle Practice's MAX_SENT_CREW: bigger crews are too rare to keep records for. */
	static int maxSentCrew(String voyage) {
		return "cursedIsles".equals(voyage) ? 7 : 12;
	}

	/** Individual records use crew size 0. Lair crew records stop at twelve pirates. */
	private static boolean supportedCrewSize(String voyage, int crewSize) {
		return crewSize >= 0 && (!"vampireLairs".equalsIgnoreCase(voyage) || crewSize <= maxSentCrew(voyage));
	}

	/**
	 * Compares one value with the record for its voyage, ocean, crew size and kind.
	 * Returns a line for a beat, a tie, or a crew size with no record yet ("" otherwise), and keeps
	 * beats and new records as claims to send in (see claimMessage).
	 */
	public synchronized String compare(String voyage, String ocean, int crewSize, String kind, int value,
	                                   String time, List<String> pirates, int[] loot) {
		return compare(voyage, ocean, crewSize, kind, value, time, pirates, loot, Map.of());
	}

	synchronized String compare(String voyage, String ocean, int crewSize, String kind, int value,
	                            String time, List<String> pirates, int[] loot, Map<String, int[]> pirateLoot) {
		if (voyage == null || ocean == null || !supportedCrewSize(voyage, crewSize) || value <= 0 || size() == 0) return "";
		String what = voyageName(voyage) + ", " + (crewSize > 0 ? crewSize + " pirates, " + describe(voyage, kind)
				: (pirates != null && pirates.size() == 1 ? pirates.get(0) + ", " : "") + describe(voyage, kind, 0));
		Record r = get(voyage, ocean, crewSize, kind);
		String line;
		if (r == null) line = String.format("New record? %s: %d (there is no %s record yet, so this would be the first)", what, value,
				crewSize > 0 ? crewSize + "-pirate" : "one-pirate");
		else if (r.value <= 0 && r.valueText != null && !r.valueText.trim().matches("\\d+")) return "";
		else if (value > r.value) line = String.format("New record! %s: %d beats %d (%s, %s)", what, value, r.value, r.date, String.join(", ", r.pirates));
		else if (value == r.value) return String.format("Record tied: %s: %d (%s, %s)", what, value, r.date, String.join(", ", r.pirates));
		else return "";
		if (crewSize <= maxSentCrew(voyage)) {
			List<Integer> scores = contributions(voyage, crewSize, kind, value, pirates, pirateLoot);
			addClaim(voyage, ocean.toLowerCase(Locale.ROOT), crewSize, kind, value, time, pirates, loot, scores);
		}
		return line;
	}

	static int lootScore(String voyage, int[] loot) {
		return "vampireLairs".equals(voyage) ? loot[0] + loot[1] + loot[2] : 3 * loot[0] + 2 * loot[1] + loot[2];
	}

	private static List<Integer> contributions(String voyage, int crewSize, String kind, int value,
	                                           List<String> pirates, Map<String, int[]> pirateLoot) {
		if (pirates == null || pirates.size() != Math.max(1, crewSize)) return List.of();
		if (pirates.size() == 1) return List.of(value);
		if (pirateLoot.isEmpty() || new HashSet<>(pirates).size() != pirates.size()
				|| !pirates.containsAll(pirateLoot.keySet())) return List.of();
		boolean score = SCORE.equals(kind) || DR_SCORE.equals(kind);
		List<Integer> totals = new ArrayList<>();
		int sum = 0;
		for (String pirate : pirates) {
			int[] tiers = pirateLoot.get(pirate);
			int contribution = tiers == null ? 0 : score ? lootScore(voyage, tiers) : tiers[0];
			totals.add(contribution);
			sum += contribution;
		}
		return sum == value ? totals : List.of();
	}

	/** One run's chests and score, using its combat crew rather than the division's recipients. */
	String check(ChatLog.Run run, String ocean, Map<String, int[]> pirateLoot) {
		if (run == null || run.voyage == null) return "";
		List<String> pirates = run.recordCrew();
		if (pirates.isEmpty() || !pirates.containsAll(pirateLoot.keySet())) return "";
		int crewSize = pirates.size();
		String time = run.arrivedAt == null ? null : run.arrivedAt.toString();
		StringBuilder sb = new StringBuilder();
		for (String kind : new String[]{CHESTS, SCORE}) {
			String l = compare(run.voyage, ocean, crewSize, kind, kind.equals(CHESTS) ? run.tiers[0] : lootScore(run.voyage, run.tiers),
					time, pirates, run.tiers, pirateLoot);
			if (!l.isEmpty()) sb.append(sb.length() > 0 ? "\n" : "").append(l);
		}
		return sb.toString();
	}

	// ---- Claims: records beaten (or first set) here, to send in.

	private Path claimsFile() {
		return folder.resolve("record_claims.jsonl");
	}

	private static String q(String v) {
		return v == null ? "null" : "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
	}

	private void addClaim(String voyage, String ocean, int crewSize, String kind, int value, String time,
	                      List<String> pirates, int[] loot, List<Integer> scores) {
		StringBuilder p = new StringBuilder("[");
		if (pirates != null) for (String n : pirates) p.append(p.length() > 1 ? "," : "").append(q(n));
		p.append("]");
		String date = time == null ? null : time.substring(0, Math.min(10, time.length()));
		String category = categoryFor(voyage, kind, crewSize);
		String line = String.format("{\"voyage\":%s,\"ocean\":%s,\"crewSize\":%d,\"kind\":%s,%s\"value\":%d,\"date\":%s,\"time\":%s,\"pirates\":%s,\"loot\":[%d,%d,%d]%s}",
				q(voyage), q(ocean), crewSize, q(kind), category == null ? "" : "\"category\":" + q(category) + ",",
				value, q(date), q(time), p, loot[0], loot[1], loot[2], scoresField(scores));
		try {
			Path f = claimsFile();
			if (Files.exists(f) && new String(Files.readAllBytes(f), StandardCharsets.UTF_8).contains(line)) return;   // already kept
			Files.createDirectories(f.getParent());
			Files.write(f, (line + System.lineSeparator()).getBytes(StandardCharsets.UTF_8),
					java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
		} catch (IOException | RuntimeException ignored) { }
	}

	private static String scoresField(List<Integer> scores) {
		return scores.isEmpty() ? "" : ",\"pirateScores\":" + scores;
	}

	private static String crewText(Record claim) {
		if (claim.pirates.isEmpty()) return "not in the log";
		if (claim.pirateScores.isEmpty()) return String.join(", ", claim.pirates);
		List<String> crew = new ArrayList<>();
		for (int i = 0; i < claim.pirates.size(); i++)
			crew.add(claim.pirates.get(i) + " (" + claim.pirateScores.get(i) + ")");
		return String.join(", ", crew);
	}

	private static final Pattern TIME = Pattern.compile("\"time\"\\s*:\\s*\"([^\"]*)\"");
	private static final Pattern LOOT = Pattern.compile("\"loot\"\\s*:\\s*\\[(\\d+),(\\d+),(\\d+)\\]");
	private static final Pattern PIRATE_SCORES = Pattern.compile("\"pirateScores\"\\s*:\\s*\\[(\\d+(?:\\s*,\\s*\\d+)*)\\s*\\]");

	/**
	 * The message to send: one numbered line per record still beaten (against the latest records
	 * downloaded), then "Data for the records:" and the same records as a JSON list in Puzzle Practice's
	 * records.json format (category, crewSize only for per-crew categories, numeric value, pirates with
	 * pirateScores biggest first, loot). Null if none. Only the best claim for each record is sent.
	 */
	public synchronized String claimMessage() {
		Claims claims = claims();
		if (claims == null) return null;
		int i = claims.count();
		return (i == 1 ? "Hi Jice, I've beaten a voyage record on Puzzle Practice:" : "Hi Jice, I've beaten " + i + " voyage records on Puzzle Practice:")
				+ "\n" + claims.text() + "\n"
				+ "I'll send a picture, image or video of " + (i == 1 ? "it" : "each one") + ", numbered as above.\n\n"
				+ "Data for the records:\n" + claims.data();
	}

	/** Just the JSON list from claimMessage, which is all Puzzle Practice needs. Null if there are no claims. */
	public synchronized String claimData() {
		Claims claims = claims();
		return claims == null ? null : claims.data();
	}

	private record Claims(int count, String text, String data) { }

	private Claims claims() {
		record Claim(Record record, String json, Record known) {}
		Map<String, Claim> best = new java.util.LinkedHashMap<>();
		try {
			if (!Files.exists(claimsFile())) return null;
			for (String l : Files.readAllLines(claimsFile(), StandardCharsets.UTF_8)) {
				List<Record> one = parse(l);
				if (one.isEmpty()) continue;
				Record c = one.get(0);
				Record known = get(c.voyage, c.ocean, c.crewSize, c.kind);
				if (known != null && c.value <= known.value) continue;   // the records have it (or better) now
				String key = c.voyage + "|" + c.ocean + "|" + c.crewSize + "|" + c.kind;
				Claim prev = best.get(key);
				if (prev == null || prev.record().value < c.value
						|| (!c.pirateScores.isEmpty() && l.replace(scoresField(c.pirateScores), "").equals(prev.json())))
					best.put(key, new Claim(c, l, known));
			}
		} catch (IOException | RuntimeException e) { return null; }
		if (best.isEmpty()) return null;
		StringBuilder text = new StringBuilder(), data = new StringBuilder("[");
		int i = 0;
		for (Claim claim : best.values()) {
			Record c = claim.record();
			String raw = claim.json();
			Record known = claim.known();
			Matcher t = TIME.matcher(raw), lo = LOOT.matcher(raw);
			String time = t.find() ? t.group(1) : null;
			int[] loot = lo.find() ? new int[]{Integer.parseInt(lo.group(1)), Integer.parseInt(lo.group(2)), Integer.parseInt(lo.group(3))} : null;
			boolean anyLoot = loot != null && loot[0] + loot[1] + loot[2] > 0;
			String ocean = c.ocean.substring(0, 1).toUpperCase() + c.ocean.substring(1);
			String category = categoryFor(c.voyage, c.kind, c.crewSize);
			String what = category != null ? (c.crewSize > 0 ? c.crewSize + " pirates, " : "") + category
					: (c.crewSize > 0 ? c.crewSize + " pirates, " + describe(c.voyage, c.kind) : describe(c.voyage, c.kind, 0));
			// Pirates in the order Puzzle Practice wants: biggest share first (the order as found when there are no shares).
			List<Integer> order = new ArrayList<>();
			for (int k = 0; k < c.pirates.size(); k++) order.add(k);
			if (c.pirateScores.size() == c.pirates.size()) order.sort((x, y) -> Integer.compare(c.pirateScores.get(y), c.pirateScores.get(x)));
			List<String> names = new ArrayList<>();
			List<Integer> shares = new ArrayList<>();
			for (int k : order) {
				names.add(c.pirates.get(k));
				if (c.pirateScores.size() == c.pirates.size()) shares.add(c.pirateScores.get(k));
			}
			StringBuilder crew = new StringBuilder();
			for (int k = 0; k < names.size(); k++)
				crew.append(k > 0 ? ", " : "").append(names.get(k)).append(shares.isEmpty() || names.size() == 1 ? "" : " (" + shares.get(k) + ")");
			text.append(String.format("%d. %s (%s), %s: %d (%s) on %s.%s %s: %s\n",
					++i, voyageName(c.voyage), ocean, what, c.value,
					known == null ? "no record for this crew size yet" : "beats " + known.value,
					time != null ? time : c.date != null ? c.date : "an unknown date",
					anyLoot ? " Loot " + loot[0] + "/" + loot[1] + "/" + loot[2] + "." : "",
					c.crewSize > 0 ? "Crew" : "Pirate", names.isEmpty() ? "not in the log" : crew));
			StringBuilder ps = new StringBuilder("[");
			for (String n : names) ps.append(ps.length() > 1 ? "," : "").append(q(n));
			ps.append("]");
			data.append(data.length() > 1 ? "," : "").append(String.format(
					"{\"voyage\":%s,\"ocean\":%s,\"category\":%s,%s\"value\":%d%s%s,\"pirates\":%s%s%s}",
					q(c.voyage), q(c.ocean), q(category != null ? category : describe(c.voyage, c.kind, c.crewSize)),
					c.crewSize > 0 ? "\"crewSize\":" + c.crewSize + "," : "", c.value,
					c.date != null ? ",\"date\":" + q(c.date) : "", time != null ? ",\"time\":" + q(time) : "", ps,
					shares.isEmpty() || names.size() == 1 ? "" : ",\"pirateScores\":" + shares.toString().replace(" ", ""),
					anyLoot ? ",\"loot\":[" + loot[0] + "," + loot[1] + "," + loot[2] + "]" : ""));
		}
		data.append("]");
		return new Claims(i, text.toString(), data.toString());
	}

	// The file is a flat list of objects with string, number and string-array fields.
	private static final Pattern OBJECT = Pattern.compile("\\{[^{}]*\\}");
	private static final Pattern STR = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
	private static final Pattern NUM = Pattern.compile("\"(\\w+)\"\\s*:\\s*(-?\\d+)");
	private static final Pattern ARR = Pattern.compile("\"pirates\"\\s*:\\s*\\[([^\\]]*)\\]");
	private static final Pattern ITEM = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");

	static List<Record> parse(String json) {
		List<Record> out = new ArrayList<>();
		Matcher o = OBJECT.matcher(json);
		while (o.find()) {
			String obj = o.group();
			Record r = new Record();
			Matcher m = STR.matcher(obj);
			while (m.find()) {
				String v = m.group(2).replace("\\\"", "\"").replace("\\\\", "\\");
				switch (m.group(1)) {
					case "voyage": r.voyage = v; break;
					case "ocean": r.ocean = v; break;
					case "kind": r.kind = v; break;
					case "date": r.date = v; break;
					case "source": r.source = v; break;
					case "category": r.category = v; break;
					case "notes": r.notes = v; break;
					case "value": r.valueText = v; if (v.trim().matches("\\d+")) r.value = Integer.parseInt(v.trim()); break;
					default:
				}
			}
			boolean hasCrewSize = false;
			m = NUM.matcher(obj);
			while (m.find()) {
				if (m.group(1).equals("crewSize")) {
					r.crewSize = Integer.parseInt(m.group(2));
					hasCrewSize = true;
				}
				else if (m.group(1).equals("value")) r.value = Integer.parseInt(m.group(2));
			}
			m = ARR.matcher(obj);
			if (m.find()) { Matcher it = ITEM.matcher(m.group(1)); while (it.find()) r.pirates.add(it.group(1)); }
			m = PIRATE_SCORES.matcher(obj);
			if (m.find()) {
				String[] scores = m.group(1).split("\\s*,\\s*");
				if (scores.length == r.pirates.size()) {
					List<Integer> parsed = new ArrayList<>();
					for (String score : scores) parsed.add(Integer.parseInt(score));
					r.pirateScores = parsed;
				}
			}
			if (r.kind == null && r.category != null)
				for (String[] k : SHEET_KINDS)
					if (k[0].equals(r.voyage) && k[1].equals(r.category)) { r.kind = k[2]; if (!k[3].isEmpty()) r.crewSize = 0; }
			// Sheet categories Gizmo doesn't check are kept as "other", for the records window.
			if (r.kind == null && r.category != null && r.voyage != null) r.kind = "other";
			if (r.voyage != null && r.ocean != null && !r.ocean.isBlank() && r.kind != null && (hasCrewSize || r.category != null)
					&& supportedCrewSize(r.voyage, r.crewSize)) out.add(r);
		}
		return out;
	}
}
