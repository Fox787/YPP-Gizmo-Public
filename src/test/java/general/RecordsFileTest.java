package test.java.general;

import main.java.general.RecordsFile;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class RecordsFileTest {

    @Test
    void readsJson() {
        Map<String, String> m = RecordsFile.parse("{\n  \"ccforage7\": 31,\n  \"SingleDR7\": 12.5\n}\n");
        assertEquals("31", m.get("ccforage7"));
        assertEquals("12.5", m.get("singledr7"));
        assertEquals(2, m.size());
    }

    @Test
    void readsLooseFormat() {
        Map<String, String> m = RecordsFile.parse("# shared records\r\nlairics12 = 40\r\nlairfray: 9, wolvesfurrballs4: 3\r\n");
        assertEquals("40", m.get("lairics12"));
        assertEquals("9", m.get("lairfray"));
        assertEquals("3", m.get("wolvesfurrballs4"));
    }

    @Test
    void rejectsNonNumbers() {
        assertThrows(IllegalArgumentException.class, () -> RecordsFile.parse("lairfray: lots"));
    }

    @Test
    void writeThenReadRoundTrips() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ccentry7", "120");
        m.put("fullentry7", "55.25");
        assertEquals(m, RecordsFile.parse(RecordsFile.write(m)));
    }
}
