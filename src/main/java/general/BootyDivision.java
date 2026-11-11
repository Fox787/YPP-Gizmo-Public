package main.java.general;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * One booty division as read from the client's booty screen, plus what the chat log and
 * game log say about the voyage that produced it.
 */
public class BootyDivision {

	/** One pirate's column on the booty screen. */
	public static class PirateLoot {
		public final String name;
		public int slots;
		public int hidden;
		/** item -> quantity, in screen order. Includes "N pieces of eight" entries. */
		public final Map<String, Integer> items = new LinkedHashMap<>();
		/** Items in "Divided treasure" (shared pot) slots, a subset of items. */
		public final Map<String, Integer> potItems = new LinkedHashMap<>();

		public PirateLoot(String name) { this.name = name; }

		private static final Pattern POE = Pattern.compile("^([\\d,]+) pieces? of eight$", Pattern.CASE_INSENSITIVE);

		/** Pieces of eight shown in this pirate's slots. */
		public long piecesOfEight() {
			long total = 0;
			for (Map.Entry<String, Integer> e : items.entrySet()) {
				Matcher m = POE.matcher(e.getKey());
				if (m.matches()) total += Long.parseLong(m.group(1).replace(",", "")) * e.getValue();
			}
			return total;
		}
	}

	/** When Gizmo read the screen. */
	public final LocalDateTime scannedAt = LocalDateTime.now();
	public final List<PirateLoot> pirates = new ArrayList<>();
	/** pirate -> (flagged item -> count): the "Special Drops" list. */
	public final Map<String, Map<String, Integer>> specialDrops = new LinkedHashMap<>();

	// Filled in from the logs; any of these may be null when a log isn't found.
	public GameLog.Info game;
	public ChatLog.Segment chat;
	/** Run files (RunLog) written since the last booty export: this voyage's duty reports and timings. */
	public final List<java.nio.file.Path> runFiles = new ArrayList<>();

	public boolean isEmpty() { return pirates.isEmpty(); }

	public String pirate() {
		if (game != null && game.pirate != null) return game.pirate;
		return pirates.isEmpty() ? null : pirates.get(0).name;
	}

	public String ocean() {
		return game == null ? null : game.ocean;
	}

	/** Same key for the same division, so scanning the screen twice doesn't export it twice. */
	public String key() {
		StringBuilder sb = new StringBuilder();
		if (chat != null && chat.openedAt != null) sb.append(chat.openedAt).append('|');
		for (PirateLoot p : pirates) sb.append(p.name).append(':').append(p.items).append('|');
		return Integer.toHexString(sb.toString().hashCode());
	}

	/** Readable text, in the same shape as the booty_division/N.txt files kept by hand. */
	public String toText() {
		String nl = System.lineSeparator();
		StringBuilder sb = new StringBuilder();
		if (chat != null) {
			for (ChatLog.Line l : chat.lines) {
				if (l.text.startsWith("Ye have taken aboard")) sb.append(l.text).append(nl);
			}
		}
		for (PirateLoot p : pirates) {
			sb.append("== ").append(p.name).append(" (").append(p.slots).append(" slots");
			if (p.hidden > 0) sb.append(", ").append(p.hidden).append(" HIDDEN by filter");
			sb.append(") ==").append(nl);
			for (Map.Entry<String, Integer> e : p.items.entrySet()) {
				if (e.getValue() > 1) sb.append(e.getValue()).append("x ");
				sb.append(e.getKey()).append(nl);
			}
		}
		if (!specialDrops.isEmpty()) {
			sb.append(nl).append("== Special Drops ==").append(nl);
			for (Map.Entry<String, Map<String, Integer>> e : specialDrops.entrySet()) {
				sb.append(e.getKey()).append(": ");
				boolean first = true;
				for (Map.Entry<String, Integer> it : e.getValue().entrySet()) {
					if (!first) sb.append(", ");
					first = false;
					if (it.getValue() > 1) sb.append(it.getValue()).append('x');
					sb.append(it.getKey());
				}
				sb.append(nl);
			}
		}
		if (!runFiles.isEmpty()) {
			sb.append(nl).append("== Runs (duty reports and station times) ==").append(nl);
			for (java.nio.file.Path f : runFiles) sb.append(f).append(nl);
		}
		if (chat != null && !chat.lines.isEmpty()) {
			sb.append(nl).append("== Chat ==").append(nl);
			for (ChatLog.Line l : chat.lines) sb.append(l.raw).append(nl);
		}
		return sb.toString();
	}

	/** One JSON object on a single line, for booty_divisions_<pirate>.jsonl. */
	public String toJson() {
		Json j = new Json();
		j.open();
		j.field("key", key());
		j.field("timestamp", scannedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
		j.field("scanned_at", scannedAt.toString());
		j.field("pirate", pirate());
		j.field("ocean", ocean());
		if (game != null) {
			j.field("vessel", game.vesselName);
			j.field("vessel_id", game.vesselId);
			j.field("game_log", game.file == null ? null : game.file.getFileName().toString());
		}
		if (chat != null) {
			j.field("chat_log", chat.file == null ? null : chat.file.getFileName().toString());
			j.field("opened_at", chat.openedAt == null ? null : chat.openedAt.toString());
			j.field("chests_opened", chat.chestsOpened);
			j.field("chest_items", chat.chestItems);
			j.field("chest_pieces_of_eight", chat.chestPoe);
			j.field("battles_won", chat.battlesWon);
			j.field("battles_lost", chat.battlesLost);
			j.field("plundered_pieces_of_eight", chat.plunderedPoe);
			j.field("my_cut", chat.myCut);
			j.key("taken_aboard").open();
			for (Map.Entry<String, Integer> e : chat.takenAboard.entrySet()) j.field(e.getKey(), e.getValue());
			j.close();
			j.key("chat").openArray();
			for (ChatLog.Line l : chat.lines) j.value(l.raw);
			j.closeArray();
		}
		j.key("pirates").openArray();
		for (PirateLoot p : pirates) {
			j.open();
			j.field("name", p.name);
			j.field("slots", p.slots);
			j.field("hidden", p.hidden);
			j.field("pieces_of_eight", p.piecesOfEight());
			j.key("items").open();
			for (Map.Entry<String, Integer> e : p.items.entrySet()) j.field(e.getKey(), e.getValue());
			j.close();
			j.key("pot_items").open();
			for (Map.Entry<String, Integer> e : p.potItems.entrySet()) j.field(e.getKey(), e.getValue());
			j.close();
			j.close();
		}
		j.closeArray();
		// Links to the run files; paths relative to this .jsonl's folder (exported_data/<ocean>).
		j.key("run_files").openArray();
		for (java.nio.file.Path f : runFiles) j.value(f.getParent() != null && f.getParent().getFileName() != null
				? f.getParent().getFileName() + "/" + f.getFileName() : f.toString());
		j.closeArray();
		j.key("special_drops").open();
		for (Map.Entry<String, Map<String, Integer>> e : specialDrops.entrySet()) {
			j.key(e.getKey()).open();
			for (Map.Entry<String, Integer> it : e.getValue().entrySet()) j.field(it.getKey(), it.getValue());
			j.close();
		}
		j.close();
		j.close();
		return j.toString();
	}

	/** Minimal JSON writer; Gizmo has no libraries on the client's classpath. */
	static final class Json {
		private final StringBuilder sb = new StringBuilder();
		private boolean needComma = false;

		private void comma() { if (needComma) sb.append(','); needComma = false; }

		Json open() { comma(); sb.append('{'); return this; }
		Json close() { sb.append('}'); needComma = true; return this; }
		Json openArray() { comma(); sb.append('['); return this; }
		Json closeArray() { sb.append(']'); needComma = true; return this; }

		Json key(String k) { comma(); str(k); sb.append(':'); return this; }

		Json value(Object v) {
			comma();
			if (v == null) sb.append("null");
			else if (v instanceof Number || v instanceof Boolean) sb.append(v);
			else str(v.toString());
			needComma = true;
			return this;
		}

		Json field(String k, Object v) { key(k); return value(v); }

		private void str(String s) {
			sb.append('"');
			for (int i = 0; i < s.length(); i++) {
				char c = s.charAt(i);
				switch (c) {
					case '"': sb.append("\\\""); break;
					case '\\': sb.append("\\\\"); break;
					case '\n': sb.append("\\n"); break;
					case '\r': sb.append("\\r"); break;
					case '\t': sb.append("\\t"); break;
					default:
						if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
						else sb.append(c);
				}
			}
			sb.append('"');
		}

		@Override public String toString() { return sb.toString(); }
	}
}
