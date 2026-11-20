package main.java.general;

import org.junit.jupiter.api.Test;

import static main.java.general.TimeLeftWarnings.Activity.*;
import static main.java.general.TimeLeftWarnings.FIFTEEN_MS;
import static main.java.general.TimeLeftWarnings.THIRTY_MS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TimeLeftWarningsTest {

	@Test
	void bothCellsWarnOnceEachAtTheirOwnTime() {
		TimeLeftWarnings warnings = new TimeLeftWarnings();
		long end = 100_000;
		assertFalse(warnings.due(CURSED_ISLES, THIRTY_MS, end, end - 31_000));
		assertTrue(warnings.due(CURSED_ISLES, THIRTY_MS, end, end - 30_000));
		assertFalse(warnings.due(CURSED_ISLES, THIRTY_MS, end, end - 29_000));   // once per end
		assertFalse(warnings.due(CURSED_ISLES, FIFTEEN_MS, end, end - 29_000));
		assertTrue(warnings.due(CURSED_ISLES, FIFTEEN_MS, end, end - 14_500));
		assertTrue(warnings.due(CURSED_ISLES, THIRTY_MS, end + 300_000, end + 270_000)); // next forage
	}

	@Test
	void activitiesAreIndependent() {
		TimeLeftWarnings warnings = new TimeLeftWarnings();
		assertTrue(warnings.due(WOODHALLOW, THIRTY_MS, 60_000, 30_000));
		assertTrue(warnings.due(VAMPIRE_LAIR, THIRTY_MS, 60_000, 30_000));
	}

	@Test
	void oldOrMovedEndsDoNotWarn() {
		TimeLeftWarnings warnings = new TimeLeftWarnings();
		// An end read back at startup, long gone.
		assertFalse(warnings.due(WOODHALLOW, THIRTY_MS, 10_000, 500_000));
		// The lair timer, corrected by the coffin warning to 15 s left: too late for its 30 s warning.
		long corrected = 200_000;
		assertFalse(warnings.due(VAMPIRE_LAIR, THIRTY_MS, corrected, corrected - 15_000));
		assertFalse(warnings.due(VAMPIRE_LAIR, THIRTY_MS, 0, 1_000));
	}
}
