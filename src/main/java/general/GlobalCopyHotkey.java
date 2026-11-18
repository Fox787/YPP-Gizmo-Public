package main.java.general;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;

/** Runs native keyboard detection in an isolated JVM, never on the game's UI thread. */
final class GlobalCopyHotkey {
	private final Runnable advance;
	private final Consumer<String> status;
	private volatile long generation;
	private boolean active;
	private Process helper;

	GlobalCopyHotkey(Runnable advance, Consumer<String> status) {
		this.advance = advance;
		this.status = status;
	}

	synchronized void setActive(boolean enabled) {
		if (enabled == active) return;
		active = enabled;
		long token = ++generation;
		if (helper != null) { helper.destroy(); helper = null; }
		if (!enabled) return;
		Thread worker = new Thread(() -> listen(token), "Gizmo global copy key");
		worker.setDaemon(true);
		worker.start();
	}

	private void listen(long token) {
		Process process = null;
		try {
			String executable = System.getProperty("os.name").toLowerCase(Locale.ROOT).startsWith("windows") ? "javaw.exe" : "java";
			String java = Path.of(System.getProperty("java.home"), "bin", executable).toString();
			String classes = Path.of(GlobalCopyHotkey.class.getProtectionDomain().getCodeSource().getLocation().toURI()).toString();
			String helperClass = GlobalCopyHotkey.class.getPackageName() + ".CopyHotkeyHelper";
			process = new ProcessBuilder(java, "-cp", classes, helperClass)
					.redirectError(ProcessBuilder.Redirect.DISCARD).start();
			synchronized (this) {
				if (token != generation) { process.destroy(); return; }
				helper = process;
			}
			try (BufferedReader lines = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				for (String line; (line = lines.readLine()) != null;) {
					if (line.equals("READY")) dispatch(token, () -> status.accept("Global Ctrl+P ready; chat-log fallback remains enabled"));
					else if (line.equals("NEXT")) dispatch(token, advance);
				}
			}
		} catch (Exception | LinkageError ignored) {
			// Unsupported OS, missing permission, or helper failure: chat confirmation still works.
		} finally {
			if (process != null) process.destroy();
			dispatch(token, () -> status.accept("Global Ctrl+P unavailable; using chat-log confirmation"));
		}
	}

	private void dispatch(long token, Runnable action) {
		SwingUtilities.invokeLater(() -> { if (token == generation) action.run(); });
	}
}
