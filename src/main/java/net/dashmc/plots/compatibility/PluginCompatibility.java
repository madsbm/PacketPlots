package net.dashmc.plots.compatibility;

import java.util.HashSet;

import org.bukkit.plugin.Plugin;

import lombok.Getter;
import net.dashmc.plots.utils.CuboidRegion;
import net.dashmc.plots.utils.misc.Debug;

public class PluginCompatibility {
	@Getter
	private static HashSet<CompatibilityMode> compatibilities = new HashSet<>();

	public static void setCompatibilities(HashSet<CompatibilityMode> compatibilities, Plugin plugin,
			CuboidRegion acDisabledRegion) {
		PluginCompatibility.compatibilities = compatibilities;
		compatibilities.forEach((compat) -> compat.activate(plugin, acDisabledRegion, true));
	}

	/**
	 * @param plugin           the plugin to register listeners for
	 * @param acDisabledRegion where anticheats are disabled
	 */
	public static void load(Plugin plugin, CuboidRegion acDisabledRegion) {
		compatibilities.clear();

		for (CompatibilityMode mode : CompatibilityMode.values()) {
			boolean shouldActivate = mode.shouldActivate();

			Debug.log(() -> "Activating compat. mode " + mode.toString() + ": " + shouldActivate);
			if (!shouldActivate)
				continue;

			mode.activate(plugin, acDisabledRegion, false);
			compatibilities.add(mode);
		}
	}

	public static boolean isActive(CompatibilityMode mode) {
		return compatibilities.contains(mode);
	}
}
