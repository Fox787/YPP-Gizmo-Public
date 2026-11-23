package test.java.general;

import main.java.general.DRGroupData;
import main.java.general.DRIndividualData;
import main.java.general.DRPersistantData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DRDatasTest {
    @Test
    void customStationWeightsApplyToIndividualGroupAndStoredReports() {
        var weights = new DRIndividualData.StationWeights(4, 5, 6, -2, 3, 7);
        var group = new DRGroupData();
        group.put("Navigating", "Nav", "Swift", new int[]{2, 3, 4}, 0);
        group.put("Carpentry", "Carp", "Swift", new int[]{2, 3, 4}, 0);
        group.calculateCustomTokenScore(1, 1, 1, 1, 1, 1, 1, 2, 3, weights);
        assertEquals(47, group.get("Navigating", "Nav").getTokenScore());
        assertEquals(33, group.get("Carpentry", "Carp").getTokenScore());
        var all = new DRPersistantData();
        all.add(group);
        all.calculateCustomTokenScore(1, 1, 1, 1, 1, 1, 1, 2, 3);
        assertEquals(20, all.groups().get(0).get("Navigating", "Nav").getTokenScore());
        assertEquals(9, all.groups().get(0).get("Carpentry", "Carp").getTokenScore());
    }


    @Test
    public void testIndividualDataConstructorAndFields() {
        int[] tokens = {2, 4, 6, 8};
        DRIndividualData data = new DRIndividualData("Sailing", "TestPirate", "Incredible", tokens, 20);

        assertEquals("Sailing", data.getStation());
        assertEquals("TestPirate", data.getName());
        assertEquals("Incredible", data.getRating());
        assertArrayEquals(tokens, data.getTokens());
        assertEquals(20, data.getTokenScore());
    }

    @Test
    void testForagingTokenScore() {
        DRIndividualData data = new DRIndividualData();
        data.setStation("Foraging");
        data.setTokens(new int[]{6, 9, 2});
        data.calculateCustomTokenScore(0, 0, 0, 0, 0, 0, 1, 2, 3); // small=1, medium=2, large=3
        assertEquals(30, data.getTokenScore()); // 6*1 + 9*2 + 2*3 = 30
    }

    @Test
    void testTreasureHaulTokenScore() {
        DRIndividualData data = new DRIndividualData();
        data.setStation("Treasure Haul");
        data.setTokens(new int[]{1, 2, 3});
        data.calculateCustomTokenScore(0, 0, 0, 0, 0, 0, 0, 0, 0);
        // Expected: 1 + 2 + 3 = 6
        assertEquals(6, data.getTokenScore());
    }

    @Test
    void testGunningTokenScore() {
        DRIndividualData data = new DRIndividualData();
        data.setStation("Gunning");
        data.setTokens(new int[]{48});
        data.calculateCustomTokenScore(0, 0, 0, 0, 0, 1, 0, 0, 0);
        // Expected: 1 * 48 = 48
        assertEquals(48, data.getTokenScore());
    }

    @Test
    void testCarpentryTokenScore() {
        // Vampire Lair carp specifically, not regular Carpentry
        DRIndividualData data = new DRIndividualData();
        data.setStation("Carpentry");
        data.setTokens(new int[]{6, 9, 2});
        data.calculateCustomTokenScore(0, 0, 0, 0, 0, 0, 0, 0, 0); // multipliers are ignored for Carpentry
        // Expected -6 + 9 + 4 = 7
        assertEquals(7, data.getTokenScore());
    }

    @Test
    void testOtherStationTokenScore() {
        DRIndividualData data = new DRIndividualData();
        data.setStation("Other");
        data.setTokens(new int[]{1, 2, 3, 4, 0, 0, 5});
        data.calculateCustomTokenScore(1, 1, 1, 1, 1, 1, 1, 2, 3);
        // Expected: 1 + 2 + 3 + 4 + 5 = 15
        assertEquals(15, data.getTokenScore());
    }

    @Test
    void testNullTokens() {
        DRIndividualData data = new DRIndividualData();
        data.setStation("Foraging");
        data.setTokens(null);
        data.calculateCustomTokenScore(1, 1, 1, 1, 1, 1, 1, 1, 1);
        assertEquals(0, data.getTokenScore());
    }

    // ---- DRGroupData Tests ----

    @Test
    public void testGroupDataPutAndGet() {
        DRGroupData group = new DRGroupData();
        group.put("Haul", "Strider", "Expert", new int[]{7, 2}, 9);
        DRIndividualData pirate = group.get("Haul", "Strider");
        assertNotNull(pirate);
        assertEquals("Strider", pirate.getName());
        assertEquals(9, pirate.getTokenScore());
    }

    @Test
    public void testGroupDataContainsAndSize() {
        DRGroupData group = new DRGroupData();
        group.put("Haul", "Strider", "Expert", new int[]{7, 2}, 9);
        assertTrue(group.contains("Haul", "Strider"));
        assertFalse(group.contains("Haul", "Ghost"));
        assertEquals(1, group.size());
        assertFalse(group.isEmpty());
    }

    @Test
    public void testGroupDataCopy() {
        DRGroupData group = new DRGroupData();
        group.put("Haul", "Strider", "Expert", new int[]{7, 2}, 9);
        DRGroupData copy = group.copy();
        assertTrue(copy.contains("Haul", "Strider"));
        assertEquals(1, copy.size());
    }

    @Test
    public void testGroupDataCalculateCustomTokenScore() {
        DRGroupData group = new DRGroupData();
        group.put("Foraging", "TestPirate", "Legendary", new int[]{2,0,0,0,0,0,0,0}, 0);
        group.calculateCustomTokenScore(0,0,0,0,0,0,10,0,0); // smallChest=10
        DRIndividualData pirate = group.get("Foraging", "TestPirate");
        assertEquals(20, pirate.getTokenScore());
    }

    @Test
    public void testPersistantDataAddAndGetAll() {
        DRPersistantData persist = new DRPersistantData();
        DRGroupData group1 = new DRGroupData();
        group1.put("Treasure Haul", "PirateA", "Frenetic", new int[]{3,3, 3}, 9);
        DRGroupData group2 = new DRGroupData();
        group2.put("Foraging", "PirateB", "Frenetic", new int[]{5,5,5}, 30);

        persist.add(group1);
        persist.add(group2);

        List<DRGroupData> allGroups = persist.getAll();
        assertEquals(2, allGroups.size());
        assertEquals("PirateA", allGroups.get(0).get("Treasure Haul", "PirateA").getName());
        assertEquals("PirateB", allGroups.get(1).get("Foraging", "PirateB").getName());
    }

    @Test
    public void testPersistantDataIsEmpty() {
        DRPersistantData persist = new DRPersistantData();
        assertTrue(persist.isEmpty());
        DRGroupData group = new DRGroupData();
        group.put("Foraging", "PirateA", "Foo", new int[]{1}, 1);
        persist.add(group);
        assertFalse(persist.isEmpty());
    }


}
