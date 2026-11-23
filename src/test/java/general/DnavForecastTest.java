package main.java.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DnavForecastTest {
	@Test
	void scoresTheFirstTripleAtTheBaseRateAndBoostsTheOtherCombos() {
		assertEquals(375.0, DnavForecast.navPoints(new int[]{0, 0, 1}));
		assertEquals(894.0, DnavForecast.navPoints(new int[]{0, 0, 2}));
		assertEquals(1067.0, DnavForecast.navPoints(new int[]{1, 0, 2}));
		assertEquals(1240.0, DnavForecast.navPoints(new int[]{0, 1, 2}));
	}

	@Test
	void tenTriplesMatchTheBoochOpeningPlan() {
		double points = DnavForecast.navPoints(new int[]{0, 0, 10});
		assertEquals(5046.0, points);
		assertEquals(DnavPlan.line(true, 10, false, 40, 12).points(), points);
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
