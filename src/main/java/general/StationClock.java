package main.java.general;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Woodhallow station periods from the live chat log, using the same boundary messages as
 * Thalassa's werewolves_performance:
 *   start  "N o' M still standin'! To yer stations!"  (or "... Plunder the lair!" for the lair)
 *   end    "The wolf-kin gather! 30 seconds!" / "The lair stirs! 30 seconds left to forage!" + 30 s,
 *          or earlier on "Wolf-kin approach!", "Beware! More wolf-kin approach!",
 *          "We've reached their lair! Almost done!", "The lair be plundered!...", the crew being
 *          overrun, falling back or retreating.
 * Arriving ("Hark the dread howlin'! 'Tis Woodhallow Isle...") starts a new run.
 * The chat log is read incrementally, like Thalassa's log_parser.
 */
public final class StationClock {

	public static final class Period {
		public long start;
		public long end;          // 0 while open
		public int survivors, crew;
		public boolean lair;

		public boolean closed() { return end > 0; }
	}

	static final String ARRIVAL = "Hark the dread howlin'! 'Tis Woodhallow Isle, realm o' the wolf-folk!";
	static final Pattern SURVIVORS = Pattern.compile("^(\\d+) o' (\\d+) still standin'! (To yer stations!|Plunder the lair!)$");
	private static final String LAIR_WARNING = "Ye hear the sound of rustling in coffins.";
	record LairRound(long arrival, long end) { }
	record LairEntry(Set<Long> rounds, int[] haul) { }

	private final Map<Long, LairEntry> lairEntries = new LinkedHashMap<>();
	private long lairArrival, lairRoundEnd;
	private final List<Period> periods = new ArrayList<>();
	private String arrivalVoyage;
	private long arrivalAt;
	private final Map<Path, Tail> tails = new LinkedHashMap<>();
	private final ArrayDeque<ChatLog.Line> ownChat = new ArrayDeque<>();
	private Path mainLog;
	private String pirate;
	private ChatLog.Merge merge = new ChatLog.Merge();
	private LocalDateTime latest;

	/** Tails the matched log family on the file thread and publishes small batches to the UI. */
	void poll(List<Path> logs, String self) {
		Path main = logs.isEmpty() ? null : logs.get(0);
		Map<Path, Long> sizes = new LinkedHashMap<>();
		boolean changed = !java.util.Objects.equals(main, mainLog) || !self.equals(pirate);
		for (Path log : logs) {
			try {
				long size = Files.size(log);
				sizes.put(log, size);
				Tail existing = tails.get(log);
				if (existing != null && size < existing.offset) changed = true;
			} catch (IOException ignored) {
				// Retry the missing or locked file on the next poll.
			}
		}
		if (changed) {
			mainLog = main;
			pirate = self;
			tails.clear();
			merge = new ChatLog.Merge();
			latest = null;
			synchronized (this) {
				resetRun();
				ownChat.clear();
				clearArrival();
				lairEntries.clear();
				lairArrival = lairRoundEnd = 0;
			}
		}

		String ownPrefix = self + " says, \"";
		LocalDateTime cutoff = latest == null ? null : latest.minusMinutes(1);
		Map<Path, List<ChatLog.Line>> batches = new LinkedHashMap<>();
		for (var log : sizes.entrySet()) {
			try {
				Tail tail = tails.computeIfAbsent(log.getKey(), file -> new Tail());
				batches.put(log.getKey(), tail.read(log.getKey(), log.getValue(), ownPrefix, cutoff));
			} catch (IOException ignored) { }
		}
		List<ChatLog.Line> lines = merge.accept(batches);
		if (lines.isEmpty()) return;
		LocalDateTime newest = lines.get(lines.size() - 1).time;
		if (latest == null || newest.isAfter(latest)) latest = newest;

		ZoneId zone = ZoneId.systemDefault();
		for (int i = 0; i < lines.size(); i += 1000) {
			synchronized (this) {
				for (int j = i; j < Math.min(lines.size(), i + 1000); j++) {
					ChatLog.Line line = lines.get(j);
					if (line.text.startsWith(ownPrefix) && line.text.endsWith("\"")
							&& line.text.length() > ownPrefix.length()) {
						String sent = line.text.substring(ownPrefix.length(), line.text.length() - 1);
						ownChat.add(new ChatLog.Line(line.time, sent, line.raw));
						while (ownChat.size() > 20) ownChat.remove();
					} else {
						message(line.text, line.time.atZone(zone).toInstant().toEpochMilli());
					}
				}
			}
		}
	}

	synchronized List<ChatLog.Line> drainOwnChat() {
		List<ChatLog.Line> lines = new ArrayList<>(ownChat);
		ownChat.clear();
		return lines;
	}

	private static final class Tail {
		private long offset;
		private final ChatLog.Parser parser = new ChatLog.Parser();
		private ChatLog.Line pendingOwn;
		private final StringBuilder ownText = new StringBuilder();

		List<ChatLog.Line> read(Path file, long size, String ownPrefix, LocalDateTime cutoff) throws IOException {
			if (size == offset) return List.of();
			byte[] buf;
			try (RandomAccessFile raf = new RandomAccessFile(file.toFile(), "r")) {
				raf.seek(offset);
				buf = new byte[(int) Math.min(size - offset, 8L * 1024 * 1024)];
				raf.readFully(buf);
			}
			int end = buf.length;
			while (end > 0 && buf[end - 1] != '\n') end--;   // leave a half-written line for next time
			if (end == 0) return List.of();
			List<ChatLog.Line> lines = new ArrayList<>();
			for (String raw : new String(buf, 0, end - 1, StandardCharsets.UTF_8).split("\\r?\\n", -1)) {
				String text = raw.trim();
				ChatLog.Line line = parser.readLine(text);
				if (line != null) {
					pendingOwn = null;
					if (cutoff != null && line.time.isBefore(cutoff)) continue;
					if (line.text.startsWith(ownPrefix)
							&& (line.text.length() == ownPrefix.length() || !line.text.endsWith("\""))) {
						pendingOwn = line;
						ownText.setLength(0);
						ownText.append(line.text);
					} else {
						lines.add(line);
					}
				} else if (text.startsWith("====== ")) {
					pendingOwn = null;
				} else if (pendingOwn != null) {
					ownText.append('\n').append(text);
					if (text.endsWith("\"")) {
						lines.add(new ChatLog.Line(pendingOwn.time, ownText.toString(), pendingOwn.raw));
						pendingOwn = null;
					}
				}
			}
			offset += end;
			return lines;
		}
	}

	private synchronized void resetRun() {
		periods.clear();
		inDen = false;
		crew = fraysDone = frayDeaths = 0;
		wolvesAtHalf = elitesLeft = wolvesLeft = -1;
		wolvesChangedAt = lastWolfClearedAt = thirtySecondsAt = 0;
	}

	private int crew;
	/** Pirates eliminated in the current fray (reset when stations open). */
	private int frayDeaths;

	public synchronized int crew() { return crew; }
	public synchronized int frayDeaths() { return frayDeaths; }

	// "Name is eliminated!" - pirate names are one word; the wolves' names have two ("Sly Moe").
	private static final Pattern ELIMINATED = Pattern.compile("^([A-Z][A-Za-z]*) is eliminated!$");

	/**
	 * Elite werewolves (the ones that transform; they deal no damage) still up in the current fray:
	 * "Transformation! N werewolves have transformed!" minus elites eliminated since. -1 if unknown.
	 */
	private int elitesLeft = -1;
	public synchronized int elitesLeft() { return elitesLeft; }

	private static final Pattern TRANSFORMED = Pattern.compile("^Transformation! (\\d+) werewolves have transformed!$");
	private static final Pattern WOLF_ELIMINATED = Pattern.compile("^(\\S+) [A-Z][A-Za-z'-]* is eliminated!$");
	/** Name prefixes of transformed (elite) werewolves; the wolf-kin use a different set (Sly, Lean, ...). */
	static final java.util.Set<String> ELITE_PREFIXES = java.util.Set.of(
			"Accursed", "Blood-moon", "Bone-cracking", "Dread", "Fell", "Flesh-rending", "Frenzied", "Ghastly",
			"Gore-jawed", "Grave-howling", "Hulking", "Iron-clawed", "Marrow-hungry", "Merciless", "Monstrous",
			"Moon-cursed", "Murderous", "Night-stalking", "Nightmarish", "Rabid", "Red-eyed", "Shadow-born",
			"Silver-scarred", "Skull-splitting", "Unholy");

	/** In a den: from "Hark the dread howlin'!" until the den ends (lair plundered, overrun, retreat). */
	private boolean inDen;
	/** Frays finished in this den (each ends with "N o' M still standin'!"). */
	private int fraysDone;
	/** Werewolves left for the next wave as the chat last said; -1 until "turned back half". */
	private int wolvesLeft = -1;
	private int wolvesAtHalf = -1;
	/** When the chat last changed wolvesLeft, and when stations opened (for the pace). */
	private long wolvesChangedAt;
	/** Times of the last "turned back the last werewolf" and "30 seconds" messages (for the sounds). */
	private long lastWolfClearedAt, thirtySecondsAt;

	public synchronized boolean inDen() { return inDen; }
	/** The fray being fought or next: frays finished + 1. */
	public synchronized int frayNumber() { return fraysDone + 1; }
	record WolfProgress(int remaining, int atHalf, long changedAt) { }

	synchronized WolfProgress wolfProgress(Period period) {
		if (period == null || periods.isEmpty() || periods.get(periods.size() - 1).start != period.start) return null;
		return new WolfProgress(wolvesLeft, wolvesAtHalf, wolvesChangedAt);
	}
	public synchronized long lastWolfClearedAt() { return lastWolfClearedAt; }
	public synchronized long thirtySecondsAt() { return thirtySecondsAt; }

	private static final Pattern HALF = Pattern.compile("charting has turned back half the werewolves! (\\d+) still to come");
	private static final Pattern ONLY = Pattern.compile("charting has turned back more o' the werewolves! Only (\\d+) left");

	private void trackLair(String message, long now) {
		String arrival = ChatLog.arrivalVoyage(message);
		if (arrival != null) {
			lairArrival = "vampireLairs".equals(arrival) ? now : 0;
			lairRoundEnd = 0;
			if (lairArrival != 0) lairEntries.computeIfAbsent(now, unused -> new LairEntry(new LinkedHashSet<>(), new int[3]));
			return;
		}
		if (lairArrival == 0) return;
		LairEntry entry = lairEntries.get(lairArrival);
		if (LAIR_WARNING.equals(message)) {
			lairRoundEnd = now + 15_000;
			entry.rounds().add(lairRoundEnd);
		} else if (message.startsWith("Ye have taken aboard ")) {
			int[] haul = ChatLog.lairHaul(message);
			if (haul != null)
				for (int i = 0; i < haul.length; i++) entry.haul()[i] += haul[i];
		}
	}

	synchronized LairRound lairRound(long now) {
		return lairArrival == 0 || lairRoundEnd == 0 || now < lairRoundEnd - 2000
				? null : new LairRound(lairArrival, lairRoundEnd);
	}

	synchronized LairEntry lairEntry(long arrival) {
		LairEntry entry = lairEntries.get(arrival);
		return entry == null ? null : new LairEntry(Set.copyOf(entry.rounds()), entry.haul().clone());
	}

	synchronized void message(String msg, long now) {
		trackLair(msg, now);
		String voyage = ChatLog.arrivalVoyage(msg);
		if (voyage != null) {
			arrivalVoyage = voyage;
			arrivalAt = now;
			resetRun();
			inDen = "woodhallow".equals(voyage);
			return;
		}
		Matcher sv = SURVIVORS.matcher(msg);
		if (!inDen && !sv.matches()) return;
		Matcher hw = HALF.matcher(msg);
		if (hw.find()) {
			wolvesAtHalf = wolvesLeft = Integer.parseInt(hw.group(1));
			wolvesChangedAt = now;
			return;
		}
		Matcher on = ONLY.matcher(msg);
		if (on.find()) { wolvesLeft = Integer.parseInt(on.group(1)); wolvesChangedAt = now; return; }
		if (msg.contains("charting has turned back the last werewolf!")) {
			wolvesLeft = 0; wolvesChangedAt = now; lastWolfClearedAt = now; return;
		}
		if (msg.startsWith("The lair be plundered!") || msg.equals("The pack has overrun the crew! Ye flee back to the ship.")
				|| msg.startsWith("The crew falls back") || msg.startsWith("Ye retreat")) inDen = false;
		Matcher el = ELIMINATED.matcher(msg);
		if (el.matches()) { frayDeaths++; return; }
		Matcher tr = TRANSFORMED.matcher(msg);
		if (tr.matches()) { elitesLeft = Integer.parseInt(tr.group(1)); return; }
		Matcher we = WOLF_ELIMINATED.matcher(msg);
		if (we.matches()) {
			if (elitesLeft > 0 && ELITE_PREFIXES.contains(we.group(1))) elitesLeft--;
			return;
		}
		if (sv.matches()) {
			inDen = true;
			Period last = periods.isEmpty() ? null : periods.get(periods.size() - 1);
			if (last != null && (!last.closed() || now < last.end)) return;
			// Pirates who leave still count for the next pack.
			crew = Math.max(crew, Integer.parseInt(sv.group(2)));
			frayDeaths = 0;
			elitesLeft = -1;
			fraysDone++;
			wolvesAtHalf = wolvesLeft = -1;
			wolvesChangedAt = 0;
			Period p = new Period();
			p.start = now;
			p.survivors = Integer.parseInt(sv.group(1));
			p.crew = Integer.parseInt(sv.group(2));
			p.lair = sv.group(3).startsWith("Plunder");
			periods.add(p);
			return;
		}
		if (periods.isEmpty()) return;
		Period last = periods.get(periods.size() - 1);
		if (msg.equals("The wolf-kin gather! 30 seconds!") || msg.equals("The lair stirs! 30 seconds left to forage!")) {
			if (!last.closed()) {
				last.end = now + 30_000;
				thirtySecondsAt = now;
			}
		} else if (msg.equals("Wolf-kin approach!") || msg.equals("Beware! More wolf-kin approach!")
				|| msg.equals("We've reached their lair! Almost done!")
				|| msg.startsWith("The lair be plundered!") || msg.equals("The pack has overrun the crew! Ye flee back to the ship.")
				|| msg.startsWith("The crew falls back") || msg.startsWith("Ye retreat")) {
			if (!last.closed() || now < last.end) last.end = now;
		}
	}

	synchronized long lastArrival(String voyage) {
		return voyage != null && voyage.equals(arrivalVoyage) ? arrivalAt : 0;
	}

	private void clearArrival() {
		arrivalAt = 0;
		arrivalVoyage = null;
	}

	/** Latest station period that has ended by now (allowing for a report arriving a little early). */
	public synchronized Period lastEnded(long now) {
		for (int i = periods.size() - 1; i >= 0; i--) {
			Period p = periods.get(i);
			if (p.closed() && p.end <= now + 5000) return copyOf(p);
		}
		return null;
	}

	/**
	 * When the given period's stations close: the "30 seconds" warning's time if seen, otherwise
	 * the expected end from the survivors and crew counts.
	 */
	public synchronized long expectedEnd(Period p) {
		if (p == null) return 0;
		if (p.closed()) return p.end;
		return p.start + duration(p);
	}

	/**
	 * How long stations last, from Thalassa's duty_seconds: 2:00 for the first wave, otherwise
	 * 2:00 + 3:00 x survivors / crew ("N o' M still standin'!"). So 5:00 with everyone standing,
	 * and e.g. 3:07.5 with 3 of 8.
	 */
	public synchronized long duration(Period p) {
		boolean first = !periods.isEmpty() && periods.get(0).start == p.start;
		if (first || p.crew <= 0) return 120_000L;
		return 120_000L + Math.round(180_000.0 * Math.min(p.survivors, p.crew) / p.crew);
	}

	public synchronized Period current() {
		return periods.isEmpty() ? null : copyOf(periods.get(periods.size() - 1));
	}
	private static Period copyOf(Period p) {
		Period copy = new Period();
		copy.start = p.start;
		copy.end = p.end;
		copy.survivors = p.survivors;
		copy.crew = p.crew;
		copy.lair = p.lair;
		return copy;
	}
}
