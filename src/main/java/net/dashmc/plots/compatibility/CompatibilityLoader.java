package net.dashmc.plots.compatibility;

import org.bukkit.plugin.Plugin;

import net.dashmc.plots.utils.CuboidRegion;

public abstract class CompatibilityLoader {
	public final CompatibilityLoader INSTANCE;

	public CompatibilityLoader() {
		this.INSTANCE = this;
	}

	public abstract boolean shouldActivate();

	/**
	 * @param plugin           the plugin to register listeners for
	 * @param acDisabledRegion where anticheats are disabled
	 * @param forced           whether it's activated even if it shouldn't be
	 */
	public abstract void activate(Plugin plugin, CuboidRegion acDisabledRegion, boolean forced);
}
