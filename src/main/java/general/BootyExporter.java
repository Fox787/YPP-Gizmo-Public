package main.java.general;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Writes a booty division to disk in two forms:
 *  - one JSON line in &lt;client&gt;/exported_data/&lt;ocean&gt;/booty_divisions_&lt;pirate&gt;.jsonl, next to the
 *    duty_reports_&lt;pirate&gt;.jsonl files the client exports (where Thalassa already looks);
 *  - a readable N.txt in a text folder (by default &lt;chat log folder&gt;/booty_division).
 */
public final class BootyExporter {

	public static final class Result {
		public Path jsonl;
		public Path text;
		public boolean duplicate;
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

	public static Result export(BootyDivision b, Path textFolder) throws IOException {
		Result r = new Result();
		String ocean = b.ocean() == null ? "unknown" : b.ocean().toLowerCase();
		String pirate = b.pirate() == null ? "unknown" : b.pirate();
		Path dir = GameLog.clientFolder().resolve("exported_data").resolve(ocean);
		Files.createDirectories(dir);
		r.jsonl = dir.resolve("booty_divisions_" + pirate + ".jsonl");

		String key = "\"key\":\"" + b.key() + "\"";
		if (Files.exists(r.jsonl)) {
			List<String> existing = Files.readAllLines(r.jsonl, StandardCharsets.UTF_8);
			for (String line : existing) {
				if (line.contains(key)) { r.duplicate = true; return r; }
			}
		}
		Files.write(r.jsonl, (b.toJson() + "\n").getBytes(StandardCharsets.UTF_8),
				StandardOpenOption.CREATE, StandardOpenOption.APPEND);

		if (textFolder != null) {
			Files.createDirectories(textFolder);
			r.text = textFolder.resolve(nextNumber(textFolder) + ".txt");
			Files.write(r.text, b.toText().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
		}
		return r;
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
