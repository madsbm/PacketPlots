package net.dashmc.plots.compatibility;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

import org.bukkit.plugin.Plugin;

import lombok.Getter;
import net.dashmc.plots.compatibility.compatibilities.ChunkMapSendCompatibility;
import net.dashmc.plots.compatibility.compatibilities.HDBCompatibility;
import net.dashmc.plots.compatibility.compatibilities.NCPCompatibility;
import net.dashmc.plots.compatibility.compatibilities.VulcanCompatibility;
import net.dashmc.plots.utils.CuboidRegion;

public enum CompatibilityMode {
	VULCAN(new VulcanCompatibility()),
	FORCE_CHUNKMAP_SEND(new ChunkMapSendCompatibility()),
	HEAD_DATABASE(new HDBCompatibility()),
	NO_CHEAT_PLUS(new NCPCompatibility()),
	UNKNOWN();

	@Getter
	private final List<CompatibilityLoader> loaders;

	CompatibilityMode(CompatibilityLoader... loaders) {
		this.loaders = new LinkedList<>(Arrays.asList(loaders));
	}

	public boolean shouldActivate() {
		for (CompatibilityLoader loader : loaders) {
			if (loader.shouldActivate())
				return true;
		}

		return false;
	}

	public void activate(Plugin plugin, CuboidRegion acDisabledRegion, boolean forced) {
		for (CompatibilityLoader loader : loaders) {
			if (forced || loader.shouldActivate())
				loader.activate(plugin, acDisabledRegion, forced);
		}
	}
}
