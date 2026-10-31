package net.dashmc.plots.compatibility;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import lombok.RequiredArgsConstructor;
import net.dashmc.plots.utils.misc.Debug;

/**
 * The compatibilities with other plugins, of which the ones for installed
 * plugins are activated. Other plugins may register their own, and load them.
 */
@RequiredArgsConstructor
public class PluginCompatibility {
	private final Debug debug;
	private final List<CompatibilityLoader> registered = new ArrayList<>();
	private final Set<CompatibilityLoader> active = new LinkedHashSet<>();

	public void register(CompatibilityLoader compatibility) {
		registered.add(compatibility);
	}

	/**
	 * Activates the registered compatibilities whose plugins are installed, and
	 * that aren't active yet.
	 */
	public void load() {
		for (CompatibilityLoader compatibility : registered) {
			if (active.contains(compatibility))
				continue;

			boolean shouldActivate = compatibility.shouldActivate();
			debug.log(() -> "Activating compat. mode " + compatibility.getClass().getSimpleName() + ": "
					+ shouldActivate);
			if (!shouldActivate)
				continue;

			compatibility.activate();
			active.add(compatibility);
		}
	}

	public boolean isActive(Class<? extends CompatibilityLoader> type) {
		for (CompatibilityLoader compatibility : active) {
			if (type.isInstance(compatibility))
				return true;
		}

		return false;
	}

	public Collection<CompatibilityLoader> getActive() {
		return Collections.unmodifiableCollection(active);
	}
}
