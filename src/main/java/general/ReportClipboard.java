package main.java.general;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Advances the clipboard after the pirate's chat log confirms the previous piece was sent, or at once
 * when the global Ctrl+P hotkey asks for the next piece.
 */
final class ReportClipboard {
	static final int CHAT_LIMIT = 240;
	static final String VESSEL_COMMAND = "/ve ";
	static final int WAIT_SECONDS = 5;

	private final ArrayDeque<String> pending = new ArrayDeque<>();
	private String waitingFor;
	private LocalDateTime copiedAt;
	private LocalDateTime expiresAt;

	void replace(List<String> messages) {
		pending.clear();
		pending.addAll(messages);
		waitingFor = null;
		copiedAt = expiresAt = null;
	}

	boolean hasPending() {
		return !pending.isEmpty();
	}

	/** Ctrl+P: copy the next piece now, without waiting for the chat log to show the last one was sent. */
	boolean advance(LocalDateTime now, Consumer<String> copy) {
		if (expire(now) || pending.isEmpty()) return false;
		waitingFor = null;
		int before = pending.size();
		update(List.of(), now, copy);
		return pending.size() < before;
	}

	/** A paste keeps the current piece alive while the player finishes sending it. */
	void pasted(LocalDateTime now) {
		if (!expire(now) && waitingFor != null) expiresAt = now.plusSeconds(WAIT_SECONDS);
	}

	private boolean expire(LocalDateTime now) {
		if (expiresAt == null || now.isBefore(expiresAt)) return false;
		replace(List.of());
		return true;
	}

	void update(List<ChatLog.Line> sent, LocalDateTime now, Consumer<String> copy) {
		if (pending.isEmpty()) return;
		if (waitingFor != null) {
			boolean confirmed = sent.stream().anyMatch(line ->
					!line.time.isBefore(copiedAt) && line.time.isBefore(expiresAt)
					&& normalize(line.text).equals(waitingFor));
			if (!confirmed) { expire(now); return; }
			waitingFor = null;
			expiresAt = null;
		}
		if (expire(now)) return;
		String next = pending.getFirst();
		try {
			copy.accept(next);
		} catch (IllegalStateException ignored) {
			return; // The clipboard can be busy. Retry on the next tick.
		}
		pending.removeFirst();
		expiresAt = null;
		if (!pending.isEmpty()) {
			String body = next.startsWith(VESSEL_COMMAND) ? next.substring(VESSEL_COMMAND.length()) : next;
			waitingFor = normalize(body);
			copiedAt = now.truncatedTo(ChronoUnit.SECONDS);
			expiresAt = now.plusSeconds(WAIT_SECONDS);
		}
	}

	private static String normalize(String message) {
		return message.lines().map(String::strip).collect(Collectors.joining("\n")).strip();
	}
}
