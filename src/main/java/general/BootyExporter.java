package main.java.general;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes a booty division to disk in two forms:
 *  - one JSON line in the data folder under &lt;ocean&gt;/booty_divisions_&lt;pirate&gt;.jsonl;
 *  - a readable N.txt in a text folder (by default &lt;chat log folder&gt;/booty_division).
 */
public final class BootyExporter {

	public static final class Result {
		public Path jsonl;
		public Path text;
		public boolean duplicate;
		public boolean replaced;
		public String textError;
	}

	private BootyExporter() { }

	/** Fills in log context: game log first (it names the pirate and ocean), then the matching chat log. */
	public static void attachLogs(BootyDivision b, Path chatFolder, StringBuilder notes) {
		b.game = GameLog.read();
		if (b.game == null) notes.append("game log: not found in ").append(GameLog.clientFolder()).append('\n');
		else notes.append("game log: ").append(b.game.file.getFileName())
				.append(" (").append(b.game.pirate).append(", ").append(b.game.ocean)
				.append(b.game.vesselName == null ? "" : ", " + b.game.vesselName).append(")\n");

		Path log = ChatLog.findLog(chatFolder, b.pirate(), b.ocean());
		if (log == null) {
			notes.append("chat log: none found for ").append(b.pirate()).append(" in ").append(chatFolder).append('\n');
			return;
		}
		try {
			b.chat = ChatLog.latestBooty(log);
			notes.append("chat log: ").append(log.getFileName()).append(", ").append(b.chat.lines.size()).append(" booty lines")
					.append(b.chat.openedAt == null ? " (no chests opened yet)" : " up to " + b.chat.openedAt).append('\n');
		} catch (IOException e) {
			notes.append("chat log: couldn't read ").append(log).append(": ").append(e.getMessage()).append('\n');
		}
	}

	public static synchronized Result export(BootyDivision division, Path textFolder) throws IOException {
		Result result = new Result();
		String ocean = division.ocean() == null ? "unknown" : division.ocean().toLowerCase(Locale.ROOT);
		String pirate = division.pirate() == null ? "unknown" : division.pirate();
		Path folder = GameLog.dataFolder().resolve(ocean).toAbsolutePath().normalize();
		Files.createDirectories(folder);
		result.jsonl = folder.resolve("booty_divisions_" + pirate + ".jsonl");
		Path lock = result.jsonl.resolveSibling(result.jsonl.getFileName() + ".lock");
		try (var channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
			 var ignored = channel.lock()) {
			saveDivision(division, textFolder, result);
		}
		return result;
	}

	private static void saveDivision(BootyDivision division, Path textFolder, Result result) throws IOException {
		Path folder = result.jsonl.getParent();
		String key = division.key();
		String opened = division.chat == null || division.chat.openedAt == null ? null : division.chat.openedAt.toString();
		List<String> lines = Files.exists(result.jsonl)
				? Files.readAllLines(result.jsonl, StandardCharsets.UTF_8) : new ArrayList<>();
		int match = -1;
		boolean exact = false;
		for (int i = 0; i < lines.size(); i++) {
			String line = lines.get(i);
			if (key.equals(field(line, "key"))) {
				match = i;
				exact = true;
				break;
			}
			if (opened != null && opened.equals(field(line, "opened_at"))) match = i;
		}

		if (match >= 0) {
			String old = lines.get(match);
			division.runFiles.clear();
			for (String reference : fields(old, "run_files"))
				division.runFiles.add(folder.resolve(reference).normalize());
			division.textFile = existingTextFile(old, textFolder);
			if (exact || knownQuantity(division) <= knownQuantity(old)) {
				result.duplicate = true;
				if (exact && textFolder != null && division.textFile != null) writeTextCopy(division, result);
				return;
			}
		}

		boolean reserved = false;
		if (division.textFile == null && textFolder != null) {
			try {
				division.textFile = reserveTextFile(textFolder.toAbsolutePath().normalize());
				reserved = true;
			} catch (IOException e) {
				result.textError = e.getMessage();
			}
		}
		try {
			String json = division.toJson(folder);
			if (match >= 0) lines.set(match, json);
			else lines.add(json);
			writeAtomically(result.jsonl, String.join("\n", lines) + "\n");
		} catch (IOException | RuntimeException e) {
			if (reserved) {
				try { Files.deleteIfExists(division.textFile); }
				catch (IOException cleanup) { e.addSuppressed(cleanup); }
			}
			throw e;
		}
		result.replaced = match >= 0;
		if (textFolder != null && division.textFile != null) writeTextCopy(division, result);
	}

	private static Path existingTextFile(String json, Path textFolder) {
		String name = field(json, "text_file");
		if (name == null || !NUMBERED.matcher(name).matches()) return null;
		String previous = field(json, "text_file_path");
		if (textFolder == null) return previous == null ? null : Path.of(previous).normalize();
		Path path = textFolder.toAbsolutePath().normalize().resolve(name);
		return previous == null || path.equals(Path.of(previous).normalize()) ? path : null;
	}

	private static Path reserveTextFile(Path folder) throws IOException {
		Files.createDirectories(folder);
		for (int number = nextNumber(folder); ; number++) {
			try {
				return Files.createFile(folder.resolve(number + ".txt"));
			} catch (FileAlreadyExistsException ignored) { }
		}
	}

	private static void writeTextCopy(BootyDivision division, Result result) {
		try {
			Files.createDirectories(division.textFile.getParent());
			writeAtomically(division.textFile, division.toText());
			result.text = division.textFile;
		} catch (IOException e) {
			result.textError = e.getMessage();
		}
	}

	private static void writeAtomically(Path path, String text) throws IOException {
		Path staged = Files.createTempFile(path.getParent(), ".gizmo-", ".tmp");
		try {
			Files.writeString(staged, text, StandardCharsets.UTF_8);
			try {
				Files.move(staged, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException ignored) {
				Files.move(staged, path, StandardCopyOption.REPLACE_EXISTING);
			}
		} finally {
			Files.deleteIfExists(staged);
		}
	}

	private static final String JSON_STRING = "\"((?:[^\"\\\\]|\\\\.)*)\"";
	private static final Pattern QUOTED = Pattern.compile(JSON_STRING);
	private static final Pattern ITEMS = Pattern.compile("\"items\":\\{((?:" + JSON_STRING + ":\\d+,?)*)\\}");
	private static final Pattern QUANTITY = Pattern.compile(JSON_STRING + ":(\\d+)");

	private static long knownQuantity(BootyDivision division) {
		return division.pirates.stream().flatMap(pirate -> pirate.items.values().stream())
				.mapToLong(Integer::longValue).sum();
	}

	private static long knownQuantity(String json) {
		long count = 0;
		Matcher items = ITEMS.matcher(json);
		while (items.find()) {
			Matcher quantities = QUANTITY.matcher(items.group(1));
			while (quantities.find()) count += Long.parseLong(quantities.group(2));
		}
		return count;
	}

	private static String field(String json, String name) {
		List<String> values = fields(json, name);
		return values.isEmpty() ? null : values.get(0);
	}

	private static List<String> fields(String json, String name) {
		Matcher value = Pattern.compile("\"" + name + "\":(" + JSON_STRING
				+ "|\\[(?:" + JSON_STRING + ",?)*\\])").matcher(json);
		if (!value.find()) return List.of();
		List<String> strings = new ArrayList<>();
		Matcher quoted = QUOTED.matcher(value.group(1));
		while (quoted.find()) strings.add(quoted.group(1).replace("\\\"", "\"").replace("\\\\", "\\"));
		return strings;
	}

	private static final Pattern NUMBERED = Pattern.compile("^(\\d+)\\.txt$");

	/** One more than the highest N.txt already in folder. */
	static int nextNumber(Path folder) throws IOException {
		int max = 0;
		try (DirectoryStream<Path> ds = Files.newDirectoryStream(folder, "*.txt")) {
			for (Path p : ds) {
				Matcher m = NUMBERED.matcher(p.getFileName().toString());
				if (m.matches()) max = Math.max(max, Integer.parseInt(m.group(1)));
			}
		}
		return max + 1;
	}
}
