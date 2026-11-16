package main.java.general;

import java.util.function.IntSupplier;

/** Keeps the last visible Ahoy reading for this round, independently of chat milestones. */
final class WolfCount {
	private int visible = -1;
	private long changedAt;

	int read(boolean chatOnly, IntSupplier readAhoy, int fromChat, long chatChangedAt, long now) {
		if (chatOnly) {
			changedAt = chatChangedAt;
			return fromChat;
		}
		int fromAhoy = readAhoy.getAsInt();
		if (fromAhoy >= 0 && fromAhoy != visible) {
			visible = fromAhoy;
			changedAt = now;
		}
		return visible;
	}

	long changedAt() { return changedAt; }

	void reset() {
		visible = -1;
		changedAt = 0;
	}
}
