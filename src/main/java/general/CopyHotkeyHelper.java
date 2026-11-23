package main.java.general;

import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Standalone helper. Reports next-piece and paste shortcuts, with no typed text. */
public final class CopyHotkeyHelper implements NativeKeyListener {
	private boolean held;
	private boolean pasteHeld;

	@Override public void nativeKeyPressed(NativeKeyEvent event) {
		if (!pasteHeld && isPasteChord(event.getKeyCode(), event.getModifiers())) {
			pasteHeld = true;
			System.out.println("PASTE");
			System.out.flush();
		}
		if (event.getKeyCode() != NativeKeyEvent.VC_P) return;
		if (!held && isCopyChord(event.getKeyCode(), event.getModifiers())) {
			held = true;
			System.out.println("NEXT");
			System.out.flush();
		}
	}

	@Override public void nativeKeyReleased(NativeKeyEvent event) {
		if (event.getKeyCode() == NativeKeyEvent.VC_P) held = false;
		if (event.getKeyCode() == NativeKeyEvent.VC_V || event.getKeyCode() == NativeKeyEvent.VC_INSERT) pasteHeld = false;
	}

	static boolean isCopyChord(int code, int modifiers) {
		int extra = NativeInputEvent.SHIFT_MASK | NativeInputEvent.ALT_MASK | NativeInputEvent.META_MASK;
		return code == NativeKeyEvent.VC_P && (modifiers & NativeInputEvent.CTRL_MASK) != 0 && (modifiers & extra) == 0;
	}

	static boolean isPasteChord(int code, int modifiers) {
		int extra = NativeInputEvent.ALT_MASK | NativeInputEvent.META_MASK;
		if ((modifiers & extra) != 0) return false;
		boolean ctrl = (modifiers & NativeInputEvent.CTRL_MASK) != 0;
		boolean shift = (modifiers & NativeInputEvent.SHIFT_MASK) != 0;
		return code == NativeKeyEvent.VC_V && ctrl || code == NativeKeyEvent.VC_INSERT && shift && !ctrl;
	}

	public static void main(String[] args) {
		// Do not even load the X11 native library in a Wayland session.
		if ("wayland".equalsIgnoreCase(System.getenv("XDG_SESSION_TYPE"))
				|| System.getenv("WAYLAND_DISPLAY") != null) return;
		Logger logger = Logger.getLogger(GlobalScreen.class.getPackageName());
		logger.setLevel(Level.OFF);
		logger.setUseParentHandlers(false);
		try {
			GlobalScreen.registerNativeHook();
			GlobalScreen.addNativeKeyListener(new CopyHotkeyHelper());
			System.out.println("READY");
			System.out.flush();
			// The pipe closes if the game exits, so the helper cannot remain orphaned.
			while (System.in.read() != -1) { }
		} catch (Exception | LinkageError ignored) {
			// The parent automatically keeps using the chat log.
		} finally {
			try { GlobalScreen.unregisterNativeHook(); } catch (Exception | LinkageError ignored) { }
		}
	}
}
