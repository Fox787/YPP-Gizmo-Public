package main.java.general;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DnavForecastTest {
	@Test
	void takesTheWorstOrderOfTheMoonsAfterABooch() {
		assertEquals(375.0, DnavForecast.navPoints(new int[]{0, 0, 1}));
		assertEquals(894.0, DnavForecast.navPoints(new int[]{0, 0, 2}));
		// A single right after each triple drops the board back to 125.
		assertEquals(923.0, DnavForecast.navPoints(new int[]{1, 0, 2}));
		assertEquals(1961.0, DnavForecast.navPoints(new int[]{1, 0, 4}));
		assertEquals(1990.0, DnavForecast.navPoints(new int[]{2, 0, 4}), "triple, single, triple, single, triple, triple");
		assertEquals(1096.0, DnavForecast.navPoints(new int[]{0, 1, 2}));
	}

	@Test
	void tenTriplesMatchTheBoochOpeningPlan() {
		double points = DnavForecast.navPoints(new int[]{0, 0, 10});
		assertEquals(5046.0, points);
		assertEquals(DnavPlan.line(true, 10, false, 40, 12).points(), points);
	}

	@Test
	void movesWithoutATripleStayOnTheBoochBoard() {
		assertEquals(500.0, DnavForecast.navPoints(new int[]{0, 2}));
		assertEquals(250.0, DnavForecast.navPoints(new int[]{2}));
		assertEquals(250.0, DnavForecast.navPoints(new int[]{0, 1, 0}));
	}

	@Test
	void scoresMissingCombosAsZero() {
		assertEquals(0.0, DnavForecast.navPoints(null));
		assertEquals(0.0, DnavForecast.navPoints(new int[0]));
		assertEquals(0.0, DnavForecast.navPoints(new int[]{0, 0, 0}));
	}

	@Test
	void theChatsWerewolfCountGivesALoneNavigatorAFloor() {
		// Crew 4, fray 2: 200 x 4 x 1.3 x 0.6 = 624 points in the wave.
		assertEquals(524.0, DnavForecast.waveFloor(4, 2, 0), 1e-9);
		assertEquals(324.0, DnavForecast.waveFloor(4, 2, 1), 1e-9);
		assertEquals(0.0, DnavForecast.waveFloor(4, 2, 9), 1e-9);
	}
}
