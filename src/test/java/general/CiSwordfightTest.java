package main.java.general;

import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class CiSwordfightTest {
	private static final String SWORD = "Reporting ready data.SwordObject:12(Alice, Malevolent Cultist, Roused Homunculus):0.";

	@Test
	void npcCuesPrepareTheCounterAndTheTeamConfirmsHomunculi() {
		var ci = new CiSwordfight();
		ci.chat("Jice says, \"Malevolent Cultist shouts, something\"");
		assertFalse(ci.showColours());
		ci.chat("Malevolent Cultist shouts, \"Oh sweet moon-LEAPER!\"");
		assertTrue(ci.showColours());
		long round = ci.round();
		ci.chat("Chanting Cultist shouts, \"G'laroth shun-maruth!\"");
		ci.feed(SWORD);
		assertEquals(round, ci.round(), "the cue and puzzle start belong to one counter");
		assertTrue(ci.showColours());
		ci.feed(SWORD);
		assertEquals(round, ci.round(), "a repeated ready event must not reset the colour counter");
	}

	@Test
	void alternateRumblesAndForageClearOldSwordfightState() {
		var ci = new CiSwordfight();
		ci.feed(SWORD);
		assertTrue(ci.showColours());
		ci.feed("Stopping foraging in 119 seconds");
		assertFalse(ci.showColours());
		ci.feed("Reporting ready data.BoxingObject:13(Alice, Enlightened One, Servile Zombie):0.");
		assertFalse(ci.showColours());
		ci.chat("Enlightened One says, \"the heart of man is darkest coal...\"");
		assertFalse(ci.showColours());
		ci.feed(SWORD.replace(":12(", ":14("));
		assertTrue(ci.showColours());
		assertEquals(2, ci.round());
		ci.chat("Game over.  Winners: Alice.");
		assertFalse(ci.showColours());
	}

	@Test
	void aCultistFightWithoutHomunculiHidesTheCounter() {
		var ci = new CiSwordfight();
		ci.chat("Chanting Cultist shouts, \"G'laroth!\"");
		ci.feed("Reporting ready data.SwordObject:15(Alice, Malevolent Cultist):0.");
		assertFalse(ci.showColours());
		ci.feed("replaced=class com.threerings.piracy.puzzle.sword.client.SwordPanel");
		assertFalse(ci.showColours());
	}

	@Test
	void startupRecoversTheActiveCiSwordfightAndSceneChangesClearIt() {
		var clock = new RumbleClock();
		clock.seedScene("Adding chat display for entering scene [scene=data.GauntletSceneObject:1]\n" + SWORD, 1000);
		assertTrue(clock.ciSwordfight().showColours());
		clock.feed("Adding chat display for entering scene [scene=data.YoSceneObject:2]", 3000);
		assertFalse(clock.ciSwordfight().showColours());
	}

	@Test
	void thalassaColourMappingsProduceBoundedCountsAndCopyableTotals() {
		var counter = new HomunculusCounter();
		for (int i = 0; i < 8; i++) counter.adjust(i, 1);
		assertEquals(Map.of("Blue", 2, "Green", 2, "Red", 2, "Yellow", 2), counter.totals());
		counter.adjust(0, -10);
		assertEquals(1, counter.totals().get("Blue"));
		counter.adjust(6, 3);
		assertTrue(counter.copyText("Yellow").startsWith("/ve Homunculus Count:\n5 Yellow <-- attacking"));
		counter.reset();
		assertTrue(counter.totals().values().stream().allMatch(value -> value == 0));
	}
}
