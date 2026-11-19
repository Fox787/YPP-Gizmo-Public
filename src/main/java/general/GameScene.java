package main.java.general;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which voyage activity the client is in, from its own logs instead of the duty report's counter enum.
 *
 * Game log: the client logs every scene it enters and leaves, e.g.
 *   "Adding chat display for entering scene [scene=data.WerewolfIsleSceneObject:650818]"
 *   "Removing chat display for leaving scene [scene=data.GauntletSceneObject:586992]"
 * WerewolfIsleSceneObject is the Den (Woodhallow) and GauntletSceneObject the Cursed Isles.
 *
 * Chat log: the Lair is told by its arrival line ("Welcome to the vampire sanctum."), held until the
 * game log shows the client moving to another scene. VampireLairSceneObject is also mapped, though
 * its game log line hasn't been seen yet.
 *
 * Fed by RumbleClock on its file thread, read on the UI thread.
 */
final class GameScene {

	/** A report scanned this soon after leaving an activity's scene still belongs to it. */
	static final long LEFT_GRACE_MS = 120_000;
	/** The chat arrival line comes a moment after the game log's scene change. */
	static final long ARRIVAL_SLACK_MS = 5_000;
	/** A Lair arrival older than this no longer tags reports (a whole voyage, with room to spare). */
	static final long LAIR_MAX_MS = 3 * 60 * 60_000L;

	private static final Pattern ENTER = Pattern.compile("Adding chat display for entering scene \\[scene=(?:\\w+\\.)*(\\w+):");
	private static final Pattern LEAVE = Pattern.compile("Removing chat display for leaving scene \\[scene=(?:\\w+\\.)*(\\w+):");

	private volatile String activity;          // the activity of the scene the client is in, or null
	private volatile String leftActivity;      // the last activity scene left
	private volatile long leftAt, changedAt;   // game log times (ms), 0 if none seen

	/** RecordChecker.DEN / CI / LAIR for a scene class's simple name; null for any other scene. */
	static String activityOf(String sceneClass) {
		if (sceneClass == null) return null;
		return switch (sceneClass) {
			case "WerewolfIsleSceneObject" -> RecordChecker.DEN;
			case "GauntletSceneObject" -> RecordChecker.CI;
			case "VampireLairSceneObject" -> RecordChecker.LAIR;
			default -> null;
		};
	}

	void feed(String line, long at) {
		if (!line.contains("chat display for ")) return;
		Matcher enter = ENTER.matcher(line);
		if (enter.find()) {
			activity = activityOf(enter.group(1));
			changedAt = at;
			return;
		}
		Matcher leave = LEAVE.matcher(line);
		if (leave.find()) {
			String left = activityOf(leave.group(1));
			if (left != null) {
				leftActivity = left;
				leftAt = at;
			}
			activity = null;
			changedAt = at;
		}
	}

	/** The scene's activity, or the one left within LEFT_GRACE_MS; null if neither. */
	String activity(long now) {
		String current = activity;
		if (current != null) return current;
		return leftActivity != null && now - leftAt <= LEFT_GRACE_MS ? leftActivity : null;
	}

	/** When the game log last showed a scene change (ms), 0 if none since Gizmo started reading it. */
	long changedAt() { return changedAt; }

	/**
	 * The activity to tag a report with: the game log's scene first, then a Lair arrival from chat that
	 * no later scene change has replaced. Null when the logs can't tell; the caller then falls back to
	 * the duty report's counters.
	 */
	String activity(long now, long lairArrival) {
		String fromScene = activity(now);
		if (fromScene != null) return fromScene;
		if (lairArrival != 0 && now - lairArrival <= LAIR_MAX_MS && changedAt <= lairArrival + ARRIVAL_SLACK_MS)
			return RecordChecker.LAIR;
		return null;
	}
}
