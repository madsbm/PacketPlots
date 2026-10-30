package net.dashmc.plots.compatibility;

import java.util.HashSet;

import lombok.Getter;
import net.dashmc.plots.utils.misc.Debug;

public class PluginCompatibility {
	@Getter
	private static HashSet<CompatibilityMode> compatibilities = new HashSet<>();

	public static void setCompatibilities(HashSet<CompatibilityMode> compatibilities) {
		PluginCompatibility.compatibilities = compatibilities;
		compatibilities.forEach((compat) -> compat.activate(true));
	}

	public static void load() {
		compatibilities.clear();

		for (CompatibilityMode mode : CompatibilityMode.values()) {
			boolean shouldActivate = mode.shouldActivate();

			Debug.log(() -> "Activating compat. mode " + mode.toString() + ": " + shouldActivate);
			if (!shouldActivate)
				continue;

			mode.activate();
			compatibilities.add(mode);
		}
	}

	public static boolean isActive(CompatibilityMode mode) {
		return compatibilities.contains(mode);
	}
}
