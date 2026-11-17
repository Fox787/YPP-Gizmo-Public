package main.java.general;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;
import java.util.function.Consumer;

/**
 * Werewolves left to turn back at Woodhallow. Read-only: Gizmo never changes the client's UI,
 * it only shows the count in its own window.
 *
 * The client keeps the count on the island's WerewolfIsleSceneObject (a server-synced object whose
 * public field names are not obfuscated):
 *   packWerewolves  werewolves in the next pack; Duty Nav charting lowers it
 *                   ("Only {1} left in the next wave", "The next wave be all wolf-kin" = 0)
 *   packWolfmen     wolf-kin in the next pack
 *   round           wave number
 *   moonWhiteness   the Duty Nav moon gauge (0..1)
 * The island's own Ahoy tab panel ("Next pack: N wolf-kin and M werewolves") holds that object in a
 * field, so Gizmo finds it by field type, without relying on obfuscated class names.
 */
public final class WolfWatch {

	public static final class Reading {
		public int werewolves, wolfkin, round;
		public float moon;
		/** When this round's stations close (client clock, ms), 0 when stations aren't running. */
		public long roundEndsAt;
		public boolean fraying;

		public String text() {
			return "Werewolves left to convert: " + werewolves + "  (wave " + round + ", " + wolfkin + " wolf-kin)";
		}
	}

	private static final String SCENE_OBJECT = "WerewolfIsleSceneObject";

	private final Window window;
	private final Consumer<Reading> listener;
	private final Timer timer;
	private Component holder;
	private Object scene;

	public WolfWatch(Window window, Consumer<Reading> listener) {
		this.window = window;
		this.listener = listener;
		this.timer = new Timer(1000, e -> tick());
		this.timer.setRepeats(true);
	}

	/** True while a rumble is running (set by the GUI from RumbleClock): a fray hides the island too. */
	private java.util.function.BooleanSupplier inRumble = () -> false;
	public void setInRumble(java.util.function.BooleanSupplier s) { inRumble = s == null ? () -> false : s; }

	/**
	 * An island that can't be found is still the one you're on while its stations are running (you're
	 * in the Duty Nav puzzle), its fray is on, or a rumble is running (you're in the fray's rumble).
	 */
	private boolean stillThere(Object previous) {
		Reading r = read(previous);
		if (r == null) return false;
		return r.roundEndsAt > System.currentTimeMillis() || r.fraying || inRumble.getAsBoolean();
	}

	public void start() { timer.start(); }
	public void stop() { timer.stop(); }

	private void tick() {
		try {
			if (holder == null || !holder.isDisplayable()) {
				// While you're in the Duty Nav puzzle or a fray's rumble the island's panel is out of the
				// window, so it can't be found; keep reading the island object found last time (it stays
				// live). At any other time, an island that can't be found means you've left Woodhallow:
				// drop it, so nothing keeps showing the old island's werewolves.
				Object previous = scene;
				holder = null;
				scene = null;
				if (!findScene(window, 0) && previous != null && stillThere(previous)) scene = previous;
			}
			listener.accept(scene == null ? null : read(scene));
		} catch (RuntimeException ex) {
			holder = null;
			scene = null;
			listener.accept(null);
		}
	}

	/** Depth-first: the first component with a WerewolfIsleSceneObject field. */
	private boolean findScene(Component c, int depth) {
		if (c == null || depth > 40) return false;
		Object s = sceneField(c);
		if (s != null) { holder = c; scene = s; return true; }
		if (c instanceof Container) {
			Container k = (Container) c;
			for (int i = 0; i < k.getComponentCount(); i++) if (findScene(k.getComponent(i), depth + 1)) return true;
		}
		return false;
	}

	private static Object sceneField(Object o) {
		for (Class<?> k = o.getClass(); k != null && k != Object.class; k = k.getSuperclass()) {
			if (k.getName().startsWith("java")) break;
			for (Field f : k.getDeclaredFields()) {
				if (!f.getType().getSimpleName().equals(SCENE_OBJECT)) continue;
				try {
					f.setAccessible(true);
					Object v = f.get(o);
					if (v != null) return v;
				} catch (Exception ignored) { }
			}
		}
		return null;
	}

	static Reading read(Object scene) {
		try {
			Class<?> k = scene.getClass();
			Reading r = new Reading();
			r.werewolves = k.getField("packWerewolves").getInt(scene);
			r.wolfkin = k.getField("packWolfmen").getInt(scene);
			r.round = k.getField("round").getInt(scene);
			r.moon = k.getField("moonWhiteness").getFloat(scene);
			try { r.roundEndsAt = k.getField("roundEndsAt").getLong(scene); } catch (ReflectiveOperationException ignored) { }
			try { r.fraying = k.getField("fraying").getBoolean(scene); } catch (ReflectiveOperationException ignored) { }
			return r;
		} catch (ReflectiveOperationException | RuntimeException e) {
			return null;
		}
	}
}
