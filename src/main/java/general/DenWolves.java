package main.java.general;

import java.awt.Component;
import java.awt.Container;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Werewolves in Woodhallow's next pack, read from the island's WerewolfIsleSceneObject: its public
 * packWerewolves, the number the Ahoy tab shows as "Next pack: N wolf-kin and M werewolves". Duty Nav
 * charting lowers it. So the count is there without the player opening Ahoy.
 *
 * The island's UI holds the scene object in a field; Gizmo finds that component once by the field's
 * type name (not obfuscated) and keeps it until it goes away. Read on the UI thread, once a second,
 * only while Den stations are open.
 */
final class DenWolves {
	private static final String SCENE_OBJECT = "WerewolfIsleSceneObject";

	private static final ClassValue<List<Field>> SCENE_FIELDS = new ClassValue<>() {
		@Override protected List<Field> computeValue(Class<?> type) {
			List<Field> fields = new ArrayList<>();
			for (Class<?> c = type; c != null && !c.getName().startsWith("java"); c = c.getSuperclass()) {
				for (Field field : c.getDeclaredFields()) {
					if (!Modifier.isStatic(field.getModifiers()) && isDenScene(field.getType()) && field.trySetAccessible())
						fields.add(field);
				}
			}
			return fields;
		}
	};

	private Component holder;
	private Field field;

	/** The next pack's werewolves, or -1 when the Den's scene isn't found. */
	int read(Container game) {
		try {
			Object scene = cached();
			if (scene == null) {
				holder = null;
				field = null;
				scene = find(game, 0);
			}
			return scene == null ? -1 : scene.getClass().getField("packWerewolves").getInt(scene);
		} catch (ReflectiveOperationException | RuntimeException e) {
			holder = null;
			field = null;
			return -1;
		}
	}

	private Object cached() throws IllegalAccessException {
		if (holder == null || !holder.isDisplayable()) return null;
		return field.get(holder);
	}

	private Object find(Component component, int depth) throws IllegalAccessException {
		if (component == null || !component.isDisplayable() || depth > 40) return null;
		for (Field f : SCENE_FIELDS.get(component.getClass())) {
			Object value = f.get(component);
			if (value != null) {
				holder = component;
				field = f;
				return value;
			}
		}
		if (component instanceof Container container) {
			for (Component child : container.getComponents()) {
				Object value = find(child, depth + 1);
				if (value != null) return value;
			}
		}
		return null;
	}

	private static boolean isDenScene(Class<?> type) {
		for (Class<?> c = type; c != null; c = c.getSuperclass())
			if (c.getSimpleName().equals(SCENE_OBJECT)) return true;
		return false;
	}
}
