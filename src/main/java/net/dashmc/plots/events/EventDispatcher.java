package net.dashmc.plots.events;

import org.bukkit.event.Event;

/**
 * Calls events on their listeners. On the server, this is the plugin manager.
 */
@FunctionalInterface
public interface EventDispatcher {
	void call(Event event);
}
