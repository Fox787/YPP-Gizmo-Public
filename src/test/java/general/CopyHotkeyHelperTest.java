package test.java.general;

import com.github.kwhat.jnativehook.NativeInputEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import main.java.general.CopyHotkeyHelper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CopyHotkeyHelperTest {
	@Test
	void recognisesPasteShortcutsWithoutConfusingOtherKeys() {
		assertTrue(CopyHotkeyHelper.isPasteChord(NativeKeyEvent.VC_V, NativeInputEvent.CTRL_L_MASK));
		assertTrue(CopyHotkeyHelper.isPasteChord(NativeKeyEvent.VC_V, NativeInputEvent.CTRL_R_MASK | NativeInputEvent.SHIFT_L_MASK));
		assertTrue(CopyHotkeyHelper.isPasteChord(NativeKeyEvent.VC_INSERT, NativeInputEvent.SHIFT_R_MASK));
		assertFalse(CopyHotkeyHelper.isPasteChord(NativeKeyEvent.VC_V, 0));
		assertFalse(CopyHotkeyHelper.isPasteChord(NativeKeyEvent.VC_P, NativeInputEvent.CTRL_L_MASK));
		assertFalse(CopyHotkeyHelper.isPasteChord(NativeKeyEvent.VC_V, NativeInputEvent.CTRL_L_MASK | NativeInputEvent.ALT_L_MASK));
	}

	@Test
	void recognisesEitherControlKeyButNotOtherShortcuts() {
		assertTrue(CopyHotkeyHelper.isCopyChord(NativeKeyEvent.VC_P, NativeInputEvent.CTRL_L_MASK));
		assertTrue(CopyHotkeyHelper.isCopyChord(NativeKeyEvent.VC_P, NativeInputEvent.CTRL_R_MASK | NativeInputEvent.CAPS_LOCK_MASK));
		assertFalse(CopyHotkeyHelper.isCopyChord(NativeKeyEvent.VC_P, 0));
		assertFalse(CopyHotkeyHelper.isCopyChord(NativeKeyEvent.VC_V, NativeInputEvent.CTRL_L_MASK));
		assertFalse(CopyHotkeyHelper.isCopyChord(NativeKeyEvent.VC_P, NativeInputEvent.CTRL_L_MASK | NativeInputEvent.SHIFT_L_MASK));
	}
}
