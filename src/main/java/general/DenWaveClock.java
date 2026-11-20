package main.java.general;

import java.awt.Component;
import java.awt.Container;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JLabel;

/**
 * When Woodhallow's stations end, from the client's own "Next wave in: m:ss" countdown: a
 * com.threerings.piracy.swing.TimeLabel (a JLabel) whose text is the server's time left.
 *
 * Read from its visible text once a second while at Woodhallow. The end (now + time left) is kept, so
 * it keeps counting while the label isn't showing (another side tab open); a new reading moves it only
 * when it disagrees by more than STEADY_MS, so the one-second text doesn't make it jitter.
 */
final class DenWaveClock {
	static final String TIME_LABEL = "com.threerings.piracy.swing.TimeLabel";
	static final long STEADY_MS = 1_500;
	private static final Pattern CLOCK = Pattern.compile("^\\s*(\\d{1,2}):(\\d{2})\\s*$");

	private Component label;
	private long end;

	/** Reads the countdown if it's showing; returns the stations' end (ms), or 0 if none is known. */
	long read(Container game, long now) {
		long left = timeLeft(game);
		if (left > 0) observe(left, now);
		return end;
	}

	/** One reading of the time left: the end moves only if it's off by more than STEADY_MS. */
	void observe(long leftMs, long now) {
		long reading = now + leftMs;
		if (end == 0 || Math.abs(reading - end) > STEADY_MS) end = reading;
	}

	/** The last end known (ms), 0 if none. */
	long end() { return end; }

	/** Forget the end: a new station period starts. */
	void reset() { end = 0; }

	/** The showing countdown's time left (ms), or -1. */
	private long timeLeft(Container game) {
		if (label == null || !label.isShowing()) label = find(game, 0);
		return label instanceof JLabel l ? parse(l.getText()) : -1;
	}

	private static Component find(Component c, int depth) {
		if (c == null || !c.isShowing() || depth > 60) return null;
		if (isTimeLabel(c.getClass())) return c;
		if (c instanceof Container k) {
			for (Component child : k.getComponents()) {
				Component found = find(child, depth + 1);
				if (found != null) return found;
			}
		}
		return null;
	}

	private static boolean isTimeLabel(Class<?> type) {
		for (Class<?> k = type; k != null; k = k.getSuperclass())
			if (k.getName().equals(TIME_LABEL) || k.getSimpleName().equals("TimeLabel")) return true;
		return false;
	}

	/** "3:49" as milliseconds, -1 if it isn't m:ss. */
	static long parse(String text) {
		if (text == null) return -1;
		Matcher m = CLOCK.matcher(text);
		if (!m.matches()) return -1;
		int seconds = Integer.parseInt(m.group(2));
		return seconds >= 60 ? -1 : (Integer.parseInt(m.group(1)) * 60L + seconds) * 1000;
	}
}
