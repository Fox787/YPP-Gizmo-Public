package main.java.general;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DnavPlanTest {
	@Test
	void customBoochTimeChangesBothOpeningAndVegasStrategies() {
		var opening = DnavPlan.options(500, -1, -1, -1, -1, 20, -1, 15).get(0);
		assertEquals("Booch, 1 Bingo", opening.name());
		assertEquals(35, opening.seconds());
		var vegas = DnavPlan.options(900, -1, 12, -1, -1, -1, 50, 20).get(0);
		assertEquals("Booch-Vegas, Single", vegas.name());
		assertEquals(82, vegas.seconds());
	}

	@Test
	void blankBoochTimeExcludesAllMovesThatNeedIt() {
		var lines = DnavPlan.options(500, 40, 12, 51, -1, 20, 50, -1);
		assertFalse(lines.isEmpty());
		for (var line : lines) {
			assertFalse(line.booch());
			assertEquals(0, line.donkeys());
			assertEquals(0, line.vegases());
		}
	}

	@Test
	void blankBingoAndVegasPreserveExistingPlans() {
		assertEquals(DnavPlan.options(1900, 40, 12, 51, -1),
				DnavPlan.options(1900, 40, 12, 51, -1, -1, -1));
	}

	@Test
	void bingoUsesTheCurrentBoardAndLeaves173ForTheNextMove() {
		var lines = DnavPlan.options(500, -1, -1, -1, -1, 20, -1);
		assertEquals("Booch, 1 Bingo", lines.get(0).name());
		assertEquals(29, lines.get(0).seconds());
		assertEquals(500, lines.get(0).points());
		var withoutBooch = lines.stream().filter(line -> !line.booch()).findFirst().orElseThrow();
		assertEquals(List.of(284, 976), withoutBooch.steps().stream().map(DnavPlan.Step::points).toList());
		assertEquals(40, withoutBooch.seconds());
	}

	@Test
	void vegasAlwaysIncludesABoochAndLeaves173ForTheSingle() {
		var line = DnavPlan.options(900, -1, 12, -1, -1, -1, 50).get(0);
		assertEquals("Booch-Vegas, Single", line.name());
		assertEquals(1, line.vegases());
		assertEquals(List.of(750, 923), line.steps().stream().map(DnavPlan.Step::points).toList());
		assertEquals(71, line.seconds());
	}

	@Test
	void bingoCanReplaceTriplesAndFollowAVegas() {
		var lines = DnavPlan.options(1400, -1, -1, -1, -1, 30, 25);
		var line = lines.get(0);
		assertEquals("Booch-Vegas, 1 Bingo", line.name());
		assertEquals(1442, line.points());
		assertEquals(64, line.seconds());
		assertTrue(lines.stream().allMatch(plan -> plan.triples() == 0 && plan.doubles() == 0 && plan.donkeys() == 0));
	}

	@Test
	void disabledMovesNeverAppearInExpandedPlans() {
		for (var line : DnavPlan.options(1400, 40, 12, -1, -1, 30, -1)) {
			assertEquals(0, line.doubles());
			assertEquals(0, line.donkeys());
			assertEquals(0, line.vegases());
			assertFalse(line.steps().stream().anyMatch(step -> step.what().contains("Vegas")));
		}
	}

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
