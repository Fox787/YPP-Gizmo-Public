package main.java.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GameSceneTest {
	private static final String ENTER = "2026/11/16 10:40:40:949 INFO ih.doLog: Adding chat display for entering scene [scene=data.%s:650818]";
	private static final String LEAVE = "2026/11/16 11:18:45:817 INFO ih.doLog: Removing chat display for leaving scene [scene=data.%s:650818]";

	@Test
	void denAndCursedIslesComeFromTheGameLogScene() {
		GameScene scene = new GameScene();
		assertNull(scene.activity(1_000, 0));
		scene.feed(String.format(ENTER, "WerewolfIsleSceneObject"), 1_000);
		assertEquals(RecordChecker.DEN, scene.activity(500_000, 0));
		scene.feed(String.format(LEAVE, "WerewolfIsleSceneObject"), 600_000);
		scene.feed(String.format(ENTER, "GauntletSceneObject"), 601_000);
		assertEquals(RecordChecker.CI, scene.activity(700_000, 0));
	}

	@Test
	void aReportScannedJustAfterLeavingStillBelongsToTheActivity() {
		GameScene scene = new GameScene();
		scene.feed(String.format(ENTER, "WerewolfIsleSceneObject"), 1_000);
		scene.feed(String.format(LEAVE, "WerewolfIsleSceneObject"), 10_000);
		scene.feed(String.format(ENTER, "YoSceneObject"), 11_000);
		assertEquals(RecordChecker.DEN, scene.activity(10_000 + GameScene.LEFT_GRACE_MS, 0));
		assertNull(scene.activity(10_001 + GameScene.LEFT_GRACE_MS, 0));
	}

	@Test
	void theLairComesFromChatUntilTheClientMovesOn() {
		GameScene scene = new GameScene();
		scene.feed(String.format(ENTER, "SomeLairRoomSceneObject"), 100_000);
		long arrival = 102_000;
		assertEquals(RecordChecker.LAIR, scene.activity(900_000, arrival));
		scene.feed(String.format(LEAVE, "SomeLairRoomSceneObject"), 1_000_000);
		scene.feed(String.format(ENTER, "YoSceneObject"), 1_001_000);
		assertNull(scene.activity(1_002_000, arrival));
	}

	@Test
	void parlorRumblesStartNoTimerButVoyageOnesDo() {
		String ready = "2026/11/20 01:56:52:228 INFO ih.doLog: Reporting ready data.BoxingObject:664054(Ozpin, Sisli):0.";
		RumbleClock clock = new RumbleClock();
		clock.feed(String.format(ENTER, "BuildingSceneObject"), 1_000);
		clock.feed(ready, 2_000);
		assertEquals(-1, clock.elapsed(3_000));
		clock.feed(String.format(LEAVE, "BuildingSceneObject"), 4_000);
		clock.feed(String.format(ENTER, "WerewolfIsleSceneObject"), 5_000);
		clock.feed(ready, 6_000);
		assertEquals(true, clock.elapsed(7_000) >= 0);
	}

	@Test
	void startingMidSessionTakesTheSceneFromTheLogSoFar() {
		RumbleClock clock = new RumbleClock();
		clock.seedScene(String.format(LEAVE, "WerewolfIsleSceneObject") + "\r\n"
				+ "2026/11/16 11:18:45:900 INFO ih.doLog: Reporting ready data.BoxingObject:1(a, b):0.\r\n"
				+ String.format(ENTER, "BuildingSceneObject") + "\r\n", 1_000);
		assertEquals(-1, clock.elapsed(2_000));   // seeding starts no timers
		clock.feed("2026/11/20 01:56:52:228 INFO ih.doLog: Reporting ready data.BoxingObject:664054(Ozpin, Sisli):0.", 3_000);
		assertEquals(-1, clock.elapsed(4_000));   // already in a parlor: no rumble timer
	}

	@Test
	void otherLinesAndScenesTagNothing() {
		GameScene scene = new GameScene();
		scene.feed("2026/11/16 10:36:02:868 INFO ih.doLog: Got updated scene model [placeId=45555]", 1_000);
		scene.feed(String.format(ENTER, "BuildingSceneObject"), 2_000);
		assertNull(scene.activity(3_000, 0));
	}
}
