package main.java.general;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VesselPiecesTest {
	@Test
	void packsWholeLinesIntoVesselMessagesWithinTheChatLimit() {
		assertEquals(List.of("/ve A 1\nB 2"), DRGizmoGUI.vesselPieces("A 1\r\nB 2\n"));
		StringBuilder text = new StringBuilder();
		for (int i = 0; i < 40; i++) text.append("Pirate").append(i).append(" 12 3.45\n");
		List<String> pieces = DRGizmoGUI.vesselPieces(text.toString());
		assertTrue(pieces.size() > 1);
		for (String piece : pieces) {
			assertTrue(piece.startsWith("/ve "));
			assertTrue(piece.length() <= ReportClipboard.CHAT_LIMIT);
		}
		assertEquals(40, String.join("\n", pieces).replace("/ve ", "").lines().count());
	}
}
