package main.java.general;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DnavPlanTest {
	@Test
	void crewOfFourWavesUseTheUnroundedPack() {
		assertEquals(624.0, DnavPlan.total(4, 2), 0.01);
		assertEquals(946.4, DnavPlan.total(4, 3), 0.1);
		assertEquals(2056.4, DnavPlan.total(4, 5), 0.1);
		assertEquals(DnavPlan.total(2, 3), DnavPlan.total(4, 3), 1e-9);   // smaller crews use 4
	}

	@Test
	void picksTheFastestLineAndOneWithTheOtherBoochChoice() {
		List<DnavPlan.Line> before2 = DnavPlan.options(DnavPlan.needed(4, 2), 40, 12);
		assertEquals("Booch, 1 Trip, Single", before2.get(0).name());
		assertEquals("1:01", before2.get(0).time());
		assertEquals(548, before2.get(0).points());
		assertTrue(!before2.get(1).booch());

		List<DnavPlan.Line> before5 = DnavPlan.options(DnavPlan.needed(4, 5), 40, 12);
		assertEquals("Booch, 4 Trips, Single", before5.get(0).name());
		assertEquals(2105, before5.get(0).points());

		assertEquals("Booch, 2 Trips", DnavPlan.options(DnavPlan.needed(4, 3), 40, 12).get(0).name());
		assertEquals("Booch, 3 Trips", DnavPlan.options(DnavPlan.needed(4, 4), 40, 12).get(0).name());
		assertEquals("Booch, 6 Trips", DnavPlan.options(DnavPlan.needed(4, 6), 40, 12).get(0).name());
	}

	@Test
	void stepsTrackTheBoards() {
		DnavPlan.Line l = DnavPlan.line(true, 2, true, 40, 12);
		assertEquals(List.of(0, 375, 894, 1067), l.steps().stream().map(DnavPlan.Step::points).toList());
		assertEquals(101, l.seconds());
	}
}
