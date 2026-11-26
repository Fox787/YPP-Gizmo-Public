package main.java.general;

import javax.swing.*;
import java.awt.event.WindowEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.prefs.Preferences;

/** An isolated, undisplayed Gizmo window with temporary settings and worker cleanup. */
final class GuiTestFixture implements AutoCloseable {
    private final Preferences previous = DRGizmoMain.prefs;
    final Preferences prefs = Preferences.userRoot().node("gizmo-gui-tests-" + UUID.randomUUID());
    private JFrame game;
    private DRGizmoGUI gui;

    GuiTestFixture(Path folder) {
        for (String key : new String[]{"datafolder", "chatlogfolder", "bootytextfolder"}) prefs.put(key, folder.toString());
        DRGizmoMain.prefs = prefs;
    }

    DRGizmoGUI open() {
        game = new JFrame();
        return gui = new DRGizmoGUI(game);
    }

    @Override public void close() throws Exception {
        try {
            SwingUtilities.invokeAndWait(() -> {
                if (gui != null) {
                    gui.dispatchEvent(new WindowEvent(gui, WindowEvent.WINDOW_CLOSING));
                    field(gui, "activityTracker", ActivityTracker.class).stop();
                    // Finish any cache write, then prevent queued download completions from
                    // recreating this fixture's temporary folder after JUnit removes it.
                    VoyageRecords records = field(gui, "voyageRecords", VoyageRecords.class);
                    if (records != null) synchronized (records) {
                        try {
                            var owner = DRGizmoGUI.class.getDeclaredField("voyageRecords");
                            owner.setAccessible(true);
                            owner.set(gui, null);
                        } catch (ReflectiveOperationException error) {
                            throw new AssertionError(error);
                        }
                    }
                    gui.dispose();
                }
                if (game != null) game.dispose();
            });
            // A download/apply thread may already have been dispatched before the EDT
            // cleared its owner. Let it exit before JUnit deletes the data directory.
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
            for (Thread worker : Thread.getAllStackTraces().keySet()) {
                if (!worker.getName().equals("Gizmo record update")) continue;
                long remaining = java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                if (remaining <= 0) break;
                worker.join(remaining);
            }
        } finally {
            DRGizmoMain.prefs = previous;
            GameLog.setDataFolder(null);
            prefs.removeNode();
        }
    }

    static JTextField recordField(DRGizmoGUI gui, String name) {
        for (Object value : field(gui, "recordValueFields", List.class)) {
            JTextField text = (JTextField) value;
            if (name.equals(text.getAccessibleContext().getAccessibleName())) return text;
        }
        throw new AssertionError("Missing record field: " + name);
    }

    static <T> T field(Object target, String name, Class<T> type) {
        try {
            var field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return type.cast(field.get(target));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
