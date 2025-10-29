package test.java.general;

import main.java.general.DRGroupData;
import main.java.general.DRIndividualData;
import main.java.general.DRPersistantData;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DRDatasTest {

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

    // ---- Broken Records Tests ----
    @Test
    void testIndividualBrokenRecord() {
        DRGroupData groupData = new DRGroupData();

        // Simulate a scan result for "Foraging" with a CC score above the record (record is 7, we use 8)
        groupData.put("Foraging", "TestPirate", "Frenetic", new int[]{0, 0, 8}, 8);

        // Set preference values
        int individualRecord = 7;
        int man1Record = 7;
        int man2Record = 0, man3Record = 0 ,man4Record = 0, man5Record = 0, man6Record = 0, man7Record = 0;

        String result = groupData.getBrokenRecords(individualRecord, man1Record, man2Record, man3Record, man4Record, man5Record, man6Record, man7Record);

        // Assert that an alert is triggered
        // 1 man is an odd scenario where only the user can run the scan, so gets "You"
        assertTrue(result.contains("TestPirate got 8 CCs in one forage which beats the old record of 7"), "Alert should be triggered for breaking the Individual record");
        assertTrue(result.contains("You got 8 CCs in one forage which beats the old record of 7."), "Alert should be triggered for breaking the 1 man record");

        groupData.data.get("Foraging").remove("TestPirate");
        groupData.put("Foraging", "TestPirate", "Frenetic", new int[]{0, 0, 7}, 7);

        result = groupData.getBrokenRecords(individualRecord, man1Record, man2Record, man3Record, man4Record, man5Record, man6Record, man7Record);

        assertTrue(result.contains("TestPirate got 7 CCs in one forage which ties the current record."), "Alert should be triggered for Tying the Individual record");
        assertTrue(result.contains("You got 7 CCs in one forage which ties the current record."), "Alert should be triggered for Tying the 1 man record");

        groupData.data.get("Foraging").remove("TestPirate");
        groupData.put("Foraging", "TestPirate", "Frenetic", new int[]{0, 0, 6}, 6);
        result = groupData.getBrokenRecords(individualRecord, man1Record, man2Record, man3Record, man4Record, man5Record, man6Record, man7Record);

        assertFalse(result.contains("TestPirate got 7 CCs in one forage which ties the current record."), "Alert should not trigger when not beating Individual record");
        assertFalse(result.contains("You got 7 CCs in one forage which ties the current record."), "Alert should not trigger for not beating the 1 man record");

    }

    @Test
    void test2ManBrokenRecord() {
        DRGroupData groupData = new DRGroupData();

        // Simulate two pirates; total CC score above 2-man record (record is 14, use 15)
        groupData.put("Foraging", "PirateA", "Frenetic", new int[]{0, 0, 9}, 9);
        groupData.put("Foraging", "PirateB", "Frenetic", new int[]{0, 0, 6}, 6);

        int individualRecord = 7; // Will be beaten by Pirate A, not by Pirate B
        int man1Record = 7; // Not used for 2-man group check
        int man2Record = 14;
        int man3Record = 0, man4Record = 0, man5Record = 0, man6Record = 0, man7Record = 0;

        String result = groupData.getBrokenRecords(individualRecord, man1Record, man2Record, man3Record, man4Record, man5Record, man6Record, man7Record);


        assertTrue(result.contains("PirateA got 9 CCs in one forage which beats the old record of 7."), "Alert should be triggered for breaking the Individual record");
        assertTrue(result.contains("We got 15 CCs in one forage which beats the old record of 14"), "Alert should be triggered for breaking the 2-man record");

        // Tie 2-man record
        groupData.data.get("Foraging").clear();
        groupData.put("Foraging", "PirateA", "Frenetic", new int[]{0, 0, 7}, 7);
        groupData.put("Foraging", "PirateB", "Frenetic", new int[]{0, 0, 7}, 7);

        result = groupData.getBrokenRecords(individualRecord, man1Record, man2Record, man3Record, man4Record, man5Record, man6Record, man7Record);

        assertTrue(result.contains("PirateA got 7 CCs in one forage which ties the current record."), "Alert should be triggered for tying the Individual record");
        assertTrue(result.contains("PirateB got 7 CCs in one forage which ties the current record."), "Alert should be triggered for tying the Individual record");
        assertTrue(result.contains("We got 14 CCs in one forage which ties the current record"), "Alert should be triggered for tying the 2-man record");

        // Below 2-man record
        groupData.data.get("Foraging").clear();
        groupData.put("Foraging", "PirateA", "Frenetic", new int[]{0, 0, 6}, 6);
        groupData.put("Foraging", "PirateB", "Frenetic", new int[]{0, 0, 6}, 6);

        result = groupData.getBrokenRecords(individualRecord, man1Record, man2Record, man3Record, man4Record, man5Record, man6Record, man7Record);

        assertFalse(result.contains("We got 14 CCs in one forage"), "No alert if not breaking/tieing 2-man record");
    }

    @Test
    void test7ManBrokenRecord() {
        DRGroupData groupData = new DRGroupData();

        // Set up six players below individual record, and one player (Pirate7) above individual record
        groupData.put("Foraging", "Pirate1", "Frenetic", new int[]{0, 0, 4}, 4);
        groupData.put("Foraging", "Pirate2", "Frenetic", new int[]{0, 0, 4}, 4);
        groupData.put("Foraging", "Pirate3", "Frenetic", new int[]{0, 0, 4}, 4);
        groupData.put("Foraging", "Pirate4", "Frenetic", new int[]{0, 0, 4}, 4);
        groupData.put("Foraging", "Pirate5", "Frenetic", new int[]{0, 0, 4}, 4);
        groupData.put("Foraging", "Pirate6", "Frenetic", new int[]{0, 0, 4}, 4);
        groupData.put("Foraging", "Pirate7", "Frenetic", new int[]{0, 0, 8}, 8); // Beats individual record

        // Record values from your panel
        int individualRecord = 7;
        int man1Record = 7, man2Record = 14, man3Record = 19, man4Record = 23, man5Record = 26, man6Record = 31, man7Record = 31;

        String result = groupData.getBrokenRecords(individualRecord, man1Record, man2Record, man3Record, man4Record, man5Record, man6Record, man7Record);

        System.out.println(result);

        // Only Pirate7 should trigger the individual record alert
        assertTrue(result.contains("Pirate7 got 8 CCs in one forage which beats the old record of 7."), "Pirate7 should trigger individual record alert");
        // The Group should still trigger the group alert
        assertTrue(result.contains("We got 32 CCs in one forage which beats the old record of 31."), "Group alert should trigger since group score is 4*6+8=32, which is above the 7-man record");
        assertFalse(result.contains("Pirate1 got 4 CCs"), "Pirate1 should NOT trigger individual record alert");

    }

    // ---- DRPersistantData Tests ----

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

    /*
    @Test
    void testScoreAllWithBrokenRecordsAndAutocopy() {
        // Simulate DR Tree Data (from your image)
        DRPersistantData data = new DRPersistantData();

        DRGroupData group3 = new DRGroupData();
        group3.put("Treasure Haul", "Malthael", "Frenetic", new int[]{5, 5, 0}, 10);
        group3.put("Treasure Haul", "Elude", "Frenetic", new int[]{6, 2, 1}, 9);
        group3.put("Treasure Haul", "Ozpin", "Frenetic", new int[]{5, 3, 1}, 9);
        group3.put("Treasure Haul", "Maros", "Frenetic", new int[]{6, 2, 0}, 8);
        group3.put("Treasure Haul", "Bkvi", "Swift", new int[]{5, 2, 0}, 7);
        group3.put("Treasure Haul", "Gummybears", "Swift", new int[]{3, 2, 1}, 6);

        DRGroupData group4 = new DRGroupData();
        group4.put("Carpentry", "Gummybears", "Frenetic", new int[]{0, 8, 14}, 36);
        group4.put("Treasure Haul", "Bkvi", "Frenetic", new int[]{7, 2, 0}, 9);
        group4.put("Treasure Haul", "Maros", "Frenetic", new int[]{8, 4, 1}, 13);
        group4.put("Treasure Haul", "Ozpin", "Swift", new int[]{7, 3, 1}, 7);
        group4.put("Treasure Haul", "Elude", "Brisk", new int[]{1, 3, 1}, 5);
        group4.put("Treasure Haul", "Malthael", "Brisk", new int[]{5, 5, 0}, 5);

        data.add(group3);
        data.add(group4);

        // Set scoring settings (simulate the UI options, use realistic multipliers if needed)
        data.calculateCustomTokenScore(
                0, 0, 0, 0, 0, 0, 1, 2, 3 // Small/Medium/Large Chest multipliers
        );

        // Set records (dummy values for test)
        double individualRecord = 10;  // Example, set according to your options panel
        double man1Record = 10, man2Record = 15, man3Record = 20, man4Record = 25, man5Record = 30, man6Record = 35, man7Record = 40;

        // Broken Records enabled
        String brokenRecords = data.getBrokenRecords(
                individualRecord, man1Record, man2Record, man3Record, man4Record, man5Record, man6Record, man7Record
        );
        System.out.println("Broken Records Output:\n" + brokenRecords);

        // Score All output (simulate copy to clipboard)
        StringBuilder scoreOutput = new StringBuilder();
        for (DRGroupData group : data.getAll()) {
            scoreOutput.append(group.toString(true, false, true)); // Simulate formatting
            scoreOutput.append("\n");
        }
        System.out.println("Score All Output:\n" + scoreOutput);

        // Check: Did Broken Records throw an exception?
        // If so, this test will fail or print a stacktrace.
        // If not, Score All + Autocopy should execute as expected.
        assertNotNull(brokenRecords, "Broken Records should not throw an exception.");
        assertFalse(brokenRecords.contains("Exception"), "Broken records should not throw an exception or print errors.");
    }
     */

}