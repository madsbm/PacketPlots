package net.dashmc.plots.utils.helpers;

import java.lang.reflect.Field;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
@Getter
public class FieldAccess<T> {
	private final Field field;
	private final Class<T> type;

	public FieldAccess(Class<?> clazz, String fieldName, Class<T> type) throws NoSuchFieldException, SecurityException {
		this.field = clazz.getDeclaredField(fieldName);
		this.field.setAccessible(true);
		this.type = type;

		if (!type.isAssignableFrom(field.getType())) {
			throw new IllegalArgumentException("Field '" + fieldName + "' is not of type " + type.getName());
		}

	}

	public void set(Object instance, T to) throws IllegalArgumentException, IllegalAccessException {
		field.set(instance, to);
	}

	public T get(Object instance) throws IllegalAccessException {
		return type.cast(field.get(instance));
	}
}
