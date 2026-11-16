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
 *   src/voyage-records/known-records.json  best score / most top-tier chests per voyage and crew size
 *   src/voyage-records/sheet-records.json  the sheets' other records; Gizmo uses the one-DR,
 *                                          one-forage and one-pirate ones that match its record boxes
 * "Get records" downloads the latest copies to exported_data/; Gizmo reads those, so it works
 * offline afterwards. Only plain HTTPS GETs to GitHub: nothing is sent about the player.
 */
public final class VoyageRecords {

	static final String BASE = "https://raw.githubusercontent.com/puzzle-practice/Puzzle_Practice/main/src/voyage-records/";
	static final String KNOWN = "known-records.json", SHEET = "sheet-records.json";

	public static final class Record {
		public String voyage, ocean, kind, category, date, source;
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
	private final Path folder;

	public VoyageRecords() {
		this(GameLog.clientFolder().resolve("exported_data"));
	}

	VoyageRecords(Path folder) {
		this.folder = folder;
		List<Record> cached = new ArrayList<>();
		long loadedAt = 0;
		for (String name : new String[]{KNOWN, SHEET}) {
			Path file = folder.resolve(name);
			try {
				cached.addAll(parse(Files.readString(file)));
				loadedAt = Math.max(loadedAt, Files.getLastModifiedTime(file).toMillis());
			} catch (IOException | IllegalArgumentException e) {
				// Either cache can stand on its own.
			}
		}
		snapshot = new Snapshot(List.copyOf(cached), loadedAt);
	}

	public int size() { return snapshot.records().size(); }
	public long loadedAt() { return snapshot.loadedAt(); }

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

	/** Fetches and parses both files off the UI thread, then publishes the new records together. */
	public int download() throws IOException {
		byte[] known = fetch(KNOWN);
		byte[] sheet = fetch(SHEET);
		List<Record> parsed = new ArrayList<>(parse(new String(known, StandardCharsets.UTF_8)));
		List<Record> sheetRecords = parse(new String(sheet, StandardCharsets.UTF_8));
		if (parsed.isEmpty() || sheetRecords.isEmpty()) throw new IOException("A record file contained no usable records");
		parsed.addAll(sheetRecords);
		Files.createDirectories(folder);
		writeCache(folder.resolve(KNOWN), known);
		writeCache(folder.resolve(SHEET), sheet);
		snapshot = new Snapshot(List.copyOf(parsed), System.currentTimeMillis());
		return parsed.size();
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
	 * Sheet categories Gizmo checks, as {voyage, category, kind, one pirate}. Kinds: "chests" and
	 * "score" are per voyage (entry), "drChests" and "drScore" per duty report (one forage).
	 */
	static final String[][] SHEET_KINDS = {
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

	static String voyageName(String voyage) {
		if ("woodhallow".equals(voyage)) return "Woodhallow";
		if ("cursedIsles".equals(voyage)) return "Cursed Isles";
		if ("vampireLairs".equals(voyage)) return "Vampire Lairs";
		return voyage;
	}

	/** Puzzle Practice's kinds, plus the Woodhallow one-DR records it is adding. */
	public static final String CHESTS = "chests", SCORE = "score", DR_CHESTS = "drChests", DR_SCORE = "drScore";

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
		else if (r.value <= 0 && r.valueText != null) return "";
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

	/** One run of a booty division: its chests and score. */
	public String check(ChatLog.Run run, int crewSize, String ocean, List<String> pirates) {
		return check(run, crewSize, ocean, pirates, Map.of());
	}

	String check(ChatLog.Run run, int crewSize, String ocean, List<String> pirates, Map<String, int[]> pirateLoot) {
		if (run == null || run.voyage == null) return "";
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
	 * Puzzle Practice's recordsMessage: one numbered line per record still beaten (against the latest
	 * records downloaded), then the same records as JSON for known-records.json. Null if none.
	 * Only the best claim for each voyage, ocean, crew size and kind is sent.
	 */
	public synchronized String claimMessage() {
		record Claim(Record record, String json, Record known) {}
		Map<String, Claim> best = new java.util.LinkedHashMap<>();
		try {
			if (!Files.exists(claimsFile())) return null;
			for (String l : Files.readAllLines(claimsFile(), StandardCharsets.UTF_8)) {
				List<Record> one = parse(l);
				if (one.isEmpty()) continue;
				Record c = one.get(0);
				Record known = get(c.voyage, c.ocean, c.crewSize, c.kind);
				if (known != null && c.value <= known.value) continue;   // the table has it (or better) now
				String key = c.voyage + "|" + c.ocean + "|" + c.crewSize + "|" + c.kind;
				Claim prev = best.get(key);
				if (prev == null || prev.record().value < c.value
						|| (!c.pirateScores.isEmpty() && l.replace(scoresField(c.pirateScores), "").equals(prev.json())))
					best.put(key, new Claim(c, l, known));
			}
		} catch (IOException | RuntimeException e) { return null; }
		if (best.isEmpty()) return null;
		StringBuilder text = new StringBuilder(), json = new StringBuilder("["), sheet = new StringBuilder("[");
		int i = 0;
		for (Claim claim : best.values()) {
			Record c = claim.record();
			String raw = claim.json();
			Record known = claim.known();
			Matcher t = TIME.matcher(raw), lo = LOOT.matcher(raw);
			String time = t.find() ? t.group(1) : null;
			String loot = lo.find() ? lo.group(1) + "/" + lo.group(2) + "/" + lo.group(3) : "?";
			String ocean = c.ocean.substring(0, 1).toUpperCase() + c.ocean.substring(1);
			String who = c.crewSize > 0 ? c.crewSize + " pirates, " + describe(c.voyage, c.kind) : describe(c.voyage, c.kind, 0);
			text.append(String.format("%d. %s (%s), %s: %d (%s) on %s. Loot %s. %s: %s\n",
					++i, voyageName(c.voyage), ocean, who, c.value,
					known == null ? "no record for this crew size yet" : "beats " + known.value, time != null ? time : c.date != null ? c.date : "an unknown date",
					loot, c.crewSize > 0 ? "Crew" : "Pirate", crewText(c)));
			String category = categoryFor(c.voyage, c.kind, c.crewSize);
			if (category == null) {
				json.append(json.length() > 1 ? "," : "").append(raw.trim());
			} else {
				// The sheet's shape: category instead of kind, value as text, no crew size for one pirate.
				StringBuilder ps = new StringBuilder("[");
				for (String n : c.pirates) ps.append(ps.length() > 1 ? "," : "").append(q(n));
				ps.append("]");
				sheet.append(sheet.length() > 1 ? "," : "").append(String.format(
						"{\"voyage\":%s,\"ocean\":%s,\"category\":%s,%s\"value\":\"%d\",\"date\":%s,\"time\":%s,\"pirates\":%s,\"loot\":[%s]%s}",
						q(c.voyage), q(c.ocean), q(category), c.crewSize > 0 ? "\"crewSize\":" + c.crewSize + "," : "", c.value,
						q(c.date), q(time), ps, loot.replace("/", ","), scoresField(c.pirateScores)));
			}
		}
		json.append("]");
		sheet.append("]");
		return (i == 1 ? "Hi Jice, I've beaten a voyage record on Puzzle Practice:" : "Hi Jice, I've beaten " + i + " voyage records on Puzzle Practice:")
				+ "\n" + text + "\n"
				+ "I'll send a picture, image or video of " + (i == 1 ? "it" : "each one") + ", numbered as above.\n\n"
				+ (sheet.length() > 2 ? "Data for the sheet records:\n" + sheet : "")
				+ (json.length() > 2 && sheet.length() > 2 ? "\n\n" : "")
				+ (json.length() > 2 ? "Data for the records table:\n" + json : "");
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
