package main.java.general;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** Advances the clipboard after the pirate's chat log confirms the previous piece was sent. */
final class ReportClipboard {
	static final int CHAT_LIMIT = 240;
	static final String VESSEL_COMMAND = "/ve ";

	private final ArrayDeque<String> pending = new ArrayDeque<>();
	private String waitingFor;
	private LocalDateTime copiedAt;

	void replace(List<String> messages) {
		pending.clear();
		pending.addAll(messages);
		waitingFor = null;
	}

	boolean hasPending() {
		return !pending.isEmpty();
	}

	void update(List<ChatLog.Line> sent, LocalDateTime now, Consumer<String> copy) {
		if (pending.isEmpty()) return;
		if (waitingFor != null) {
			boolean confirmed = sent.stream().anyMatch(line ->
					!line.time.isBefore(copiedAt) && normalize(line.text).equals(waitingFor));
			if (!confirmed) return;
			waitingFor = null;
		}
		String next = pending.getFirst();
		try {
			copy.accept(next);
		} catch (IllegalStateException ignored) {
			return; // The clipboard can be busy. Retry on the next tick.
		}
		pending.removeFirst();
		if (!pending.isEmpty()) {
			String body = next.startsWith(VESSEL_COMMAND) ? next.substring(VESSEL_COMMAND.length()) : next;
			waitingFor = normalize(body);
			copiedAt = now.truncatedTo(ChronoUnit.SECONDS);
		}
	}

	private static String normalize(String message) {
		return message.lines().map(String::strip).collect(Collectors.joining("\n")).strip();
	}
}
