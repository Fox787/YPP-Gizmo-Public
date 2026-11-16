package main.java.general;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.SwingUtilities;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.prefs.Preferences;

import static main.java.general.SpecialDrops.Scope.OWN;
import static main.java.general.SpecialDrops.Scope.SHIPWIDE;
import static org.junit.jupiter.api.Assertions.*;

class SpecialDropsTest {

	@Test
	void remembersItemAndScopeChangesIncludingAnEmptyList() throws Exception {
		String path = "GizmoSpecialDropsTest/" + UUID.randomUUID();
		Preferences prefs = Preferences.userRoot().node(path);
		try {
			assertEquals(SHIPWIDE, SpecialDrops.scopeOf(SpecialDrops.load(prefs), "Serpent (green)"));
			var rules = List.of(new SpecialDrops.Rule("Paint Brush (blue)", OWN), new SpecialDrops.Rule("Rat", SHIPWIDE));
			SpecialDrops.save(prefs, rules);
			prefs.flush();
			assertEquals(rules, SpecialDrops.load(Preferences.userRoot().node(path)));
			SpecialDrops.save(prefs, List.of());
			assertTrue(SpecialDrops.load(prefs).isEmpty());
		} finally {
			prefs.removeNode();
		}
	}

	@Test
	void matchesPartOfTheTooltipAndUsesTheLongestRule() {
		var rules = List.of(new SpecialDrops.Rule("fireplace", SHIPWIDE), new SpecialDrops.Rule("Fancy Fireplace", OWN));
		assertEquals(SHIPWIDE, SpecialDrops.scopeOf(rules, "FIREPLACE"));
		assertEquals(SHIPWIDE, SpecialDrops.scopeOf(rules, "Fireplace Screen"));
		assertEquals(SHIPWIDE, SpecialDrops.scopeOf(rules, "Stone Fireplace (oak)"));
		assertEquals(OWN, SpecialDrops.scopeOf(rules, "Fancy Fireplace (oak)"));
		assertEquals(OWN, SpecialDrops.scopeOf(List.of(rules.get(1), rules.get(0)), "Fancy Fireplace (oak)"));
		assertNull(SpecialDrops.scopeOf(rules, "Stone Mantel"));
	}

	@Test
	void scanUsesPerItemAlertScopesAndKeepsTrackedWinsInTheExport() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			JFrame game = new JFrame("Puzzle Pirates - MyPirate on the Emerald ocean");
			try {
				JPanel booty = new JPanel();
				booty.add(pirate("Crewmate", slot("Serpent (green)", 2, "Won"), slot("Rare trinket", 1, "Won")));
				booty.add(pirate("Mypirate", slot("Serpent (blue)", 3, "Won"), slot("Krakling (red)", 1, "Won"),
						slot("Rare trinket", 1, "Divided treasure")));
				game.setContentPane(booty);
				var rules = List.of(new SpecialDrops.Rule("Serpent", OWN), new SpecialDrops.Rule("Rare trinket", SHIPWIDE));
				JTextArea output = new JTextArea();
				DRGizmo scanner = new DRGizmo(game, output, false);
				assertNull(scanner.scanDutyReport(rules));
				assertEquals(List.of("== Booty Alerts ==", "Crewmate: Rare trinket", "Mypirate: 3x Serpent (blue)"),
						output.getText().lines().toList());
				assertEquals(Map.of("Serpent (green)", 2, "Rare trinket", 1),
						scanner.getBootyDivision().specialDrops.get("Crewmate"));
				assertEquals(Map.of("Serpent (blue)", 3), scanner.getBootyDivision().specialDrops.get("Mypirate"));
				assertTrue(scanner.getBootyDivision().toJson().contains("\"special_drops\":{\"Crewmate\":{\"Serpent (green)\":2"));
				assertTrue(scanner.getBootyDivision().toText().contains("== Booty Alerts =="));
				assertTrue(scanner.getBootyDivision().toText().contains("2x Serpent (green)"));

				output.setText("");
				assertNull(scanner.scanDutyReport(List.of(new SpecialDrops.Rule("Serpent (green)", OWN))));
				assertEquals("", output.getText());
			} finally {
				game.dispose();
			}
		});
	}

	private static JPanel pirate(String name, JPanel... loot) {
		JPanel header = new JPanel();
		header.add(new JLabel());
		header.add(new JButton(name));
		header.add(new JLabel());
		JPanel slots = new JPanel();
		for (JPanel slot : loot) slots.add(slot);
		JPanel pirate = new JPanel();
		pirate.add(header);
		pirate.add(slots);
		return pirate;
	}

	private static JPanel slot(String tooltip, int quantity, String status) {
		JPanel slot = new JPanel();
		slot.setToolTipText(tooltip);
		slot.add(new JLabel("×" + quantity));
		slot.add(new YoMultiLineLabel(status));
		return slot;
	}

	private static final class YoMultiLineLabel extends JLabel {
		private final Status _label;
		YoMultiLineLabel(String text) { _label = new Status(text); }
	}

	private static final class Status {
		private final String text;
		Status(String text) { this.text = text; }
	}
}
