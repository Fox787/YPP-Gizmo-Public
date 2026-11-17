package main.java.general;

/**
 * Cursed Isles: whether a merc (swabbie) should be planked before the next fray, as Thalassa's
 * original plank check worked. Fed lines of this client's game log (by RumbleClock, on its file thread)
 * and live chat log messages (by StationClock, on the log thread); read on the event thread.
 *
 *   CI start   "Enabling environment mod for skirmish [mod=dark_seas]"
 *   CI end     "Disabling skirmish environment mod [mod=dark_seas]"
 *   team       each fray's "Reporting ready data.BoxingObject/SwordObject:id(names...)" line: pirates have
 *              one-word names, thralls are "X's Thrall", the enemies are zombies, cultists, homunculi, the
 *              Enlightened One and Vargas the Mad, and every other two-word name is a merc
 *   fray won   each "Stopping foraging in 119 seconds"
 *
 * The next fray takes at most CAPS[frays won] of pirates + mercs (2 after the fifth), so when more
 * are aboard, that many mercs (no more than there are) should be planked during the forage.
 */
public final class MercPlank {

	static final int[] CAPS = {4, 4, 3, 3, 2};
	private static final String[] ENEMIES = {" zombie", "enlightened one", "vargas the mad", " homunculus", " cultist"};

	private boolean inCi;
	private int fraysWon;
	private int pirates, mercs;
	/** From a fray won (forage starting) until the next fray's team line: the time to plank. */
	private boolean betweenFrays;

	/** One game log line. Returns true when it was a fray won (a forage period starting). */
	synchronized boolean feed(String line) {
		if (line.contains("Enabling environment mod for skirmish [mod=dark_seas]")) {
			inCi = true; fraysWon = 0; pirates = 0; mercs = 0; betweenFrays = false;
			return false;
		}
		if (line.contains("Disabling skirmish environment mod [mod=dark_seas]")) {
			inCi = false; fraysWon = 0; pirates = 0; mercs = 0; betweenFrays = false;
			return false;
		}
		if (line.contains("Reporting ready data.BoxingObject:") || line.contains("Reporting ready data.SwordObject:")) {
			team(line);
			return false;
		}
		if (inCi && line.contains("Stopping foraging in ")) {
			fraysWon++;
			betweenFrays = true;
			return true;
		}
		return false;
	}

	private void team(String line) {
		int open = line.indexOf('('), close = line.lastIndexOf(')');
		if (open < 0 || close <= open) return;
		int p = 0, m = 0;
		boolean ciEnemies = false;
		for (String raw : line.substring(open + 1, close).split(",")) {
			String name = raw.trim();
			if (name.isEmpty()) continue;
			String lower = " " + name.toLowerCase();
			boolean enemy = false;
			for (String e : ENEMIES) if (lower.contains(e)) { enemy = true; break; }
			if (enemy) { ciEnemies = true; continue; }
			if (name.endsWith(" Thrall")) continue;
			if (name.contains(" ")) m++;
			else p++;
		}
		// Woodhallow's wolves also have two-word names: only count a team that is fighting CI's enemies.
		if (!ciEnemies) return;
		inCi = true;   // also when Gizmo started part way through the CI
		pirates = p;
		mercs = m;
		betweenFrays = false;
	}

	private static final java.util.regex.Pattern SWABBIES_LEFT = java.util.regex.Pattern.compile("^(\\d+) swabbies have left the vessel\\.$");
	private static final java.util.regex.Pattern PIRATE_LEFT = java.util.regex.Pattern.compile("^[A-Z][A-Za-z]* has left the vessel\\.$");

	/**
	 * One chat log message (live, not replayed): mercs and pirates leaving between frays, as PP_Timer
	 * counted them. "A swabbie has left the vessel." (a plank), "N swabbies have left the vessel.",
	 * "Name has left the vessel."
	 */
	synchronized void chat(String msg) {
		if (msg.equals("A swabbie has left the vessel.")) { mercs = Math.max(0, mercs - 1); return; }
		java.util.regex.Matcher m = SWABBIES_LEFT.matcher(msg);
		if (m.matches()) { mercs = Math.max(0, mercs - Integer.parseInt(m.group(1))); return; }
		if (PIRATE_LEFT.matcher(msg).matches()) pirates = Math.max(0, pirates - 1);
	}

	/** True from a fray won until the next fray starts. */
	public synchronized boolean betweenFrays() { return inCi && betweenFrays; }

	/** Mercs to plank before the next fray, 0 if none (or not at Cursed Isles). */
	public synchronized int toPlank() {
		if (!inCi || mercs <= 0 || fraysWon <= 0) return 0;
		int cap = fraysWon < CAPS.length ? CAPS[fraysWon] : 2;
		return Math.max(0, Math.min(mercs, pirates + mercs - cap));
	}

	public synchronized int fraysWon() { return fraysWon; }
	public synchronized int pirates() { return pirates; }
	public synchronized int mercs() { return mercs; }
}
