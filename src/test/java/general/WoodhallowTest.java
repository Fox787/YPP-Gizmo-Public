package main.java.general;

import com.threerings.piracy.swing.YoMultiLineLabel;
import com.threerings.yohoho.swing.SunshineTab;
import java.awt.BorderLayout;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WoodhallowTest {
	@Test
	void usesSelectedVisibleAhoyWhilePuzzleIsOpen() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var game = new JPanel(new BorderLayout()) {
				@Override public boolean isShowing() { return true; }
				@Override public boolean isDisplayable() { return true; }
			};
			var sidebar = new JTabbedPane();
			var ahoy = new JLabel("<html>Next pack:<br>8 wolf-kin and 2 werewolves</html>");
			sidebar.addTab("Ahoy!", ahoy);
			sidebar.addTab("Crew", new JLabel("Next pack: 5 wolf-kin and 99 werewolves"));
			game.add(sidebar, BorderLayout.EAST);
			game.add(new JPanel(), BorderLayout.CENTER); // The client can replace its puzzle view here.
			game.addNotify();
			try {
				assertEquals(2, AhoyWolfCount.read(game));
				sidebar.setSelectedIndex(1);
				assertEquals(-1, AhoyWolfCount.read(game));
				ahoy.setText("Next pack: 5 wolf-kin and 1 werewolf");
				sidebar.setSelectedIndex(0);
				assertEquals(1, AhoyWolfCount.read(game));
				sidebar.setVisible(false);
				assertEquals(-1, AhoyWolfCount.read(game));
			} finally {
				game.removeNotify();
			}
		});
	}

	@Test
	void usesSelectedAhoyInButtonTabStrip() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var game = new JPanel() {
				@Override public boolean isShowing() { return true; }
				@Override public boolean isDisplayable() { return true; }
			};
			var strip = new JPanel();
			var tab = new JToggleButton("Ahoy!", true);
			strip.add(tab);
			game.add(strip);
			game.add(new JLabel("Next pack: 10 wolf-kin and 4 werewolves"));
			game.addNotify();
			try {
				assertEquals(4, AhoyWolfCount.read(game));
				tab.setSelected(false);
				assertEquals(-1, AhoyWolfCount.read(game));
			} finally {
				game.removeNotify();
			}
		});
	}

	@Test
	void readsOnlyTheSelectedAhoyPageInTheNativeSidebar() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var frame = new javax.swing.JFrame();
			try {
				var sidebar = new SunshineTab();
				var ahoy = new YoMultiLineLabel("Next pack: 8 wolf-kin and 5 werewolves");
				sidebar.select(4, ahoy);
				frame.add(sidebar);
				frame.pack();
				frame.setVisible(true);
				assertEquals(5, AhoyWolfCount.read(frame));
				sidebar.select(2, new YoMultiLineLabel("Next pack: 8 wolf-kin and 99 werewolves"));
				assertEquals(-1, AhoyWolfCount.read(frame));
				sidebar.select(4, ahoy);
				ahoy.setVisible(false);
				assertEquals(-1, AhoyWolfCount.read(frame));
			} finally {
				frame.dispose();
			}
		});
	}

	@Test
	void ahoyRemainsTheSourceAfterChatAndChatOnlyNeverReadsAhoy() {
		var wolves = new WolfCount();
		long start = 100_000;
		var unknown = new StationClock.WolfProgress(-1, -1, 0);
		var half = new StationClock.WolfProgress(4, 4, start + 40_000);
		var choices = java.util.List.of(8, 9);
		var visible = new AtomicInteger(8);
		assertEquals(new WolfCount.Reading(8, 8, start + 1_000),
				wolves.read(false, visible::get, unknown, start, start + 1_000, choices));
		visible.set(-1);
		assertEquals(new WolfCount.Reading(8, 8, start + 1_000),
				wolves.read(false, visible::get, half, start, start + 45_000, choices));
		visible.set(3);
		assertEquals(new WolfCount.Reading(3, 8, start + 50_000),
				wolves.read(false, visible::get, half, start, start + 50_000, choices));

		wolves.clearRemaining();
		assertEquals(new WolfCount.Reading(4, -1, half.changedAt()),
				wolves.read(true, () -> fail("Chat only must never read Ahoy"), half, start, start + 51_000, choices));
		assertEquals(-1, wolves.read(false, () -> -1, half, start, start + 52_000, choices).remaining());
	}

	@Test
	void lateAhoyReadingsOnlyConfirmTheHighestCandidate() {
		var count = new WolfCount();
		var unknown = new StationClock.WolfProgress(-1, -1, 0);
		var half = new StationClock.WolfProgress(4, 4, 32_000);
		var choices = java.util.List.of(8, 9);
		assertEquals(-1, count.read(false, () -> 8, unknown, 1000, 11_000, choices).starting());
		assertEquals(9, count.read(false, () -> 9, unknown, 1000, 12_000, choices).starting());
		assertEquals(new WolfCount.Reading(4, 9, 32_000),
				count.read(false, () -> 4, half, 1000, 33_000, choices));
	}

	@Test
	void usesSurvivorRatioAndKeepsOneWarningPerStationRound() {
		var clock = new StationClock();
		clock.message(StationClock.ARRIVAL, 500);
		clock.message("8 o' 8 still standin'! To yer stations!", 1_000);
		assertEquals(121_000, clock.expectedEnd(clock.current()));
		clock.message("The wolf-kin gather! 30 seconds!", 91_000);
		clock.message("8 o' 8 still standin'! To yer stations!", 92_000);
		assertEquals(2, clock.frayNumber());
		clock.message("Beware! More wolf-kin approach!", 121_000);
		clock.message("3 o' 8 still standin'! To yer stations!", 200_000);
		assertEquals(187_500, clock.duration(clock.current()));
		clock.message("Yer charting has turned back half the werewolves! 5 still to come", 300_000);
		clock.message("Yer charting has turned back more o' the werewolves! Only 2 left", 350_000);
		assertEquals(new StationClock.WolfProgress(2, 5, 350_000), clock.wolfProgress(clock.current()));
		clock.message("The wolf-kin gather! 30 seconds!", 357_500);
		clock.message("The wolf-kin gather! 30 seconds!", 358_500);
		assertEquals(357_500, clock.thirtySecondsAt());
		assertEquals(387_500, clock.expectedEnd(clock.current()));
	}

	@Test
	void timesOwnSwitchesAndOnlyEstimatesOtherSwitchesWhenEnabled() {
		var clock = new StationClock();
		clock.message(StationClock.ARRIVAL, 99_000);
		clock.message("8 o' 8 still standin'! To yer stations!", 100_000);
		clock.message("The wolf-kin gather! 30 seconds!", 190_000);
		var tracker = new ActivityTracker(null, clock);
		var report = new DRGroupData();
		tracker.setSelf("Me");
		report.put("Navigating", "Solo", "Excellent", new int[]{1}, 12);
		for (String name : new String[]{"Me", "Other"}) {
			report.put("Navigating", name, "Fine", new int[]{2}, 6);
			report.put("Foraging", name, "Excellent", new int[]{3}, 3);
		}
		tracker.ownPuzzle("NaviObject", 102_000);
		tracker.ownPuzzle("ForageObject", 160_000);
		tracker.observeSceneActivity("Other", 10, 102_000);
		tracker.observeSceneActivity("Other", 56, 140_000);
		tracker.report(report, 220_000);
		assertEquals(120_000, row(tracker, "Solo", "Navigating").ms);
		assertEquals(60_000, row(tracker, "Me", "Navigating").ms);
		assertTrue(row(tracker, "Me", "Foraging").exact);
		assertEquals(0, row(tracker, "Other", "Navigating").ms);

		clock.message("Beware! More wolf-kin approach!", 220_000);
		clock.message("8 o' 8 still standin'! To yer stations!", 300_000);
		clock.message("The wolf-kin gather! 30 seconds!", 570_000);
		tracker.setSceneTrackingEnabled(true, 299_000);
		tracker.observeSceneActivity("Other", 10, 302_000);
		tracker.observeSceneActivity("Other", 56, 400_000);
		tracker.report(report, 600_000);
		assertEquals(99_700, row(tracker, "Other", "Navigating").ms);
		assertFalse(row(tracker, "Other", "Navigating").exact);
		assertEquals(0, row(tracker, "Me", "Navigating").ms);
		String ratings = RunLog.ratingsJsonl(tracker.lastPeriod());
		assertTrue(ratings.contains("\"exact\":false,\"perMin\":null"));
		assertTrue(ratings.contains("\"exact\":true,\"perMin\":2.4"));
	}

	private static ActivityTracker.Row row(ActivityTracker tracker, String pirate, String station) {
		return tracker.lastPeriod().rows.stream()
				.filter(row -> row.pirate.equals(pirate) && row.station.equals(station))
				.findFirst().orElseThrow();
	}

	@Test
	void followsGameLogEntriesAndExitsAndDecodesPackagedSounds() throws Exception {
		var clock = new RumbleClock();
		var puzzles = new java.util.ArrayList<String>();
		clock.setPuzzleListener((puzzle, at) -> puzzles.add(puzzle));
		long start = LocalDateTime.of(2026, 11, 16, 10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
		clock.feed("2026/11/16 10:00:00:000 INFO Reporting ready data.BoxingObject:", start + 250);
		clock.feed("Reporting ready data.BoxingObject:", start + 500);
		assertEquals(5_000, clock.elapsed(start + 5_000));
		clock.feed("View changed: replaced=class com.threerings.piracy.puzzle.boxing.client.BoxingPanel", start + 7_000);
		assertEquals(-1, clock.elapsed(start + 7_000));
		clock.feed("Reporting ready navi.NaviObject:", start + 8_000);
		clock.feed("Reporting ready data.ForageObject:", start + 9_000);
		assertEquals(java.util.List.of("NaviObject", "ForageObject"), puzzles);
		assertTrue(Sounds.scaled(Sounds.CLEAR, 50).length > 44);
		assertTrue(Sounds.scaled(Sounds.THIRTY_SECONDS, 0).length > 44);
		assertEquals(120_000, DnavForecast.during(1, 4, 0, 300).clearMs);
	}
}
