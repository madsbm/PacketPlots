package net.dashmc.plots.plot;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.dashmc.plots.events.EventDispatcher;
import net.dashmc.plots.plot.data.VirtualBlockRegistry;
import net.dashmc.plots.utils.misc.Debug;

/**
 * What the plugin gives every environment, which the environment passes on
 * through {@link IEnvironment} to what happens in it.
 */
@Getter
@RequiredArgsConstructor
public class EnvironmentContext {
	private final EventDispatcher events;
	private final VirtualBlockRegistry blockHandlers;
	private final Debug debug;
}
