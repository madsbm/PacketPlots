package net.dashmc.plots.plot;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.dashmc.plots.events.EventDispatcher;
import net.dashmc.plots.plot.data.VirtualBlockRegistry;
import net.dashmc.plots.utils.misc.Debug;

/**
 * What the plugin gives every CuboidEnvironment for its own use: what calls the
 * events of its block bag, how its blocks behave and where it logs. The
 * environment doesn't pass these on; whatever else needs them gets them
 * itself.
 */
@Getter
@RequiredArgsConstructor
public class EnvironmentContext {
	private final EventDispatcher events;
	private final VirtualBlockRegistry blockHandlers;
	private final Debug debug;
}
