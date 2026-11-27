package main.java.general;

import java.util.Locale;
import java.util.regex.Pattern;

/** CI swordfight lifecycle, from live NPC cues and the client's actual puzzle/team messages. */
final class CiSwordfight {
	private static final Pattern CULTIST = Pattern.compile("^[\\w'-]+ Cultist (?:shouts|says), \\\".*", Pattern.DOTALL);
	private boolean preview, active, homunculi;
	private long round;
	private String swordId;

	synchronized void chat(String message) {
		if (CULTIST.matcher(message).matches()) {
			if (!preview && !active) { round++; preview = true; }
		} else if (message.startsWith("Enlightened One says, ") || message.startsWith("Game over.")
				|| message.equals(StationClock.ARRIVAL) || ChatLog.arrivalVoyage(message) != null
				|| message.startsWith("With the islanders subdued,")) {
			clear();
		}
	}

	synchronized void feed(String line) {
		if (line.contains("Disabling skirmish environment mod [mod=dark_seas]")
				|| line.contains("Adding chat display for entering scene ")
				|| line.contains("Removing chat display for leaving scene [scene=data.GauntletSceneObject:")
				|| line.contains("Stopping foraging in ")
				|| line.contains("replaced=class com.threerings.piracy.puzzle.sword.client.")) {
			clear(); return;
		}
		if (line.contains("Reporting ready data.BoxingObject:")) { clear(); return; }
		int sword = line.indexOf("Reporting ready data.SwordObject:");
		if (sword < 0) return;
		int open = line.indexOf('(', sword), close = line.lastIndexOf(')');
		if (open < 0 || close <= open) return;
		String team = line.substring(open + 1, close).toLowerCase(Locale.ROOT);
		if (!team.contains(" cultist") && !team.contains(" homunculus")) { clear(); return; }
		String id = line.substring(sword, open);
		if (active && id.equals(swordId)) return;
		if (!preview) round++;
		swordId = id;
		active = true;
		preview = false;
		homunculi = team.contains(" homunculus");
	}

	private void clear() { preview = active = homunculi = false; swordId = null; }
	synchronized void reset() { clear(); }
	/** Only once the swordfight's team has a homunculus in it; a cultist's cue alone doesn't show the counter. */
	synchronized boolean showColours() { return active && homunculi; }
	synchronized long round() { return round; }
}
