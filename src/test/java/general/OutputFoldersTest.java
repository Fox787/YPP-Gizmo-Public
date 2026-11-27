package main.java.general;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class OutputFoldersTest {
    @Test
    void defaultAndLegacyDataChoicesStayOutsideTheGame(@TempDir Path folder) {
        Path previous = GameLog.dataFolder();
        try {
            GameLog.setDataFolder(null);
            assertEquals(Path.of(System.getProperty("user.home"), "Gizmo").toAbsolutePath(), GameLog.dataFolder());
            GameLog.setDataFolder(GameLog.clientFolder().resolve("exported_data"));
            assertEquals(GameLog.defaultDataFolder(), GameLog.dataFolder());
            GameLog.setDataFolder(folder.resolve("Puzzle Pirates/app/exported_data"));
            assertEquals(GameLog.defaultDataFolder(), GameLog.dataFolder());
            GameLog.setDataFolder(folder.resolve("chosen-data"));
            assertEquals(folder.resolve("chosen-data"), GameLog.dataFolder());
        } finally {
            GameLog.setDataFolder(previous);
        }
    }

    @Test
    void bootyExportRejectsTextCopiesInGameWithoutCreatingThatFolder(@TempDir Path folder) throws Exception {
        Path previous = GameLog.dataFolder();
        Path game = folder.resolve("Puzzle Pirates");
        GameLog.setDataFolder(folder.resolve("data"));
        try {
            BootyDivision division = division();
            BootyExporter.Result result = BootyExporter.export(division, game.resolve("text"));
            assertTrue(Files.isRegularFile(result.jsonl));
            assertNull(result.text);
            assertNotNull(result.textError);
            assertFalse(Files.exists(game));
            assertThrows(IOException.class, () -> RunLog.writeUnobservedRun(game, division, "Pirate", 50));
            assertFalse(Files.exists(game));
        } finally {
            GameLog.setDataFolder(previous);
        }
    }

    @Test
    void temporaryFilesCannotFallBackIntoTheGame(@TempDir Path folder) throws Exception {
        String previous = System.getProperty("java.io.tmpdir");
        try {
            System.setProperty("java.io.tmpdir", folder.resolve("Puzzle Pirates/app").toString());
            Path nativeFiles = GameLog.temporaryFolder("Gizmo-native");
            Path soundFiles = GameLog.temporaryFolder("Gizmo-sounds");
            assertEquals(GameLog.defaultDataFolder().resolve("temp/Gizmo-native"), nativeFiles);
            assertEquals(GameLog.defaultDataFolder().resolve("temp/Gizmo-sounds"), soundFiles);
            assertFalse(Files.exists(folder.resolve("Puzzle Pirates")));
        } finally {
            System.setProperty("java.io.tmpdir", previous);
        }
    }

    private static BootyDivision division() {
        BootyDivision division = new BootyDivision();
        division.game = new GameLog.Info();
        division.game.pirate = "Pirate";
        division.game.ocean = "Emerald";
        division.pirates.add(new BootyDivision.PirateLoot("Pirate"));
        division.chat = new ChatLog.Segment();
        division.chat.openedAt = LocalDateTime.of(2026, 11, 26, 12, 0);
        return division;
    }
}
