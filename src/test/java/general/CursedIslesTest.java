package main.java.general;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CursedIslesTest {

	private static final String TEAM = "2026/11/16 00:48:49:673 INFO ih.doLog: Reporting ready data.BoxingObject:459026(Enlightened One, "
			+ "Enlightened One, Servile Zombie, Cursed Zombie, Coldest, Kai Slashin', Ozpin, Pilipala, Coldest's Thrall, "
			+ "Pilipala's Thrall, Ozpin's Thrall):0.";
	private static final String FORAGE = "2026/11/16 01:06:36:591 INFO ih.doLog: Stopping foraging in 119 seconds";

	@Test
	void forageTimerRunsFromTheLogLineUntilTheServerStops() {
		RumbleClock clock = new RumbleClock();
		long at = RumbleClock.lineTime(FORAGE, 0);
		clock.feed(FORAGE, at);
		assertEquals(120_000, clock.forageLeft(at));
		assertEquals(30_000, clock.forageLeft(at + 90_000));
		assertEquals(-1, clock.elapsed(at));
		clock.feed("2026/11/16 01:08:36:200 INFO ih.doLog: Asking server to stop foraging", at + 119_609);
		assertEquals(-1, clock.forageLeft(at + 119_609));

		clock.feed(FORAGE, at);
		assertEquals(-1, clock.forageLeft(at + 120_500), "ends at the stated time + 1 s without the server line");

		clock.feed(FORAGE, at);
		clock.feed("2026/11/16 01:06:41:591 INFO ih.doLog: Reporting ready data.BoxingObject:1(a):0.", at + 5000);
		assertEquals(-1, clock.forageLeft(at + 6000), "a rumble ends the forage period");
		assertTrue(clock.elapsed(at + 6000) >= 0);
	}

	@Test
	void plansPlankingFromTheTeamFraysWonAndLeavers() {
		MercPlank plank = new MercPlank();
		plank.feed("2026/11/16 00:25:38:674 INFO ih.doLog: Enabling environment mod for skirmish [mod=dark_seas]");
		plank.feed(TEAM);
		assertEquals(3, plank.pirates());
		assertEquals(1, plank.mercs());
		assertFalse(plank.betweenFrays());

		plank.feed(FORAGE);   // fray 1 won: the next fray takes 4
		assertTrue(plank.betweenFrays());
		assertEquals(0, plank.toPlank());

		plank.feed(TEAM);
		assertFalse(plank.betweenFrays());
		plank.feed(FORAGE);   // fray 2 won: the next fray takes 3
		assertEquals(1, plank.toPlank());
		plank.chat("A swabbie has left the vessel.");
		assertEquals(0, plank.toPlank());

		plank.feed("2026/11/16 01:47:42:792 INFO ih.doLog: Disabling skirmish environment mod [mod=dark_seas]");
		assertFalse(plank.betweenFrays());
	}

	@Test
	void capsDropToTwoAndCountMercsAndPiratesLeaving() {
		MercPlank plank = new MercPlank();
		plank.feed("x Reporting ready data.BoxingObject:1(Enlightened One, A, B, C, Kai Slashin', George Black-eye, Dylan Bloodlust):0.");
		for (int i = 0; i < 5; i++) plank.feed(FORAGE);
		assertEquals(3, plank.toPlank());
		plank.chat("2 swabbies have left the vessel.");
		plank.chat("A has left the vessel.");
		assertEquals(2, plank.pirates());
		assertEquals(1, plank.mercs());
		assertEquals(1, plank.toPlank());
	}

	@Test
	void ignoresWoodhallowRumbleTeams() {
		MercPlank plank = new MercPlank();
		plank.feed("x Reporting ready data.BoxingObject:377509(Hulking Mano, Silver-scarred Hal, Ozpin, Coldest):0.");
		plank.feed(FORAGE);
		assertEquals(0, plank.mercs());
		assertEquals(0, plank.toPlank());
	}

	@Test
	void bundlesThePlankAndRumbleSounds() throws Exception {
		byte[] plank = Sounds.scaled(Sounds.PLANK, 50), rumble = Sounds.scaled(Sounds.RUMBLE_WARNING, 50);
		assertNotNull(plank);
		assertNotNull(rumble);
		assertTrue(plank.length > 44 && rumble.length > 44);
	}

	@Test
	void woodhallowShorthandIsTheWhiteNameInstaThenTheRange() {
		// Minute 1: 9 groups send 7 rows, 10 send 11 (past the 10-row white line); the range is 12-13.
		assertEquals(10, RumbleBoard.instaGroups(1, RumbleBoard.WHITE_INSTA_ROWS));
		assertEquals("<html><font color=#ffffff>(10)</font> (12-13)</html>", RumbleBoard.instaShorthand(1, false));
		for (int m = 0; m <= 15; m++) assertTrue(RumbleBoard.instaGroups(m, RumbleBoard.WHITE_INSTA_ROWS) > 0);
	}
}
