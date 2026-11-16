package main.java.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DnavForecastTest {
	@Test
	void scoresTheFirstTripleAtTheBaseRateAndBoostsTheOtherCombos() {
		assertEquals(375.0, DnavForecast.navPoints(new int[]{0, 0, 1}));
		assertEquals(903.0, DnavForecast.navPoints(new int[]{0, 0, 2}));
		assertEquals(1079.0, DnavForecast.navPoints(new int[]{1, 0, 2}));
		assertEquals(1255.0, DnavForecast.navPoints(new int[]{0, 1, 2}));
	}

	@Test
	void discountsOneHighestComboForOtherRuns() {
		assertEquals(963.0, DnavForecast.navPoints(new int[]{1, 1, 2}));
		assertEquals(392.0, DnavForecast.navPoints(new int[]{0, 2}));
		assertEquals(196.0, DnavForecast.navPoints(new int[]{2}));
		assertEquals(142.0, DnavForecast.navPoints(new int[]{0, 1, 0}));
	}

	@Test
	void scoresMissingCombosAsZero() {
		assertEquals(0.0, DnavForecast.navPoints(null));
		assertEquals(0.0, DnavForecast.navPoints(new int[0]));
		assertEquals(0.0, DnavForecast.navPoints(new int[]{0, 0, 0}));
	}
}
