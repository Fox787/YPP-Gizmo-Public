package main.java.general;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.List;

/**
 * Records how many werewolves each Woodhallow pack really has, to learn how the game rounds the
 * werewolf share of the pack (always up, or at random by the fraction).
 *
 * When stations open, before anyone can chart, the island object already holds the next pack:
 * packWerewolves (W) and packWolfmen (K). The whole pack is W + K, and the sheet's model says
 * W = pack x (0.4 + 0.1 x fray) for the fray those stations prepare. Each station period adds one
 * line to exported_data/werewolf_rounding.jsonl with the figures and which way W was rounded:
 *   "up"    W is the pack x share rounded up (and that isn't a whole number)
 *   "down"  W is it rounded down
 *   "whole" pack x share is a whole number, so it says nothing about rounding
 *   "other" neither (the model or the reading is off)
 */
public final class PackRecorder {

	private long recordedFor;   // roundEndsAt of the period already recorded
	private int up, down, whole, other;

	public PackRecorder() {
		// Totals so far, from the file.
		try {
			Path f = file();
			if (Files.exists(f)) {
				List<String> lines = Files.readAllLines(f, StandardCharsets.UTF_8);
				for (String l : lines) count(l.contains("\"rounded\":\"up\"") ? "up" : l.contains("\"rounded\":\"down\"") ? "down"
						: l.contains("\"rounded\":\"whole\"") ? "whole" : "other");
			}
		} catch (IOException | RuntimeException ignored) { }
	}

	static Path file() {
		return GameLog.clientFolder().resolve("exported_data").resolve("werewolf_rounding.jsonl");
	}

	private void count(String v) {
		switch (v) {
			case "up": up++; break;
			case "down": down++; break;
			case "whole": whole++; break;
			default: other++;
		}
	}

	/** "up 12, down 0, whole 3, other 1" for the Debug line. */
	public String summary() {
		return "Werewolf rounding: up " + up + ", down " + down + ", whole " + whole + ", other " + other;
	}

	/**
	 * Called with each island reading during stations. Records the first reading of each station
	 * period (the pack before any charting). {@code crew} and {@code survivors} come from the latest
	 * "N o' M still standin'!" line (0 if unknown).
	 */
	public void onStations(WolfWatch.Reading r, int crew, int survivors) {
		if (r == null || r.roundEndsAt == 0 || r.roundEndsAt == recordedFor || r.round < 1 || r.round > 5) return;
		recordedFor = r.roundEndsAt;
		int fray = r.round + 1;
		int pack = r.werewolves + r.wolfkin;
		double share = 0.4 + 0.1 * fray;
		double x = pack * share;
		long lo = (long) Math.floor(x + 1e-9), hi = (long) Math.ceil(x - 1e-9);
		String rounded = lo == hi ? (r.werewolves == lo ? "whole" : "other")
				: r.werewolves == hi ? "up" : r.werewolves == lo ? "down" : "other";
		count(rounded);
		String line = String.format(java.util.Locale.ROOT,
				"{\"time\":%d,\"round\":%d,\"fray\":%d,\"crew\":%d,\"survivors\":%d,\"pack\":%d,\"werewolves\":%d,\"wolfkin\":%d,"
						+ "\"share\":%.1f,\"pack_x_share\":%.3f,\"model_pack_crew\":%.3f,\"model_pack_survivors\":%.3f,\"rounded\":\"%s\"}%n",
				System.currentTimeMillis(), r.round, fray, crew, survivors, pack, r.werewolves, r.wolfkin, share, x,
				crew > 0 ? DnavForecast.packRaw(crew, fray) : 0.0, survivors > 0 ? DnavForecast.packRaw(survivors, fray) : 0.0, rounded);
		try {
			Path f = file();
			Files.createDirectories(f.getParent());
			Files.write(f, line.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		} catch (IOException | RuntimeException ignored) { }
	}
}
