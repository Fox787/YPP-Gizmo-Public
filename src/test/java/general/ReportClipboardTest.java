package main.java.general;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ReportClipboardTest {
	private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 23, 12, 0, 0, 800_000_000);

	@Test
	void cancelsAnUnpastedQueueAtFiveSecondsWithoutChangingTheClipboard() {
		var clipboard = new ReportClipboard();
		var copied = new ArrayList<String>();
		clipboard.replace(List.of("First", "Second"));
		clipboard.update(List.of(), NOW, copied::add);
		clipboard.update(List.of(), NOW.plusNanos(4_999_000_000L), copied::add);
		assertTrue(clipboard.hasPending(), "the exact copy time controls expiry, not the rounded chat timestamp");
		clipboard.update(List.of(), NOW.plusSeconds(5), copied::add);
		assertFalse(clipboard.hasPending());
		assertEquals(List.of("First"), copied);
		assertFalse(clipboard.advance(NOW.plusSeconds(6), copied::add));
	}

	@Test
	void aLateHotkeyOrPasteCannotReviveExpiredPieces() {
		var clipboard = new ReportClipboard();
		var copied = new ArrayList<String>();
		clipboard.replace(List.of("First", "Second"));
		clipboard.update(List.of(), NOW, copied::add);
		clipboard.pasted(NOW.plusSeconds(5));
		assertFalse(clipboard.advance(NOW.plusSeconds(5), copied::add));
		assertFalse(clipboard.hasPending());
		assertEquals(List.of("First"), copied);
	}

	@Test
	void pastingAndAdvancingRenewTheFiveSecondWindow() {
		var clipboard = new ReportClipboard();
		var copied = new ArrayList<String>();
		clipboard.replace(List.of("First", "Second", "Third"));
		clipboard.update(List.of(), NOW, copied::add);
		clipboard.pasted(NOW.plusSeconds(4));
		clipboard.update(List.of(), NOW.plusSeconds(6), copied::add);
		assertTrue(clipboard.hasPending());
		assertTrue(clipboard.advance(NOW.plusSeconds(8), copied::add));
		clipboard.update(List.of(), NOW.plusSeconds(12), copied::add);
		assertTrue(clipboard.hasPending());
		clipboard.update(List.of(), NOW.plusSeconds(13), copied::add);
		assertFalse(clipboard.hasPending());
		assertEquals(List.of("First", "Second"), copied);
	}

	@Test
	void confirmationGetsAFreshDeadlineAndLateConfirmationIsIgnored() {
		var clipboard = new ReportClipboard();
		var copied = new ArrayList<String>();
		clipboard.replace(List.of("/ve First", "/ve Second", "/ve Third"));
		clipboard.update(List.of(), NOW, copied::add);
		clipboard.update(List.of(new ChatLog.Line(NOW.plusSeconds(4), "First", "")), NOW.plusSeconds(4), copied::add);
		clipboard.update(List.of(), NOW.plusSeconds(6), copied::add);
		assertTrue(clipboard.hasPending());
		clipboard.update(List.of(new ChatLog.Line(NOW.plusSeconds(9), "Second", "")), NOW.plusSeconds(9), copied::add);
		assertFalse(clipboard.hasPending());
		assertEquals(List.of("/ve First", "/ve Second"), copied);
	}

	@Test
	void replacingAnExpiredQueueStartsFresh() {
		var clipboard = new ReportClipboard();
		var copied = new ArrayList<String>();
		clipboard.replace(List.of("Old first", "Old second"));
		clipboard.update(List.of(), NOW, copied::add);
		clipboard.replace(List.of("New first", "New second"));
		clipboard.update(List.of(), NOW.plusSeconds(10), copied::add);
		assertTrue(clipboard.hasPending());
		assertEquals(List.of("Old first", "New first"), copied);
	}
}
