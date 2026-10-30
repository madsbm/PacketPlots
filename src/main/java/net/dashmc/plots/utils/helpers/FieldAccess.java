package net.dashmc.plots.utils.helpers;

import java.lang.reflect.Field;

import com.google.common.primitives.Primitives;

import lombok.Getter;

@Getter
public class FieldAccess<T> {
	private final Field field;
	private final Class<T> type;

	public FieldAccess(Class<?> clazz, String fieldName, Class<T> type) throws NoSuchFieldException, SecurityException {
		this(clazz.getDeclaredField(fieldName), type);
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

	public void set(Object instance, T to) throws IllegalArgumentException, IllegalAccessException {
		field.set(instance, to);
	}

	public T get(Object instance) throws IllegalAccessException {
		return type.cast(field.get(instance));
	}
}
