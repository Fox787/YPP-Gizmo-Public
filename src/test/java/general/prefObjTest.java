package test.java.general;

import main.java.prefobj.PrefObj;
import org.junit.jupiter.api.*;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.io.IOException;

class PrefObjTest {

    private Preferences prefs;

    @BeforeEach
    void setUp() {
        prefs = Preferences.userRoot().node("testNode");
    }

    @Test
    void testPutAndGetObject() throws IOException, ClassNotFoundException, BackingStoreException {
        String key = "testKey";
        String value = "testValue";
        PrefObj.putObject(prefs, key, value);
        Object retrieved = PrefObj.getObject(prefs, key);
        Assertions.assertEquals(value, retrieved);
    }

}