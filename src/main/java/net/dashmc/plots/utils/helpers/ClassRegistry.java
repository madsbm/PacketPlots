package net.dashmc.plots.utils.helpers;

import java.util.HashMap;

/**
 * Handlers, by the class they handle. A handler also handles the subclasses
 * of its class, unless they have a handler of their own.
 */
public class ClassRegistry<H> {
	private final HashMap<Class<?>, H> handlers = new HashMap<>();

	public void register(Class<?> type, H handler) {
		handlers.put(type, handler);
	}

	/**
	 * @return the handler of the class, or else of its closest superclass with
	 *         one, or null if none has one
	 */
	public H get(Class<?> type) {
		for (Class<?> current = type; current != null; current = current.getSuperclass()) {
			H handler = handlers.get(current);
			if (handler != null)
				return handler;
		}

		return null;
	}
}
