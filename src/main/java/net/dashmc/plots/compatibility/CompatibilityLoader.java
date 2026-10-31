package net.dashmc.plots.compatibility;

/**
 * Makes PacketPlots work with another plugin, e.g. an anticheat that would
 * otherwise flag players in the virtualized region. Registered with
 * PluginCompatibility, which activates it if the plugin is installed.
 */
public interface CompatibilityLoader {
	/**
	 * @return whether the plugin it's for is installed
	 */
	boolean shouldActivate();

	/**
	 * Called once, e.g. to register listeners.
	 */
	void activate();
}
