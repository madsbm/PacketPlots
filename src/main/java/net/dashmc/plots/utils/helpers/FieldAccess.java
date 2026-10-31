package net.dashmc.plots.utils.helpers;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.google.common.primitives.Primitives;

import lombok.Getter;

/**
 * Typed access to a field through reflection. A field that doesn't exist or
 * has the wrong type fails when the FieldAccess is created, which is usually
 * when the class holding it is loaded.
 */
@Getter
public class FieldAccess<T> {
	private final Field field;
	private final Class<T> type;

	public FieldAccess(Class<?> clazz, String fieldName, Class<T> type) {
		this(findField(clazz, fieldName), type);
	}

	public FieldAccess(Field field, Class<T> type) {
		this.field = field;
		this.field.setAccessible(true);
		// reflection boxes primitive values, so e.g. int.class is checked and cast as
		// Integer.class
		this.type = Primitives.wrap(type);

		if (!this.type.isAssignableFrom(Primitives.wrap(field.getType()))) {
			throw new IllegalArgumentException("Field '" + field.getName() + "' is not of type " + type.getName());
		}
	}

	public void set(Object instance, T to) {
		try {
			field.set(instance, to);
		} catch (IllegalAccessException e) {
			// can't happen, as the field was made accessible
			throw new IllegalStateException("Couldn't set field '" + field.getName() + "'", e);
		}
	}

	public T get(Object instance) {
		try {
			return type.cast(field.get(instance));
		} catch (IllegalAccessException e) {
			// can't happen, as the field was made accessible
			throw new IllegalStateException("Couldn't get field '" + field.getName() + "'", e);
		}
	}

	/**
	 * Copies the instance fields the class and its superclasses declare from one
	 * instance of it to another. Fields of the instances' subclasses are left out,
	 * so the instances may be of different subclasses.
	 */
	public static <T> void copyFields(Class<T> clazz, T source, T target) {
		for (Class<?> current = clazz; current != null; current = current.getSuperclass()) {
			for (Field field : current.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()))
					continue;

				FieldAccess<Object> access = new FieldAccess<>(field, Object.class);
				access.set(target, access.get(source));
			}
		}
	}

	private static Field findField(Class<?> clazz, String fieldName) {
		try {
			return clazz.getDeclaredField(fieldName);
		} catch (NoSuchFieldException e) {
			throw new IllegalArgumentException("No field '" + fieldName + "' in " + clazz.getName(), e);
		}
	}
}
